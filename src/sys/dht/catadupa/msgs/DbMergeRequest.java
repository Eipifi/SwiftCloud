package sys.dht.catadupa.msgs;

import sys.dht.catadupa.crdts.time.LVV;
import sys.net.api.rpc.RpcConnection;
import sys.net.api.rpc.RpcHandler;
import sys.net.api.rpc.RpcMessage;

public class DbMergeRequest implements RpcMessage {

	public LVV clock;

	DbMergeRequest()  {
	}

	public DbMergeRequest( final LVV clock)  {
		this.clock = clock.clone() ;
	}

	public void deliverTo(final RpcConnection conn, final RpcHandler handler) {
		((CatadupaHandler) handler).onReceive(conn, this);
	}

}