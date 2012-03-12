package sys.dht.test;

import java.util.logging.Level;

import sys.dht.api.DHT;
import sys.dht.api.StringKey;
import sys.dht.api.DHT.Connection;
import sys.dht.test.msgs.StoreData;
import sys.dht.test.msgs.StoreDataReply;
import sys.utils.Threading;

import static sys.Sys.*;
import static sys.utils.Log.Log;

/**
 * 
 * An example of a client application interacting with the DHT.
 * 
 * Sends a request to the DHT (Key + Data) and (asynchronously) awaits a reply.
 * 
 * Note that to simplify binding, multicast is currently used to discover the endpoint of the DHT node.
 * 
 * @author smd (smd@fct.unl.pt)
 * 
 */
public class Client {

    public static void main(String[] args) throws Exception {
        Log.setLevel(Level.ALL);
        sys.Sys.init();

        DHT stub = Sys.getDHT_ClientStub();

        while (stub != null) {
            String key = "" + Sys.rg.nextInt(1000);
            stub.send(new StringKey(key), new StoreData(Sys.rg.nextDouble()), new KVS.ReplyHandler() {
                @Override
                public void onReceive(StoreDataReply reply) {
                    System.out.println(reply.msg);
                }
            });
            Threading.sleep(1000);
        }
    }
}