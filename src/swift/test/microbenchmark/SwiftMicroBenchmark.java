package swift.test.microbenchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import swift.client.SwiftImpl;
import swift.crdt.CRDTIdentifier;
import swift.crdt.interfaces.CachePolicy;
import swift.crdt.interfaces.IsolationLevel;
import swift.crdt.interfaces.Swift;
import swift.dc.DCConstants;
import swift.dc.DCSequencerServer;
import swift.dc.DCServer;
import swift.test.microbenchmark.interfaces.MicroBenchmarkWorker;
import swift.test.microbenchmark.interfaces.ResultHandler;
import swift.test.microbenchmark.interfaces.WorkerManager;
import sys.Sys;

public class SwiftMicroBenchmark implements WorkerManager {

    private boolean initialize;
    private Semaphore stopSemaphore;
    private Random random;
    private double updateRatio;
    private int numObjects, maxTxSize, numWorkers, executionTime, runs;
    private Map<String, List<ResultHandler>> results;

    private static final int /* valueLength = 20, valueLengthDeviation = 0 , */randomSeed = 1;

    public static final String TABLE_NAME = "BENCHMARK";
    private static final int ESTIMATED_THGPT_MILLIS = 1;
    private static String serverLocation = "localhost";
    private static int portId = 2001;
    private static Logger logger = Logger.getLogger("swift.benchmark");
    private static CachePolicy cachePolicy;
    private static IsolationLevel isolationLevel;

    public SwiftMicroBenchmark(boolean initialize, int numObjects, int maxTxSize, int numWorkers, double updateRatio,
            int executionTime, int runs) {
        this.initialize = initialize;
        this.random = new Random(randomSeed);
        this.numObjects = numObjects;
        this.maxTxSize = maxTxSize;
        this.numWorkers = numWorkers;
        this.updateRatio = updateRatio;
        this.executionTime = executionTime;
        this.runs = runs;
        this.results = new HashMap<String, List<ResultHandler>>();
    }

