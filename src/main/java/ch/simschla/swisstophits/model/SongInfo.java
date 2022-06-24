package ch.simschla.swisstophits.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

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

    public String toShortDesc() {
        return String.format("%s by %s", getSong(), getArtists()) + (getSwissAct() ? " [CH]" : "");
    }

}
