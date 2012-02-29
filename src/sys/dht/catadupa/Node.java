package sys.dht.catadupa;


import java.math.BigInteger;
import java.util.Random;

import sys.net.api.Endpoint;
import static sys.dht.catadupa.Config.*;

/**
 * 
 * @author smd
 *
 */
public class Node {

	public long key ;
	public Endpoint endpoint;
	
	public Node() {}

	protected Node( Node other ) {
		this.endpoint = other.endpoint ;
		this.key = locator2key( endpoint.locator() ) ;
	}

	public Node( Endpoint endpoint ) {
		this.endpoint = endpoint ;
		this.key = locator2key( endpoint.locator() ) ;
	}
	
	public boolean isOnline() {
		return true ;
	}
	
	public boolean isOffline() {
		return ! isOnline() ;
	}
	
	public String toString() {
		return "" + key ; //+ ":" + endpoint ;
	} 

	private static long locator2key( Object locator ) {
		return new BigInteger( Config.NODE_KEY_LENGTH, new Random( (Long)locator )).longValue() ;
	}
}

class DeadNode extends Node {
	
	public DeadNode( Node other ) {
		this.endpoint = other.endpoint ;
		this.key = other.key ;
	}
	
	public boolean isOnline() {
		return true ;
	}
}