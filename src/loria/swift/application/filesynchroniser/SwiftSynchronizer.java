package loria.swift.application.filesynchroniser;

import java.util.logging.Level;
import java.util.logging.Logger;
import loria.swift.application.filesystem.mapper.TextualContent;
import swift.crdt.CRDTIdentifier;
import swift.crdt.interfaces.CachePolicy;
import swift.crdt.interfaces.IsolationLevel;
import swift.crdt.interfaces.ObjectUpdatesListener;
import swift.crdt.interfaces.Swift;
import swift.crdt.interfaces.TxnHandle;
import swift.exceptions.SwiftException;

// implements the social network functionality
// see wsocial_srv.h

public class SwiftSynchronizer {

    private static final Logger logger = Logger.getLogger("swift.social");

    private final Class textClass;
            
    private Swift server;
    private final IsolationLevel isolationLevel;
    private final CachePolicy cachePolicy;
    private final ObjectUpdatesListener updatesSubscriber;
    private final boolean asyncCommit;

    public SwiftSynchronizer(Swift clientServer, IsolationLevel isolationLevel, CachePolicy cachePolicy,
            boolean subscribeUpdates, boolean asyncCommit, Class textClass) {
        server = clientServer;
        this.isolationLevel = isolationLevel;
        this.cachePolicy = cachePolicy;
        this.updatesSubscriber = subscribeUpdates ? TxnHandle.UPDATES_SUBSCRIBER : null;
        this.asyncCommit = asyncCommit;
        this.textClass = textClass;
    }

    public static CRDTIdentifier naming(final String textName) {
        return new CRDTIdentifier("texts", textName);
    }
    
    public String update(String textName) {
        TxnHandle txn = null;
        try {
            final CachePolicy loginCachePolicy;
            if (isolationLevel == IsolationLevel.SNAPSHOT_ISOLATION && cachePolicy == CachePolicy.CACHED) {
                loginCachePolicy = CachePolicy.MOST_RECENT;
            } else {
                loginCachePolicy = cachePolicy;
            }
            txn = server.beginTxn(isolationLevel, loginCachePolicy, true);
            TextualContent text = (TextualContent) (txn.get(naming(textName), 
                                   true, textClass, updatesSubscriber)).getValue();
            logger.log(Level.INFO, "{0} update", textName);
            commitTxn(txn);
            return text.getText();
        } catch (SwiftException e) {
            logger.warning(e.getMessage());
        } finally {
            if (txn != null && !txn.getStatus().isTerminated()) {
                txn.rollback();
            }
        }
        return null;
    }
    
    public void commit(String textName, String newValue) {
        TxnHandle txn = null;
        try {
            txn = server.beginTxn(isolationLevel, CachePolicy.STRICTLY_MOST_RECENT, false);
            TextualContent text = (TextualContent) (txn.get(naming(textName), 
                    true, textClass, updatesSubscriber)).getValue();
            text.set(newValue);
            logger.log(Level.INFO, "{0} commit", textName);
            txn.commit();        
        } catch (SwiftException e) {
            logger.warning(e.getMessage());
        } finally {
            if (txn != null && !txn.getStatus().isTerminated()) {
                txn.rollback();
            }
        }
    }

    private void commitTxn(final TxnHandle txn) {
        if (asyncCommit) {
            txn.commitAsync(null);
        } else {
            txn.commit();
        }
    }

}