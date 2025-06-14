package pt.ulisboa.tecnico.cnv;

import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class Worker {
    public enum Status {
        AVAILABLE, DRAINING, UNHEALTHY
    }

    private static final long BOOTUP_TIME = 20000;

    @Getter
    private final String host;
    @Getter
    private final int port;
    // Getters and setters
    @Getter
    private final String id;
    private final AtomicLong currentLoad = new AtomicLong(0);
    private volatile Status status = Status.UNHEALTHY;
    @Getter
    private CompletableFuture<Void> terminateFuture;
    private final long creationTime;

    public Worker(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.creationTime = System.currentTimeMillis();
    }

    public long getCurrentLoad() { return currentLoad.get(); }
    public boolean isDraining() { return status == Status.DRAINING; }
    public boolean isAvailable() {
        return status == Status.AVAILABLE && System.currentTimeMillis() - creationTime > BOOTUP_TIME;
    }
    public boolean isUnhealthy() { return status == Status.UNHEALTHY; }

    public CompletableFuture<Void> setDraining() {
        this.status = Status.DRAINING;
        this.terminateFuture = new CompletableFuture<>();
        return terminateFuture;
    }
    public void setAvailable() { this.status = Status.AVAILABLE; }
    public void setUnhealthy() { this.status = Status.UNHEALTHY; }

    public void completeTerminate(){
        terminateFuture.complete(null);
    }

    public void decreaseLoad(long load) { currentLoad.addAndGet(-load); }


    public boolean hasCapacityToExecute(long complexity) {
        return currentLoad.get() + complexity <= LoadBalancer.VM_CAPACITY;
    }

    public boolean tryAssignLoad(long complexity) {
        // Use compare-and-swap loop for atomic check-and-increment
        while (true) {
            long current = currentLoad.get();

            if (!hasCapacityToExecute(complexity) || !isAvailable()) {
                return false;
            }

            // Try to atomically update currentLoad
            if (currentLoad.compareAndSet(current, current + complexity)) {
                return true;
            }
        }
    }

    @Override
    public String toString() {
        return "Worker{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", id='" + id + '\'' +
                ", currentLoad=" + currentLoad +
                ", status=" + status +
                '}';
    }
}