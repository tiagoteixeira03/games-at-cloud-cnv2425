package pt.ulisboa.tecnico.cnv;

import com.sun.net.httpserver.HttpExchange;


import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HttpForwarder {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public static void forwardResponse(WorkerResponse response, HttpExchange exchange) {
        try {
            int statusCode = response.statusCode();

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            String responseBody = response.body();

            exchange.sendResponseHeaders(statusCode, responseBody.length());

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes());
            }

            System.out.println("Sent response " + statusCode);

        } catch (IOException e) {
            try {
                System.out.println("Exception occurred while sending response: " + e.getMessage());
                exchange.getResponseBody().close();
            } catch (IOException ex) {
                System.out.println("Error sending response: " + ex.getMessage());
            }
        }
    }

    public static CompletableFuture<WorkerResponse> forwardRequest(Worker worker, HttpExchange exchange, boolean storeMetrics) {
        String targetUrl = buildTargetUrl(worker, exchange, storeMetrics);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(httpResponse -> new WorkerResponse(httpResponse.statusCode(), httpResponse.body()))
                .exceptionally(e -> {
                    if (e instanceof HttpTimeoutException) {
                        System.out.println("Request timed out to " + targetUrl + ": " + e.getMessage());
                    } else {
                        System.out.println("Failed to forward request to " + targetUrl + ": " + e.getMessage());
                    }
                    return null;
                });
    }


    private static String buildTargetUrl(Worker worker, HttpExchange exchange, boolean storeMetrics) {
        StringBuilder url = new StringBuilder();
        url.append("http://").append(worker.getHost()).append(":").append(worker.getPort());
        url.append(exchange.getRequestURI().getPath());

        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            url.append("?").append(query);
            if(storeMetrics) {
                url.append("&storeMetrics=true");
            }
        }


        return url.toString();
    }
}