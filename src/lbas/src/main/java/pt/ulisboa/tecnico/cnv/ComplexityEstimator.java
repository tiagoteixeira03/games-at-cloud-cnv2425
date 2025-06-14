package pt.ulisboa.tecnico.cnv;

import pt.ulisboa.tecnico.cnv.storage.StorageUtil;
import pt.ulisboa.tecnico.cnv.util.CaptureTheFlagEstimator;
import pt.ulisboa.tecnico.cnv.util.FifteenPuzzleEstimator;
import pt.ulisboa.tecnico.cnv.util.GameOfLifeEstimator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ComplexityEstimator {
    private static final int MAX_ENTRIES = 5000;
    private static final double FIFTEEN_PUZZLE_SCALE_WEIGHT = 3.54;
    private static final double GAME_OF_LIFE_SCALE_WEIGHT = 1.84;

    public record ComplexityEstimate(long value, boolean storeMetrics) {}

    // Thread-safe LRU Cache
    private final Map<String, Long> localCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_ENTRIES;
                }
            }
    );

    public ComplexityEstimate estimateComplexity(String game, Map<String, String> params) {
        String cacheKey = StorageUtil.serializeParameters(params);

        // Try local cache first
        Long complexity = localCache.get(cacheKey);
        if (complexity != null) {
            return new ComplexityEstimate(normalizeComplexity(game, complexity), false);
        }

        // Try fetching from storage
        complexity = fetchFromStorage(game, params);
        if (complexity != null) {
            localCache.put(cacheKey, complexity);
            return new ComplexityEstimate(normalizeComplexity(game, complexity), false);
        }
        // Fallback to approximation
        complexity = approximateComplexityFromParams(game, params);
        return new ComplexityEstimate(normalizeComplexity(game, complexity), true);
    }

    private Long normalizeComplexity(String game, Long complexity) {
        if(game.equals("FifteenPuzzle"))
            return Math.round(complexity * FIFTEEN_PUZZLE_SCALE_WEIGHT);
        else if (game.equals("GameOfLife"))
            return Math.round(complexity * GAME_OF_LIFE_SCALE_WEIGHT);
        return complexity < LoadBalancer.VM_CAPACITY ? complexity : LoadBalancer.VM_CAPACITY;
    }

    private Long fetchFromStorage(String game, Map<String, String> params) {
        try {
            return StorageUtil.getMetrics(game, StorageUtil.serializeParameters(params));
        } catch (Exception e) {
            System.err.println("Error fetching complexity from storage: " + e.getMessage());
            return null;
        }
    }

    private Long approximateComplexityFromParams(String game, Map<String, String> params) {
        return switch (game.toLowerCase()) {
            case "fifteenpuzzle" -> FifteenPuzzleEstimator.estimateComplexity(params);
            case "capturetheflag" -> CaptureTheFlagEstimator.estimateComplexity(params);
            case "gameoflife" -> GameOfLifeEstimator.estimateComplexity(params);
            default -> throw new IllegalArgumentException("Unsupported game: " + game);
        };
    }
}
