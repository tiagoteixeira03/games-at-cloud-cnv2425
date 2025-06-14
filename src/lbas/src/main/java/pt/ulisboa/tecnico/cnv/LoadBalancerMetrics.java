// LoadBalancerMetrics.java
package pt.ulisboa.tecnico.cnv;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class LoadBalancerMetrics {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong forwardedToWorkers = new AtomicLong(0);
    private final AtomicLong forwardedToLambda = new AtomicLong(0);
    private final AtomicLong queuedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final LongAdder totalResponseTime = new LongAdder();

    public void incrementTotalRequests() { totalRequests.incrementAndGet(); }
    public void incrementForwardedToWorkers() { forwardedToWorkers.incrementAndGet(); }
    public void incrementForwardedToLambda() { forwardedToLambda.incrementAndGet(); }
    public void incrementQueuedRequests() { queuedRequests.incrementAndGet(); }
    public void incrementRejectedRequests() { rejectedRequests.incrementAndGet(); }
    public void addResponseTime(long responseTimeMs) { totalResponseTime.add(responseTimeMs); }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getForwardedToWorkers() { return forwardedToWorkers.get(); }
    public long getForwardedToLambda() { return forwardedToLambda.get(); }
    public long getQueuedRequests() { return queuedRequests.get(); }
    public long getRejectedRequests() { return rejectedRequests.get(); }
    public long getTotalResponseTime() { return totalResponseTime.sum(); }

    public double getAverageResponseTime() {
        long total = getTotalRequests();
        return total > 0 ? (double) getTotalResponseTime() / total : 0.0;
    }

    public void printStats() {
        System.out.println("=== Load Balancer Metrics ===");
        System.out.println("Total Requests: " + getTotalRequests());
        System.out.println("Forwarded to Workers: " + getForwardedToWorkers());
        System.out.println("Forwarded to Lambda: " + getForwardedToLambda());
        System.out.println("Queued Requests: " + getQueuedRequests());
        System.out.println("Rejected Requests: " + getRejectedRequests());
        System.out.println("Average Response Time: " + String.format("%.2f ms", getAverageResponseTime()));
        System.out.println("============================");
    }
}