package swift.antidote.api;

import com.basho.riak.client.api.RiakCommand;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakFuture;
import swift.antidote.operations.GetCounterOperation;

import java.nio.ByteBuffer;

public class GetCounter extends RiakCommand<Integer, Void> {

    private final ByteBuffer key;

    public GetCounter(ByteBuffer key) {
        this.key = key;
    }

    @Override
    protected RiakFuture<Integer, Void> executeAsync(RiakCluster cluster) {
        return cluster.execute(new GetCounterOperation(key));
    }
}
