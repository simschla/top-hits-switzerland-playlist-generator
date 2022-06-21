package ch.simschla.swisstophits.model;

import lombok.*;

import java.net.URL;
import java.util.List;

@Value
@Builder
public class SongInfo {

    @NonNull
    Integer chartYear;

    @NonNull
    Integer position;

    @NonNull
    @Singular
    List<String> artists;

    @NonNull
    String song;

    @NonNull
    @Builder.Default
    Boolean swissAct = false;

    URL coverImageUrl;

}
