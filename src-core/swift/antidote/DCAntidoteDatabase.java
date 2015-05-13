package swift.antidote;

import com.basho.riak.client.api.RiakClient;
import swift.antidote.api.GetReg;
import swift.antidote.api.SetReg;
import swift.crdt.core.CRDTIdentifier;
import swift.dc.CRDTData;
import swift.dc.DCConstants;
import swift.dc.db.DCNodeDatabase;
import sys.net.api.Networking;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class DCAntidoteDatabase implements DCNodeDatabase {

    private RiakClient client;

    @Override
    public void sync(boolean flag) {
        // No synchronization needed
    }

    @Override
    public boolean ramOnly() {
        return false;
    }

    @Override
    public void init(Properties props) {
        try {
            // This assumes the default port, 8087.
            client = RiakClient.newClient(props.getProperty("ANTIDOTE_ADDRESS"));
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public CRDTData<?> read(CRDTIdentifier id) {
        ByteBuffer resp = readFromAntidote(idToBuffer(id));
        if (resp == null) return null;
        return (CRDTData<?>) Networking.getInstance().serializer().readObject(bufferBytes(resp));
    }

    @Override
    public boolean write(CRDTIdentifier id, CRDTData<?> data) {
        byte[] bytes = Networking.getInstance().serializer().writeObject(data);
        return writeToAntidote(idToBuffer(id), ByteBuffer.wrap(bytes));
    }

    @Override
    public Object readSysData(String table, String key) {
        ByteBuffer resp = readFromAntidote(stringToBuffer(table + "/" + key));
        if (resp == null) return null;
        return Networking.getInstance().serializer().readObject(bufferBytes(resp));
    }

    @Override
    public boolean writeSysData(String table, String key, Object data) {
        byte[] bytes = Networking.getInstance().serializer().writeObject(data);
        return writeToAntidote(stringToBuffer(table + "/" + key), ByteBuffer.wrap(bytes));
    }

    private ByteBuffer readFromAntidote(ByteBuffer key) {
        try {
            ByteBuffer res = client.execute(new GetReg(key));
            return (res.remaining() == 0) ? null : res;
        } catch (ExecutionException | InterruptedException e) {
            DCConstants.DCLogger.throwing("DCAntidoteDatabase", "read", e);
            return null;
        }
    }

    private boolean writeToAntidote(ByteBuffer key, ByteBuffer payload) {
        try {
            client.execute(new SetReg(key, payload));
            return true;
        } catch (ExecutionException | InterruptedException e) {
            DCConstants.DCLogger.throwing("DCAntidoteDatabase", "write", e);
            return false;
        }
    }

    private ByteBuffer idToBuffer(CRDTIdentifier id) {
        return stringToBuffer(id.toString());
    }

    private ByteBuffer stringToBuffer(String key) {
        return ByteBuffer.wrap(key.getBytes());
    }

    private byte[] bufferBytes(ByteBuffer buffer) {
        byte[] tmp = new byte[buffer.duplicate().remaining()];
        buffer.get(tmp);
        return tmp;
    }
}
