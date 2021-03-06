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
package swift.dc;

import static swift.clocks.CausalityClock.CMP_CLOCK.CMP_DOMINATES;
import static swift.clocks.CausalityClock.CMP_CLOCK.CMP_EQUALS;
import static swift.dc.DCConstants.DATABASE_CLASS;
import static sys.net.api.Networking.TransportProvider.DEFAULT;
import static sys.net.api.Networking.TransportProvider.INPROC;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import swift.clocks.CausalityClock;
import swift.clocks.CausalityClock.CMP_CLOCK;
import swift.clocks.ClockFactory;
import swift.clocks.IncrementalTimestampGenerator;
import swift.clocks.Timestamp;
import swift.crdt.core.CRDTObjectUpdatesGroup;
import swift.dc.db.DCNodeDatabase;
import swift.proto.CommitTSReply;
import swift.proto.CommitTSRequest;
import swift.proto.GenerateDCTimestampReply;
import swift.proto.GenerateDCTimestampRequest;
import swift.proto.LatestKnownClockReply;
import swift.proto.LatestKnownClockRequest;
import swift.proto.PingReply;
import swift.proto.PingRequest;
import swift.proto.SeqCommitUpdatesReply;
import swift.proto.SeqCommitUpdatesRequest;
import swift.proto.SwiftProtocolHandler;
import sys.net.api.Endpoint;
import sys.net.api.Networking;
import sys.net.api.Networking.TransportProvider;
import sys.net.api.rpc.RpcEndpoint;
import sys.net.api.rpc.RpcHandle;
import sys.utils.Args;
import sys.utils.FifoQueue;
import sys.utils.Threading;

/**
 * 
 * @author nmp
 * 
 */
public class DCSequencerServer extends SwiftProtocolHandler {
    private static Logger logger = Logger.getLogger(DCSequencerServer.class.getName());

    DCSequencerServer thisServer = this;

    RpcEndpoint srvEndpoint;

    RpcEndpoint srvEndpoint4Surrogates;
    RpcEndpoint cltEndpoint4Surrogates;

    IncrementalTimestampGenerator clockGen;
    CausalityClock receivedMessages;
    CausalityClock currentState;
    CausalityClock notUsed;
    Map<Timestamp, Long> pendingTS;
    Map<String, CausalityClock> remoteClock;
    CausalityClock clientClock; // keeps information about last known client
                                // operation
    CausalityClock maxRemoteClock;
    private CausalityClock stableClock;
    List<String> servers;
    List<Endpoint> serversEP;
    List<String> sequencers; // list of other sequencers
    List<Endpoint> sequencersEP;
    String sequencerShadow;
    Endpoint sequencerShadowEP;
    String siteId;
    Properties props;
    int port;
    boolean isBackup;
    DCNodeDatabase dbServer;
    Map<String, LinkedList<CommitRecord>> ops;
    LinkedList<SeqCommitUpdatesRequest> pendingOps; // ops received from other
                                                    // sites that need to be
                                                    // executed locally
    Map<Timestamp, BlockedTimestampRequest> pendingTsReq; // timestamp requests
                                                          // awaiting reply

    ExecutorService stableExecutor = Executors.newCachedThreadPool();
    Set<Timestamp> unstableTS = new HashSet<Timestamp>();

    public DCSequencerServer(String siteId, List<String> servers, List<String> sequencers, String sequencerShadow,
            boolean isBackup, Properties props) {
        this(siteId, DCConstants.SEQUENCER_PORT, servers, sequencers, sequencerShadow, isBackup, props);
    }

    public DCSequencerServer(String siteId, int port, List<String> servers, List<String> sequencers,
            String sequencerShadow, boolean isBackup, Properties props) {
        this.siteId = siteId;
        this.servers = servers;
        this.sequencers = sequencers;
        this.port = port;
        this.sequencerShadow = sequencerShadow;
        this.isBackup = isBackup;
        this.props = props;
        init();
        initDB(props);

    }

    protected synchronized CausalityClock receivedMessagesCopy() {
        return receivedMessages.clone();
    }

    protected synchronized CausalityClock stableClockCopy() {
        return stableClock.clone();
    }

    protected CausalityClock getRemoteState(String endp) {
        synchronized (remoteClock) {
            CausalityClock clk = remoteClock.get(endp);
            if (clk == null) {
                clk = ClockFactory.newClock();
                remoteClock.put(endp, clk);
            }
            return clk;
        }
    }

