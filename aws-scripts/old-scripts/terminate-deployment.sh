#!/bin/bash

source config.sh

# Step 1: delete auto scaling group.
aws autoscaling delete-auto-scaling-group \
	--auto-scaling-group-name CNV-AutoScalingGroup \
	--force-delete

# Step 2: delete launch template.
aws ec2 delete-launch-template --launch-template-name CNV-LaunchTemplate


# Step 3: delete load balancer.
aws elb delete-load-balancer \
	--load-balancer-name CNV-LoadBalancer
