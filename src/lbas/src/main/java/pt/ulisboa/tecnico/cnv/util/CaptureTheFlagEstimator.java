package pt.ulisboa.tecnico.cnv.util;

import java.util.Map;

public class CaptureTheFlagEstimator {
    // Model parameters from JSON
    private static final double INTERCEPT = 16.444668341118454;
    private static final double[] COEFFICIENTS = {
            0.5566244161369187,    // gridSize
            0.5566244161369189,    // numBlueAgents
            0.5566244161369186,    // numRedAgents
            -0.582629164084132,    // flagPlacementType_B
            -0.26080560520453505,  // flagPlacementType_C
            -0.06481903585870188,  // gridSize^2
            -0.06481903585870182,  // gridSize*numBlueAgents
            -0.06481903585870176,  // gridSize*numRedAgents
            -0.0360513570832752,   // gridSize*flagPlacementType_B
            0.0029728806640509087, // gridSize*flagPlacementType_C
            -0.06481903585870176,  // numBlueAgents^2
            -0.06481903585870176,  // numBlueAgents*numRedAgents
            -0.0360513570832752,   // numBlueAgents*flagPlacementType_B
            0.0029728806640509087, // numBlueAgents*flagPlacementType_C
            -0.06481903585870176,  // numRedAgents^2
            -0.0360513570832752,   // numRedAgents*flagPlacementType_B
            0.0029728806640509087, // numRedAgents*flagPlacementType_C
            -0.5826291640841318,   // flagPlacementType_B^2
            0.0,                   // flagPlacementType_B*flagPlacementType_C
            -0.26080560520453455   // flagPlacementType_C^2
    };

    // Scaler parameters
    private static final double[] SCALER_MEAN = {24.36111111111111, 19.36111111111111, 19.36111111111111};
    private static final double[] SCALER_SCALE = {8.614112380202933, 8.614112380202933, 8.614112380202933};

    public static Long estimateComplexity(Map<String, String> params) {
        double gridSize = Double.parseDouble(params.get("gridSize"));
        double numBlueAgents = Double.parseDouble(params.get("numBlueAgents"));
        double numRedAgents = Double.parseDouble(params.get("numRedAgents"));
        String flagPlacement = params.get("flagPlacementType");

        // One-hot encoding for flag placement
        int flagPlacementType_B = flagPlacement.equals("B") ? 1 : 0;
        int flagPlacementType_C = flagPlacement.equals("C") ? 1 : 0;

        // Apply scaling (standardization)
        double gridSize_scaled = (gridSize - SCALER_MEAN[0]) / SCALER_SCALE[0];
        double numBlueAgents_scaled = (numBlueAgents - SCALER_MEAN[1]) / SCALER_SCALE[1];
        double numRedAgents_scaled = (numRedAgents - SCALER_MEAN[2]) / SCALER_SCALE[2];

        // Create polynomial features
        double[] features = {
                gridSize_scaled,                                           // gridSize
                numBlueAgents_scaled,                                      // numBlueAgents
                numRedAgents_scaled,                                       // numRedAgents
                flagPlacementType_B,                                       // flagPlacementType_B
                flagPlacementType_C,                                       // flagPlacementType_C
                gridSize_scaled * gridSize_scaled,                         // gridSize^2
                gridSize_scaled * numBlueAgents_scaled,                    // gridSize*numBlueAgents
                gridSize_scaled * numRedAgents_scaled,                     // gridSize*numRedAgents
                gridSize_scaled * flagPlacementType_B,                     // gridSize*flagPlacementType_B
                gridSize_scaled * flagPlacementType_C,                     // gridSize*flagPlacementType_C
                numBlueAgents_scaled * numBlueAgents_scaled,               // numBlueAgents^2
                numBlueAgents_scaled * numRedAgents_scaled,                // numBlueAgents*numRedAgents
                numBlueAgents_scaled * flagPlacementType_B,                // numBlueAgents*flagPlacementType_B
                numBlueAgents_scaled * flagPlacementType_C,                // numBlueAgents*flagPlacementType_C
                numRedAgents_scaled * numRedAgents_scaled,                 // numRedAgents^2
                numRedAgents_scaled * flagPlacementType_B,                 // numRedAgents*flagPlacementType_B
                numRedAgents_scaled * flagPlacementType_C,                 // numRedAgents*flagPlacementType_C
                flagPlacementType_B * flagPlacementType_B,                 // flagPlacementType_B^2
                flagPlacementType_B * flagPlacementType_C,                 // flagPlacementType_B*flagPlacementType_C
                flagPlacementType_C * flagPlacementType_C                  // flagPlacementType_C^2
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