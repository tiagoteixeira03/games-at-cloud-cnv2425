package pt.ulisboa.tecnico.cnv.util;

import java.util.Map;

public class GameOfLifeEstimator {
    // Model parameters from JSON
    private static final double INTERCEPT = 67.17800871655345;
    private static final double[] COEFFICIENTS = {
            893.8556339255783  // iterations
    };

    // No scaling needed (scaler_params is null)

    public static Long estimateComplexity(Map<String, String> params) {
        double iterations = Double.parseDouble(params.get("iterations"));

        // Simple linear model (no scaling, no log transformation)
        double complexity = INTERCEPT + COEFFICIENTS[0] * iterations;

        return Math.round(complexity);
    }
}