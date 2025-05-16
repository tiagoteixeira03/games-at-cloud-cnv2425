package pt.ulisboa.tecnico.cnv.javassist.model;

public class Statistics {
    /**
     * Number of executed basic blocks.
     */
    private long nblocks;

    /**
     * Number of executed methods.
     */
    private long nmethod;

    /**
     * Number of executed instructions.
     */
    private long ninsts;

    public Statistics(){
        nblocks = 0;
        nmethod = 0;
        ninsts = 0;
    }

    public long getNblocks(){
        return nblocks;
    }

    public long getNmethod(){
        return nmethod;
    }

    public long getNinsts(){
        return ninsts;
    }

    public void incrementNblocks(){
        nblocks++;
    }
    public void incrementNmethod(){
        nmethod++;
    }
    public void incrementNinsts(int length){
        ninsts += length;
    }
}
