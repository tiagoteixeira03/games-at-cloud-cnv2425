package pt.ulisboa.tecnico.cnv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import pt.ulisboa.tecnico.cnv.strategies.BalancedStrategy;
import pt.ulisboa.tecnico.cnv.strategies.PackingStrategy;
import pt.ulisboa.tecnico.cnv.strategies.SpreadingStrategy;
import pt.ulisboa.tecnico.cnv.strategies.VmSelectionStrategy;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RequestAssigner implements HttpHandler {

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MILLIS = 100;

    private final LoadBalancer loadBalancer;

    private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();


    public RequestAssigner(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    // Helper record to group related data
    public record RequestContext(
            long complexity,
            boolean storeMetrics,
            long avgLoad
    ) {}

    @Override
    public void handle(HttpExchange exchange) {
        loadBalancer.getMetrics().incrementTotalRequests();
        System.out.println("Handling " + exchange.getRequestURI());

        long startTime = System.currentTimeMillis();
        try {
            RequestContext context = buildRequestContext(exchange);
            WorkerResponse response = processRequest(exchange, context);
            handleResponse(response, exchange);
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
            loadBalancer.getMetrics().incrementRejectedRequests();
            sendErrorResponse(exchange);
        } finally {
            recordMetrics(startTime);
            if(loadBalancer.getGlobalQueueLength() > 0) {
                cleanupExecutor.submit(loadBalancer::clearGlobal);
            }
        }
    }

    private RequestContext buildRequestContext(HttpExchange exchange) {
        Map<String, String> params = extractParameters(exchange);
        String game = extractGame(exchange);
        ComplexityEstimator.ComplexityEstimate estimatedComplexity =
                loadBalancer.getComplexityEstimator().estimateComplexity(game, params);
        System.out.println("Complexity: " + estimatedComplexity.value());
        return new RequestContext(
                estimatedComplexity.value(),
                estimatedComplexity.storeMetrics(),
                loadBalancer.calculateAverageLoad()
        );
    }

    private WorkerResponse processRequest(HttpExchange exchange, RequestContext context)
            throws Exception {
        loadBalancer.printWorkerSummary();
        for(int i = 0; i < MAX_RETRIES; i++) {
            VmSelectionStrategy strategy = selectStrategy(context);
            CompletableFuture<WorkerResponse> responseFuture = loadBalancer.tryAssignToBestCandidate(exchange, context, strategy);
            if (responseFuture != null) {
                WorkerResponse response = responseFuture.get();
                if (response != null && response.isSuccess()) {
                    return response;
                }
            }
            Thread.sleep(RETRY_DELAY_MILLIS);
            context = buildRequestContext(exchange);
        }
        return null;
    }

    private VmSelectionStrategy selectStrategy(RequestContext context) {
        if (shouldUseSpreadingMode(context)) {
            System.out.println("Spreading mode");
            return new SpreadingStrategy();
        } else if (shouldUsePackingMode(context)) {
            System.out.println("Packing mode");
            return new PackingStrategy();
        } else {
            System.out.println("Balanced mode");
            return new BalancedStrategy();

        }
    }

    private boolean shouldUseSpreadingMode(RequestContext context) {
        return context.avgLoad > LoadBalancer.SPREAD_THRESHOLD * LoadBalancer.VM_CAPACITY;
    }

    private boolean shouldUsePackingMode(RequestContext context) {
        return context.avgLoad < LoadBalancer.PACK_THRESHOLD * LoadBalancer.VM_CAPACITY;
    }

    private void handleResponse(WorkerResponse response, HttpExchange exchange) {
        if (response != null) {
            HttpForwarder.forwardResponse(response, exchange);
        } else {
            sendErrorResponse(exchange);
        }
    }

    private void recordMetrics(long startTime) {
        long responseTime = System.currentTimeMillis() - startTime;
        loadBalancer.getMetrics().addResponseTime(responseTime);
        //loadBalancer.getMetrics().printStats();
    }

    private void sendErrorResponse(HttpExchange exchange) {
        try {
            byte[] response = "Internal Server Error".getBytes();
            exchange.sendResponseHeaders(500, response.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (Exception e) {
            System.err.println("Error sending error response: " + e.getMessage());
        }
    }

    private String extractGame(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    private Map<String, String> extractParameters(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }
}