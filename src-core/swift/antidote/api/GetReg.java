package swift.antidote.api;

import com.basho.riak.client.api.RiakCommand;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakFuture;
import swift.antidote.operations.GetRegOperation;

import java.nio.ByteBuffer;

public class GetReg extends RiakCommand<ByteBuffer, Void> {

    private final ByteBuffer key;

    public GetReg(ByteBuffer key) {
        this.key = key;
    }

    @Override
    protected RiakFuture<ByteBuffer, Void> executeAsync(RiakCluster riakCluster) {
        return riakCluster.execute(new GetRegOperation(key));
    }
}
