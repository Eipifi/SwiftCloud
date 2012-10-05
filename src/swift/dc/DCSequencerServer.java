package swift.dc;

import static sys.Sys.Sys;
import static sys.net.api.Networking.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import swift.client.proto.*;
import swift.clocks.CausalityClock;
import swift.clocks.CausalityClock.CMP_CLOCK;
import swift.clocks.ClockFactory;
import swift.clocks.IncrementalTimestampGenerator;
import swift.clocks.Timestamp;
import swift.crdt.CRDTIdentifier;
import swift.crdt.operations.CRDTObjectUpdatesGroup;
import swift.dc.db.DCNodeDatabase;
import swift.dc.proto.CommitTSReply;
import swift.dc.proto.CommitTSReplyHandler;
import swift.dc.proto.CommitTSRequest;
import swift.dc.proto.GenerateDCTimestampReply;
import swift.dc.proto.GenerateDCTimestampRequest;
import swift.dc.proto.SeqCommitUpdatesReply;
import swift.dc.proto.SeqCommitUpdatesReplyHandler;
import swift.dc.proto.SeqCommitUpdatesRequest;
import swift.dc.proto.SequencerServer;
import sys.Sys;
import sys.net.api.Endpoint;
import sys.net.api.Networking;
import sys.net.api.rpc.AbstractRpcHandler;
import sys.net.api.rpc.RpcHandle;
import sys.net.api.rpc.RpcEndpoint;
import sys.net.api.rpc.RpcHandler;
import sys.net.api.rpc.RpcMessage;
import sys.utils.Log;
import sys.utils.Threading;

/**
 * Single server replying to client request. This class is used only to allow
 * client testing.
 * 
 * @author nmp
 * 
 */
public class DCSequencerServer extends Handler implements SequencerServer {
    RpcEndpoint endpoint;
    IncrementalTimestampGenerator clockGen;
    CausalityClock receivedMessages;
    CausalityClock currentState;
    CausalityClock notUsed;
    Map<Timestamp, Long> pendingTS;
    Map<String,CausalityClock> remoteClock;
    CausalityClock clientClock;     // keeps information about last known client operation
    CausalityClock maxRemoteClock;
    CausalityClock stableClock;
    List<String> servers;
    List<Endpoint> serversEP;
    List<String> sequencers;
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
    Map<Timestamp,BlockedTimestampRequest> pendingTsReq;    //timestamp requests awaiting reply

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

    protected CausalityClock getRemoteState( String endp) {
        synchronized( remoteClock) {
            CausalityClock clk = remoteClock.get( endp);
            if( clk == null) {
                clk = ClockFactory.newClock();
                remoteClock.put(endp, clk);
            }
            return clk;
        }
    }

    protected void setRemoteState( String endp, CausalityClock clk) {
        synchronized( remoteClock) {
            remoteClock.put( endp, clk);
        }
        
        synchronized( maxRemoteClock) {
            maxRemoteClock.merge(clk);
        }
    }

    protected void init() {
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
        remoteClock = new HashMap<String,CausalityClock>();
        pendingTsReq = new HashMap<Timestamp,BlockedTimestampRequest>();
    }

    void initDB(Properties props) {
        try {
            dbServer = (DCNodeDatabase) Class.forName(props.getProperty(DCConstants.DATABASE_CLASS)).newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot start underlying database", e);
        }
        dbServer.init(props);

    }

