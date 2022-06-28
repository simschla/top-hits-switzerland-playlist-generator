package ch.simschla.swisstophits.mode;

import lombok.Data;

@Data
public class TopHitsGeneratorMode {

    public static final TopHitsGeneratorMode INSTANCE = new TopHitsGeneratorMode();

    private TopHitsGeneratorMode() {
    }

    boolean isDryRunEnabled = true;

    boolean isNormalizeEnabled = false;
}
