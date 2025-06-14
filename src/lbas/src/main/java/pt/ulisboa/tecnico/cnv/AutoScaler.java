package pt.ulisboa.tecnico.cnv;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import pt.ulisboa.tecnico.cnv.util.EMACalculator;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class AutoScaler implements Runnable, AutoscalerNotifier{
    private static final double SCALE_OUT_CPU_THRESHOLD = 85;
    private static final double SCALE_IN_CPU_THRESHOLD = 25;
    private static final int MIN_WORKERS = 1;
    private static final int MAX_WORKERS = 5;
    private static final String AWS_REGION = "us-east-1";
    private final String amiId;
    private static final long OBS_TIME = 1000 * 60 * 5;
    private static final long SCALING_TIMEOUT = 1000 * 60 * 2;


    private final LoadBalancer loadBalancer;
    private final AmazonEC2 ec2;
    private final AmazonCloudWatch cloudWatch;
    private boolean running = true;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private long latestScalingTimestamp;
    private final ScheduledExecutorService scheduler;
    private Thread autoscalerThread;

    public AutoScaler(LoadBalancer loadBalancer, String amiId) {
        this.loadBalancer = loadBalancer;
        this.ec2 = AmazonEC2ClientBuilder.standard().withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        this.cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        this.amiId = amiId;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.latestScalingTimestamp = System.currentTimeMillis();
        // Create first worker here if needed
        this.scaleOut();
    }


    public void start() {
        if (autoscalerThread == null || !autoscalerThread.isAlive()) {
            autoscalerThread = new Thread(this, "AutoScaler-Thread");
            autoscalerThread.setDaemon(false); // Make sure it's not a daemon thread
            autoscalerThread.start();
            System.out.println("AutoScaler thread started: " + autoscalerThread.getName());
        }
    }


    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            autoscalerTick();
            long elapsed = System.currentTimeMillis() - start;
            lock.lock();
            try {
                long waitTime = SCALING_TIMEOUT - elapsed;
                if (waitTime > 0) {
                    condition.await(waitTime, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void wakeUp() {
        if (lock.tryLock()) {
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    private Set<Instance> getInstances(AmazonEC2 ec2) {
        Set<Instance> instances = new HashSet<>();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            instances.addAll(reservation.getInstances());
        }
        return instances;
    }


    public void autoscalerTick() {
        double avgCpuUsage = fetchAverageCpuUsage();
        int globalQueueLength = loadBalancer.getGlobalQueueLength();
        int nrActiveVms = loadBalancer.getNrActiveVms();
        long avgLoad = loadBalancer.calculateAverageLoad();
        if (new Date().getTime() - latestScalingTimestamp < SCALING_TIMEOUT) {
            return;
        }
        System.out.println("Avg cpu usage: " + avgCpuUsage + " queue size: " + globalQueueLength);
        if (avgCpuUsage > SCALE_OUT_CPU_THRESHOLD || globalQueueLength > 0) {
            if (nrActiveVms < MAX_WORKERS) {
                scaleOut();
            }
        } else if (avgCpuUsage < SCALE_IN_CPU_THRESHOLD &&
                avgLoad < SCALE_IN_CPU_THRESHOLD / 100 * LoadBalancer.VM_CAPACITY) {
            if (nrActiveVms - 1 >= MIN_WORKERS) {
                scaleIn();
            }
        }
    }


    private double fetchAverageCpuUsage() {
        try {
            Set<Instance> instances = getInstances(ec2);

            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");

            double sum = 0;

            for (Instance instance : instances) {
                String iid = instance.getInstanceId();
                String state = instance.getState().getName();
                if (state.equals("running")) {
                    System.out.println("running instance id = " + iid);
                    instanceDimension.setValue(iid);
                    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest().withStartTime(new Date(new Date().getTime() - OBS_TIME))
                            .withNamespace("AWS/EC2")
                            .withPeriod(60)
                            .withMetricName("CPUUtilization")
                            .withStatistics("Average")
                            .withDimensions(instanceDimension)
                            .withEndTime(new Date());

                    List<Double> dps = cloudWatch.getMetricStatistics(request).getDatapoints().stream()
                            .map(Datapoint::getAverage).toList();
                    Double ema = EMACalculator.calculateEMA(dps); // Using the exponential moving average to give more importance to recent datapoints
                    if(ema != null){
                        sum += ema;
                    }
                }
            }

            return sum / instances.size();
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
            return -1;
        }
    }

    private void scaleOut() {
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        this.latestScalingTimestamp = System.currentTimeMillis();
        System.out.println("Scaling out, current workers: " + loadBalancer.getWorkers().size());

        runInstancesRequest.withImageId(amiId)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(System.getenv("AWS_KEYPAIR_NAME"))
                .withSecurityGroupIds(System.getenv("AWS_SECURITY_GROUP"));
        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
        String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();

        waitForPublicIp(newInstanceId, Duration.ofSeconds(60))
                .thenAccept(ip -> loadBalancer.addNewWorker(newInstanceId, ip, 8000)).exceptionally(ex -> {
                    System.err.println("Failed to get IP for instance " + newInstanceId + ": " + ex.getMessage());
                    return null;
                });

    }

    public CompletableFuture<String> waitForPublicIp(String instanceId, Duration timeout) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        Runnable pollTask = () -> {
            try {
                if (System.currentTimeMillis() >= deadline) {
                    future.completeExceptionally(new TimeoutException("Timed out waiting for public IP"));
                    return;
                }

                DescribeInstancesRequest describeRequest = new DescribeInstancesRequest()
                        .withInstanceIds(instanceId);

                DescribeInstancesResult describeResult = ec2.describeInstances(describeRequest);

                if (describeResult.getReservations().isEmpty() ||
                        describeResult.getReservations().get(0).getInstances().isEmpty()) {
                    future.completeExceptionally(new IllegalStateException("Instance not found: " + instanceId));
                    return;
                }

                Instance instance = describeResult.getReservations().get(0).getInstances().get(0);

                if (instance.getPublicIpAddress() != null) {
                    future.complete(instance.getPublicIpAddress());
                }
            } catch (Exception e) {
                System.err.println("Exception thrown when waiting for public IP for: " + instanceId + " | error: " +  e.getMessage());
            }
        };

        ScheduledFuture<?> scheduledFuture = scheduler.scheduleAtFixedRate(pollTask, 0, 2, TimeUnit.SECONDS);
        return future.whenComplete((result, throwable) -> scheduledFuture.cancel(true));
    }


    private void scaleIn() {
        String vmId = loadBalancer.getLeastLoadedWorker();
        this.latestScalingTimestamp = System.currentTimeMillis();
        if (vmId != null) {
            loadBalancer.initiateWorkerRemoval(vmId).thenRun(() -> {
                TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
                termInstanceReq.withInstanceIds(vmId);
                ec2.terminateInstances(termInstanceReq);
                loadBalancer.finalizeWorkerRemoval(vmId);
                System.out.println("Scaled in: removing worker " + vmId);
            }); // Sets worker to Draining status (stops receiving new requests)
        }
    }
}
