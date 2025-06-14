package pt.ulisboa.tecnico.cnv;

import com.sun.net.httpserver.HttpExchange;
import lombok.Getter;
import lombok.Setter;
import pt.ulisboa.tecnico.cnv.storage.StorageUtil;
import pt.ulisboa.tecnico.cnv.strategies.SpreadingStrategy;
import pt.ulisboa.tecnico.cnv.strategies.VmSelectionStrategy;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Getter
public class LoadBalancer {
    public static final long VM_CAPACITY = 188632527L; // 20 seconds on capture the flag complexity scale
    public static final long LAMBDA_THRESHOLD = 1000000L; // 0.27 seconds on the capture the flag complexity scale
    public static final double SPREAD_THRESHOLD = 0.7;
    public static final double PACK_THRESHOLD = 0.3;

    private final ConcurrentMap<String, Worker> workers = new ConcurrentHashMap<>();
    final Queue<QueuedRequest> globalOverflowQueue = new ConcurrentLinkedQueue<>();
    private final ComplexityEstimator complexityEstimator;
    private final LambdaInvoker lambdaInvoker;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final ScheduledExecutorService queueProcessor;
    private final LoadBalancerMetrics metrics;
    private final HealthChecker healthChecker;
    final AtomicBoolean clearingGlobal;
    @Setter
    private AutoscalerNotifier autoscalerNotifier;

    public LoadBalancer() throws InterruptedException {
        this.complexityEstimator = new ComplexityEstimator();
        this.lambdaInvoker = new LambdaInvoker();
        this.queueProcessor = Executors.newScheduledThreadPool(1);
        this.metrics = new LoadBalancerMetrics();
        this.healthChecker = new HealthChecker(workers);
        this.clearingGlobal = new AtomicBoolean(false);

        StorageUtil.createTable();

        healthChecker.start();

        // Print metrics every 60 seconds
        //queueProcessor.scheduleAtFixedRate(metrics::printStats, 60, 60, TimeUnit.SECONDS);
    }

    public RequestAssigner getNewRequestAssigner() {
        return new RequestAssigner(this);
    }

    public String getLeastLoadedWorker(){
        return workers.values().stream().min(Comparator.comparing(Worker::getCurrentLoad)).get().getId();
    }

    void printWorkerSummary(){
        for(Worker worker : workers.values()){
            System.out.println(worker);
        }
    }

    public CompletableFuture<WorkerResponse> tryAssignToBestCandidate(
            HttpExchange exchange, RequestAssigner.RequestContext requestContext, VmSelectionStrategy strategy) {

        long requestComplexity = requestContext.complexity();
        List<String> candidateVmIds = strategy.selectVms(workers, requestComplexity, requestContext.avgLoad());

        CompletableFuture<WorkerResponse> future = tryAssign(candidateVmIds, exchange, requestComplexity, requestContext.storeMetrics());

        if (future != null) {
            return future;
        }

        if(requestComplexity < LAMBDA_THRESHOLD) {
            System.out.println("Invoked lambda for complexity: " + requestComplexity);
            return lambdaInvoker.invokeLambda(exchange.getRequestURI());
        }

        return queueRequest(exchange, requestComplexity, requestContext.storeMetrics());
    }

    private CompletableFuture<WorkerResponse> tryAssign(List<String> candidateVmIds, HttpExchange exchange, long complexity, boolean storeMetrics) {

        for (String vmId : candidateVmIds) {
            Worker worker = workers.get(vmId);
            if (worker == null || !worker.isAvailable()) {
                System.out.println("VM " + vmId + " is not available");
                continue; // Skip non-existent or known unhealthy workers
            }

            if (worker.tryAssignLoad(complexity)) {
                System.out.printf("Reserved load on VM %s for request with complexity %d.%n",
                        vmId, complexity);

                CompletableFuture<WorkerResponse> future = HttpForwarder.forwardRequest(worker, exchange, storeMetrics);

                future.whenComplete((response, throwable) -> {
                    worker.decreaseLoad(complexity);
                    if(worker.isDraining() && worker.getCurrentLoad() == 0) {
                        worker.completeTerminate();
                    }
                    if (throwable != null || response == null) {
                        worker.setUnhealthy();
                    }
                });

                getMetrics().incrementForwardedToWorkers();
                return future;
            }
        }
        return null;
    }

