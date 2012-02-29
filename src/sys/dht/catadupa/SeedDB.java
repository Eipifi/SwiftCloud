package sys.dht.catadupa;

import static sys.utils.Log.Log;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import sys.dht.catadupa.riak.Riak;
import sys.dht.discovery.Discovery;

import sys.net.api.Endpoint;

/**
 * 
 * @author smd
 * 
 */
public class SeedDB {

    private static final String CATADUPA_ENDPOINT = "CATADUPA_ENDPOINT";

    static String RIAK_KEY = "seedDB";
    static String RIAK_BUCKET = "swift.catadupa";

    static Set<Node> seeds = new HashSet<Node>();

    public static RandomList<Node> nodes() {
        return new RandomList<Node>(seeds);
    }

    public static Node randomSeedNode() {
        return new RandomList<Node>(seeds).randomElement();
    }

    static void initWithRiak(Node self) {
        Collection<Node> riakSeeds = Riak.load(RIAK_BUCKET, RIAK_KEY);
        if (riakSeeds != null) {
            seeds.addAll(riakSeeds);
        } else {
            seeds.add(self);
            Riak.store(RIAK_BUCKET, RIAK_KEY, seeds);
        }

        // Riak.delete(SeedDB.RIAK_BUCKET, SeedDB.RIAK_KEY);
    }

    static void initWithMulticast(Node self) {
        Endpoint seed = Discovery.lookup(CATADUPA_ENDPOINT, 1000);
        if (seed == null) {
            Log.fine("No seed node found in local machine/network");
            seeds.add(self);
            Discovery.register(CATADUPA_ENDPOINT, self.endpoint);
        } else {
            Log.fine("Seed node found in local machine/network");
            seeds.add(new Node(seed));
        }
    }

    static void init(final Node self) {
        initWithMulticast(self);
    }
}
