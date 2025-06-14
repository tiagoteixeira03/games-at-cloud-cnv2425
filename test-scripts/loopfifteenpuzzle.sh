#!/bin/bash

# Syntax:  ./loopfifteenpuzzle.sh <ip> <port> <size> <num-of-shuffles>
# Example: ./loopfifteenpuzzle.sh 127.0.0.1 8000 10 120
HOST=$1
PORT=$2
size=$3
shuffles=$4

# Infinite loop until you terminate with Ctrl+C
while true; do
    curl -X GET "http://$HOST:$PORT/fifteenpuzzle?size=$size&shuffles=$shuffles"
    echo -e "\n--- Request sent ---\n"
    # Optional: Add a small delay to avoid overwhelming the server (adjust as needed)
    # sleep 0.5
done
