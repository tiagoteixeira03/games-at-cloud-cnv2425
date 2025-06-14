#!/bin/bash

source config.sh

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat lbas-instance.id)
echo "Rebooting instance to test web server auto-start."

# Letting the instance shutdown.
sleep 1

# Wait for port 80 to become available.
while ! nc -z $(cat lbas-instance.dns) 80; do
	echo "Waiting for $(cat lbas-instance.dns):80..."
	sleep 0.5
done

# Sending a query!
echo "Sending a query!"
curl $(cat lbas-instance.dns):80/test
