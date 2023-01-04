package ch.simschla.swisstophits.spotify;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import se.michaelthelin.spotify.model_objects.specification.Track;

@ToString
@EqualsAndHashCode
public class SongSearchResult {

    private final Map<SongMatchPriority, List<Track>> searchResults = new TreeMap<>();

    void add(SongMatchPriority priority, List<Track> tracks) {
        searchResults.put(priority, tracks);
    }

    boolean isEmpty() {
        return searchResults.isEmpty() || searchResults.values().stream().allMatch(List::isEmpty);
    }

    String getTrackCountPerPriorityString() {
        Map<SongMatchPriority, Integer> trackCountPerPriority = new LinkedHashMap<>();
        for (SongMatchPriority priority : searchResults.keySet()) {
            trackCountPerPriority.put(priority, searchResults.get(priority).size());
        }
        return trackCountPerPriority.toString();
    }

    public Stream<List<Track>> resultStream(){
        return searchResults.values().stream();
    }

    enum SongMatchPriority {
        EXACT_MATCH,
        MATCH_WITHOUT_ARTIST_TAGS,
        MATCH_WITHOUT_YEAR_TAG,
        MATCH_WITHOUT_YEAR_AND_ARTIST_TAGS,
        MATCH_WITHOUT_TRACK_AND_ARTIST_TAGS,
        MATCH_WITHOUT_TAGS
    }
}
