package pt.ulisboa.tecnico.cnv.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;

import pt.ulisboa.tecnico.cnv.javassist.model.Statistics;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StorageUtil {

    private static final String OUTPUT_FILE = "statistics.json";

    public static void storeStatistics(Map<String, String> parameters, Statistics statistics) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Combine parameters and statistics into a single Map
        Map<String, Object> record = new HashMap<>(parameters);
        record.put("nblocks", statistics.getNblocks());
        record.put("nmethod", statistics.getNmethod());
        record.put("ninsts", statistics.getNinsts());

        List<Map<String, Object>> records = new ArrayList<>();

        File file = new File(OUTPUT_FILE);

        // Load existing records if the file exists
        if (file.exists()) {
            try {
                records = mapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
            } catch (IOException e) {
                System.err.println("Failed to read existing JSON file: " + e.getMessage());
            }
        }

        // Add the new record
        records.add(record);

        // Write updated list to file
        try {
            mapper.writeValue(file, records);
        } catch (IOException e) {
            System.err.println("Failed to write JSON file: " + e.getMessage());
        }
    }
}
