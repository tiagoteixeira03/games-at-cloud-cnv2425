package pt.ulisboa.tecnico.cnv.strategies;

import pt.ulisboa.tecnico.cnv.LoadBalancer;
import pt.ulisboa.tecnico.cnv.Worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BalancedStrategy implements VmSelectionStrategy {

    @Override
    public List<String> selectVms(Map<String, Worker> workers, long requestComplexity, long avgLoad) {
        List<Map.Entry<String, Double>> candidates = new ArrayList<>();

        for (Map.Entry<String, Worker> entry : workers.entrySet()) {
            String id = entry.getKey();
            Worker worker = entry.getValue();

            if (!worker.isAvailable()) continue;

            long projectedLoad = worker.getCurrentLoad() + requestComplexity;
            if (projectedLoad > LoadBalancer.VM_CAPACITY) continue;

            double packScore = (double) worker.getCurrentLoad() / LoadBalancer.VM_CAPACITY;
            double spreadScore = 1.0 - packScore;
            double spreadWeight = 0.3 + 0.7 * ((double) avgLoad / LoadBalancer.VM_CAPACITY);
            double packWeight = 1.0 - spreadWeight;

            double score = spreadScore * spreadWeight + packScore * packWeight;
            candidates.add(Map.entry(id, score));
        }
        return candidates.stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()) // ascending order
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
