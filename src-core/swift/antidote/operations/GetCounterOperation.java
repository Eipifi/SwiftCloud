package swift.antidote.operations;

import com.basho.riak.client.core.RiakMessage;
import com.basho.riak.client.core.operations.Operations;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import swift.antidote.AntidoteMessageCodes;
import swift.antidote.pb.AntidotePB;
import swift.antidote.utils.SimpleFutureOperation;

import java.nio.ByteBuffer;
import java.util.List;

public class GetCounterOperation extends SimpleFutureOperation<Integer, AntidotePB.FpbGetCounterResp> {

    private final ByteBuffer key;

    public GetCounterOperation(ByteBuffer key) {
        this.key = key;
    }


    @Override
    protected Integer convert(List<AntidotePB.FpbGetCounterResp> rawResponse) {
        AntidotePB.FpbGetCounterResp rr = rawResponse.get(0);
        return rr.hasValue() ? rr.getValue() : null;
    }

    @Override
    protected RiakMessage createChannelMessage() {
        return new RiakMessage(AntidoteMessageCodes.MSG_GetCounterReq, AntidotePB.FpbGetCounterReq.newBuilder().setKey(ByteString.copyFrom(key)).build().toByteArray());
    }

    @Override
    protected AntidotePB.FpbGetCounterResp decode(RiakMessage rawMessage) {
        Operations.checkMessageType(rawMessage, AntidoteMessageCodes.MSG_GetCounterResp);
        try {
            return AntidotePB.FpbGetCounterResp.parseFrom(rawMessage.getData());
        }
        catch (InvalidProtocolBufferException e)
        {
            throw new IllegalArgumentException("Invalid message received", e);
        }
    }
}
