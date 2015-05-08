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
package sys;

import swift.utils.KryoCRDTRegistry;
import sys.net.impl.KryoLib;
import sys.utils.IP;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class Sys {
    private static Sys instance;

    public Random rg = new Random();
    public AtomicLong uploadedBytes = new AtomicLong(1);
    public AtomicLong downloadedBytes = new AtomicLong(1);
    public String mainClass;

    protected Sys() {
        StackTraceElement[] sta = Thread.currentThread().getStackTrace();
        mainClass = sta[sta.length - 1].getClassName() + " @ " + IP.localHostname();
        KryoLib.submit("default", new KryoCRDTRegistry());
    }

    synchronized public static Sys getInstance() {
        if (instance == null) instance = new Sys();
        return instance;
    }

    private double T0n = System.nanoTime();
    private long T0m = System.currentTimeMillis();

    private static final double NANOSECOND = 1e-9;

    public double currentTime() {
        return (System.nanoTime() - T0n) * NANOSECOND;
    }

    public long timeMillis() {
        return System.currentTimeMillis() - T0m;
    }
}
