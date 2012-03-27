package swift.test.crdt;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import swift.clocks.ClockFactory;
import swift.crdt.RegisterTxnLocal;
import swift.crdt.RegisterVersioned;
import swift.crdt.operations.RegisterUpdate;
import swift.exceptions.ConsistentSnapshotVersionNotFoundException;
import swift.exceptions.NoSuchObjectException;
import swift.exceptions.WrongTypeException;

public class RegisterMergeTest {
    RegisterVersioned<Integer> i1, i2;
    TxnHandleForTestingLocalBehaviour txn1, txn2;

    private <V> RegisterTxnLocal<V> getTxnLocal(RegisterVersioned<V> i, TxnHandleForTestingLocalBehaviour txn) {
        return (RegisterTxnLocal<V>) i.getTxnLocalCopy(i.getClock(), txn);
    }

    private void printInformtion(RegisterVersioned<?> i, TxnHandleForTestingLocalBehaviour txn) {
        System.out.println(getTxnLocal(i, txn).get());
        System.out.println(i.getClock());
        System.out.println(txn.getClock());
        System.out.println(i);
    }

    private void merge() {
        i1.merge(i2);
        txn1.updateClock(txn2.getClock());
    }

    private <V> void registerUpdate(V value, RegisterVersioned<V> i, TxnHandleForTestingLocalBehaviour txn) {
        txn1.registerOperation(i, new RegisterUpdate<V>(txn.nextTimestamp(), value));
    }

    @Before
    public void setUp() throws WrongTypeException, NoSuchObjectException, ConsistentSnapshotVersionNotFoundException {
        i1 = new RegisterVersioned<Integer>();
        i1.setClock(ClockFactory.newClock());
        i1.setPruneClock(ClockFactory.newClock());

        i2 = new RegisterVersioned<Integer>();
        i2.setClock(ClockFactory.newClock());
        i2.setPruneClock(ClockFactory.newClock());
        txn1 = new TxnHandleForTestingLocalBehaviour("client1", ClockFactory.newClock());
        txn2 = new TxnHandleForTestingLocalBehaviour("client2", ClockFactory.newClock());
    }

    // Merge with empty set
    @Test
    public void mergeEmpty1() {
        i1.executeOperation(new RegisterUpdate<Integer>(txn1.nextTimestamp(), 5));
        printInformtion(i1, txn1);
        i1.merge(i2);
        printInformtion(i1, txn1);

        assertTrue(getTxnLocal(i1, txn1).get() == 5);
    }

    // Merge with empty set
    @Test
    public void mergeEmpty2() {
        registerUpdate(5, i2, txn2);
        i1.merge(i2);
        assertTrue(getTxnLocal(i1, txn1).get() == 5);
    }

    @Test
    public void mergeNonEmpty() {
        registerUpdate(5, i1, txn1);
        registerUpdate(6, i2, txn2);
        i1.merge(i2);
        assertTrue(getTxnLocal(i1, txn1).get() == 6 || getTxnLocal(i1, txn1).get() == 5);
    }

    @Test
    public void mergeMultiple() {
        registerUpdate(5, i2, txn2);
        registerUpdate(6, i2, txn2);
        i1.merge(i2);
        assertTrue(getTxnLocal(i1, txn1).get() == 6);
    }

    @Test
    public void mergeConcurrentAddRem() {
        registerUpdate(5, i1, txn1);
        registerUpdate(-5, i2, txn2);
        merge();
        assertTrue(getTxnLocal(i1, txn1).get() == -5 || getTxnLocal(i1, txn1).get() == 5);

        registerUpdate(2, i1, txn1);
        assertTrue(getTxnLocal(i1, txn1).get() == 2);
    }

    @Test
    public void mergeConcurrentCausal() {
        registerUpdate(1, i1, txn1);
        registerUpdate(-1, i1, txn1);
        registerUpdate(2, i2, txn2);
        merge();
        printInformtion(i1, txn1);
        assertTrue(getTxnLocal(i1, txn1).get() == 2 || getTxnLocal(i1, txn1).get() == -1);
    }

    @Test
    public void mergeConcurrentCausal2() {
        registerUpdate(1, i1, txn1);
        registerUpdate(-1, i1, txn1);
        registerUpdate(2, i2, txn2);
        registerUpdate(-2, i2, txn2);
        merge();
        assertTrue(getTxnLocal(i1, txn1).get() == -1 || getTxnLocal(i1, txn1).get() == -2);

        registerUpdate(-5, i2, txn2);
        merge();
        assertTrue(getTxnLocal(i1, txn1).get() == -5);
    }

    // TODO Tests for prune

}