package ch.simschla.swisstophits.model;

import lombok.*;

import java.net.URL;
import java.util.List;

@Value
@Builder
public class SongInfo {

    @NonNull
    Integer position;

    @NonNull
    @With
    String song;

    @NonNull
    @Singular
    @With
    List<String> artists;

    @NonNull
    @Builder.Default
    Boolean swissAct = false;

    @NonNull
    Integer chartYear;

    URL coverImageUrl;

    public String toShortDesc() {
        return String.format("%s %s", getSong(), getArtists()) + (getSwissAct() ? " [CH]" : "");
    }

}
