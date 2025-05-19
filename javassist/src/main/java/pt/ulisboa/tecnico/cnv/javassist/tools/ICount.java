package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import pt.ulisboa.tecnico.cnv.javassist.model.Statistics;

public class ICount extends CodeDumper {

    private static final Map<Long, Statistics> threadPoolStatistics = new ConcurrentHashMap<>();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static Statistics getThreadStatistics(){
        Long myId = Thread.currentThread().threadId();
        threadPoolStatistics.putIfAbsent(myId, new Statistics());
        return threadPoolStatistics.get(myId);
    }

    public static void incBasicBlock(int position, int length) {
        Statistics currentThreadStatistics = getThreadStatistics();
        currentThreadStatistics.incrementNblocks();
        currentThreadStatistics.incrementNinsts(length);
    }

    public static void incBehavior(String name) {
        Statistics currentThreadStatistics = getThreadStatistics();
        currentThreadStatistics.incrementNmethod();
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        System.out.println("Calling behavior transform on ICount");
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(), block.getPosition(), block.getLength()));
    }

}
