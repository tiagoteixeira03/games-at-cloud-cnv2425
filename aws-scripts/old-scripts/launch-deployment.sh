#!/bin/bash

source config.sh

# Create load balancer and configure health check
aws elb create-load-balancer \
	--load-balancer-name CNV-LoadBalancer \
	--listeners "Protocol=HTTP,LoadBalancerPort=80,InstanceProtocol=HTTP,InstancePort=8000" \
	--availability-zones us-east-1a \
	--security-groups $AWS_SECURITY_GROUP

# Set load balancer idle timeout to 3600 seconds
aws elb modify-load-balancer-attributes \
  --load-balancer-name CNV-LoadBalancer \
  --load-balancer-attributes '{"ConnectionSettings": {"IdleTimeout": 3600}}'

# Configure health check for load balancer
aws elb configure-health-check \
	--load-balancer-name CNV-LoadBalancer \
	--health-check Target=HTTP:8000/test,Interval=30,UnhealthyThreshold=2,HealthyThreshold=10,Timeout=5

# Create a launch template
aws ec2 create-launch-template \
  --launch-template-name CNV-LaunchTemplate \
  --version-description "v1" \
  --launch-template-data "{
    \"ImageId\": \"$(cat vm-image.id)\",
    \"InstanceType\": \"t2.micro\",
    \"SecurityGroupIds\": [\"$AWS_SECURITY_GROUP\"],
    \"KeyName\": \"$AWS_KEYPAIR_NAME\",
    \"Monitoring\": {\"Enabled\": true}
  }"

# Create Auto Scaling Group
aws autoscaling create-auto-scaling-group \
  --auto-scaling-group-name CNV-AutoScalingGroup \
  --launch-template "LaunchTemplateName=CNV-LaunchTemplate,Version=1" \
  --load-balancer-names CNV-LoadBalancer \
  --availability-zones us-east-1a \
  --health-check-type ELB \
  --health-check-grace-period 60 \
  --min-size 1 \
  --max-size 5 \
  --desired-capacity 1

# Create Scale-Out Policy and capture its ARN
SCALE_OUT_POLICY_ARN=$(aws autoscaling put-scaling-policy \
  --auto-scaling-group-name CNV-AutoScalingGroup \
  --policy-name ScaleOutPolicy \
  --policy-type StepScaling \
  --step-adjustments '[{"MetricIntervalLowerBound":0,"ScalingAdjustment":1}]' \
  --adjustment-type ChangeInCapacity \
  --cooldown 300 \
  --query PolicyARN --output text)

# Create Scale-In Policy and capture its ARN
SCALE_IN_POLICY_ARN=$(aws autoscaling put-scaling-policy \
  --auto-scaling-group-name CNV-AutoScalingGroup \
  --policy-name ScaleInPolicy \
  --policy-type StepScaling \
  --step-adjustments '[{"MetricIntervalUpperBound":0,"ScalingAdjustment":-1}]' \
  --adjustment-type ChangeInCapacity \
  --cooldown 300 \
  --query PolicyARN --output text)

# Create CloudWatch Alarms

# Scale out when ANY single instance has CPU > 70%
aws cloudwatch put-metric-alarm \
  --alarm-name ScaleOutIfAnyInstanceHighCPU \
  --metric-name CPUUtilization \
  --namespace AWS/EC2 \
  --statistic Maximum \
  --period 60 \
  --threshold 70 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=AutoScalingGroupName,Value=CNV-AutoScalingGroup \
  --evaluation-periods 1 \
  --alarm-actions $SCALE_OUT_POLICY_ARN

# Scale in when ALL instances have CPU < 20%
aws cloudwatch put-metric-alarm \
  --alarm-name ScaleInIfAllInstancesLowCPU \
  --metric-name CPUUtilization \
  --namespace AWS/EC2 \
  --statistic Maximum \
  --period 60 \
  --threshold 20 \
  --comparison-operator LessThanThreshold \
  --dimensions Name=AutoScalingGroupName,Value=CNV-AutoScalingGroup \
  --evaluation-periods 1 \
  --alarm-actions $SCALE_IN_POLICY_ARN