    public static void main(String[] args) {

        int sampleSize, maxTxSize, execTime, numRuns, numWorkers;
        double updateRatio;
        boolean populate = false;
        if (args.length == 9) {
            if (args[8].equals("-p"))
                populate = true;
        }

        if (args.length < 8 || args.length > 9) {
            System.out
                    .println("[SAMPLE SIZE] [MAX TX SIZE] [NUM WORKERS] [UPDATE RATIO] [EXECUTION TIME SECONDS] [NUM RUNS] [CACHE POLICY] [ISOLATION LEVEL]");
            return;
        } else {
            sampleSize = Integer.parseInt(args[0]);
            maxTxSize = Integer.parseInt(args[1]);
            numWorkers = Integer.parseInt(args[2]);
            updateRatio = Double.parseDouble(args[3]);
            execTime = Integer.parseInt(args[4]);
            numRuns = Integer.parseInt(args[5]);
            cachePolicy = CachePolicy.valueOf(args[6]);
            isolationLevel = IsolationLevel.valueOf(args[7]);
        }
        Sys.init();
        logger.info("SAMPLE SIZE " + sampleSize + " MAX_TX_SIZE " + maxTxSize + " NUM_WORKERS " + numWorkers
                + " UPDATE_RATIO " + updateRatio + " EXECUTION_TIME_SECONDS " + execTime + " NUM_RUNS " + numRuns);
        SwiftMicroBenchmark mb = new SwiftMicroBenchmark(populate, sampleSize, maxTxSize, numWorkers, updateRatio,
                1000 * execTime, numRuns);
        try {
            mb.doIt();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void doIt() throws InterruptedException {
        CRDTIdentifier[] identifiers = BenchUtil.generateOpIdentifiers(numObjects);
        Swift client = BenchUtil.getNewSwiftInterface(serverLocation, DCConstants.SURROGATE_PORT);
        if (initialize) {
            stopSemaphore = new Semaphore(-1);
            MicroBenchmarkWorker initializer = new SwiftInitializerWorker(this, identifiers, random, client);
            new Thread(initializer).start();
            try {
                logger.info("START POPULATOR");
                stopSemaphore.acquire();
                logger.info("END POPULATOR");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (int r = 0; r < runs; r++) {
            logger.info("WARMING UP FOR " + executionTime / 2 + "ms");
            executeWorkers("WARM_UP", numWorkers, identifiers, executionTime / 2, client);
            logger.info("START");
            executeWorkers("Worker", numWorkers, identifiers, executionTime, client);
            logger.info("END");

        }
        printResults();

    }

    private void executeWorkers(String workersName, int numWorkers, CRDTIdentifier[] identifiers, long executionTime,
            Swift client) throws InterruptedException {
        List<MicroBenchmarkWorker> workers = new ArrayList<MicroBenchmarkWorker>();
        stopSemaphore = new Semaphore(-numWorkers + 1);
        for (int i = 0; i < numWorkers; i++) {
            // client = BenchUtil.getNewSwiftInterface(serverLocation,
            // DCConstants.SURROGATE_PORT);
            SwiftExecutorWorker worker = new SwiftExecutorWorker(this, workersName + i, identifiers, updateRatio,
                    random, client, maxTxSize, cachePolicy, isolationLevel);
            new Thread(worker).start();
            workers.add(worker);

        }
        stopSemaphore.acquire();
        Thread.sleep(executionTime);
        stopSemaphore = new Semaphore(-numWorkers + 1);
        for (MicroBenchmarkWorker w : workers) {
            w.stop();

        }
        stopSemaphore.acquire();
        if (!workersName.equals("WARM_UP"))
            for (MicroBenchmarkWorker w : workers) {
                System.out.println(w.getRawData().RawData());
            }
    }

    @Override
    public void onWorkerStart(MicroBenchmarkWorker worker) {
        stopSemaphore.release();
        // System.out.println(worker.getWorkerID() + " STARTED");
    }

    @Override
    public void onWorkerFinish(MicroBenchmarkWorker worker) {
        if (worker.getWorkerID().contains("WARM_UP") || worker.getWorkerID().equals("INITIALIZER")) {
            stopSemaphore.release();
            return;
        }

        // System.out.println(worker.getResults().toString());
        List<ResultHandler> workerRuns = results.get(worker.getWorkerID());
        if (workerRuns == null) {
            workerRuns = new ArrayList<ResultHandler>();
            results.put(worker.getWorkerID(), workerRuns);
        }
        workerRuns.add(worker.getResults());
        // System.out.println(worker.getWorkerID() + " STOPPED");
        stopSemaphore.release();

    }

    // TODO: Need refactoring to become generic
    private void printResults() {

        double totalExecutedTransactions = 0;
        double totalWriteOps = 0;
        double totalReadOps = 0;

        for (Entry<String, List<ResultHandler>> workerResults : results.entrySet()) {
            double numExecutedTransactions = 0;
            double writeOps = 0;
            double readOps = 0;
            int runCounter = 1;
            for (ResultHandler run : workerResults.getValue()) {
                if (run instanceof SwiftOperationExecutorResultHandler) {
                    System.out.println("RUN " + runCounter++);
                    SwiftOperationExecutorResultHandler wr = (SwiftOperationExecutorResultHandler) run;
                    System.out.println(run.toString());
                    numExecutedTransactions += wr.getNumExecutedTransactions() / workerResults.getValue().size();
                    writeOps += wr.getWriteOps() / workerResults.getValue().size();
                    readOps += wr.getReadOps() / workerResults.getValue().size();
                }
            }

            totalExecutedTransactions += numExecutedTransactions;
            totalWriteOps += writeOps;
            totalReadOps += readOps;
        }
        String results = "Mean Total Results:\n";
        results += "Executed Transactions:\t" + totalExecutedTransactions + " W:\t" + totalWriteOps + "\tR:\t"
                + totalReadOps + "\n";
        results += "Throughput(Tx/min):\t" + totalExecutedTransactions / ((executionTime / 1000) / 60d) + "\n";
        System.out.println(results);
    }

    @Override
    public RawDataCollector getNewRawDataCollector(String workerName) {
        int initialSize = (int) (maxTxSize * (1 - updateRatio) + 1) * executionTime * ESTIMATED_THGPT_MILLIS;
        return new RawDataCollector(initialSize, workerName);
    }

}