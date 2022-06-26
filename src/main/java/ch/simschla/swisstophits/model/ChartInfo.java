package ch.simschla.swisstophits.model;

import lombok.*;

import java.util.List;

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
