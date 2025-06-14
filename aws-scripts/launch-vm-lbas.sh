#!/bin/bash

# Load environment variables
source config.sh

# Set your desired instance name
INSTANCE_NAME="LB-AS"

# Run new EC2 instance with Name tag
aws ec2 run-instances \
  --image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2 \
  --instance-type t2.micro \
  --key-name "$AWS_KEYPAIR_NAME" \
  --security-group-ids "$AWS_SECURITY_GROUP" \
  --monitoring Enabled=true \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$INSTANCE_NAME}]" \
  | jq -r ".Instances[0].InstanceId" > lbas-instance.id

echo "New instance with ID: $(cat lbas-instance.id) and name: $INSTANCE_NAME."

# Wait until the instance is running
aws ec2 wait instance-running --instance-ids "$(cat lbas-instance.id)"
echo "Instance $(cat lbas-instance.id) is now running."

# Get public DNS name of the instance
aws ec2 describe-instances \
  --instance-ids "$(cat lbas-instance.id)" \
  | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > lbas-instance.dns

echo "Instance DNS: $(cat lbas-instance.dns)"

# Wait for SSH to be ready
while ! nc -z "$(cat lbas-instance.dns)" 22; do
  echo "Waiting for SSH on $(cat lbas-instance.dns):22..."
  sleep 0.5
done

echo "Instance $(cat lbas-instance.id) is ready for SSH access."
