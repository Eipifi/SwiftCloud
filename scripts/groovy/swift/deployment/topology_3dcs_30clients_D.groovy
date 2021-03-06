#!/bin/bash
//usr/bin/env groovy -classpath .:scripts/groovy "$0" $@; exit $?

package swift.deployment
import static swift.deployment.Topology.*;

// NODES
EuropeEC2 = [
    // DC only
    'ec2-54-76-13-204.eu-west-1.compute.amazonaws.com',
]

NVirginiaEC2 = [
    // first node is a DC, followed by two groups of 10 scouts
'ec2-54-165-83-13.compute-1.amazonaws.com',
    
'ec2-54-165-81-214.compute-1.amazonaws.com',
'ec2-54-165-73-21.compute-1.amazonaws.com',
'ec2-54-165-86-111.compute-1.amazonaws.com',
'ec2-54-165-86-101.compute-1.amazonaws.com',
'ec2-54-164-255-168.compute-1.amazonaws.com',
'ec2-54-165-64-238.compute-1.amazonaws.com',
'ec2-54-165-73-136.compute-1.amazonaws.com',
'ec2-54-165-83-114.compute-1.amazonaws.com',
'ec2-54-165-64-253.compute-1.amazonaws.com',
'ec2-54-165-70-112.compute-1.amazonaws.com',

'ec2-54-165-65-151.compute-1.amazonaws.com',
'ec2-54-165-65-80.compute-1.amazonaws.com',
'ec2-54-165-70-63.compute-1.amazonaws.com',
'ec2-54-84-139-143.compute-1.amazonaws.com',
'ec2-54-165-20-214.compute-1.amazonaws.com',
'ec2-54-165-31-202.compute-1.amazonaws.com',
'ec2-54-165-71-188.compute-1.amazonaws.com',
'ec2-54-165-57-169.compute-1.amazonaws.com',
'ec2-54-165-64-120.compute-1.amazonaws.com',
'ec2-54-164-5-227.compute-1.amazonaws.com',
]

OregonEC2 = [
    // first node is a DC, followed by 10 scouts
    'ec2-54-68-115-242.us-west-2.compute.amazonaws.com',

    'ec2-54-68-115-141.us-west-2.compute.amazonaws.com',
    'ec2-54-68-115-203.us-west-2.compute.amazonaws.com',
    'ec2-54-68-119-31.us-west-2.compute.amazonaws.com',
    'ec2-54-68-53-89.us-west-2.compute.amazonaws.com',
    'ec2-54-68-116-99.us-west-2.compute.amazonaws.com',
    'ec2-54-68-119-39.us-west-2.compute.amazonaws.com',
    'ec2-54-68-118-75.us-west-2.compute.amazonaws.com',
    'ec2-54-68-114-55.us-west-2.compute.amazonaws.com',
    'ec2-54-68-115-172.us-west-2.compute.amazonaws.com',
    'ec2-54-68-116-132.us-west-2.compute.amazonaws.com',
]

// TOPOLOGY
Topology.clear()

// DC_NV as a first DC - used to initialize the DB 
DC_NV = DC([NVirginiaEC2[0]], [NVirginiaEC2[0]])
DC_EU = DC([EuropeEC2[0]], [EuropeEC2[0]])
DC_OR = DC([OregonEC2[0]], [OregonEC2[0]])

ScoutsToEU = SGroup(NVirginiaEC2[1..10], DC_EU)
ScoutsToNV = SGroup(OregonEC2[1..10], DC_NV)
ScoutsToOR = SGroup(NVirginiaEC2[11..20], DC_OR)
