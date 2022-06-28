package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.model.SongInfo;
import com.neovisionaries.i18n.CountryCode;
import lombok.NonNull;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
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
        final String searchString = searchString(songInfo);
        LOGGER.info("({}) Searching with '{}' for {}", songInfo.getPosition(), searchString, songInfo.toShortDesc());
        List<Track> tracks = search(searchString);
        if (tracks.isEmpty()) {
            final String searchStringReduced = searchStringReduced(songInfo);
            LOGGER.info("({}) Nothing found -- Searching with '{}' for {}", songInfo.getPosition(), searchStringReduced, songInfo.toShortDesc());
            tracks = search(searchStringReduced);
        }
        return tracks;
    }

    private List<Track> search(@NonNull String searchString) {
        try {
            Paging<Track> trackPaging = this.spotifyApi.searchTracks(searchString)
                    .market(CountryCode.CH)
                    .build()
                    .execute();
            return new ArrayList<>(Arrays.asList(trackPaging.getItems()));
        } catch (NotFoundException e) {
            return Collections.emptyList();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyException(e);
        }
    }

    private static String searchString(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                Stream.of("track:" + songInfo.getSong()),
                replaceSpecialArtists(songInfo).map(artist -> "artist:" + artist),
                Stream.of("year:" + (songInfo.getChartYear() - 1) + "-" + (songInfo.getChartYear()))
        ));
    }

    private static String searchStringReduced(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                Stream.of("track:" + songInfo.getSong()),
                replaceSpecialArtists(songInfo).limit(2).map(artist -> "artist:" + artist)
        ));
    }

    private static String searchString(Stream<Stream<String>> searchStreams) {
        return Normalizer.normalize(
                        searchStreams
                                .flatMap(s -> s)
                                .map(String::trim)
                                .map(s -> "".equals(s) ? null : s)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" ")),
                        Normalizer.Form.NFKD)
                .replaceAll("[^\\p{ASCII}]", "");
    }

    private static Stream<String> replaceSpecialArtists(@NonNull SongInfo songInfo) {
        return songInfo.getArtists()
                .stream()
                .map(artist -> {
                    if (songInfo.getChartYear() <= 1994 && artist.equalsIgnoreCase("the symbol")) {
                        return "Prince";
                    }
                    return artist;
                });
    }
}
