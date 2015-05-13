package swift.antidote.operations;

import com.basho.riak.client.core.RiakMessage;
import com.basho.riak.client.core.operations.Operations;
import com.google.protobuf.ByteString;
import swift.antidote.AntidoteMessageCodes;
import swift.antidote.pb.AntidotePB;
import swift.antidote.utils.SimpleFutureOperation;

import java.nio.ByteBuffer;
import java.util.List;

public class SetRegOperation extends SimpleFutureOperation<Void, Void> {

    private final ByteBuffer key;
    private final ByteBuffer payload;

    public SetRegOperation(ByteBuffer key, ByteBuffer payload) {
        this.key = key;
        this.payload = payload;
    }

    @Override
    protected Void convert(List<Void> rawResponse) {
        return null;
    }

    @Override
    protected RiakMessage createChannelMessage() {
        return new RiakMessage(AntidoteMessageCodes.MSG_UpdateRegReq,
                AntidotePB.FpbUpdateRegReq.newBuilder()
                        .setKey(ByteString.copyFrom(key))
                        .setValue(ByteString.copyFrom(payload)).build().toByteArray());
    }

    @Override
    protected Void decode(RiakMessage rawMessage) {
        Operations.checkMessageType(rawMessage, AntidoteMessageCodes.MSG_OperationResp);
        return null;
    }
}
