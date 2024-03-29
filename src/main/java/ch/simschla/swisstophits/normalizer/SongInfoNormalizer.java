package ch.simschla.swisstophits.normalizer;

import ch.simschla.swisstophits.mode.TopHitsGeneratorMode;
import ch.simschla.swisstophits.model.ChartInfo;
import ch.simschla.swisstophits.model.SongInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SongInfoNormalizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongInfoNormalizer.class);

    private static final Map<Integer, List<SongInfoFixer>> FIXERS = Map.of(
            2003,
            List.of(new SongInfoFixerSongName(
                    List.of("Mia Aegerter"), "Hie u jetzt - Right Here Right Now", null, "Hie u jetzt")));

    public ChartInfo normalize(@NonNull ChartInfo chartInfo) {
        if (!TopHitsGeneratorMode.INSTANCE.isNormalizeEnabled()) {
            LOGGER.info("Normalizing disabled. Skipping.");
            return chartInfo;
        }
        if (!FIXERS.containsKey(chartInfo.getChartYear())) {
            return chartInfo;
        }
        List<SongInfoFixer> fixers = FIXERS.get(chartInfo.getChartYear());
        return chartInfo.withChartSongs(chartInfo.getChartSongs().stream()
                .map(songInfo -> normalize(chartInfo.getChartYear(), songInfo))
                .toList());
    }

    public SongInfo normalize(@NonNull Integer chartYear, @NonNull SongInfo songInfo) {
        SongInfo fixed = songInfo;

        List<SongInfoFixer> fixers = FIXERS.getOrDefault(chartYear, Collections.emptyList());

        for (SongInfoFixer fixer : fixers) {
            fixed = fixer.fix(fixed);
        }
        return fixed;
    }
}
