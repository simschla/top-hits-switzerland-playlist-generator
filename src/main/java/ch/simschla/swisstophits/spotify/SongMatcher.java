package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.model.SongInfo;
import lombok.NonNull;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public class SongMatcher {

    @NonNull
    private final SongInfo songToLookFor;

    public SongMatcher(@NonNull SongInfo songToLookFor) {
        this.songToLookFor = songToLookFor;
    }


    public Optional<Track> selectBestMatchingTrack(List<Track> tracks) {
        // first remove blocklist
        List<Track> welcomedTracks = tracks.stream()
                .filter(not(this::isBlocklisted))
                .toList();

        return findExactMatch(welcomedTracks)
                .or(() -> findContainedTitleAndSimilarArtists(welcomedTracks));
    }

    private Optional<Track> findExactMatch(List<Track> tracks) {
        return tracks.stream()
                .filter(track -> track.getName().equalsIgnoreCase(songToLookFor.getSong()))
                .filter(this::allArtistNamesAreContainedIn)
                .findFirst();
    }

    private Optional<Track> findContainedTitleAndSimilarArtists(List<Track> tracks) {
        return tracks.stream()
                .filter(track -> songNameIsContainedIn(track))
                .filter(track -> allArtistNamesAreContainedIn(track))
                .min(new TrackRanker());
    }

    private boolean isBlocklisted(Track track) {
        return matchesKaraoke(track);
    }

    private boolean matchesKaraoke(Track t) {
        if (simplified(songToLookFor.getSong()).contains("karaoke")) {
            return false;
        }
        if (songToLookFor.getArtists().stream().map(SongMatcher::simplified).anyMatch(s -> s.contains("karaoke"))) {
            return false;
        }
        return simplified(t.getName()).contains("karaoke")
                || simplified(t.getAlbum().getName()).contains("karaoke")
                || Arrays.stream(t.getArtists()).map(artist -> simplified(artist.getName())).anyMatch(a -> a.contains("karaoke"));
    }

    private boolean songNameIsContainedIn(Track t) {
        return simplified(t.getName()).contains(simplified(songToLookFor.getSong()));
    }

    private boolean allArtistNamesAreContainedIn(Track t) {
        final String trackArtists = Arrays.stream(t.getArtists())
                .map(s -> simplified(s.getName()))
                .flatMap(s -> Arrays.stream(s.split("\\s+")))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .collect(Collectors.joining(" "));

        return songToLookFor.getArtists()
                .stream()
                .flatMap(artistName -> Arrays.stream(artistName.split("\\s+")))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .map(SongMatcher::simplified)
                .allMatch(trackArtists::contains);
    }

    private static String simplified(String original) {
        if (original == null) {
            return null;
        }
        return original
                .toLowerCase() // case insensitive
                .replaceAll("[^a-z0-9]", ""); // no special chars
    }

    private class TrackRanker implements Comparator<Track> {
        @Override
        public int compare(Track o1, Track o2) {
            if (o1 == null && o2 == null) {
                return 0;
            }
            if (o1 == null) {
                return +1;
            }
            if (o2 == null) {
                return -1;
            }

            // most populars first
            if (o1.getPopularity() > o2.getPopularity()) {
                return -1;
            }
            if (o1.getPopularity() < o2.getPopularity()) {
                return +1;
            }

            // then the one with closest release date
            final int releaseDelta1 = releaseDelta(o1.getAlbum().getReleaseDate());
            final int releaseDelta2 = releaseDelta(o2.getAlbum().getReleaseDate());

            if (releaseDelta1 < releaseDelta2) {
                return -1;
            }
            if (releaseDelta1 > releaseDelta2) {
                return +1;
            }

            // then pick shortest (to get radio edits / single versions before long/remixed versions)
            if (o1.getDurationMs() < o2.getDurationMs()) {
                return -1;
            }
            if (o2.getDurationMs() > o2.getDurationMs()) {
                return +1;
            }

//                                    .comparingInt(Track::getPopularity).reversed()
//                    .thenComparingInt(t -> songToLookFor.getChartYear() - Integer.parseInt(t.getAlbum().getReleaseDate().substring(0, 4)))
//                    .thenComparingInt((Track track) -> track.getName().contains("Radio Edit") || track.getName().contains("Short Version") || track.getName().contains("Single Version") ? -11 : 1))
            return 0;
        }

        private int releaseDelta(String releaseDate) {
            try {
                return Integer.parseInt(releaseDate.substring(0, 4)) - songToLookFor.getChartYear();
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
