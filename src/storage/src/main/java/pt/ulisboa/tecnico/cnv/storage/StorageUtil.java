package pt.ulisboa.tecnico.cnv.storage;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import pt.ulisboa.tecnico.cnv.javassist.model.Statistics;

import java.util.*;
import java.util.stream.Collectors;

public class StorageUtil {


    public static final String TABLE_NAME = "metrics";

    public static final String PARTITION_KEY = "game";

    public static final String SORT_KEY = "parameters";

    private static final String AWS_REGION = "us-east-1";

    private static final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion(AWS_REGION)
            .build();

    public static void createTable() throws InterruptedException {
        CreateTableRequest createTableRequest = new CreateTableRequest()
                .withTableName(TABLE_NAME)
                .withKeySchema(
                        new KeySchemaElement(PARTITION_KEY, KeyType.HASH),     // Partition key
                        new KeySchemaElement(SORT_KEY, KeyType.RANGE) // Sort key
                )
                .withAttributeDefinitions(
                        new AttributeDefinition(PARTITION_KEY, ScalarAttributeType.S),
                        new AttributeDefinition(SORT_KEY, ScalarAttributeType.S)
                )
                .withProvisionedThroughput(
                        new ProvisionedThroughput(1L, 1L)
                );
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
        TableUtils.waitUntilActive(dynamoDB, TABLE_NAME);
        System.out.println("Table created and ready: " + TABLE_NAME);
    }

    public static String serializeParameters(Map<String, String> params) {
        params.remove("storeMetrics");
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Ensure consistent ordering
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("#"));
    }

    public static Long getMetrics(String game, String parameters) {
        System.out.println("Fetching from " + game + " params: " + parameters);
        GetItemRequest getItemRequest = new GetItemRequest()
                .withTableName(TABLE_NAME)
                .addKeyEntry(PARTITION_KEY, new AttributeValue(game))
                .addKeyEntry(SORT_KEY, new AttributeValue(parameters));

        Map<String, AttributeValue> metrics = dynamoDB.getItem(getItemRequest).getItem();

        return metrics != null ? Long.parseLong(metrics.get("complexity").getN()) : null;

    }

    public static void storeMetrics(Map<String, String> parameters, Statistics statistics, String game) {
        String paramKey = serializeParameters(parameters);

        long nmethod = statistics.getNmethod();
        long ninsts = statistics.getNinsts();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PARTITION_KEY, new AttributeValue(game)); // Partition key
        item.put(SORT_KEY, new AttributeValue(paramKey)); // Sort key
        item.put("nmethod", new AttributeValue().withN(String.valueOf(nmethod)));
        item.put("ninsts", new AttributeValue().withN(String.valueOf(ninsts)));
        item.put("complexity", new AttributeValue().withN(String.valueOf(statistics.computeComplexity(game, nmethod, ninsts))));
        PutItemRequest putRequest = new PutItemRequest()
                .withTableName(TABLE_NAME)
                .withItem(item);

        dynamoDB.putItem(putRequest);
        System.out.println("Stored statistics for " + game);

    }
}