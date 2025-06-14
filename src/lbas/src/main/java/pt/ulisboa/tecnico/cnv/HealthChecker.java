package pt.ulisboa.tecnico.cnv;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.time.Duration;

public class HealthChecker {
    private final ConcurrentMap<String, Worker> workers;
    private final ScheduledExecutorService scheduler;
    private final ScheduledExecutorService fastCheckScheduler;

    // Configuration
    private static final int NORMAL_CHECK_INTERVAL = 30; // seconds
    private static final int FAST_CHECK_INTERVAL = 2; // seconds

    public HealthChecker(ConcurrentMap<String, Worker> workers) {
        this.workers = workers;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.fastCheckScheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Start fast health checking for a worker and return a future that completes when healthy
     */
    public CompletableFuture<Boolean> startFastHealthChecking(String workerId, Duration timeout) {
        CompletableFuture<Boolean> healthFuture = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        System.out.println("Starting fast health checking for worker: " + workerId);

        // Schedule the recurring health check
        ScheduledFuture<?> checkTask = fastCheckScheduler.scheduleAtFixedRate(() -> {
            try {
                if (System.currentTimeMillis() >= deadline) {
                    healthFuture.complete(false); // Timeout
                    return;
                }

                Worker worker = workers.get(workerId);
                if (worker == null) {
                    healthFuture.complete(false); // Worker was removed
                    return;
                }

                checkWorkerHealth(worker);

                if (worker.isAvailable()) {
                    System.out.println("Worker " + workerId + " became healthy!");
                    healthFuture.complete(true); // Worker is healthy!
                }
            } catch (Exception e) {
                healthFuture.completeExceptionally(e);
            }
        }, 0, FAST_CHECK_INTERVAL, TimeUnit.SECONDS);

        // Clean up the scheduled task when the future completes
        healthFuture.whenComplete((result, throwable) -> {
            System.out.println("Stopping fast health checking for worker: " + workerId +
                    " (result: " + result + ")");
            checkTask.cancel(false);
        });

        return healthFuture;
    }

    private void performHealthChecks() {
        workers.values().parallelStream().forEach(this::checkWorkerHealth);
    }

    public boolean isHealthy(Worker worker) {
        try {
            String healthUrl = "http://" + worker.getHost() + ":" + worker.getPort() + "/test";
            HttpURLConnection connection = (HttpURLConnection) new URL(healthUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } catch(Exception e) {
            return false;
        }
    }

    public void checkWorkerHealth(Worker worker) {
        boolean isHealthy = isHealthy(worker);
        if (!isHealthy && worker.isAvailable()) {
            System.err.println("Worker " + worker.getId() + " failed health check, marking unavailable");
            worker.setUnhealthy();
        } else if (isHealthy && worker.isUnhealthy()) {
            System.out.println("Worker " + worker.getId() + " recovered, marking available");
            worker.setAvailable();
        }
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::performHealthChecks, 30, NORMAL_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
        fastCheckScheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!fastCheckScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                fastCheckScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            fastCheckScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}