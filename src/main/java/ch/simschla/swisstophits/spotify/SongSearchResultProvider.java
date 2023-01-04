package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.lang.MemoizingSupplier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.builder.ToStringBuilder;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class SongSearchResultProvider {

    private final Map<SongMatchPriority, MemoizingSupplier<List<Track>>> searchResultSuppliers = new TreeMap<>();

    void add(SongMatchPriority priority, Supplier<List<Track>> tracksSupplier) {
        // wrap tracksSupplier with memoization and add to searchResultSuppliers

        searchResultSuppliers.put(priority, new MemoizingSupplier<>(tracksSupplier));
    }

    public Stream<List<Track>> resultStream() {
        return searchResultSuppliers.values().stream().map(Supplier::get);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        searchResultSuppliers.entrySet().stream()
                .filter(entry -> entry.getValue().isResolved())
                .forEach(entry -> builder.append(
                        String.valueOf(entry.getKey()), entry.getValue().get()));
        return builder.build();
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
