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
package swift.clocks;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import swift.exceptions.IncompatibleTypeException;

/**
 * Class to represent common version vectors.
 * 
 * @author nmp
 */
// TODO: provide alternative implementation with explicit non-contigous events,
// rather than explicit exceptions.
public class VersionVectorWithExceptionsOld implements CausalityClock {

    private static final long serialVersionUID = 1L;
    protected Map<String, Set<Long>> excludedTimestamps;
    protected Map<String, Long> vv;

    protected VersionVectorWithExceptionsOld() {
        vv = new TreeMap<String, Long>();
        excludedTimestamps = new TreeMap<String, Set<Long>>();
    }

    protected VersionVectorWithExceptionsOld(VersionVectorWithExceptionsOld v) {
        vv = new TreeMap<String, Long>(v.vv);
        excludedTimestamps = new TreeMap<String, Set<Long>>(v.excludedTimestamps);
    }

    protected VersionVectorWithExceptionsOld(VersionVector v) {
        vv = new TreeMap<String, Long>(v.vv);
        excludedTimestamps = new TreeMap<String, Set<Long>>();
    }

    /**
     * Checks if a given event clock is reflected in this clock
     * 
     * @param c
     *            Event clock.
     * @return Returns true if the given event clock is included in this
     *         causality clock.
     */
    @Override
    public boolean includes(Timestamp cc) {
        Long i = vv.get(cc.getIdentifier());
        if (i == null || cc.getCounter() > i) {
            return false;
        }
        Set<Long> siteExcludes = excludedTimestamps.get(cc.getIdentifier());
        return siteExcludes == null || !siteExcludes.contains(cc.getCounter());
    }

    /**
     * Records an event.
     * 
     * @param cc
     *            Timestamp to insert.
     */
    public boolean record(Timestamp cc) {
        Set<Long> set = excludedTimestamps.get(cc.getIdentifier());
        if (set != null) {
            if (set.remove(cc.getCounter())) {
                return true;
            }
        }
        Long i = vv.get(cc.getIdentifier());
        long prev = i == null ? Timestamp.MIN_VALUE + 1 : i + 1;
        if (prev <= cc.getCounter()) {
            vv.put(cc.getIdentifier(), cc.getCounter());
        } else {
            return false;
        }
        long fol = cc.getCounter();
        if (prev <= fol) {
            return true;
        }
        if (set == null) {
            set = new TreeSet<Long>();
            excludedTimestamps.put(cc.getIdentifier(), set);
        }
        for (; prev < fol; prev++) {
            set.add(prev);
        }
        return true;
    }

    protected CMP_CLOCK mergeOneEntryVV(String siteid, long last, Set<Long> excluded) {
        Long i = vv.get(siteid);
        Set<Long> excludedThis = excludedTimestamps.get(siteid);
        if (i == null) {
            vv.put(siteid, last);
            if (excludedThis == null) {
                if (excluded != null) {
                    excludedTimestamps.put(siteid, new TreeSet<Long>(excluded));
                }
            } else { // should not happen !!!
                if (excluded != null) {
                    excludedThis.addAll(excluded);
                }
            }
            return CMP_CLOCK.CMP_ISDOMINATED;
        }
        long iL = i;

        boolean lessThan = false;
        boolean greaterThan = false;

        if (excludedThis != null) {
            // remove from excluded elements those that are reflected in cc
            Iterator<Long> itS = excludedThis.iterator();
            while (itS.hasNext()) {
                long l = itS.next();
                if (l <= last && excluded != null && !excluded.contains(new Long(l))) {
                    itS.remove();
                    lessThan = true;
                }
            }
        }
        if (excluded != null && last > iL) {
            // add excluded elements that are
            // larger than local max value
            // Iterator<Long> itS =
            // sThis.iterator();
            Iterator<Long> itS = excluded.iterator();
            while (itS.hasNext()) {
                long l = itS.next();
                if (l > iL) {
                    excludedThis.add(l);
                    greaterThan = true;
                }
            }
        }
        if (iL < last) {
            lessThan = true;
            vv.put(siteid, last);
        } else if (iL > last) {
            greaterThan = true;
        }

        if (greaterThan && lessThan) {
            return CMP_CLOCK.CMP_CONCURRENT;
        }
        if (greaterThan) {
            return CMP_CLOCK.CMP_DOMINATES;
        }
        if (lessThan) {
            return CMP_CLOCK.CMP_ISDOMINATED;
        }
        return CMP_CLOCK.CMP_EQUALS;
    }