    void execPending() {
    	Threading.newThread(true, new Runnable() {
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
    	                    CMP_CLOCK cmp = currentStateCopy.compareTo(req0.getObjectUpdateGroups().get(0).getDependency());
    	                    if ((cmp == CMP_CLOCK.CMP_DOMINATES || cmp == CMP_CLOCK.CMP_EQUALS) &&
    	                            clientClock.getLatestCounter( req0.getCltTimestamp().getIdentifier()) >= req0.getCltTimestamp().getCounter() - 1 ) {
    	                        req = req0;
    	                        break;
    	                    }
    	                }
    	            }
    	            if (req != null) { 
	    	            SeqCommitUpdatesRequest req1 = req;
	    	            if (serversEP.size() > 0) {
	    	                endpoint.send(serversEP.get( Math.abs(req1.hashCode()) % servers.size()), req1);
	    	                req.lastSent = System.currentTimeMillis();
	    	            }
    	            } else
    	            	Threading.synchronizedWaitOn( pendingOps, 500 ) ;
    	        }
    		}
    	}).start();    	
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
            Iterator<Entry<Timestamp,BlockedTimestampRequest>> it = pendingTsReq.entrySet().iterator();
            while( it.hasNext()) {
                Entry<Timestamp,BlockedTimestampRequest> entry = it.next();
                if( processGenerateDCTimestampRequest( entry.getValue().conn, entry.getValue().request))
                    it.remove();
            }
        }
    }
    
    void addPendingTimestampReq( BlockedTimestampRequest request) {
        synchronized (pendingTsReq) {
            pendingTsReq.put(request.request.getCltTimestamp(), request);
        }
    }

    public void start() {
        Sys.init();
        this.serversEP = new ArrayList<Endpoint>();
        Iterator<String> it = servers.iterator();
        while (it.hasNext()) {
            String s = it.next();
            int pos = s.indexOf(":");
            if (pos != -1) {
                int port = Integer.parseInt(s.substring(pos + 1));
                s = s.substring(0, pos);
                serversEP.add(Networking.resolve(s, port));
            } else
                serversEP.add(Networking.resolve(s, DCConstants.SURROGATE_PORT));
        }
        this.sequencersEP = new ArrayList<Endpoint>();
        it = sequencers.iterator();
        while (it.hasNext()) {
            String s = it.next();
            int pos = s.indexOf(":");
            if (pos != -1) {
                int port = Integer.parseInt(s.substring(pos + 1));
                s = s.substring(0, pos);
                sequencersEP.add(Networking.resolve(s, port));
            } else
                sequencersEP.add(Networking.resolve(s, DCConstants.SEQUENCER_PORT));
        }

        this.endpoint = Networking.rpcBind(port).toDefaultService();
        this.endpoint.setHandler(this);

        if (sequencerShadow != null) {
            String s = sequencerShadow;
            int pos = s.indexOf(":");
            if (pos != -1) {
                int port = Integer.parseInt(s.substring(pos + 1));
                s = s.substring(0, pos);
                sequencerShadowEP = Networking.resolve(s, port);
            } else
                sequencerShadowEP = Networking.resolve(s, DCConstants.SEQUENCER_PORT);
        }
        if (!isBackup) {
            synchronizer();
            execPending(); //smd
        }
        if (isBackup)
            DCConstants.DCLogger.info("Sequencer backup ready...");
        else
            DCConstants.DCLogger.info("Sequencer ready...");
    }

    private boolean upgradeToPrimary() {
        // TODO: code to move this to primary
        DCConstants.DCLogger.warning("Sequencer backup upgrading to primary...");
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
                                if( c.acked.nextClearBit(0) == sequencers.size() ) {
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
                            if (r == null) {
                                try {
                                    long waitime = lastEffectiveSendTime - System.currentTimeMillis()
                                            + DCConstants.INTERSEQ_RETRY;
                                    if (waitime > 0)
                                        s.wait(Math.min(DCConstants.INTERSEQ_RETRY, waitime));
                                } catch (InterruptedException e) {
                                    // do nothing
                                }
                            }
                        }
                        if (r != null) {
                            final CommitRecord r0 = r;
                            SeqCommitUpdatesRequest req = new SeqCommitUpdatesRequest(siteId, r.baseTimestamp, r.cltTimestamp,
                                    r.objectUpdateGroups, receivedMessagesCopy(), r.notUsed);
                            for (int i = 0; i < sequencersEP.size(); i++) {
                                synchronized (r) {
                                    if (r.acked.get(i) == true)
                                        continue;
                                }
                                final int i0 = i;
                                Endpoint ep = sequencersEP.get(i);
                                endpoint.send(ep, req, new SeqCommitUpdatesReplyHandler() {

                                    @Override
                                    public void onReceive(RpcHandle conn, SeqCommitUpdatesReply reply) {
                                        synchronized (r0) {
                                            r0.acked.set(i0);
                                        }
                                        setRemoteState( reply.getDcName(), reply.getDcKnownClock());
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
        t.setDaemon(true);
        t.setPriority(Thread.currentThread().getPriority());
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

    private synchronized boolean refreshId(Timestamp t) {
        boolean hasTS = pendingTS.containsKey(t);
        if (hasTS)
            pendingTS.put(t, System.currentTimeMillis());
        return hasTS;
    }

    private synchronized boolean commitTS(CausalityClock clk, Timestamp t, Timestamp cltTs, boolean commit) {
        boolean hasTS = pendingTS.remove(t) != null || ((! t.getIdentifier().equals( this.siteId)) && ! currentState.includes(t));
        currentState.merge(clk);    //nmp: not sure why is this here
        currentState.record(t);
        if( sequencers.size() == 0)
            stableClock.record(t);
        clientClock.record(cltTs);
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

    @Override
    public void onReceive(RpcHandle conn, GenerateTimestampRequest request) {
        DCConstants.DCLogger.info("sequencer: generatetimestamprequest");
        if (isBackup && !upgradeToPrimary())
            return;
        conn.reply(new GenerateTimestampReply(generateNewId(), DCConstants.DEFAULT_TRXIDTIME));
        cleanPendingTS();
    }

    private boolean processGenerateDCTimestampRequest(RpcHandle conn, GenerateDCTimestampRequest request) {
        synchronized( clientClock) {
            if( clientClock.includes( request.getCltTimestamp())) {
                conn.reply(new GenerateDCTimestampReply(clientClock.getLatestCounter(request.getClientId())));
                return true;
            }
        }
        CMP_CLOCK cmp = CMP_CLOCK.CMP_EQUALS;
        synchronized( currentState) {
            cmp = currentState.compareTo( request.getDependencyClk());
        }
        if( cmp == CMP_CLOCK.CMP_EQUALS || cmp == CMP_CLOCK.CMP_DOMINATES) {
            conn.reply(new GenerateDCTimestampReply(generateNewId(), clientClock.getLatestCounter(request.getClientId())));
            return true;
        } else {
            addPendingTimestampReq( new BlockedTimestampRequest( conn, request));
            return false;
        }
    }
    
    @Override
    public void onReceive(RpcHandle conn, GenerateDCTimestampRequest request) {
        DCConstants.DCLogger.info("sequencer: generateDCtimestamprequest");
        if (isBackup && !upgradeToPrimary())
            return;
        
        if( ! processGenerateDCTimestampRequest( conn, request))
            addPendingTimestampReq( new BlockedTimestampRequest( conn, request));
        cleanPendingTS();
    }

    @Override
    public void onReceive(RpcHandle conn, KeepaliveRequest request) {
        DCConstants.DCLogger.info("sequencer: keepaliverequest");
        if (isBackup && !upgradeToPrimary())
            return;
        boolean success = refreshId(request.getTimestamp());
        conn.reply(new KeepaliveReply(success, success, DCConstants.DEFAULT_TRXIDTIME));
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
        DCConstants.DCLogger.info("sequencer: latestknownclockrequest:" + currentClockCopy());
        if (isBackup && !upgradeToPrimary())
            return;
        conn.reply(new LatestKnownClockReply(currentClockCopy(), stableClockCopy()));
    }

    /**
     * @param conn
     *            connection such that the remote end implements
     *            {@link CommitTSReplyHandler} and expects {@link CommitTSReply}
     * @param request
     *            request to serve
     */
    @Override
    public void onReceive(RpcHandle conn, CommitTSRequest request) {
        DCConstants.DCLogger.info("sequencer: commitTSRequest:" + request.getTimestamp() + ":nops="
                + request.getObjectUpdateGroups().size());
        if (isBackup && !upgradeToPrimary())
            return;
      
        boolean ok = false;
        CausalityClock clk = null;
        CausalityClock stableClk = null;
        CausalityClock nuClk = null;
        
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
        
        conn.reply(new CommitTSReply(CommitTSReply.CommitTSStatus.OK, clk, stableClk));
        if (!isBackup && sequencerShadowEP != null) {
            final SeqCommitUpdatesRequest msg = new SeqCommitUpdatesRequest(siteId,request.getTimestamp(),request.getCltTimestamp(),
                    request.getObjectUpdateGroups(), clk, nuClk);
            endpoint.send(sequencerShadowEP, msg, new AbstractRpcHandler() {
                @Override
                public void onReceive(RpcMessage m) {
                    // do nothing
                }

                @Override
                public void onReceive(RpcHandle conn, RpcMessage m) {
                    // do nothing
                }

            }, 0);
        }

        addToOps(new CommitRecord(nuClk, request.getObjectUpdateGroups(), request.getTimestamp(), request.getCltTimestamp()));
        Threading.synchronizedNotifyAllOn( pendingOps );
        cleanPendingTSReq();
   }

    @Override
    public void onReceive(RpcHandle conn, final SeqCommitUpdatesRequest request) {
    
        DCConstants.DCLogger.info("sequencer: received commit record:" + request.getTimestamp() + 
                ":clt=" + request.getCltTimestamp()+ 
                ":nops=" + request.getObjectUpdateGroups().size());

        if (isBackup) {
            this.addToOps(new CommitRecord(request.getDcNotUsed(), request.getObjectUpdateGroups(), 
                    request.getTimestamp(), request.getCltTimestamp()));
            
            conn.reply(new SeqCommitUpdatesReply(siteId,currentClockCopy(),stableClockCopy(),receivedMessagesCopy()));

            synchronized (this) {
                currentState.merge(request.getDcNotUsed());
                stableClock.merge(request.getDcNotUsed());
                currentState.record(request.getTimestamp());
                if( sequencers.size() == 0)
                    stableClock.record(request.getTimestamp());
                clientClock.record(request.getCltTimestamp());
            }
            return;
        }

        this.addToOps(new CommitRecord(request.getDcNotUsed(), request.getObjectUpdateGroups(), 
                request.getTimestamp(), request.getCltTimestamp()));
                
        conn.reply(new SeqCommitUpdatesReply(siteId,currentClockCopy(),stableClockCopy(),receivedMessagesCopy()));
        if (!isBackup && sequencerShadowEP != null) {
            endpoint.send(sequencerShadowEP, request);
        }
        
        addPending(request);
        
        Threading.synchronizedNotifyAllOn( pendingOps );        
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.setProperty(DCConstants.DATABASE_CLASS, "swift.dc.db.DevNullNodeDatabase");
        List<String> sequencers = new ArrayList<String>();
        List<String> servers = new ArrayList<String>();
        int port = DCConstants.SEQUENCER_PORT;
        boolean isBackup = false;
        String sequencerShadow = null;
        String siteId = "X";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-name")) {
                siteId = args[++i];
            } else if (args[i].equals("-servers")) {
                for (; i + 1 < args.length; i++) {
                    if (args[i + 1].startsWith("-"))
                        break;
                    servers.add(args[i + 1]);
                }
            } else if (args[i].equals("-sequencers")) {
                for (; i + 1 < args.length; i++) {
                    if (args[i + 1].startsWith("-"))
                        break;
                    sequencers.add(args[i + 1]);
                }
            } else if (args[i].equals("-port")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-backup")) {
                isBackup = true;
            } else if (args[i].equals("-sequencerShadow")) {
                sequencerShadow = args[++i];
            } else if (args[i].startsWith("-prop:")) {
                props.setProperty(args[i].substring(6), args[++i]);
            }
        }
        new DCSequencerServer(siteId, port, servers, sequencers, sequencerShadow, isBackup, props).start();
    }

}

class BlockedTimestampRequest
{
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
    long lastSent;

    public CommitRecord(CausalityClock notUsed, List<CRDTObjectUpdatesGroup<?>> objectUpdateGroups,
            Timestamp baseTimestamp, Timestamp cltTimestamp) {
        this.notUsed = notUsed;
        this.objectUpdateGroups = objectUpdateGroups;
        this.baseTimestamp = baseTimestamp;
        this.cltTimestamp = cltTimestamp;
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
