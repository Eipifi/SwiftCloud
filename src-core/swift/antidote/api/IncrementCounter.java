package swift.antidote.api;

import com.basho.riak.client.api.RiakCommand;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakFuture;
import swift.antidote.operations.IncrementOperation;

import java.nio.ByteBuffer;

public class IncrementCounter extends RiakCommand<Void, Void> {

    private final ByteBuffer key;
    private final int value;

    public IncrementCounter(ByteBuffer key, int value) {
        this.key = key;
        this.value = value;
    }

    @Override
    protected RiakFuture<Void, Void> executeAsync(RiakCluster cluster) {
        return cluster.execute(new IncrementOperation(key, value));
    }
}
