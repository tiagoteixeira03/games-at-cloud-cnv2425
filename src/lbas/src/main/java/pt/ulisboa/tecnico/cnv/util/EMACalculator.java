package pt.ulisboa.tecnico.cnv.util;

import java.util.List;

public class EMACalculator {
    public static Double calculateEMA(List<Double> datapoints) {
        if(datapoints.isEmpty()) {
            return null;
        }
        double ema = datapoints.get(0);
        double alpha = 2.0 / (datapoints.size() + 1);

        // Apply EMA formula across the rest
        for (int i = 1; i < datapoints.size(); i++) {
            ema = alpha * datapoints.get(i) + (1 - alpha) * ema;
        }

        return ema;
    }

    public static Double calculateAverage(List<Double> datapoints) {
        if(datapoints.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double d : datapoints) {
            sum += d;
        }
        return sum / datapoints.size();
    }
}

