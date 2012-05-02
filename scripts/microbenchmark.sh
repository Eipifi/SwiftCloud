#!/bin/bash	
. scripts/ec2-common.sh

#TODO SCRIPT to execute microbenchmark swiftcloud on cluster

modes=("CACHED REPEATABLE_READS" "MOST_RECENT SNAPSHOT_ISOLATION")
numWorkers="1 2 10 20 30 40 50"
executionTime=$1 #The warm up will be half of this time
outputDir='results-micro'

if [ ! -d "$outputDir" ]; then
    mkdir $outputDir
fi

client=$EC2_ASIA_SINGAPORE
server=$EC2_ASIA_TOKYO

for i in ${!modes[*]}; do
	echo "${modes[$i]}"
	for workers in $numWorkers; do
	if [ ! -d "$outputDir"/""${modes[$i]/ /_}"" ]; then
		mkdir $outputDir"/""${modes[$i]/ /_}"
	fi	
		#TODO CMD Variable to execute the benchmark
		echo -i $EC2_IDENTITY_FILE "$EC2_USER@$client" "$cmd" > $outputDir"/""${modes[$i]/ /_}""/""$workers".log


	done
done

