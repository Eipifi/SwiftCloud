package swift.application;

import swift.client.SwiftImpl;
import swift.crdt.CRDTIdentifier;
import swift.crdt.IntegerTxnLocal;
import swift.crdt.interfaces.CachePolicy;
import swift.crdt.interfaces.IsolationLevel;
import swift.crdt.interfaces.TxnHandle;
import swift.dc.DCConstants;
import swift.dc.DCSequencerServer;
import swift.dc.DCServer;
import swift.exceptions.NetworkException;
import swift.exceptions.NoSuchObjectException;
import swift.exceptions.VersionNotFoundException;
import swift.exceptions.WrongTypeException;
import swift.utils.NanoTimeCollector;
import sys.Sys;

/**
 * Local setup/test with one server and two clients.
 * 
 * @author annettebieniusa
 * 
 */
public class PingSpeedTest {
    static String sequencerName = "localhost";

    public static void main(String[] args) {
        // start sequencer server
        DCSequencerServer.main( new String[] { "-name", sequencerName});
//      DCSequencerServer sequencer = new DCSequencerServer(sequencerName);
//      sequencer.start();

        // start DC server
        DCServer.main(new String[] { sequencerName });

        final int portId = 2000;
        Thread client1 = new Thread("client1") {
            public void run() {
                Sys.init();
                SwiftImpl clientServer = SwiftImpl.newInstance(portId, "localhost", DCConstants.SURROGATE_PORT);
                client1Code(clientServer);
                clientServer.stop(true);
            }
        };
        client1.start();

        final int portId2 = 2002;
        Thread client2 = new Thread("client2") {
            public void run() {
                Sys.init();
                SwiftImpl clientServer = SwiftImpl.newInstance(portId2, "localhost", DCConstants.SURROGATE_PORT);
                client2Code(clientServer);
                clientServer.stop(true);
            }
        };
        client2.start();
    }

    private static void client1Code(SwiftImpl server) {
        try {
            NanoTimeCollector timer = new NanoTimeCollector();
            timer.start();
            TxnHandle handle = server.beginTxn(IsolationLevel.SNAPSHOT_ISOLATION, CachePolicy.STRICTLY_MOST_RECENT,
                    false);
            IntegerTxnLocal i1 = handle.get(new CRDTIdentifier("e", "1"), true, swift.crdt.IntegerVersioned.class);
            i1.add(1);
            handle.commit();
            int expected = 2;

            while (true) {
                TxnHandle txn = server.beginTxn(IsolationLevel.SNAPSHOT_ISOLATION, CachePolicy.STRICTLY_MOST_RECENT,
                        false);
                IntegerTxnLocal i = txn.get(new CRDTIdentifier("e", "1"), false, swift.crdt.IntegerVersioned.class);
                if (expected == i.getValue()) {
                    long pingTime = timer.stop();
                    txn.commit();

                    System.out.println("Ping time: " + pingTime);
                    Thread.sleep(1000);
                    expected += 2;
                    timer.start();
                    increment(server);
                } else {
                    // System.out.println("Value " + i.getValue());
                    txn.rollback();

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void increment(SwiftImpl server) throws NetworkException, WrongTypeException, NoSuchObjectException,
            VersionNotFoundException {
        TxnHandle handle = server.beginTxn(IsolationLevel.SNAPSHOT_ISOLATION, CachePolicy.STRICTLY_MOST_RECENT, false);
        IntegerTxnLocal i1 = handle.get(new CRDTIdentifier("e", "1"), false, swift.crdt.IntegerVersioned.class);
        i1.add(1);
        handle.commit();
    }

    private static void client2Code(SwiftImpl server) {
        try {
            int expected = 1;
            while (true) {
                TxnHandle handle = server.beginTxn(IsolationLevel.SNAPSHOT_ISOLATION, CachePolicy.STRICTLY_MOST_RECENT,
                        false);
                IntegerTxnLocal i1 = handle.get(new CRDTIdentifier("e", "1"), false, swift.crdt.IntegerVersioned.class);
                if (i1.getValue() == expected) {
                    i1.add(1);
                    handle.commit();
                    expected += 2;
                } else {
                    handle.rollback();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}