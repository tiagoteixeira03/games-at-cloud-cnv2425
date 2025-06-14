#!/bin/bash

# Syntax:  ./testfifteenpuzzle.sh <ip> <port> <size> <num-of-shuffles>
# Example: ./testfifteenpuzzle.sh 127.0.0.1 8000  10 120
HOST=$1
PORT=$2
size=$3
shuffles=$4

function test_batch_requests {
	REQUESTS=300
	CONNECTIONS=300
	ab -n $REQUESTS -c $CONNECTIONS http://$HOST:$PORT/fifteenpuzzle\?size=$size\&shuffles=$shuffles
}

function test_single_requests {

	curl -X GET http://$HOST:$PORT/fifteenpuzzle\?size=$size\&shuffles=$shuffles
}

test_single_requests
test_batch_requests
