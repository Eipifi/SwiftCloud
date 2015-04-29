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

import java.util.*;

/**
 * KryoLib oversees the serialization process. Each class must be registered
 * prior to system run. In order to ensure unique and deterministic class-id associations,
 * class registrations must be performed through registries (see KryoClassRegistry).
 *
 * Submissions will be automatically committed when the first Kryo instance is created.
 */
public class KryoLib {

    private static List<Entry> entries = null;

    private static final TreeMap<String, KryoClassRegistry> registries = new TreeMap<>();
    private static final ThreadLocal<Kryo> localKryoReset = ThreadLocal.withInitial(() -> newInstance(true));
    private static final ThreadLocal<Kryo> localKryoNoReset = ThreadLocal.withInitial(() -> newInstance(false));

    static {
        submit("net", new KryoNetRegistry());
    }

    /**
     * Traverses all submitted registries in deterministic order and associates identifiers to classes.
     */
    private synchronized static void commitRegistries() {
        if (entries == null) {
            entries = new LinkedList<>();
            registries.forEach((n,r) -> r.registerClasses(KryoLib::register));
        }
    }

    private static <T> void register(Class<T> clazz, Serializer<? super T> serializer) {
        Entry<T> e = new Entry<>();
        e.clazz = clazz;
        e.serializer = serializer;
        entries.add(e);
    }

    private static Kryo newInstance(boolean autoReset) {
        commitRegistries();
        Kryo kryo = new Kryo();
        int counter = 0;
        for (Entry<?> e: entries) {
            if (e.serializer == null) kryo.register(e.clazz, ++counter);
            else kryo.register(e.clazz, e.serializer, ++counter);
        }
        kryo.setAsmEnabled(true);
        kryo.setReferences(true);
        kryo.setAutoReset(autoReset);
        return kryo;
    }

    private static class Entry<T> { // Java devs, why don't we have the Pair<K,V>?
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

    public static synchronized void submit(String name, KryoClassRegistry registry) {
        if(entries != null) throw new IllegalStateException("Registries already committed");
        if(registries.containsKey(name)) throw new IllegalArgumentException("Registry name already in use");
        registries.put(name, registry);
    }
}
