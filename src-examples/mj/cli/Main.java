package mj.cli;

import swift.client.SwiftImpl;
import swift.client.SwiftOptions;
import swift.crdt.IntegerCRDT;
import swift.crdt.core.*;
import swift.exceptions.NetworkException;
import swift.exceptions.NoSuchObjectException;
import swift.exceptions.VersionNotFoundException;
import swift.exceptions.WrongTypeException;

import java.util.Properties;

public class Main {
    public static void main(String[] args) throws NetworkException, WrongTypeException, NoSuchObjectException, VersionNotFoundException {

        String sessionId = "session_id";

        final SwiftOptions options = new SwiftOptions("localhost", 123, new Properties());
        SwiftSession swiftClient = SwiftImpl.newSingleSessionInstance(options, sessionId);

        TxnHandle txn = swiftClient.beginTxn(IsolationLevel.SNAPSHOT_ISOLATION, CachePolicy.CACHED, false);

        CRDTIdentifier counter_id = new CRDTIdentifier("my_table", "counter_foobar");
        IntegerCRDT counter = txn.get(counter_id, true, IntegerCRDT.class);

        counter.add(5);
        counter.sub(2);

        txn.commit();
    }
}
