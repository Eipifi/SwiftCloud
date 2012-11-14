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
package sys.pubsub;

import sys.net.api.Endpoint;

abstract public class PubSub<K,P> {

	public interface Handler<K,P> {
		void notify( final K key, final P info);
	}

	public abstract void publish(K key, P info);

	public abstract void subscribe(K key, Handler<K,P> handler);

	public abstract void unsubscribe(K key, Handler<K,P> handler);
	
	public abstract void addRemoteSubscriber(K key, Endpoint subscriber);
}