package swift.crdt;

import swift.clocks.TripleTimestamp;
import swift.crdt.core.CRDTUpdate;

public class PutOnlyLWWMapUpdate<K, V, T extends AbstractPutOnlyLWWMapCRDT<K, V, T>> implements CRDTUpdate<T> {
    protected K key;
    protected V val;
    protected long registerTimestamp;
    protected TripleTimestamp tiebreakingTimestamp;

    // required for kryo
    public PutOnlyLWWMapUpdate() {
    }

    public PutOnlyLWWMapUpdate(K key, long registerTimestamp, TripleTimestamp tiebreakingTimestamp, V val) {
        this.key = key;
        this.registerTimestamp = registerTimestamp;
        this.tiebreakingTimestamp = tiebreakingTimestamp;
        this.val = val;
    }

    @Override
    public void applyTo(T map) {
        map.applyPut(key, registerTimestamp, tiebreakingTimestamp, val);
    }

    @Override
    public Object getValueWithoutMetadata() {
        // TODO: check if it works with Kryo
        return new Object[] { key, val };
    }
}
