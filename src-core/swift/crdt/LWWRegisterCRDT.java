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
package swift.crdt;

import swift.clocks.CausalityClock;
import swift.crdt.core.CRDTIdentifier;
import swift.crdt.core.TxnHandle;

/**
 * Generic register CRDT with Last Writer Wins resolution policy for concurrent
 * assignments. WARNING: When assigning to a register, make sure that the
 * elements in the set are either immutable or that they are cloned!
 * 
 * 
 * @param <V>
 *            type of the content of a register
 * @author mzawirsk
 */
// TODO: it used to be that Register's content was copyable, but nothing was
// copied actually in the implementation? Current solution is consistent with
// Set CRDTs, but perhaps suboptimal - we could enforce clonability of V and
// clone values just after the API calls.
public class LWWRegisterCRDT<V> extends AbstractLWWRegisterCRDT<V, LWWRegisterCRDT<V>> {
    // Kryo
    public LWWRegisterCRDT() {
    }

    public LWWRegisterCRDT(CRDTIdentifier uid) {
        super(uid);
    }

    private LWWRegisterCRDT(CRDTIdentifier id, TxnHandle txn, CausalityClock clock,
            LWWRegisterUpdate<V, LWWRegisterCRDT<V>> lastUpdate) {
        super(id, txn, clock, lastUpdate);
    }

    @Override
    public LWWRegisterCRDT<V> copy() {
        return new LWWRegisterCRDT<V>(id, txn, clock, lastUpdate);
    }
}
