package ch.simschla.swisstophits.model;

import java.util.List;
import lombok.*;

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
