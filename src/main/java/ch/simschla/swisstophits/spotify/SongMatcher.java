package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.model.SongInfo;
import lombok.NonNull;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.text.Normalizer;
import java.util.*;
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
                .filter(track -> anyArtistNameIsContainedIn(track))
                .min(new TrackRanker());
    }

    private boolean isBlocklisted(Track track) {
        return matchesKaraoke(track) || isLive(track) /*|| isRemix(track)*/;
    }

    private boolean matchesKaraoke(Track t) {
        if (simplified(songToLookFor.getSong()).contains("karaoke")) {
            return false;
        }
        if (songToLookFor.getArtists().stream().map(this::simplified).anyMatch(s -> s.contains("karaoke"))) {
            return false;
        }
        return simplified(t.getName()).contains("karaoke")
                || simplified(t.getAlbum().getName()).contains("karaoke")
                || Arrays.stream(t.getArtists()).map(artist -> simplified(artist.getName())).anyMatch(a -> a.contains("karaoke"));
    }

    private boolean isRemix(Track track) {
        return trackNameContainsButNotSongToLookFor(track, "mix") || trackNameContainsButNotSongToLookFor(track, "dub");
    }

    private boolean trackNameContainsButNotSongToLookFor(Track track, String searchString) {
        return track.getName().toLowerCase().contains(searchString.toLowerCase()) && !songToLookFor.getSong().toLowerCase().contains(searchString.toLowerCase());
    }

    private boolean isLive(Track track) {
        return trackNameContainsButNotSongToLookFor(track, "live");
    }

    private boolean songNameIsContainedIn(Track t) {
        String songName1 = simplified(t.getName());
        String songName2 = simplified(songToLookFor.getSong());
        return songName1.contains(songName2) ||
                songName2.contains(songName1) ||
                Arrays.stream(songToLookFor.getSong().split("-"))
                        .map(String::trim)
                        .filter(not(String::isEmpty))
                        .map(this::simplified)
                        .anyMatch(part -> simplified(t.getName()).contains(part));
    }

    private boolean allArtistNamesAreContainedIn(Track t) {
        final String trackArtists = Arrays.stream(t.getArtists())
                .map(s -> simplified(s.getName()))
                .flatMap(s -> Arrays.stream(s.split("\\s+")))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord)) // remove fill-words
                .collect(Collectors.joining(" "));

        return songToLookFor.getArtists()
                .stream()
                .map(this::replaceExceptionalArtistCases)
                .flatMap(artistName -> Arrays.stream(artistName.split("\\s+")))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord)) // remove fill-words
                .map(this::simplified)
                .allMatch(trackArtists::contains);
    }


    private boolean anyArtistNameIsContainedIn(Track t) {
        final Set<String> trackArtists = Arrays.stream(t.getArtists())
                .map(s -> simplified(s.getName()))
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord)) // remove fill-words
                .collect(Collectors.toSet());

        return songToLookFor.getArtists()
                .stream()
                .map(this::replaceExceptionalArtistCases)
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord)) // remove fill-words
                .map(this::simplified)
                .anyMatch(trackArtists::contains);
    }

    private String replaceExceptionalArtistCases(String orig) {
        if (orig.equalsIgnoreCase("star academy") || orig.equalsIgnoreCase("star academy 1")) {
            return "Star Academy I";
        }
        if (orig.equalsIgnoreCase("star academy 3")) {
            return "Star Academy III";
        }
        if (songToLookFor.getChartYear() <= 1994 && orig.equalsIgnoreCase("the symbol")) {
            return "Prince";
        }
        return orig;
    }

    private boolean isFillWord(String word) {
        return word.matches("(?i)(&|und|and|feat\\.?|featuring|the|der|die|das)");
    }

    private String simplified(String original) {
        if (original == null) {
            return null;
        }

        String simplified = Normalizer.normalize(original, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(); // case insensitive
        return Arrays.stream(simplified.split("\\s"))
                .map(String::trim)
                .filter(not(this::isFillWord))
                .map(s -> s.replaceAll("[^a-z0-9]", " ")) // no special chars)
                .collect(Collectors.joining(" "));
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

            // song name matches better
            if (songNameIsContainedIn(o1) && !songNameIsContainedIn(o2)) {
                return -1;
            }
            if (!songNameIsContainedIn(o1) && songNameIsContainedIn(o2)) {
                return +1;
            }

            // artists are perfect match
            if (allArtistNamesAreContainedIn(o1) && !allArtistNamesAreContainedIn(o2)) {
                return -1;
            }
            if (!allArtistNamesAreContainedIn(o1) && allArtistNamesAreContainedIn(o2)) {
                return +1;
            }

            // prefer non-remixes
            if (!isRemix(o1) && isRemix(o2)) {
                return -1;
            }
            if (isRemix(o1) && !isRemix(o2)) {
                return +1;
            }

            // prefer non-live
            if (!isLive(o1) && isLive(o2)) {
                return -1;
            }
            if (isLive(o1) && !isLive(o2)) {
                return +1;
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
