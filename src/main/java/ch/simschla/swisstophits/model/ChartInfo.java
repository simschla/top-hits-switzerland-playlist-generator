package ch.simschla.swisstophits.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ChartInfo {

    @NonNull
    Integer chartYear;

    @NonNull
    @Singular
    List<SongInfo> chartSongs;
}
