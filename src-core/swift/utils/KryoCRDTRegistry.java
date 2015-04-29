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
package swift.utils;

import swift.clocks.Timestamp;
import swift.clocks.TimestampMapping;
import swift.clocks.TripleTimestamp;
import swift.clocks.VersionVectorWithExceptions;
import swift.crdt.*;
import swift.crdt.core.CRDTIdentifier;
import swift.crdt.core.CRDTObjectUpdatesGroup;
import swift.crdt.core.ManagedCRDT;
import swift.proto.*;
import swift.pubsub.BatchUpdatesNotification;
import swift.pubsub.UpdateNotification;
import sys.net.impl.KryoClassRegistry;

import java.util.*;

public class KryoCRDTRegistry extends KryoClassRegistry {
    
    @Override
    public void registerClasses(Registrable reg) {
        // Java primitives
        reg.register(ArrayList.class);
        reg.register(LinkedList.class);
        reg.register(TreeMap.class);
        reg.register(HashMap.class);
        reg.register(Map.Entry.class);
        reg.register(TreeSet.class);
        reg.register(Timestamp.class);
        reg.register(TripleTimestamp.class);
        reg.register(VersionVectorWithExceptions.class);
        reg.register(VersionVectorWithExceptions.Interval.class);
        reg.register(TimestampMapping.class);
        reg.register(CRDTIdentifier.class);
        reg.register(ManagedCRDT.class);
        reg.register(CRDTObjectUpdatesGroup.class);
        reg.register(CommitTSRequest.class);
        reg.register(CommitTSReply.class);
        reg.register(DHTExecCRDT.class);
        reg.register(DHTExecCRDTReply.class);
        reg.register(DHTGetCRDT.class);
        reg.register(DHTGetCRDTReply.class);
        reg.register(ClientRequest.class);
        reg.register(CommitUpdatesRequest.class);
        reg.register(CommitUpdatesReply.class);
        reg.register(CommitUpdatesReply.CommitStatus.class);
        reg.register(BatchCommitUpdatesRequest.class);
        reg.register(BatchCommitUpdatesReply.class);
        reg.register(BatchUpdatesNotification.class);
        reg.register(UpdateNotification.class);
        reg.register(BatchFetchObjectVersionRequest.class);
        reg.register(BatchFetchObjectVersionReply.class);
        reg.register(BatchFetchObjectVersionReply.FetchStatus.class);
        reg.register(LatestKnownClockRequest.class);
        reg.register(LatestKnownClockReply.class);
        reg.register(UnsubscribeUpdatesRequest.class);
        reg.register(IntegerCRDT.class);
        reg.register(IntegerUpdate.class);
        reg.register(AbstractLWWRegisterCRDT.class);
        reg.register(LWWRegisterUpdate.class);
        reg.register(LWWRegisterCRDT.class);
        reg.register(LWWStringRegisterCRDT.class);
        reg.register(LWWStringRegisterUpdate.class);
        reg.register(LWWStringMapRegisterCRDT.class);
        reg.register(LWWStringMapRegisterUpdate.class);
        reg.register(AbstractPutOnlyLWWMapCRDT.class);
        reg.register(AbstractPutOnlyLWWMapCRDT.LWWEntry.class);
        reg.register(PutOnlyLWWMapUpdate.class);
        reg.register(PutOnlyLWWMapCRDT.class);
        reg.register(PutOnlyLWWStringMapCRDT.class);
        reg.register(PutOnlyLWWStringMapUpdate.class);
        reg.register(AbstractAddOnlySetCRDT.class);
        reg.register(AddOnlySetUpdate.class);
        reg.register(AddOnlySetCRDT.class);
        reg.register(AddOnlyStringSetCRDT.class);
        reg.register(AddOnlyStringSetUpdate.class);
        reg.register(AbstractAddWinsSetCRDT.class);
        reg.register(AddWinsSetUpdate.class);
        reg.register(AddWinsSetCRDT.class);
        reg.register(PubSubHandshake.class);
        reg.register(PubSubHandshakeReply.class);
    }
}
