#!/bin/bash
//usr/bin/env groovy -classpath .:scripts/groovy "$0" $@; exit $?

package swift.deployment

import static swift.deployment.SwiftYCSB.*
import static swift.deployment.Tools.*
import static swift.deployment.Topology.*;

def __ = onControlC({
    pnuke(AllMachines, "java", 60)
    System.exit(0);
})


EuropeEC2 = [
    'ec2-54-72-81-159.eu-west-1.compute.amazonaws.com',
    'ec2-54-76-112-193.eu-west-1.compute.amazonaws.com'
]

// Optional argument - limit of scouts number
if (args.length < 2) {
    System.err.println "usage: runycsb_workloada.groovy <limits on scouts number per DC> <threads per scout>"
    System.exit(1)
}
PerDCClientNodesLimit = Integer.valueOf(args[0])
Threads = Integer.valueOf(args[1])


Europe = DC([EuropeEC2[0]], [EuropeEC2[0]])
ScoutsEU = SGroup( EuropeEC2[1..PerDCClientNodesLimit], Europe )
Scouts = ( Topology.scouts() ).unique()
ShepardAddr = Topology.datacenters[0].surrogates[0];
AllMachines = ( Topology.allMachines() + ShepardAddr).unique()

YCSBProps = "swiftycsb.properties"
// DbSize = 200*Scouts.size()*Threads

Duration = 240

Version = getGitCommitId()
println getBinding().getVariables()

dumpTo(AllMachines, "/tmp/nodes.txt")

pnuke(AllMachines, "java", 60)


println "==== BUILDING JAR for version " + Version + "..."
sh("ant -buildfile smd-jar-build.xml").waitFor()
deployTo(AllMachines, "swiftcloud.jar")
deployTo(AllMachines, "stuff/logging.properties", "logging.properties")
deployTo(AllMachines, SwiftYCSB.genPropsFile(['recordcount':'10000',
    'operationcount':'100000'], SwiftYCSB.DEFAULT_PROPS + SwiftYCSB.WORKLOAD_A).absolutePath, YCSBProps)

def shep = SwiftBase.runShepard( ShepardAddr, Duration, "Released" )

println "==== LAUNCHING SEQUENCERS"
Topology.datacenters.each { datacenter ->
    datacenter.deploySequencers(ShepardAddr, "1024m" )
}

Sleep(10)
println "==== LAUNCHING SURROGATES"
Topology.datacenters.each { datacenter ->
    datacenter.deploySurrogates(ShepardAddr, "1536m")
}

println "==== WAITING A BIT BEFORE INITIALIZING DB ===="
Sleep(InterCmdDelay)

println "==== INITIALIZING DATABASE ===="
def INIT_DB_DC = Surrogates.get(0)
def INIT_DB_CLIENT = Surrogates.get(0)

SwiftYCSB.initDB( INIT_DB_CLIENT, INIT_DB_DC, YCSBProps, Threads)

println "==== WAITING A BIT BEFORE STARTING SCOUTS ===="
Sleep(InterCmdDelay)

SwiftYCSB.runClients(Topology.scoutGroups, YCSBProps, Threads, "1024m")

println "==== WAITING FOR SHEPARD SIGNAL PRIOR TO COUNTDOWN ===="
shep.take()

Countdown( "Max. remaining time: ", Duration + InterCmdDelay)

pnuke(AllMachines, "java", 60)

def dstDir="results/ycsb/workloada/" + new Date().format('MMMdd-') + System.currentTimeMillis() + "-" + Version + "-" +
        String.format("DC-%s-SU-%s-SC-%s-TH-%s-records-%d-operations-%d", Topology.datacenters.size(), Topology.datacenters[0].surrogates.size(), Topology.totalScouts(), Threads, DbSize, OpsNumber)

pslurp( Scouts, "scout-stdout.txt", dstDir, "scout-stdout.log", 300)
props.renameTo(new File(dstDir, YCSBProps))

exec([
    "/bin/bash",
    "-c",
    "wc " + dstDir + "/*/*"
]).waitFor()

System.exit(0)

