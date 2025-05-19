#!/bin/bash

source config.sh

# Install Java 11 (Amazon Corretto)
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) "$cmd"

# Install latest Maven manually
cmd='
MAVEN_VERSION=3.9.4
MAVEN_DIR=apache-maven-$MAVEN_VERSION
cd /tmp
curl -O https://dlcdn.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/$MAVEN_DIR-bin.tar.gz
sudo tar -xzf $MAVEN_DIR-bin.tar.gz -C /opt
sudo ln -sfn /opt/$MAVEN_DIR /opt/maven
echo "export M2_HOME=/opt/maven" | sudo tee /etc/profile.d/maven.sh
echo "export PATH=\$M2_HOME/bin:\$PATH" | sudo tee -a /etc/profile.d/maven.sh
sudo chmod +x /etc/profile.d/maven.sh
source /etc/profile.d/maven.sh
'
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) "$cmd"

# Copy web server code to instance
scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/cnv25-g32 ec2-user@$(cat instance.dns):

# Build web server
cmd="cd cnv25-g32; source /etc/profile.d/maven.sh; mvn clean package"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) "$cmd"

# Setup web server to start on instance launch
cmd='
echo "[Unit]
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
WantedBy=multi-user.target" | sudo tee /etc/systemd/system/rc-local.service

echo "#!/bin/sh -e
cd /home/ec2-user/cnv25-g32
java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
-Xbootclasspath/a:javassist/target/JavassistWrapper-1.0-jar-with-dependencies.jar \
-javaagent:webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.capturetheflag,pt.ulisboa.tecnico.cnv.fifteenpuzzle,pt.ulisboa.tecnico.cnv.gameoflife:output \
pt.ulisboa.tecnico.cnv.webserver.WebServer &> /tmp/webserver.log &
exit 0" | sudo tee /etc/rc.local

sudo chmod +x /etc/rc.local
sudo systemctl enable rc-local.service
sudo systemctl status rc-local.service
'
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) "$cmd"
