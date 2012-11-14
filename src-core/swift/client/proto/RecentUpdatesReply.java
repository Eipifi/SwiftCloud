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
package swift.client.proto;

import java.util.List;
import java.util.Map;

import swift.clocks.CausalityClock;
import swift.crdt.CRDTIdentifier;
import swift.crdt.operations.CRDTObjectUpdatesGroup;
import sys.net.api.rpc.RpcHandle;
import sys.net.api.rpc.RpcHandler;
import sys.net.api.rpc.RpcMessage;

/**
 * Server reply to recent updates request, a summary of all subscription changes
 * and updates since the last message.
 * 
 * @author mzawirski
 */
public class RecentUpdatesReply implements RpcMessage {
    public enum SubscriptionStatus {
        /**
         * Subscriptions active at the time of last communication specified in
         * the request ({@link RecentUpdatesRequest#getLastClock()}) are still
         * active.
         */
        ACTIVE,
        /**
         * Previous subscriptions are lost due to timeout or lost message,
         * client should renew all subscriptions.
         */
        LOST
        // TODO(mzawirski): a more involved protocol could distinguish these two
        // cases and deal with them more efficiently; let's keep it as a
        // possible optimization
    }

    protected SubscriptionStatus status;
    protected Map<CRDTIdentifier, CausalityClock> newlyConfirmedSubscriptions;
    protected List<CRDTObjectUpdatesGroup> updates;
    protected CausalityClock clock;

    /**
     * No-args constructor for Kryo-serialization.
     */
    RecentUpdatesReply() {
    }

    public RecentUpdatesReply(SubscriptionStatus status,
            Map<CRDTIdentifier, CausalityClock> newlyConfirmedSubscriptions, List<CRDTObjectUpdatesGroup> updates,
            CausalityClock clock) {
        this.status = status;
        this.newlyConfirmedSubscriptions = newlyConfirmedSubscriptions;
        this.updates = updates;
        this.clock = clock;
    }

    /**
     * @return status of subscription
     */
    public SubscriptionStatus getStatus() {
        return status;
    }

    /**
     * @return confirmation of successful new (or needlessly renewed)
     *         subscriptions since the last message, triggered by
     *         {@link FetchObjectVersionRequest#getSubscriptionType()};
     *         map of object identifier to the first clock covered by this and
     *         future notifications, i.e. client will be informed about all
     *         updates since that clock; null in case of no subscriptions;
     *         meaningless if status is {@link SubscriptionStatus#LOST}
     */
    public Map<CRDTIdentifier, CausalityClock> getNewlyConfirmedSubscriptions() {
        return newlyConfirmedSubscriptions;
    }

    /**
     * @return true if this notification confirms any new subscription
     * @see #getNewlyConfirmedSubscriptions()
     */
    public boolean hasNewlyConfirmedSubscriptions() {
        return newlyConfirmedSubscriptions != null;
    }

    /**
     * @return the latest clock for which every update on subscribed objects was
     *         sent; more precisely, all updates for every subscribed object
     *         performed between {@link RecentUpdatesRequest#getLastClock()}
     *         exclusive (or {@link #getNewlyConfirmedSubscriptions()} clock for
     *         a newly subscribed object) and this clock inclusive are included
     *         in this message; meaningless if status is
     *         {@link SubscriptionStatus#LOST}
     */
    public CausalityClock getClock() {
        return clock;
    }

    /**
     * @return list of updates on subscribed objects; null in case of no
     *         updates; the order in the list is a linear extension of the
     *         causal order; meaningless if status is
     *         {@link SubscriptionStatus#LOST}
     */
    public List<CRDTObjectUpdatesGroup> getUpdates() {
        return updates;
    }

    /**
     * @return true if the message contains any updates
     */
    public boolean hasUpdates() {
        return !updates.isEmpty();
    }

    @Override
    public void deliverTo(RpcHandle conn, RpcHandler handler) {
        ((RecentUpdatesReplyHandler) handler).onReceive(conn, this);
    }
}