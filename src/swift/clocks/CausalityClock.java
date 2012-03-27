package swift.clocks;

import java.io.Serializable;

/**
 * Interface for clocks that allow to trace causality, such as version vector
 * and dotted version vectors
 * 
 * @author nmp
 */
public interface CausalityClock extends Serializable {
    enum CMP_CLOCK {
        CMP_EQUALS, CMP_DOMINATES, CMP_ISDOMINATED, CMP_CONCURRENT
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
