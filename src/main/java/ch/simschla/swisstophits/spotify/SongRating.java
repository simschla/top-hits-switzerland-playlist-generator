package ch.simschla.swisstophits.spotify;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.Setter;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.util.Collection;
import java.util.stream.Stream;

@Data
class SongRating {

    @Setter(value = AccessLevel.PRIVATE)
    Double calculatedScore;

    boolean blocked;

    double songNameScore;

    double artistCountScore;

    double artistNamesScore;

    double remixScore;

    double liveScore;

    double radioVersionScore;

    double popularityScore;

    double rankingScore;

    double releaseDateScore;

    double durationScore;

    double trackNumberScore;

    @NonNull
    final Track track;

    void calculateScore(Collection<SongRating> all) {
        if (calculatedScore != null) {
            return; // already done
        }

        if (blocked) {
            calculatedScore = Double.MIN_VALUE;
            return;
        }

        calculatedScore = Stream.of(
                        getSongNameScore(),
                        getArtistNamesScore(),
                        getArtistCountScore(),
                        getRemixScore(),
                        getLiveScore(),
                        getRadioVersionScore(),
                        getPopularityScore(),
                        getRankingScore(),
                        getReleaseDateScore(),
                        getDurationScore(),
                        getTrackNumberScore()
                ).reduce(Double::sum)
                .orElseThrow();
    }
}