    protected void setRemoteState(String endp, CausalityClock clk) {
        synchronized (remoteClock) {
            remoteClock.put(endp, clk);
        }

        synchronized (maxRemoteClock) {
            maxRemoteClock.merge(clk);
        }
    }

    protected synchronized void init() {
        // TODO: reinitiate clock to a correct value
        currentState = ClockFactory.newClock();
        stableClock = ClockFactory.newClock();
        clientClock = ClockFactory.newClock();
        maxRemoteClock = ClockFactory.newClock();
        receivedMessages = ClockFactory.newClock();
        notUsed = ClockFactory.newClock();
        clockGen = new IncrementalTimestampGenerator(siteId);
        pendingTS = new HashMap<Timestamp, Long>();
        ops = new HashMap<String, LinkedList<CommitRecord>>();
        pendingOps = new LinkedList<SeqCommitUpdatesRequest>();
        remoteClock = new HashMap<String, CausalityClock>();
        pendingTsReq = new HashMap<Timestamp, BlockedTimestampRequest>();
    }

    void initDB(Properties props) {
        try {
            dbServer = (DCNodeDatabase) Class.forName(props.getProperty(DCConstants.DATABASE_CLASS)).newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot start underlying database", e);
        }
        dbServer.init(props);

        // HACK HACK
        CausalityClock clk = (CausalityClock) dbServer.readSysData("SYS_TABLE", "CLK");
        if (clk != null) {
            System.err.println(clk);
            currentState.merge(clk);
            stableClock.merge(clk);
        }
    }

    void execPending() {
        Thread th = Threading.newThread(true, new Runnable() {
            public void run() {
                for (;;) {
                    SeqCommitUpdatesRequest req = null;
                    synchronized (pendingOps) {
                        long curTime = System.currentTimeMillis();
                        CausalityClock currentStateCopy = currentClockCopy();
                        Iterator<SeqCommitUpdatesRequest> it = pendingOps.iterator();
                        while (it.hasNext()) {
                            SeqCommitUpdatesRequest req0 = it.next();
                            if (currentStateCopy.includes(req0.getTimestamp())) {
                                it.remove();
                                continue;
                            }
                            if (curTime < req0.lastSent + 2000)
                                continue;

                            CMP_CLOCK cmp = currentStateCopy.compareTo(req0.getObjectUpdateGroups().get(0)
                                    .getDependency());
                            if ((cmp == CMP_CLOCK.CMP_DOMINATES || cmp == CMP_CLOCK.CMP_EQUALS)
                                    && clientClock.getLatestCounter(req0.getCltTimestamp().getIdentifier()) >= req0
                                            .getCltTimestamp().getCounter() - 1) {
                                req = req0;
                                break;
                            }
                        }
                    }
                    if (req != null) {
                        SeqCommitUpdatesRequest req1 = req;
                        if (serversEP.size() > 0) {
                            Endpoint surrogate = serversEP.get(Math.abs(req1.hashCode()) % serversEP.size());
                            cltEndpoint4Surrogates.send(surrogate, req1);
                            req.lastSent = System.currentTimeMillis();
                        }
                    } else
                        Threading.synchronizedWaitOn(pendingOps, 50);
                }
            }
        });
        th.setPriority(Math.max(th.getPriority() - 1, Thread.MIN_PRIORITY));
        th.start();
    }

    void addPending(SeqCommitUpdatesRequest request) {
        synchronized (pendingOps) {
            request.lastSent = Long.MIN_VALUE;
            pendingOps.addLast(request);
        }
        synchronized (this) {
            this.currentState.merge(request.getDcNotUsed());
            this.stableClock.merge(request.getDcNotUsed());
        }
    }

