package ch.simschla.swisstophits;

import ch.simschla.swisstophits.model.ChartInfo;
import ch.simschla.swisstophits.normalizer.SongInfoNormalizer;
import ch.simschla.swisstophits.scraper.ChartSongsScraper;
import ch.simschla.swisstophits.spotify.ListManager;
import ch.simschla.swisstophits.spotify.SongManager;
import ch.simschla.swisstophits.spotify.auth.SpotifyAuth;
import lombok.AccessLevel;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Playlist;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.IntStream;

public class SwissTopHitsPlaylistsGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwissTopHitsPlaylistsGenerator.class);


    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final SpotifyApi spotifyApi = createSpotifyApi();

    private static SpotifyApi createSpotifyApi() {
        try {
            final SpotifyApi spotifyApi = new SpotifyApi.Builder()
                    .setClientId(System.getProperty("spotify.client_id"))
                    .setClientSecret(System.getProperty("spotify.client_secret"))
                    .setRedirectUri(new URI("http://localhost:4567/spotify-auth-redir"))
                    .build();
            SpotifyAuth auth = new SpotifyAuth();
            auth.authorized(spotifyApi);
            return spotifyApi;
        } catch (URISyntaxException e) {
            throw new TopHitsGeneratorException(e);
        }
    }

    private void generate() {
        // 2003/2004
        IntStream.of(1994)//, 2003)
//        IntStream.range(1968, LocalDate.now().getYear() - 1)
                .forEach(this::generate);
    }

    private void generate(int year) {
        LOGGER.info("Handling year: {}", year);
        // scrape
        LOGGER.info("{} - scraping", year);
        ChartSongsScraper scraper = new ChartSongsScraper(year);
        ChartInfo info = scraper.fetchChartInfo();

        // normalize
        info = new SongInfoNormalizer().normalize(info);

        // search + create
        SpotifyApi spotifyApi = getSpotifyApi();

        // assert list
        LOGGER.info("{} - asserting playlist exists", year);
        ListManager listManager = new ListManager(spotifyApi);
        Playlist playlist = listManager.fetchPlaylist(year)
                .orElseGet(() -> listManager.createPlaylist(year));

        // add songs
        LOGGER.info("{} - searching songs and updating playlist if needed", year);
        SongManager songManager = new SongManager(spotifyApi, playlist);
        songManager.setTrackList(info, true);
    }

    public static void main(String[] args) {
        SwissTopHitsPlaylistsGenerator generator = new SwissTopHitsPlaylistsGenerator();
        generator.generate();
    }

}
