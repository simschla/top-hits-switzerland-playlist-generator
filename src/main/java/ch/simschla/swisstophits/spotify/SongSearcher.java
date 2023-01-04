package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.model.SongInfo;
import ch.simschla.swisstophits.spotify.SongSearchResultProvider.SongMatchPriority;
import com.neovisionaries.i18n.CountryCode;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class SongSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongSearcher.class);

    @NonNull
    private final SpotifyApi spotifyApi;

    public SongSearcher(@NonNull SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    public SongSearchResultProvider search(@NonNull SongInfo songInfo) {
        SongSearchResultProvider searchResult = new SongSearchResultProvider();
        // EXACT_MATCH
        final String searchString = searchString(songInfo);
        searchResult.add(SongMatchPriority.EXACT_MATCH, () -> {
            LOGGER.info(
                    "({}) Searching with '{}' for {}", songInfo.getPosition(), searchString, songInfo.toShortDesc());
            return search(searchString);
        });

        // MATCH_WITHOUT_ARTIST_TAGS
        final String searchStringWithoutArtistTags = searchStringWithoutArtistTags(songInfo);
        searchResult.add(SongMatchPriority.MATCH_WITHOUT_ARTIST_TAGS, () -> {
            LOGGER.info(
                    "({}) Searching with '{}' for {}",
                    songInfo.getPosition(),
                    searchStringWithoutArtistTags,
                    songInfo.toShortDesc());
            return search(searchStringWithoutArtistTags);
        });

        // MATCH_WITHOUT_YEAR_TAG
        final String searchStringWithoutYearTag = searchStringWithoutYearTag(songInfo);
        searchResult.add(SongMatchPriority.MATCH_WITHOUT_YEAR_TAG, () -> {
            LOGGER.info(
                    "({}) Searching with '{}' for {}",
                    songInfo.getPosition(),
                    searchStringWithoutYearTag,
                    songInfo.toShortDesc());
            return search(searchStringWithoutYearTag);
        });

        // MATCH_WITHOUT_YEAR_AND_ARTIST_TAGS
        final String searchStringWithoutYearAndArtistTags = searchStringWithoutYearAndArtistTags(songInfo);
        searchResult.add(SongMatchPriority.MATCH_WITHOUT_YEAR_AND_ARTIST_TAGS, () -> {
            LOGGER.info(
                    "({}) Searching with '{}' for {}",
                    songInfo.getPosition(),
                    searchStringWithoutYearAndArtistTags,
                    songInfo.toShortDesc());
            return search(searchStringWithoutYearAndArtistTags);
        });

        // MATCH_WITHOUT_TRACK_AND_ARTIST_TAGS
        final String searchStringWithoutTrackAndArtistTags = searchStringWithoutTrackAndArtistTags(songInfo);
        searchResult.add(SongMatchPriority.MATCH_WITHOUT_TRACK_AND_ARTIST_TAGS, () -> {
            LOGGER.info(
                    "({}) Searching with '{}' for {}",
                    songInfo.getPosition(),
                    searchStringWithoutTrackAndArtistTags,
                    songInfo.toShortDesc());
            return search(searchStringWithoutTrackAndArtistTags);
        });

        // MATCH_WITHOUT_TAGS
        final String searchStringWithoutTags = searchStringWithoutTags(songInfo);
        searchResult.add(SongMatchPriority.MATCH_WITHOUT_TAGS, () -> {
            LOGGER.info(
                    "({}) Searching with '{}' for {}",
                    songInfo.getPosition(),
                    searchStringWithoutTags,
                    songInfo.toShortDesc());
            return search(searchStringWithoutTags);
        });

        return searchResult;
    }

    private List<Track> search(@NonNull String searchString) {
        try {
            Paging<Track> trackPaging = this.spotifyApi
                    .searchTracks(searchString)
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
                Stream.of(String.format("track:\"%s\"", songInfo.getSong())),
                replaceSpecialArtists(songInfo).map(artist -> String.format("artist:\"%s\"", artist)),
                Stream.of("year:" + (songInfo.getChartYear() - 1) + "-" + (songInfo.getChartYear()))));
    }

    private static String searchStringWithoutArtistTags(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                replaceSpecialArtists(songInfo).map(artist -> String.format("\"%s\"", artist)),
                Stream.of(String.format("track:\"%s\"", songInfo.getSong())),
                Stream.of("year:" + (songInfo.getChartYear() - 1) + "-" + (songInfo.getChartYear()))));
    }

    private static String searchStringWithoutTrackAndArtistTags(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                replaceSpecialArtists(songInfo).map(artist -> String.format("\"%s\"", artist)),
                Stream.of(String.format("\"%s\"", songInfo.getSong())),
                Stream.of("year:" + (songInfo.getChartYear() - 1) + "-" + (songInfo.getChartYear()))));
    }

    private static String searchStringWithoutTags(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                replaceSpecialArtists(songInfo).map(artist -> String.format("\"%s\"", artist)),
                Stream.of(String.format("\"%s\"", songInfo.getSong()))));
    }

    private static String searchStringWithoutYearTag(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                Stream.of(String.format("track:\"%s\"", songInfo.getSong())),
                replaceSpecialArtists(songInfo).limit(2).map(artist -> "artist:" + artist)));
    }

    private static String searchStringWithoutYearAndArtistTags(@NonNull SongInfo songInfo) {
        return searchString(Stream.of(
                replaceSpecialArtists(songInfo).limit(2).map(artist -> String.format("\"%s\"", artist)),
                Stream.of(String.format("track:\"%s\"", songInfo.getSong()))));
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
        return songInfo.getArtists().stream().map(artist -> {
            if (songInfo.getChartYear() <= 1994 && artist.equalsIgnoreCase("the symbol")) {
                return "Prince";
            }
            return artist;
        });
    }
}
