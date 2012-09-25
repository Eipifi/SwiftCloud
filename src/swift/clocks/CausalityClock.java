package swift.clocks;

import java.io.Serializable;

/**
 * Interface for clocks that allow to trace causality, such as version vector
 * and dotted version vectors
 * 
 * @author nmp
 */
// TODO: Create read-only (parent) interface or decorator.
public interface CausalityClock extends Serializable {
    enum CMP_CLOCK {
        CMP_EQUALS, CMP_DOMINATES, CMP_ISDOMINATED, CMP_CONCURRENT;

        /**
         * Syntactic sugar for frequent checks if comparison result is one of
         * the desired cases.
         * 
         * @param expectedResults
         *            one of the expected comparison results
         * @return true when the result is one of expectedResults
         */
        public boolean is(final CMP_CLOCK... expectedResults) {
            for (final CMP_CLOCK cmp : expectedResults) {
                if (this == cmp) {
                    return true;
                }
            }
            return false;
        }
    };

    /**
     * Records the given event. Assume the timestamp can be recorded in the
     * given version vector.
     * 
     * @param ec
     *            Event clock.
     * @return Returns false if the object was already recorded.
     */
    boolean record(Timestamp ec);

    /**
     * Records all events from the source of a given timestamp until the
     * timestamp.
     * <p>
     * E.g. recordAllUntil(siteId="abc", counter=3) causes events ("abc", 1),
     * ("abc", 2), ("abc", 3) to be recorded.
     * 
     * @param timestamp the highest event timestamp to record for the source
     */
    void recordAllUntil(Timestamp timestamp);

    /**
     * Checks if a given event clock is reflected in this clock
     * 
     * @param t
     *            Event clock.
     * @return Returns true if the given event clock is included in this
     *         causality clock.
     */
    boolean includes(Timestamp t);

    /**
     * Returns the most recent event for a given site. <br>
     * 
     * @param siteid
     *            Site identifier.
     * @return Returns an event clock.
     */
    Timestamp getLatest(String siteid);

    /**
     * Returns the most recent event for a given site. <br>
     * 
     * @param siteid
     *            Site identifier.
     * @return Returns an event clock.
     */
    long getLatestCounter(String siteid);

    /**
     * Checks whether the clock contains any event recorder at the provided
     * site.
     * 
     * @param siteid
     *            Site identifier.
     * @return true if the clock contains any even from the provided site.
     */
    boolean hasEventFrom(String siteid);

    /**
     * Removes all events generated by a given site from the clock.
     * <p>
     * Use with CAUTION: this is the only method allowing CausalityClock to
     * degrade in the order, i.e. the output state is dominated by the prior
     * state!
     * 
     * @param siteId
     *            site identifier
     */
    void drop(String siteId);

    /**
     * Removes a single timestamp from the clock if it includes the timestamp.
     * 
     * @param timestamp
     *            timestamp to remove from the clock
     * @throws UnsupportedOperationException
     *             when operation is not supported by this clock implementation
     */
    void drop(Timestamp timestamp);

    /**
     * Compares two causality clock.
     * 
     * @param c
     *            Clock to compare to
     * @return Returns one of the following:<br>
     *         CMP_EQUALS : if clocks are equal; <br>
     *         CMP_DOMINATES : if this clock dominates the given c clock; <br>
     *         CMP_ISDOMINATED : if this clock is dominated by the given c
     *         clock; <br>
     *         CMP_CONCUREENT : if this clock and the given c clock are
     *         concurrent; <br>
     */
    CMP_CLOCK compareTo(CausalityClock c);

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
     */
    CMP_CLOCK merge(CausalityClock c);

    /**
     * Create a copy of this causality clock.
     */
    CausalityClock clone();

    /**
     * Test if version vector has exceptions or holes.
     * 
     * @return true if there are exceptions
     */
    boolean hasExceptions();
}
