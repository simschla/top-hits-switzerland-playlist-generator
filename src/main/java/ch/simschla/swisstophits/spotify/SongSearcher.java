package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.model.SongInfo;
import com.neovisionaries.i18n.CountryCode;
import lombok.NonNull;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SongSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongSearcher.class);

    @NonNull
    private final SpotifyApi spotifyApi;

    public SongSearcher(@NonNull SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }


    public List<Track> search(@NonNull SongInfo songInfo) {
        Paging<Track> trackPaging = null;
        try {
            final String searchString = searchString(songInfo);
            LOGGER.info("Searching with '{}' for {}", searchString, songInfo);
            trackPaging = this.spotifyApi.searchTracks(searchString)
                    .market(CountryCode.CH)
                    .build()
                    .execute();
            return new ArrayList<>(Arrays.asList(trackPaging.getItems()));
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyException(e);
        }
    }

    private static String searchString(@NonNull SongInfo songInfo) {
        return Stream.of(
                        Stream.of(songInfo.getSong()),
                        songInfo.getArtists().stream()/*,
                        Stream.of(String.valueOf(songInfo.getChartYear()))*/)
                .flatMap(s -> s)
//                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));
    }
}
