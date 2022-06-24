package ch.simschla.swisstophits.mode;


public enum TopHitsGeneratorMode {

    INSTANCE;

    boolean dryRun = true;

    public boolean dryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