    /**
     * Merge this clock with the given c clock.
     * 
     * @param c
     *            Clock to merge to
     * @return Returns one of the following, based on the initial value of
     *         clocks:<br>
     *         CMP_EQUALS : if clocks were equal; <br>
     *         CMP_DOMINATES : if this clock dominated the given c clock; <br>
     *         CMP_ISDOMINATED : if this clock was dominated by the given c
     *         clock; <br>
     *         CMP_CONCUREENT : if this clock and the given c clock were
     *         concurrent; <br>
     * @throws IncompatibleTypeException
     *             Case comparison cannot be made
     */
    protected CMP_CLOCK mergeVV(VersionVectorWithExceptionsOld cc) {
        CMP_CLOCK result = CMP_CLOCK.CMP_EQUALS;
        Iterator<Entry<String, Long>> it = cc.vv.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> e = it.next();
            CMP_CLOCK partialResult = mergeOneEntryVV(e.getKey(), e.getValue(), cc.excludedTimestamps.get(e.getKey()));
            result = ClockUtils.combineCmpClock(result, partialResult);
        }
        it = vv.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> e = it.next();
            Long i = cc.vv.get(e.getKey());
            if (i == null) {
                result = ClockUtils.combineCmpClock(result, CMP_CLOCK.CMP_DOMINATES);
                break;
            }
        }
        return result;
    }

    /**
     * Merge this clock with the given c clock.
     * 
     * @param c
     *            Clock to merge to
     * @return Returns one of the following, based on the initial value of
     *         clocks:<br>
     *         CMP_EQUALS : if clocks were equal; <br>
     *         CMP_DOMINATES : if this clock dominated the given c clock; <br>
     *         CMP_ISDOMINATED : if this clock was dominated by the given c
     *         clock; <br>
     *         CMP_CONCUREENT : if this clock and the given c clock were
     *         concurrent; <br>
     * @throws IncompatibleTypeException
     *             Case comparison cannot be made
     */
    public CMP_CLOCK merge(CausalityClock cc) {
        // if ( ! VersionVectorWithExceptions.class.equals(cc.getClass())) {
        // throw new IncompatibleTypeException();
        // }
        return mergeVV((VersionVectorWithExceptionsOld) cc);
    }

    /**
     * Intersect this clock with the given c clock.
     * 
     * @param c
     *            Clock to merge to
     * @return Returns one of the following, based on the initial value of
     *         clocks:<br>
     *         CMP_EQUALS : if clocks were equal; <br>
     *         CMP_DOMINATES : if this clock dominated the given c clock; <br>
     *         CMP_ISDOMINATED : if this clock was dominated by the given c
     *         clock; <br>
     *         CMP_CONCUREENT : if this clock and the given c clock were
     *         concurrent; <br>
     * @throws IncompatibleTypeException
     *             Case comparison cannot be made
     */
    @Override
    public CMP_CLOCK intersect(CausalityClock cc) {
        throw new RuntimeException("not implemented");
    }

    protected CMP_CLOCK compareOneEntryVV(String siteid, long last, Set<Long> excluded) {
        Long i = vv.get(siteid);
        Set<Long> excludedThis = excludedTimestamps.get(siteid);
        if (i == null) {
            return CMP_CLOCK.CMP_ISDOMINATED;
        }
        long iL = i;

        boolean lessThan = false;
        boolean greaterThan = false;

        if (excludedThis != null) {
            // remove from excluded elements those that are reflected in cc
            Iterator<Long> itS = excludedThis.iterator();
            while (itS.hasNext()) {
                long l = itS.next();
                if (l <= last && excluded != null && !excluded.contains(new Long(l))) {
                    lessThan = true;
                }
            }
        }
        if (excluded != null && last > iL) {
            // add excluded elements that are larger than local max value
            // Iterator<Long> itS = sThis.iterator();
            Iterator<Long> itS = excluded.iterator();
            while (itS.hasNext()) {
                long l = itS.next();
                if (l > iL) {
                    greaterThan = true;
                }
            }
        }
        if (iL < last) {
            lessThan = true;
        } else if (iL > last) {
            greaterThan = true;
        }
        if (greaterThan && lessThan) {
            return CMP_CLOCK.CMP_CONCURRENT;
        }
        if (greaterThan) {
            return CMP_CLOCK.CMP_DOMINATES;
        }
        if (lessThan) {
            return CMP_CLOCK.CMP_ISDOMINATED;
        }
        return CMP_CLOCK.CMP_EQUALS;
    }

    /**
     * compare this clock with the given c clock.
     * 
     * @param c
     *            Clock to compare to
     * @return Returns one of the following, based on the initial value of
     *         clocks:<br>
     *         CMP_EQUALS : if clocks were equal; <br>
     *         CMP_DOMINATES : if this clock dominated the given c clock; <br>
     *         CMP_ISDOMINATED : if this clock was dominated by the given c
     *         clock; <br>
     *         CMP_CONCUREENT : if this clock and the given c clock were
     *         concurrent; <br>
     * @throws IncompatibleTypeException
     *             Case comparison cannot be made
     */
    protected CMP_CLOCK compareVV(VersionVectorWithExceptionsOld cc) {
        CMP_CLOCK result = CMP_CLOCK.CMP_EQUALS;
        Iterator<Entry<String, Long>> it = cc.vv.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> e = it.next();
            CMP_CLOCK partialResult = compareOneEntryVV(e.getKey(), e.getValue(), cc.excludedTimestamps.get(e.getKey()));
            result = ClockUtils.combineCmpClock(result, partialResult);
        }
        it = vv.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> e = it.next();
            Long i = cc.vv.get(e.getKey());
            if (i == null) {
                result = ClockUtils.combineCmpClock(result, CMP_CLOCK.CMP_DOMINATES);
                break;
            }
        }
        return result;
    }

    // TODO: fix parametric types
    @Override
    public CMP_CLOCK compareTo(CausalityClock cc) {
        // if ( ! VersionVectorWithExceptions.class.equals(cc.getClass())) {
        // throw new IncompatibleTypeException();
        // }
        return compareVV((VersionVectorWithExceptionsOld) cc);
    }

    /**
     * Returns the most recent event for a given site. <br>
     * 
     * @param siteid
     *            Site identifier.
     * @return Returns an event clock.
     */
    public Timestamp getLatest(String siteid) {
        Long i = vv.get(siteid);
        if (i == null) {
            return new Timestamp(siteid, Timestamp.MIN_VALUE);
        } else {
            return new Timestamp(siteid, i);
        }
    }

    /**
     * Returns the most recent event for a given site. <br>
     * 
     * @param siteid
     *            Site identifier.
     * @return Returns an event clock.
     */
    public long getLatestCounter(String siteid) {
        Long i = vv.get(siteid);
        if (i == null) {
            return Timestamp.MIN_VALUE;
        } else {
            return i;
        }
    }

    @Override
    public boolean hasEventFrom(String siteid) {
        return getLatestCounter(siteid) != Timestamp.MIN_VALUE;
    }

    /**
     * Create a copy of this causality clock.
     */
    public CausalityClock clone() {
        return new VersionVectorWithExceptionsOld(this);
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("[");
        Iterator<Entry<String, Long>> it = vv.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> e = it.next();
            buf.append(e.getKey() + ":" + e.getValue());
            if (it.hasNext()) {
                buf.append(",");
            }
        }
        buf.append("]");
        buf.append("X:[" + excludedTimestamps + "]");
        return buf.toString();
    }

    public int getExceptionsNumber() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void drop(String siteId) {
        vv.remove(siteId);
        excludedTimestamps.remove(siteId);
    }

    @Override
    public void drop(final Timestamp timestamp) {
        if (!includes(timestamp)) {
            return;
        }

        final String id = timestamp.getIdentifier();
        Set<Long> excludes = excludedTimestamps.get(id);
        if (excludes == null) {
            excludes = new TreeSet<Long>();
            excludedTimestamps.put(id, excludes);
        }
        excludes.add(timestamp.getCounter());

        // Garbage collect VV entry fully covered with exceptions.
        // TODO: implement more GC covering all cases if needed
        if (excludes.size() == getLatestCounter(id)) {
            drop(id);
        }
    }

    @Override
    public void recordAllUntil(Timestamp timestamp) {
        final Long maxValueForEntry = vv.get(timestamp.getIdentifier());
        if (maxValueForEntry == null || maxValueForEntry < timestamp.getCounter()) {
            vv.put(timestamp.getIdentifier(), timestamp.getCounter());
        }
        final Iterator<Long> exceptionsIter = excludedTimestamps.get(timestamp.getIdentifier()).iterator();
        while (exceptionsIter.hasNext()) {
            if (exceptionsIter.next() < timestamp.getCounter()) {
                exceptionsIter.remove();
            } else {
                // Optimization: make use of the fact that the set is ordered.
                break;
            }
        }
    }

    @Override
    public Object copy() {
        return new VersionVectorWithExceptionsOld(this);
    }

    /**
     * Trim this clock so that all events recorded are consecutive.
     */
    @Override
    public void trim() {
        // FIXME !!!
        throw new RuntimeException("not implemented");
    }

    @Override
    public int getSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Set<String> getSiteIds() {
        // TODO Auto-generated method stub
        return null;
    }

    // @Override
    // public CausalityClock retain(String siteId) {
    // throw new RuntimeException("Not implemented...");
    // }
    //
    // @Override
    // public CausalityClock retain(CausalityClock cc) {
    // throw new RuntimeException("Not implemented...");
    // }

}
