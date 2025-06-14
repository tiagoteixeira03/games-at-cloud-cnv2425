package pt.ulisboa.tecnico.cnv;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LambdaInvoker {

    private final AWSLambda awsLambda;
    private final ObjectMapper mapper = new ObjectMapper();

    public LambdaInvoker() {
        awsLambda = AWSLambdaClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .build();
    }

    public CompletableFuture<WorkerResponse> invokeLambda(URI uri) {
        String game = uri.getPath().split("/")[1];
        Map<String, String> params = queryToMap(uri.getRawQuery());

        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] jsonPayload = mapper.writeValueAsBytes(params); // only params

                InvokeRequest request = new InvokeRequest()
                        .withFunctionName(game)
                        .withPayload(ByteBuffer.wrap(jsonPayload));

                InvokeResult result = awsLambda.invoke(request);

                int statusCode = result.getStatusCode();
                String payload = new String(result.getPayload().array(), StandardCharsets.UTF_8);

                if (statusCode != 200) {
                    System.out.println("Error invoking Lambda: " + statusCode);
                }

                return new WorkerResponse(statusCode, payload);
            } catch (Exception e) {
                System.err.println("Error invoking Lambda: " + e.getMessage());
                return null;
            }
        });
    }

    private Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
