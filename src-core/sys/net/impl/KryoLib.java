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
package sys.net.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import sys.net.impl.providers.InitiatorInfo;
import sys.net.impl.rpc.RpcPacket;

import java.util.HashMap;
import java.util.Map;

public class KryoLib {

    private static final Map<Integer, Entry> entries = new HashMap<>();
    private static final ThreadLocal<Kryo> localKryoReset = ThreadLocal.withInitial(() -> newInstance(true));
    private static final ThreadLocal<Kryo> localKryoNoReset = ThreadLocal.withInitial(() -> newInstance(false));

    public static <T> void register(Class<T> clazz, Serializer<? super T> serializer, int id) {
        if(entries.containsKey(id)) { throw new IllegalArgumentException("Type ID already taken"); }
        Entry<T> e = new Entry<>();
        e.clazz = clazz;
        e.serializer = serializer;
        entries.put(id, e);
    }

    public static void register(Class<?> clazz, int id) {
        register(clazz, null, id);
    }

    private static Kryo newInstance(boolean autoReset) {
        Kryo kryo = new Kryo();
        for(Map.Entry<Integer, Entry> e: entries.entrySet()) {
            Entry entry = e.getValue();
            if (entry.serializer == null)
                kryo.register(entry.clazz, e.getKey());
            else
                kryo.register(entry.clazz, entry.serializer, e.getKey());
        }
        kryo.setAsmEnabled(true);
        kryo.setReferences(true);
        kryo.setAutoReset(autoReset);
        return kryo;
    }

    private static class Entry<T> {
        Class<T> clazz;
        Serializer<? super T> serializer;
    }

    synchronized public static <T> T copy(T obj) {
        return kryo().copy(obj);
    }

    synchronized public static <T> T copyShallow(T obj) {
        return kryo().copyShallow(obj);
    }

    public static Kryo kryo() { return localKryoReset.get(); }

    public static Kryo kryoWithoutAutoReset() { return localKryoNoReset.get(); }

    synchronized public static void init() {
        register(LocalEndpoint.class, new EndpointSerializer(), 0x20);
        register(RemoteEndpoint.class, new EndpointSerializer(), 0x21);
        register(RpcPacket.class, 0x22);
        register(InitiatorInfo.class, 0x23);
    }
}
