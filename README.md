# Games@Cloud

The following modules can be found in `src/`:

1. `capturetheflag` - the Capture the Flag workload
2. `fifteenpuzzle` - the 15-Puzzle Solver workload
3. `gameoflife` - the Conway's Game of Life workload
4. `webserver` - the web server exposing the functionality of the workloads
5. `javassist` - javassist tool to gather metrics
6. `storage` - storage of the gathered metrics using Amazon DynamoDB
7. `lbas` - the load balancer and autoscaler in a single webserver

Refer to the `README.md` files of the first three modules to get more details about each workload.

## How to run the project

### 1. Creating an AMI for the VM Workers
1. `cd aws-scripts`
2. Create a `config.sh` with your AWS credentials following [this structure](aws-scripts/config-template.sh)
3. `chmod +x *.sh` (This is to give the necessary permissions for the shell scripts to run)
4. `./create-image.sh` - This will create an AMI with the necessary webserver code for the VM Workers present in `src/`

### 2. Creating the lambda functions
1. `./create-lambda.sh` - Creates three lambda functions (one for each game) using the .jar files inside `lambda-jars/`

### 3. Deploying the Load Balancer and Auto Scaler in AWS
1. `./create-launch-lbas.sh` - Launches a new EC2 Instance, installs all the necessary dependencies and sets up auto start for the Load Balancer and Auto Scaler webserver

### 4. Sending Requests
1. Once the "LB-AS" EC2 instance appears running on AWS, requests can be sent to the Load Balancer's IP Address on port 80