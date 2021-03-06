/*****************************************************************************
 * Copyright 2011-2012 INRIA
 * Copyright 2011-2012 Universidade Nova de Lisboa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *****************************************************************************/
package swift.microbenchmark;

import java.nio.ByteBuffer;
import java.util.Random;

import swift.microbenchmark.interfaces.MicroBenchmarkWorker;
import swift.microbenchmark.interfaces.ResultHandler;
import swift.microbenchmark.interfaces.WorkerManager;
import sys.net.impl.KryoSerializer;

import com.basho.riak.client.IRiakClient;
import com.basho.riak.client.IRiakObject;
import com.basho.riak.client.RiakRetryFailedException;
import com.basho.riak.client.cap.UnresolvedConflictException;
import com.basho.riak.client.convert.ConversionException;
import com.esotericsoftware.kryo.Kryo;

public class RiakExecutorWorker implements MicroBenchmarkWorker {

    private WorkerManager manager;
    private IRiakClient clientServer;
    private Integer[] identifiers;
    private double updateRatio;
    private String workerID;
    private int maxTxSize;
    private Random random;
    private boolean stop;
    private Kryo kryo;

    protected long startTime, endTime;
    protected int numExecutedTransactions, writeOps, readOps;
    private RawDataCollector rawData;
    private String outputDir;

    public RiakExecutorWorker(WorkerManager manager, String workerID, Integer[] identifiers, double updateRatio,
            Random random, IRiakClient clientServer, int maxTxSize, int runCounter, String outputDir) {
        this.manager = manager;
        this.identifiers = identifiers;
        this.updateRatio = updateRatio;
        this.workerID = workerID;
        this.random = random;
        this.clientServer = clientServer;
        this.maxTxSize = maxTxSize;
        this.outputDir = outputDir;
        kryo = new Kryo();
        rawData = manager.getNewRawDataCollector(workerID, runCounter, outputDir);

    }

    @Override
    public void run() {
        manager.onWorkerStart(this);
        startTime = System.currentTimeMillis();

        /*
         * NOTE NOTE NOTE. Adapted code to work with Kryo V2. Not Tested!!!!!
         */
        KryoSerializer serializer = new KryoSerializer();

        while (!stop) {
            try {
                OpType operationType = (random.nextDouble() > updateRatio) ? OpType.READ_ONLY : OpType.UPDATE;
                ByteBuffer bb = ByteBuffer.allocate(1024);
                switch (operationType) {

                case UPDATE: {
                    long txStartTime = System.nanoTime();
                    int randomIndex = (int) Math.floor(random.nextDouble() * identifiers.length);
                    IRiakObject riakObj = clientServer.fetchBucket(RiakMicroBenchmark.TABLE_NAME).execute()
                            .fetch("object" + randomIndex).execute();

                    Integer objValue = serializer.readObject(riakObj.getValue());

                    if (random.nextDouble() > 0.5) {
                        objValue += 10;
                    } else {
                        objValue -= 10;
                    }

                    byte[] data = serializer.writeObject(objValue);
                    riakObj.setValue(data);

                    clientServer.fetchBucket(RiakMicroBenchmark.TABLE_NAME).execute().store(riakObj);
                    long txEndTime = System.nanoTime();
                    rawData.registerOperation(txEndTime - txStartTime, 1, 1, txStartTime);
                    writeOps++;
                    break;
                }
                case READ_ONLY: {
                    long txStartTime = System.nanoTime();
                    int txSize = (int) Math.ceil(random.nextDouble() * maxTxSize);
                    for (int i = 0; i < txSize; i++) {
                        int randomIndex = (int) Math.floor(Math.random() * identifiers.length);
                        IRiakObject riakObj = clientServer.fetchBucket(RiakMicroBenchmark.TABLE_NAME).execute()
                                .fetch("object" + randomIndex).execute();

                        Integer objValue = serializer.readObject(riakObj.getValue());
                        readOps++;
                    }
                    long txEndTime = System.nanoTime();
                    rawData.registerOperation(txEndTime - txStartTime, 0, txSize, txStartTime);
                    break;
                }
                default:
                    break;
                }
                bb.clear();
                numExecutedTransactions++;

            } catch (UnresolvedConflictException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (RiakRetryFailedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ConversionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        endTime = System.currentTimeMillis();
        manager.onWorkerFinish(this);

    }

    @Override
    public ResultHandler getResults() {
        return new RiakOperationExecutorResultHandler(this);
    }

    @Override
    public void stop() {
        stop = true;
        rawData.rawDataToFile();
    }

    @Override
    public String getWorkerID() {
        return workerID;
    }

    @Override
    public RawDataCollector getRawData() {
        return rawData;
    }
}

class RiakOperationExecutorResultHandler implements ResultHandler {

    private double executionTime;
    private String workerID;
    private int numExecutedTransactions, writeOps, readOps;
    private RawDataCollector rawData;

    public RiakOperationExecutorResultHandler(RiakExecutorWorker worker) {
        executionTime = (worker.endTime - worker.startTime);
        workerID = worker.getWorkerID();
        readOps = worker.readOps;
        writeOps = worker.writeOps;
        numExecutedTransactions = (int) worker.numExecutedTransactions;
        this.rawData = worker.getRawData();
    }

    @Override
    public String toString() {
        String results = workerID + " Results:\n";
        results += "Execution Time:\t" + executionTime + "ms" + "\n";
        results += "Executed Transactions:\t" + numExecutedTransactions + " W:\t" + writeOps + "\tR:\t" + readOps
                + "\n";
        results += "Throughput(Tx/min):\t" + numExecutedTransactions / ((executionTime) / (1000 * 60d)) + "\n";
        results += "Throughput(Tx/sec):\t" + numExecutedTransactions / ((executionTime) / (1000d)) + "\n";
        return results;
    }

    @Override
    public double getExecutionTime() {
        return executionTime;
    }

    @Override
    public int getNumExecutedTransactions() {
        return numExecutedTransactions;
    }

    @Override
    public int getWriteOps() {
        return writeOps;
    }

    @Override
    public int getReadOps() {
        return readOps;
    }

    @Override
    public String getWorkerID() {
        // TODO Auto-generated method stub
        return workerID;
    }

    /*
     * @Override public String getRawResults() { return rawData.RawData(); }
     */
}
