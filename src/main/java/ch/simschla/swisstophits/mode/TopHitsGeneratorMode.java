package ch.simschla.swisstophits.mode;

import lombok.Data;

@Data
public class TopHitsGeneratorMode {

    public static final TopHitsGeneratorMode INSTANCE = new TopHitsGeneratorMode();

    private TopHitsGeneratorMode() {
    }

    boolean isDryRunEnabled = Boolean.parseBoolean(System.getProperty("dryRun", "true"));

    boolean isNormalizeEnabled = false;
}
