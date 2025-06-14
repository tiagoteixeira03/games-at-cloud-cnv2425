package pt.ulisboa.tecnico.cnv;

import com.sun.net.httpserver.HttpExchange;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

@Getter
public class QueuedRequest {
    private final HttpExchange exchange;
    private final long estimatedComplexity;
    private final boolean storeMetrics;
    private final CompletableFuture<WorkerResponse> future;

    public QueuedRequest(HttpExchange exchange, long estimatedComplexity, boolean storeMetrics) {
        this.exchange = exchange;
        this.estimatedComplexity = estimatedComplexity;
        this.future = new CompletableFuture<>();
        this.storeMetrics = storeMetrics;
    }

    @Override
    public String toString() {
        return "QueuedRequest{" +
                "exchange=" + exchange +
                ", estimatedComplexity=" + estimatedComplexity +
                ", storeMetrics=" + storeMetrics +
                ", future=" + future +
                '}';
    }
}