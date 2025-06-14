package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;

import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import pt.ulisboa.tecnico.cnv.javassist.model.Statistics;

public class ICount extends CodeDumper {

    private static final Map<Long, Statistics> threadPoolStatistics = new ConcurrentHashMap<>();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static Statistics getThreadStatistics(){
        Long myId = Thread.currentThread().getId();
        threadPoolStatistics.putIfAbsent(myId, new Statistics());
        return threadPoolStatistics.get(myId);
    }

    public static void clearThreadStatistics() {
        threadPoolStatistics.remove(Thread.currentThread().getId());
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

    public static void incDataWrite() {
        Statistics currentThreadStatistics = getThreadStatistics();
        currentThreadStatistics.incrementNdataWrites();
    }

    public static void incDataRead() {
        Statistics currentThreadStatistics = getThreadStatistics();
        currentThreadStatistics.incrementNdataReads();
    }


    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);

        // Count method invocations
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));

        // Instrument data accesses (field read)
        /*behavior.instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess f) throws CannotCompileException {
                if (f.isReader()) {
                    f.replace(String.format("{ %s.incDataRead(); $_ = $proceed($$); }", ICount.class.getName()));
                }
                else if (f.isWriter()){
                    f.replace(String.format("{ %s.incDataWrite(); $proceed($$); }", ICount.class.getName()));
                }
            }
        });*/
    }

    // Count number of basic blocks and instructions
    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(), block.getPosition(), block.getLength()));
   }

}
