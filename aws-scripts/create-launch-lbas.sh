#!/bin/bash

source config.sh

# Step 1: launch a vm instance.
$DIR/launch-vm-lbas.sh

# Step 2: install software in the VM instance.
$DIR/install-vm-lbas.sh

# Step 3: test VM instance.
$DIR/test-lbas.sh