package swift.antidote.api;

import com.basho.riak.client.api.RiakCommand;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakFuture;
import swift.antidote.operations.SetRegOperation;

import java.nio.ByteBuffer;

public class SetReg extends RiakCommand<Void, Void> {

    private final ByteBuffer key;
    private final ByteBuffer payload;

    public SetReg(ByteBuffer key, ByteBuffer payload) {
        this.key = key;
        this.payload = payload;
    }

    @Override
    protected RiakFuture<Void, Void> executeAsync(RiakCluster cluster) {
        return cluster.execute(new SetRegOperation(key, payload));
    }
}
