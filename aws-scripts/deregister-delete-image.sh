#!/bin/bash

source config.sh

# Read image ID from file
IMAGE_ID=$(cat vm-image.id)

# Get the snapshot ID associated with the image
SNAPSHOT_ID=$(aws ec2 describe-images \
    --image-ids $IMAGE_ID \
    --query "Images[0].BlockDeviceMappings[0].Ebs.SnapshotId" \
    --output text)

echo "Deregistering AMI: $IMAGE_ID"
aws ec2 deregister-image --image-id $IMAGE_ID

if [ "$SNAPSHOT_ID" != "None" ] && [ -n "$SNAPSHOT_ID" ]; then
    echo "Deleting associated snapshot: $SNAPSHOT_ID"
    aws ec2 delete-snapshot --snapshot-id $SNAPSHOT_ID
else
    echo "No snapshot found or associated with this AMI."
fi