    public void clearGlobal() {
        boolean isClearing = clearingGlobal.getAndSet(true);
        if (!isClearing && getGlobalQueueLength() > 0 ) {
            while(!globalOverflowQueue.isEmpty()) {
                QueuedRequest request = globalOverflowQueue.peek();
                if (request != null) {
                    long requestComplexity = request.getEstimatedComplexity();
                    VmSelectionStrategy strategy = new SpreadingStrategy();
                    List<String> candidateVmIds = strategy.selectVms(workers, requestComplexity, calculateAverageLoad());
                    CompletableFuture<WorkerResponse> future = tryAssign(candidateVmIds, request.getExchange(), requestComplexity, request.isStoreMetrics());
                    if (future == null) {
                        clearingGlobal.set(false);
                        return;
                    }
                    future.whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            request.getFuture().completeExceptionally(throwable);
                        } else {
                            request.getFuture().complete(response);
                        }
                    });
                    globalOverflowQueue.poll();
                    System.out.println("Cleared request from global queue: " + request);
                    System.out.println("Global queue size: " + globalOverflowQueue.size());
                }
            }
        }
        clearingGlobal.set(false);
    }

    public CompletableFuture<WorkerResponse> queueRequest(HttpExchange exchange, long complexity, boolean storeMetrics) {
        QueuedRequest request = new QueuedRequest(exchange, complexity, storeMetrics);
        globalOverflowQueue.add(request);
        System.out.println("Queued request " + request);
        if(getQueuedComplexity() > VM_CAPACITY / 4)
            autoscalerNotifier.wakeUp();
        return request.getFuture();
    }


    // Method to calculate average load - called by RequestAssigner
    public long calculateAverageLoad() {
        long totalLoad = workers.values().stream()
                .filter(Worker::isAvailable)
                .mapToLong(Worker::getCurrentLoad)
                .sum();

        long availableCount = workers.values().stream()
                .filter(Worker::isAvailable)
                .count();

        return availableCount > 0 ?  totalLoad / availableCount : 0L;
    }


    // Worker management methods
    public void addNewWorker(String workerId, String host, int port) {
        System.out.println("Added new worker id = " + workerId);
        Worker worker = new Worker(workerId, host, port);
        workers.put(workerId, worker);
        healthChecker.startFastHealthChecking(workerId, Duration.ofSeconds(60)).whenComplete((healthy, throwable) -> {
            if (throwable == null) {
                if (healthy) {
                    clearGlobal();
                } else {
                    System.out.println("New worker " + workerId + " unhealthy after 60 seconds");
                }
            } else {
                System.err.println("Error when fast checking worker " + workerId + ": " + throwable.getMessage());
            }
        });
    }

    public CompletableFuture<Void> initiateWorkerRemoval(String workerId) {
        Worker worker = workers.get(workerId);
        if (worker != null) {
            CompletableFuture<Void> terminationFuture = worker.setDraining();
            if(worker.getCurrentLoad() == 0)
                terminationFuture.complete(null); // Complete termination future immediately if worker has no requests
            return terminationFuture;
        }
        return null;
    }

    public void finalizeWorkerRemoval(String workerId) {
        workers.remove(workerId);
    }

    public int getGlobalQueueLength() {
        return globalOverflowQueue.size();
    }

    public long getQueuedComplexity(){
        return globalOverflowQueue.stream().mapToLong(QueuedRequest::getEstimatedComplexity).sum();
    }

    public int getNrActiveVms() {
        return workers.size();
    }

    public Set<String> getDrainingWorkers() {
        return workers.entrySet().stream()
                .filter(entry -> entry.getValue().isDraining())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }


    public Worker getWorker(String id){
        return workers.get(id);
    }

}