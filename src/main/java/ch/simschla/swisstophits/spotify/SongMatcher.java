package ch.simschla.swisstophits.spotify;

import static java.util.function.Predicate.not;

import ch.simschla.swisstophits.model.SongInfo;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class SongMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongMatcher.class);

    @NonNull
    private final SongInfo songToLookFor;

    public SongMatcher(@NonNull SongInfo songToLookFor) {
        this.songToLookFor = songToLookFor;
    }

    public Optional<Track> selectBestMatchingTrack(List<Track> tracks) {

        RatingCalculator ratingCalculator = new RatingCalculator(tracks);
        List<SongRating> ratings = ratingCalculator.sortedRatings(22d);
        if (ratings.isEmpty()) {
            LOGGER.info(
                    "--> no match in limit. Max 5: {}",
                    ratingCalculator.sortedRatings(0d).stream()
                            .limit(5)
                            .map(Object::toString)
                            .collect(Collectors.joining("\n\n")));
            return Optional.empty();
        }
        LOGGER.info("Rating of {} for {}.", ratings.get(0), songToLookFor.toShortDesc());
        if (songToLookFor.getSong().equals("Jenny From The Block")) {
            LOGGER.info(
                    "--> First 10 were: {}",
                    ratingCalculator.sortedRatings(0d).stream()
                            .limit(10)
                            .map(Object::toString)
                            .collect(Collectors.joining("\n\n")));
        }
        LOGGER.debug(
                "--> First 5 were: {}",
                ratingCalculator.sortedRatings(0d).stream()
                        .limit(5)
                        .map(Object::toString)
                        .collect(Collectors.joining("\n\n")));
        return Optional.of(ratings.get(0).getTrack());
    }

    private boolean isBlocklisted(Track track) {
        return matchesKaraoke(track) || isLive(track) || isInstrumental(track) /*|| isRemix(track)*/;
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
                || Arrays.stream(t.getArtists())
                        .map(artist -> simplified(artist.getName()))
                        .anyMatch(a -> a.contains("karaoke"));
    }

    private boolean isInstrumental(Track t) {
        if (simplified(songToLookFor.getSong()).contains("instrumental")) {
            return false;
        }
        return simplified(t.getName()).contains("instrumental")
                || simplified(t.getAlbum().getName()).contains("instrumental");
    }

    private boolean isRemix(Track track) {
        return (trackNameContainsButNotSongToLookFor(track, "mix")
                        && !trackNameContainsButNotSongToLookFor(track, "radio mix"))
                || trackNameContainsButNotSongToLookFor(track, "remix")
                || trackNameContainsButNotSongToLookFor(track, "megamix")
                || trackNameContainsButNotSongToLookFor(track, "reloaded")
                || trackNameContainsButNotSongToLookFor(track, "dub")
                || trackNameContainsButNotSongToLookFor(track, "new version");
    }

    private boolean isRadioVersion(Track track) {
        return !isRemix(track)
                && (trackNameContainsButNotSongToLookFor(track, "radio edit")
                        || trackNameContainsButNotSongToLookFor(track, "radio version")
                        || trackNameContainsButNotSongToLookFor(track, "radio mix"));
    }

    private boolean trackNameContainsButNotSongToLookFor(Track track, String searchString) {
        return containsWord(
                        (track.getName() + " " + track.getAlbum().getName()).toLowerCase(), searchString.toLowerCase())
                && !containsWord(songToLookFor.getSong().toLowerCase(), searchString.toLowerCase());
    }

    private boolean isLive(Track track) {
        return trackNameContainsButNotSongToLookFor(track, "live");
    }

    private boolean songNameIsContainedIn(Track t) {
        String trackSongName = simplified(t.getName());
        String songToLookForName = simplified(songToLookFor.getSong());
        return containsWord(trackSongName, songToLookForName) || containsWord(songToLookForName, trackSongName);
    }

    private boolean containsWord(String s, String word) {
        return s.matches(".*\\b" + word + "\\b.*");
    }

    private boolean songNamePartsAreContainedIn(Track t) {
        Set<String> trackSongParts = Arrays.stream(t.getName().split("-"))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .filter(not(this::isIgnoredSongPart))
                .map(this::simplified)
                .collect(Collectors.toSet());
        Set<String> songToLookForParts = Arrays.stream(songToLookFor.getSong().split("-"))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .filter(not(this::isIgnoredSongPart))
                .map(this::simplified)
                .collect(Collectors.toSet());
        if (songToLookForParts.stream().anyMatch(songToLookForPart -> trackSongParts.stream()
                .anyMatch(trackSongPart -> containsWord(trackSongPart, songToLookForPart)
                        || containsWord(songToLookForPart, trackSongPart)))) {
            return true;
        }
        return false;
    }

    private boolean isIgnoredSongPart(String part) {
        return part.matches("(?i)(radio version|radio edit|\\(.* Theme\\)|\\(.* Version\\))");
    }

    private boolean allArtistNamesAreContainedIn(Track t) {
        final String trackArtists = trackArtists(t) // remove fill-words
                .collect(Collectors.joining(" "));

        return songToLookForArtists().allMatch(trackArtists::contains);
    }

    private Stream<String> songToLookForArtists() {
        return songToLookFor.getArtists().stream()
                .map(this::replaceExceptionalArtistCases)
                .flatMap(artistName -> Arrays.stream(artistName.split("\\s+")))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord)) // remove fill-words
                .map(this::simplified);
    }

    private Stream<String> trackArtists(Track t) {
        return Arrays.stream(t.getArtists())
                .map(s -> simplified(s.getName()))
                .flatMap(s -> Arrays.stream(s.split("\\s+")))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord));
    }

    private boolean noOtherArtistNamesAreContainedIn(Track t) {
        String songArtists = songToLookForArtists().collect(Collectors.joining(" "));
        return trackArtists(t).noneMatch(not(songArtists::contains));
    }

    private boolean anyArtistNameIsContainedIn(Track t) {
        final Set<String> trackArtists = Arrays.stream(t.getArtists())
                .map(s -> simplified(s.getName()))
                .filter(s -> s.length() > 1) // remove one-char things
                .filter(not(this::isFillWord)) // remove fill-words
                .collect(Collectors.toSet());

        return songToLookFor.getArtists().stream()
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
                .map(String::trim)
                .filter(not(String::isEmpty))
                .collect(Collectors.joining(" "));
    }

    private int releaseDelta(String releaseDate) {
        try {
            return Integer.parseInt(releaseDate.substring(0, 4)) - songToLookFor.getChartYear();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private class RatingCalculator {

        private final Map<Track, SongRating> ratings = new HashMap<>();
        private final List<Track> allTracks;

        public RatingCalculator(List<Track> allTracks) {
            this.allTracks = allTracks;
            rateAllTracks();
        }

        private void rateAllTracks() {
            for (Track track : allTracks) {
                rate(track);
            }
            Collection<SongRating> allRatings = this.ratings.values();
            allRatings.forEach(rating -> rating.calculateScore(allRatings));
        }

        private void rate(Track track) {
            SongRating rating = new SongRating(track);

            rating.setBlocked(isBlocklisted(track));
            if (rating.isBlocked()) {
                ratings.put(track, rating);
                return; // nothing else to do
            }

            rating.setSongNameScore(calculateSongNameRating(track));

            if (songToLookFor.getArtists().size() == track.getArtists().length) {
                rating.setArtistCountScore(2d);
            }
            if (allArtistNamesAreContainedIn(track) && noOtherArtistNamesAreContainedIn(track)) {
                rating.setArtistNamesScore(10d);
            } else if (allArtistNamesAreContainedIn(track)) {
                rating.setArtistNamesScore(7d);
            } else if (anyArtistNameIsContainedIn(track)) {
                rating.setArtistNamesScore(5d);
            } else {
                rating.setArtistNamesScore(-5d);
            }

            rating.setPopularityScore(track.getPopularity()
                    * 5d
                    / allTracks.stream().mapToDouble(Track::getPopularity).max().orElseThrow());

            // rely on upstream ranking

            rating.setRankingScore(5.0d * ((allTracks.size() - allTracks.indexOf(track)) / (1.0d * allTracks.size())));

            // shorter is better (longer tracks tend to be remixes)
            List<Track> sortedByLength = allTracks.stream()
                    .sorted(Comparator.comparing(Track::getDurationMs))
                    .toList();
            rating.setDurationScore(
                    2.0d * ((allTracks.size() - sortedByLength.indexOf(track)) / (1.0d * allTracks.size())));

            // TODO: maybe we could use trackNumber on album as a ranking part?

            if (isLive(track)) {
                rating.setLiveScore(-2.0d);
            }
            if (isRemix(track)) {
                rating.setRemixScore(-2.0d);
            }

            if (isRadioVersion(track)) {
                rating.setRadioVersionScore(2.0d);
            }

            // below zero here means better, but to far away from chart year is probably remix or re-recorded or
            // birthday version
            int deltaYears = releaseDelta(track.getAlbum().getReleaseDate());
            if (deltaYears == 0 || deltaYears == -1) {
                rating.setReleaseDateScore(2d);
            } else if (deltaYears > -4 && deltaYears < -1) {
                rating.setReleaseDateScore(1d);
            } else if (deltaYears > 1 || Math.abs(deltaYears) > 10) {
                rating.setReleaseDateScore(-1d);
            }

            // usually singles are in the first 7 tracks, so boost that
            if (track.getTrackNumber() <= 3) {
                rating.setTrackNumberScore(2d);
            } else if (track.getTrackNumber() <= 7) {
                rating.setTrackNumberScore(1d);
            }

            ratings.put(track, rating);
        }

        public List<SongRating> sortedRatings(double minVal) {
            return ratings.values().stream()
                    .sorted(Comparator.comparing(SongRating::getCalculatedScore).reversed())
                    .filter(r -> r.getCalculatedScore() >= minVal)
                    .toList();
        }
    }

    private double calculateSongNameRating(Track track) {
        // check for words, the more "in the front" the words match, the better, sequential matches might be boosted?
        List<String> songToLookForParts = selectTokens(songToLookFor.getSong());
        List<String> trackNameParts = selectTokens(track.getName());

        List<Integer> trackNamePositions =
                trackNameParts.stream().map(s -> songToLookForParts.indexOf(s)).toList();

        final double pointsToGiveInTotal = 10d;

        List<Double> weights = new ArrayList<>(trackNamePositions.size());
        double weight = 1.0d;
        for (int i = 0; i < trackNamePositions.size(); i++) {
            weights.add(0, weight);
            weight *= 1.5d;
        }

        Double pointPerWeight =
                pointsToGiveInTotal / weights.stream().mapToDouble(d -> d).sum();

        double achieved = 0d;

        double boost = 1.0d;

        for (int i = 0; i < trackNamePositions.size(); i++) {
            if (trackNamePositions.get(i) >= 0) {
                achieved += (boost * weights.get(i) * pointPerWeight);
                boost += 0.1d;
            } else {
                boost = 1.0d; // reset
            }
        }

        if (achieved > 0) {
            return achieved;
        }

        return -5d;
    }

    private List<String> selectTokens(String origString) {
        return Arrays.stream(origString.split("\\b"))
                .filter(Objects::nonNull)
                .map(this::simplified)
                .map(String::trim)
                .filter(not(String::isEmpty))
                .distinct()
                .toList();
    }
}
