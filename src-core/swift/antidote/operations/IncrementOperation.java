package swift.antidote.operations;

import com.basho.riak.client.core.RiakMessage;
import com.basho.riak.client.core.operations.Operations;
import com.google.protobuf.ByteString;
import swift.antidote.AntidoteMessageCodes;
import swift.antidote.pb.AntidotePB;
import swift.antidote.utils.SimpleFutureOperation;

import java.nio.ByteBuffer;
import java.util.List;

public class IncrementOperation extends SimpleFutureOperation<Void, Void> {

    private final ByteBuffer key;
    private final int value;

    public IncrementOperation(ByteBuffer key, int value) {
        this.key = key;
        this.value = value;
    }

    @Override
    protected Void convert(List<Void> rawResponse) { return null; }

    @Override
    protected RiakMessage createChannelMessage() {
        return new RiakMessage(AntidoteMessageCodes.MSG_IncrementReq,
                AntidotePB.FpbIncrementReq.newBuilder()
                        .setKey(ByteString.copyFrom(key))
                        .setAmount(value)
                .build().toByteArray()
        );
    }

    @Override
    protected Void decode(RiakMessage rawMessage) {
        Operations.checkMessageType(rawMessage, AntidoteMessageCodes.MSG_OperationResp);
        return null;
    }
}
