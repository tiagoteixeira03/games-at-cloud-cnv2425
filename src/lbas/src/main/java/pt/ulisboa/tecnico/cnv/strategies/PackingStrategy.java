package pt.ulisboa.tecnico.cnv.strategies;

import pt.ulisboa.tecnico.cnv.LoadBalancer;
import pt.ulisboa.tecnico.cnv.Worker;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PackingStrategy implements VmSelectionStrategy {
    @Override
    public List<String> selectVms(Map<String, Worker> workers, long requestComplexity, long avgLoad) {
        return workers.entrySet().stream()
                .filter(entry -> {
                    Worker worker = entry.getValue();
                    // Pre-filter to avoid considering obviously overloaded workers
                    return worker.isAvailable() && (worker.getCurrentLoad() + requestComplexity <= LoadBalancer.VM_CAPACITY);
                })
                // Sort by current load descending (most loaded first)
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(Worker::getCurrentLoad).reversed()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}