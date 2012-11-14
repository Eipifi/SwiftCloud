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
package swift.client;

import java.util.HashMap;
import java.util.Map;

import swift.clocks.CausalityClock;
import swift.clocks.TimestampMapping;
import swift.crdt.CRDTIdentifier;
import swift.crdt.interfaces.CRDT;
import swift.crdt.interfaces.CachePolicy;
import swift.crdt.interfaces.IsolationLevel;
import swift.crdt.interfaces.ObjectUpdatesListener;
import swift.crdt.interfaces.TxnHandle;
import swift.crdt.interfaces.TxnLocalCRDT;
import swift.exceptions.NetworkException;
import swift.exceptions.NoSuchObjectException;
import swift.exceptions.VersionNotFoundException;
import swift.exceptions.WrongTypeException;
import swift.utils.TransactionsLog;

/**
 * Implementation of {@link IsolationLevel#SNAPSHOT_ISOLATION} transaction,
 * which always read from a consistent snapshot.
 * 
 * @author mzawirski
 */
class SnapshotIsolationTxnHandle extends AbstractTxnHandle implements TxnHandle {
    final Map<CRDTIdentifier, TxnLocalCRDT<?>> objectViewsCache;

    /**
     * Creates update transaction.
     * 
     * @param manager
     *            manager maintaining this transaction
     * @param sessionId
     *            id of the client session issuing this transaction
     * @param durableLog
     *            durable log for recovery
     * @param cachePolicy
     *            cache policy used by this transaction
     * @param timestampMapping
     *            timestamp and timestamp mapping information used for all
     *            update of this transaction
     * @param snapshotClock
     *            clock representing committed update transactions visible to
     *            this transaction; left unmodified
     */
    SnapshotIsolationTxnHandle(final TxnManager manager, final String sessionId, final TransactionsLog durableLog,
            final CachePolicy cachePolicy, final TimestampMapping timestampMapping, final CausalityClock snapshotClock) {
        super(manager, sessionId, durableLog, cachePolicy, timestampMapping);
        this.objectViewsCache = new HashMap<CRDTIdentifier, TxnLocalCRDT<?>>();
        updateUpdatesDependencyClock(snapshotClock);
    }

    /**
     * Creates read-only transaction.
     * 
     * @param manager
     *            manager maintaining this transaction
     * @param sessionId
     *            id of the client session issuing this transaction
     * @param cachePolicy
     *            cache policy used by this transaction
     * @param snapshotClock
     *            clock representing committed update transactions visible to
     *            this transaction; left unmodified
     */
    SnapshotIsolationTxnHandle(final TxnManager manager, final String sessionId, final CachePolicy cachePolicy,
            final CausalityClock snapshotClock) {
        super(manager, sessionId, cachePolicy);
        this.objectViewsCache = new HashMap<CRDTIdentifier, TxnLocalCRDT<?>>();
        updateUpdatesDependencyClock(snapshotClock);
    }

    @Override
    protected <V extends CRDT<V>, T extends TxnLocalCRDT<V>> T getImpl(CRDTIdentifier id, boolean create,
            Class<V> classOfV, ObjectUpdatesListener updatesListener) throws WrongTypeException, NoSuchObjectException,
            VersionNotFoundException, NetworkException {
        TxnLocalCRDT<V> localView = (TxnLocalCRDT<V>) objectViewsCache.get(id);
        if (localView != null && updatesListener != null) {
            // force another read to install the listener and discard it
            manager.getObjectVersionTxnView(this, id, localView.getClock(), create, classOfV, updatesListener);
        }
        if (localView == null) {
            localView = manager.getObjectVersionTxnView(this, id, getUpdatesDependencyClock(), create, classOfV,
                    updatesListener);
            objectViewsCache.put(id, localView);
        }
        return (T) localView;
    }
}