    private synchronized void cleanPendingTSReq() {
        synchronized (pendingTsReq) {
            Iterator<Entry<Timestamp, BlockedTimestampRequest>> it = pendingTsReq.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Timestamp, BlockedTimestampRequest> entry = it.next();
                if (processGenerateDCTimestampRequest(entry.getValue().conn, entry.getValue().request))
                    it.remove();
            }
        }
    }

    void addPendingTimestampReq(BlockedTimestampRequest request) {
        synchronized (pendingTsReq) {
            pendingTsReq.put(request.request.getCltTimestamp(), request);
        }
    }

    public void start() {

        // Note: Networking.resolve() now accepts host[:port], the port
        // parameter is used as default, if port is missing
        this.serversEP = new ArrayList<>();
        for (String s : servers)
            serversEP.add(Networking.getInstance().resolve(s, DCConstants.SURROGATE_PORT_FOR_SEQUENCERS));

        if (serversEP.isEmpty())
            serversEP.add(Networking.getInstance().resolve("localhost", DCConstants.SURROGATE_PORT_FOR_SEQUENCERS));

        this.sequencersEP = new ArrayList<Endpoint>();
        for (String s : sequencers)
            sequencersEP.add(Networking.getInstance().resolve(s, DCConstants.SEQUENCER_PORT));

        this.srvEndpoint = Networking.getInstance().rpcBind(port).toDefaultService().setHandler(this);

        TransportProvider provider = Args.contains("-integrated") ? INPROC : DEFAULT;

        this.cltEndpoint4Surrogates = Networking.getInstance().rpcConnect(provider).toDefaultService();

        if (Args.contains("-integrated"))
            this.srvEndpoint4Surrogates = Networking.getInstance().rpcBind(port, provider).toDefaultService().setHandler(this);
        else
            this.srvEndpoint4Surrogates = srvEndpoint;

        if (sequencerShadow != null)
            sequencerShadowEP = Networking.getInstance().resolve(sequencerShadow, DCConstants.SEQUENCER_PORT);

        if (!isBackup) {
            synchronizer();
            execPending();
        }
        if (isBackup) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Sequencer backup ready...");
            }
        } else {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Sequencer ready...");
            }
        }
    }

    private boolean upgradeToPrimary() {
        // TODO: code to move this to primary
        if (logger.isLoggable(Level.WARNING)) {
            logger.warning("Sequencer backup upgrading to primary...");
        }
        // synchronizer();
        return false;
    }

    /**
     * Synchronizes state with other sequencers
     */
    private void synchronizer() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                LinkedList<CommitRecord> s = null;
                synchronized (ops) {
                    s = ops.get(siteId);
                    if (s == null) {
                        s = new LinkedList<CommitRecord>();
                        ops.put(siteId, s);
                    }
                }

                for (;;) {
                    try {
                        CommitRecord r = null;
                        long lastSendTime = System.currentTimeMillis() - DCConstants.INTERSEQ_RETRY;
                        long lastEffectiveSendTime = Long.MAX_VALUE;
                        synchronized (s) {
                            Iterator<CommitRecord> it = s.iterator();
                            while (it.hasNext()) {
                                CommitRecord c = it.next();
                                if (c.acked.nextClearBit(0) == sequencersEP.size()) {
                                    it.remove();
                                    continue;
                                }
                                long l = c.lastSentTime();
                                if (l < lastSendTime) {
                                    r = c;
                                    break;
                                } else if (l < lastEffectiveSendTime) {
                                    lastEffectiveSendTime = l;
                                }
                            }
                            if (logger.isLoggable(Level.INFO)) {
                                logger.info("sequencer: synchronizer: num operations to propagate : " + s.size());
                            }
                            if (r == null) {
                                long waitime = lastEffectiveSendTime - System.currentTimeMillis()
                                        + DCConstants.INTERSEQ_RETRY;
                                Threading.waitOn(s, Math.min(DCConstants.INTERSEQ_RETRY, waitime));
                            }
                        }
                        if (r != null) {
                            final CommitRecord r0 = r;
                            final SeqCommitUpdatesRequest req = new SeqCommitUpdatesRequest(siteId, r.baseTimestamp,
                                    r.cltTimestamp, r.prvCltTimestamp, r.objectUpdateGroups, receivedMessagesCopy(),
                                    r.notUsed);
                            for (int i = 0; i < sequencersEP.size(); i++) {
                                synchronized (r) {
                                    if (r.acked.get(i) == true)
                                        continue;
                                }
                                final int i0 = i;
                                final Endpoint other = sequencersEP.get(i);
                                srvEndpoint.send(other, req, new SwiftProtocolHandler() {

                                    @Override
                                    public void onReceive(RpcHandle conn, SeqCommitUpdatesReply reply) {
                                        synchronized (r0) {
                                            r0.acked.set(i0);
                                        }

                                        synchronized (thisServer) {
                                            stableClock.record(req.getTimestamp());
                                        }

                                        synchronized (unstableTS) {
                                            unstableTS.remove(req.getTimestamp());
                                            Threading.notifyAllOn(unstableTS);
                                        }
                                        setRemoteState(reply.getDcName(), reply.getDcKnownClock());
                                    }
                                }, 0);
                            }
                            r.setTime(System.currentTimeMillis());
                        }
                    } catch (Exception e) {
                        // do nothing
                    }
                }
            }
        });
        t.setPriority(Math.max(t.getPriority() - 1, Thread.MIN_PRIORITY));
        t.start();
    }

    private void addToOps(CommitRecord record) {
        // TODO: remove this if anti-entropy is to be used
        if (!record.baseTimestamp.getIdentifier().equals(siteId))
            return;
        synchronized (this) {
            if (receivedMessages.includes(record.baseTimestamp))
                return;
        }

        dbServer.writeSysData("SYS_TABLE", record.baseTimestamp.getIdentifier(), record);
        LinkedList<CommitRecord> s = null;
        synchronized (ops) {
            s = ops.get(record.baseTimestamp.getIdentifier());
            if (s == null) {
                s = new LinkedList<CommitRecord>();
                ops.put(record.baseTimestamp.getIdentifier(), s);
            }
        }
        synchronized (this) {
            receivedMessages.record(record.baseTimestamp);
        }
        if (record.acked.nextClearBit(0) < sequencers.size())
            synchronized (s) {
                s.addLast(record);
                s.notifyAll();
            }

    }

    private synchronized void cleanPendingTS() {
        long curTime = System.currentTimeMillis();
        Iterator<Entry<Timestamp, Long>> it = pendingTS.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Timestamp, Long> entry = it.next();
            if (curTime - entry.getValue().longValue() > 2 * DCConstants.DEFAULT_TRXIDTIME) {
                it.remove();
                currentState.record(entry.getKey());
                stableClock.record(entry.getKey());
                notUsed.record(entry.getKey());
            }
        }
    }

    private synchronized Timestamp generateNewId() {
        Timestamp t = clockGen.generateNew();
        pendingTS.put(t, System.currentTimeMillis());
        return t;
    }

    private synchronized boolean commitTS(CausalityClock clk, Timestamp t, Timestamp cltTs, boolean commit) {
        boolean hasTS = pendingTS.remove(t) != null
                || ((!t.getIdentifier().equals(this.siteId)) && !currentState.includes(t));

        // currentState.merge(clk); // nmp: not sure why is this here
        currentState.record(t);
        clientClock.record(cltTs);
        if (sequencers.size() == 0 || !siteId.equals(t.getIdentifier())) // HACK:
                                                                         // Stable
                                                                         // is
                                                                         // updated
                                                                         // only
                                                                         // when
                                                                         // Op
                                                                         // is
                                                                         // recorded
                                                                         // in
                                                                         // local
                                                                         // DC.
            stableClock.record(t);

        return hasTS;
    }

    private synchronized CausalityClock currentClockCopy() {
        return currentState.clone();
    }

    private synchronized CausalityClock notUsedCopy() {
        CausalityClock c = notUsed.clone();
        notUsed = ClockFactory.newClock();
        return c;
    }

    private boolean processGenerateDCTimestampRequest(RpcHandle conn, GenerateDCTimestampRequest request) {
        long last;

        Timestamp cltTs = request.getCltTimestamp();
        synchronized (clientClock) {
            last = clientClock.getLatestCounter(request.getClientId());
            if (clientClock.includes(cltTs)) {
                conn.reply(new GenerateDCTimestampReply(last));
                return true;
            }
        }
        CMP_CLOCK cmp = CMP_CLOCK.CMP_EQUALS;
        synchronized (this) {
            cmp = currentState.compareTo(request.getDependencyClk());
        }
        if (cltTs.getCounter() == (last + 1L) && cmp.is(CMP_EQUALS, CMP_DOMINATES)) {
            conn.reply(new GenerateDCTimestampReply(generateNewId(),
                    clientClock.getLatestCounter(request.getClientId())));

            return true;
        } else {
            addPendingTimestampReq(new BlockedTimestampRequest(conn, request));
            return false;
        }
    }

    @Override
    public void onReceive(RpcHandle conn, GenerateDCTimestampRequest request) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("sequencer: generateDCtimestamprequest");
        }
        if (isBackup && !upgradeToPrimary())
            return;
        if (!processGenerateDCTimestampRequest(conn, request)) {
            addPendingTimestampReq(new BlockedTimestampRequest(conn, request));
        }
        cleanPendingTS();
    }

    /**
     * @param conn
     *            connection such that the remote end implements
     *            {@link LatestKnownClockReplyHandler} and expects
     *            {@link LatestKnownClockReply}
     * @param request
     *            request to serve
     */
    public void onReceive(RpcHandle conn, LatestKnownClockRequest request) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("sequencer: latestknownclockrequest:" + currentClockCopy());
        }
        if (isBackup && !upgradeToPrimary())
            return;

        conn.reply(new LatestKnownClockReply(currentClockCopy(), stableClockCopy()));
    }

    final ConcurrentHashMap<String, FifoQueue<CommitTSRequest>> fifoQueues = new ConcurrentHashMap<String, FifoQueue<CommitTSRequest>>();

    public FifoQueue<CommitTSRequest> queueFor(final Timestamp ts) {
        String id = ts.getIdentifier();
        FifoQueue<CommitTSRequest> res = fifoQueues.get(id), nq;
        if (res == null) {
            res = fifoQueues.putIfAbsent(id, nq = new FifoQueue<CommitTSRequest>(id) {
                public void process(CommitTSRequest request) {
                    doCommit(request.getReplyHandle(), request);
                }
            });
            if (res == null)
                res = nq;
        }
        return res;
    }

    @Override
    public void onReceive(final RpcHandle conn, final CommitTSRequest request) {
        request.setReplyHandle(conn);

        Timestamp ts = request.getTimestamp();
        queueFor(ts).offer(ts.getCounter(), request);
    }

    /**
     * @param conn
     *            connection such that the remote end implements
     *            {@link CommitTSReplyHandler} and expects {@link CommitTSReply}
     * @param request
     *            request to serve
     */
    void doCommit(final RpcHandle conn, final CommitTSRequest request) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("sequencer: commitTSRequest:" + request.getTimestamp() + ":nops="
                    + request.getObjectUpdateGroups().size());
        }
        if (isBackup && !upgradeToPrimary())
            return;

        boolean ok = false;
        final CausalityClock clk;
        final CausalityClock stableClk;
        CausalityClock nuClk;

        synchronized (this) {
            ok = commitTS(request.getVersion(), request.getTimestamp(), request.getCltTimestamp(), request.getCommit());
            clk = currentClockCopy();
            stableClk = stableClockCopy();
            nuClk = notUsedCopy();
        }

        if (!ok) {
            conn.reply(new CommitTSReply(CommitTSReply.CommitTSStatus.FAILED, clk, stableClk));
            return;
        }
        if (!isBackup && sequencerShadowEP != null) {
            final SeqCommitUpdatesRequest msg = new SeqCommitUpdatesRequest(siteId, request.getTimestamp(),
                    request.getCltTimestamp(), request.getPrvCltTimestamp(), request.getObjectUpdateGroups(), clk,
                    nuClk);

            srvEndpoint.send(sequencerShadowEP, msg);
        }

        addToOps(new CommitRecord(nuClk, request.getObjectUpdateGroups(), request.getTimestamp(),
                request.getCltTimestamp(), request.getPrvCltTimestamp()));

        Threading.synchronizedNotifyAllOn(pendingOps);

        dbServer.writeSysData("SYS_TABLE", "CLK", currentState);

        conn.reply(new CommitTSReply(CommitTSReply.CommitTSStatus.OK, clk, stableClk));
        cleanPendingTSReq();
    }

    @Override
    public void onReceive(RpcHandle conn, final SeqCommitUpdatesRequest request) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("sequencer: received commit record:" + request.getTimestamp() + ":clt="
                    + request.getCltTimestamp() + ":nops=" + request.getObjectUpdateGroups().size());
        }
        // System.out.println("sequencer: received commit record:" +
        // request.getTimestamp() + ":clt="
        // + request.getCltTimestamp() + ":nops=" +
        // request.getObjectUpdateGroups().size());

        if (isBackup) {
            this.addToOps(new CommitRecord(request.getDcNotUsed(), request.getObjectUpdateGroups(), request
                    .getTimestamp(), request.getCltTimestamp(), request.getPrvCltTimestamp()));

            conn.reply(new SeqCommitUpdatesReply(siteId, currentClockCopy(), stableClockCopy(), receivedMessagesCopy()));

            synchronized (this) {
                currentState.merge(request.getDcNotUsed());
                stableClock.merge(request.getDcNotUsed());
                currentState.record(request.getTimestamp());
                stableClock.record(request.getTimestamp());
                clientClock.record(request.getCltTimestamp());
            }
            return;
        }

        this.addToOps(new CommitRecord(request.getDcNotUsed(), request.getObjectUpdateGroups(), request.getTimestamp(),
                request.getCltTimestamp(), request.getPrvCltTimestamp()));

        //synchronized (this) {
        //    stableClock.record(request.getTimestamp());
        //}

        conn.reply(new SeqCommitUpdatesReply(siteId, currentClockCopy(), stableClockCopy(), receivedMessagesCopy()));

        if (!isBackup && sequencerShadowEP != null) {
            srvEndpoint.send(sequencerShadowEP, request);
        }

        addPending(request);

        Threading.synchronizedNotifyAllOn(pendingOps);
    }

    @Override
    public void onReceive(final RpcHandle conn, PingRequest request) {
        PingReply reply = new PingReply(request.getTimeAtSender(), System.nanoTime());
        conn.reply(reply);
    }

    public static void main(String[] args) {
        Args.use(args);

        final String dbSuffix = "_seq";

        final Properties props = new Properties();
        props.putAll(System.getProperties());

        props.setProperty("sync_commit", Args.contains(args, "-sync") + "");

        String restoreDBdir = Args.valueOf(args, "-rdb", null);
        boolean useBerkeleyDB = Args.contains(args, "-db");

        if (restoreDBdir != null) {
            useBerkeleyDB = true;
            props.setProperty("restore_db", restoreDBdir + dbSuffix);
        }
        if (!props.containsKey(DATABASE_CLASS) || useBerkeleyDB) {
            if (DCConstants.DEFAULT_DB_NULL && !useBerkeleyDB) {
                props.setProperty(DCConstants.DATABASE_CLASS, "swift.dc.db.DevNullNodeDatabase");
            } else {
                props.setProperty(DCConstants.DATABASE_CLASS, "swift.dc.db.DCBerkeleyDBDatabase");
                props.setProperty(DCConstants.BERKELEYDB_DIR, "db/default" + dbSuffix);
            }
        }
        if (!props.containsKey(DCConstants.DATABASE_CLASS)) {
            props.setProperty(DCConstants.PRUNE_POLICY, "false");
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-prop:")) {
                props.setProperty(args[i].substring(6), args[++i]);
            }
        }

        String siteId = Args.valueOf("-name", "X");
        boolean isBackup = Args.valueOf("-backup", false);
        String sequencerShadow = Args.valueOf("-sequencerShadow", null);

        int port = Args.valueOf("-port", DCConstants.SEQUENCER_PORT);

        List<String> sequencers = Args.subList("-sequencers");
        List<String> servers = Args.subList("-servers");
        if (Args.contains("-integrated") && servers.isEmpty()) {
            servers = Args.subList("-surrogates");
        }

        new DCSequencerServer(siteId, port, servers, sequencers, sequencerShadow, isBackup, props).start();
    }
}

