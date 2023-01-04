package ch.simschla.swisstophits.model;

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class ChartInfo {

    @NonNull
    Integer chartYear;

    @NonNull
    @Singular
    @With
    List<SongInfo> chartSongs;
}
