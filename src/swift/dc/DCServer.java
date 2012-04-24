package swift.dc;

import static sys.net.api.Networking.Networking;

import java.util.Properties;

import sys.Sys;

/**
 * Single server replying to client request. This class is used only to allow
 * client testing.
 * 
 * @author nmp
 * 
 */
public class DCServer {
    DCSurrogate server;
    String sequencerHost;
    Properties props;

    public DCServer(String sequencerHost, Properties props) {
        this.sequencerHost = sequencerHost;
        this.props = props;
        init();
    }

    protected void init() {

    }

    public void startSurrogServer() {
        Sys.init();

        server = new DCSurrogate(Networking.Networking.rpcBind(DCConstants.SURROGATE_PORT, null), Networking.rpcBind(0,
                null), Networking.resolve(sequencerHost, DCConstants.SEQUENCER_PORT), props);
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.setProperty( DCConstants.DATABASE_CLASS, "swift.dc.db.DevNullNodeDatabase");
        
        new DCServer(args.length == 0 ? "localhost" : args[0], props).startSurrogServer( );
    }
}
