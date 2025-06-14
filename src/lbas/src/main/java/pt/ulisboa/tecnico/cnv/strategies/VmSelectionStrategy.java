package pt.ulisboa.tecnico.cnv.strategies;

import pt.ulisboa.tecnico.cnv.Worker;

import java.util.List;
import java.util.Map;

public interface VmSelectionStrategy {
    List<String> selectVms(Map<String, Worker> workers, long requestComplexity, long avgLoad);
}