class BlockedTimestampRequest {
    public BlockedTimestampRequest(RpcHandle conn, GenerateDCTimestampRequest request) {
        this.conn = conn;
        this.request = request;
    }

    RpcHandle conn;
    GenerateDCTimestampRequest request;
}

class CommitRecord implements Comparable<CommitRecord> {
    BitSet acked;
    CausalityClock notUsed;
    List<CRDTObjectUpdatesGroup<?>> objectUpdateGroups;
    Timestamp baseTimestamp;
    Timestamp cltTimestamp;
    Timestamp prvCltTimestamp;
    long lastSent;

    CommitRecord() {
    }

    public CommitRecord(CausalityClock notUsed, List<CRDTObjectUpdatesGroup<?>> objectUpdateGroups,
            Timestamp baseTimestamp, Timestamp cltTimestamp, Timestamp prvCltTimestamp) {
        this.notUsed = notUsed;
        this.objectUpdateGroups = objectUpdateGroups;
        this.baseTimestamp = baseTimestamp;
        this.cltTimestamp = cltTimestamp;
        this.prvCltTimestamp = prvCltTimestamp;
        acked = new BitSet();
        lastSent = Long.MIN_VALUE;
    }

    synchronized long lastSentTime() {
        return lastSent;
    }

    synchronized void setTime(long t) {
        lastSent = t;
    }

    @Override
    public int compareTo(CommitRecord o) {
        return (int) (baseTimestamp.getCounter() - o.baseTimestamp.getCounter());
    }
}
