package pt.ulisboa.tecnico.cnv.util;

import java.util.Map;

public class FifteenPuzzleEstimator {
    // Model parameters from JSON
    private static final double INTERCEPT = 13.911182271961003;
    private static final double[] COEFFICIENTS = {
            2.7197878925380454,   // shuffles
            -2.8012436729844765,  // size
            -0.700637577062229,   // shuffles^2
            1.125892037431241,    // shuffles*size
            0.6631370317346617,   // size^2
            -0.3122297659104736,  // shuffles^3
            0.6075140717414949,   // shuffles^2*size
            0.6158385604063749,   // shuffles*size^2
            0.46671759524734224   // size^3
    };

    // Scaler parameters
    private static final double[] SCALER_MEAN = {57.372727272727275, 10.6};
    private static final double[] SCALER_SCALE = {14.775815061911194, 2.8514748974713227};

    public static Long estimateComplexity(Map<String, String> params) {
        double shuffles = Double.parseDouble(params.get("shuffles"));
        double size = Double.parseDouble(params.get("size"));

        // Apply scaling (standardization)
        double shuffles_scaled = (shuffles - SCALER_MEAN[0]) / SCALER_SCALE[0];
        double size_scaled = (size - SCALER_MEAN[1]) / SCALER_SCALE[1];

        // Create polynomial features
        double[] features = {
                shuffles_scaled,                                    // shuffles
                size_scaled,                                        // size
                shuffles_scaled * shuffles_scaled,                  // shuffles^2
                shuffles_scaled * size_scaled,                      // shuffles*size
                size_scaled * size_scaled,                          // size^2
                shuffles_scaled * shuffles_scaled * shuffles_scaled, // shuffles^3
                shuffles_scaled * shuffles_scaled * size_scaled,    // shuffles^2*size
                shuffles_scaled * size_scaled * size_scaled,        // shuffles*size^2
                size_scaled * size_scaled * size_scaled             // size^3
        };

        // Calculate linear combination
        double result = INTERCEPT;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            result += COEFFICIENTS[i] * features[i];
        }

        // Apply inverse log transformation (exp)
        double complexity = Math.expm1(result);

        return Math.round(complexity);
    }
}