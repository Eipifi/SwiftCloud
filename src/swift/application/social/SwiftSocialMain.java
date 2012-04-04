package swift.application.social;

import static sys.net.api.Networking.Networking;
import swift.client.SwiftImpl;
import swift.crdt.interfaces.Swift;
import swift.dc.DCConstants;
import swift.dc.DCSequencerServer;
import swift.dc.DCServer;
import sys.Sys;
import sys.net.api.Endpoint;
import sys.net.api.rpc.RpcEndpoint;

public class SwiftSocialMain {
    static String sequencerName = "localhost";

    public static void main(String[] args) {
        Thread sequencer = new Thread() {
            public void run() {
                DCSequencerServer sequencer = new DCSequencerServer(sequencerName);
                sequencer.start();
            }
        };
        sequencer.start();

        Thread server = new Thread() {
            public void run() {
                DCServer server = new DCServer(sequencerName);
                server.startSurrogServer();
            }
        };
        server.start();

        Sys.init();
        int portId = 2001;
        RpcEndpoint localEP = Networking.rpcBind(portId, null);
        final Endpoint serverEP = Networking.resolve("localhost", DCConstants.SURROGATE_PORT);
        Swift clientServer = new SwiftImpl(localEP, serverEP);
        SwiftSocial client = new SwiftSocial(clientServer);
        client.addUser("Biene", "Honig");
        client.login("Biene", "Honig");
        client.postMessage("Biene", "Biene", "What a wonderful day!", System.currentTimeMillis());
        client.getSiteReport("Biene");

    }
}
