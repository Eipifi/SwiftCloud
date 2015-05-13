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

public class GetRegOperation extends SimpleFutureOperation<ByteBuffer, AntidotePB.FpbGetRegResp> {

    private final ByteBuffer key;

    public GetRegOperation(ByteBuffer key) {
        this.key = key;
    }

    @Override
    protected ByteBuffer convert(List<AntidotePB.FpbGetRegResp> list) {
        AntidotePB.FpbGetRegResp rr = list.get(0);
        return rr.hasValue() ? rr.getValue().asReadOnlyByteBuffer() : null;
    }

    @Override
    protected RiakMessage createChannelMessage() {
        return new RiakMessage(AntidoteMessageCodes.MSG_GetRegReq, AntidotePB.FpbGetRegReq.newBuilder().setKey(ByteString.copyFrom(key)).build().toByteArray());
    }

    @Override
    protected AntidotePB.FpbGetRegResp decode(RiakMessage riakMessage) {
        Operations.checkMessageType(riakMessage, AntidoteMessageCodes.MSG_GetRegResp);
        try {
            return AntidotePB.FpbGetRegResp.parseFrom(riakMessage.getData());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid message received", e);
        }
    }
}
