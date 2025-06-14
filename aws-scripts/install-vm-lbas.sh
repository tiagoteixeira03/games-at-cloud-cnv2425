#!/bin/bash

source config.sh

# Install Java 17 and set as default
cmd="sudo yum update -y; sudo yum install java-17-amazon-corretto.x86_64 -y; sudo alternatives --set java /usr/lib/jvm/java-17-amazon-corretto.x86_64/bin/java;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat lbas-instance.dns) "$cmd"

# Install latest Maven manually
cmd='
MAVEN_VERSION=3.9.10
MAVEN_DIR=apache-maven-$MAVEN_VERSION
cd /tmp
curl -O https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/$MAVEN_DIR-bin.tar.gz
if file $MAVEN_DIR-bin.tar.gz | grep -q "gzip compressed data"; then
  sudo tar -xzf $MAVEN_DIR-bin.tar.gz -C /opt
  sudo ln -sfn /opt/$MAVEN_DIR /opt/maven
  echo "export M2_HOME=/opt/maven" | sudo tee /etc/profile.d/maven.sh
  echo "export PATH=\$M2_HOME/bin:\$PATH" | sudo tee -a /etc/profile.d/maven.sh
  sudo chmod +x /etc/profile.d/maven.sh
  source /etc/profile.d/maven.sh
else
  echo "ERROR: Download failed or not a gzip file"
  exit 1
fi
'
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat lbas-instance.dns) "$cmd"

# Copy web server code to instance
cmd='mkdir /home/ec2-user/cnv-project' # Create directory for web server
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat lbas-instance.dns) "$cmd"
scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH \
  $DIR/../src/capturetheflag \
  $DIR/../src/fifteenpuzzle \
  $DIR/../src/gameoflife \
  $DIR/../src/javassist \
  $DIR/../src/storage \
  $DIR/../src/webserver \
  $DIR/../src/lbas \
  $DIR/../src/pom.xml \
  $DIR/../aws-scripts/config.sh \
  ec2-user@$(cat lbas-instance.dns):/home/ec2-user/cnv-project/

# Build web server
cmd="cd /home/ec2-user/cnv-project; source /etc/profile.d/maven.sh; mvn clean package"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat lbas-instance.dns) "$cmd"

# Setup web server to start on instance launch
AMI_ID=$(cat vm-image.id)

cmd="
echo \"[Unit]
Description=/etc/rc.local Compatibility
ConditionPathExists=/etc/rc.local

[Service]
Type=forking
ExecStart=/etc/rc.local start
TimeoutSec=0
StandardOutput=tty
RemainAfterExit=yes
SysVStartPriority=99

[Install]
WantedBy=multi-user.target\" | sudo tee /etc/systemd/system/rc-local.service

echo \"#!/bin/sh -e
cd /home/ec2-user/cnv-project
source /home/ec2-user/cnv-project/config.sh
java -cp lbas/target/lbas-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.LbAs $AMI_ID &> /tmp/webserver.log &
exit 0\" | sudo tee /etc/rc.local

sudo chmod +x /etc/rc.local
sudo systemctl enable rc-local.service
sudo systemctl status rc-local.service
"

ssh -o StrictHostKeyChecking=no -i "$AWS_EC2_SSH_KEYPAR_PATH" ec2-user@$(cat lbas-instance.dns) "$cmd"

