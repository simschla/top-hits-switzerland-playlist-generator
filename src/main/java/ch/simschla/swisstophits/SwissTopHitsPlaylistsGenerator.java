package ch.simschla.swisstophits;

import static ch.simschla.swisstophits.spotify.ListManager.nameForYear;

import ch.simschla.swisstophits.mode.TopHitsGeneratorMode;
import ch.simschla.swisstophits.model.ChartInfo;
import ch.simschla.swisstophits.normalizer.SongInfoNormalizer;
import ch.simschla.swisstophits.scraper.ChartSongsScraper;
import ch.simschla.swisstophits.spotify.ListManager;
import ch.simschla.swisstophits.spotify.SongManager;
import ch.simschla.swisstophits.spotify.auth.SpotifyAuth;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Playlist;

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
        // 1994/2003/2004
        String specificYears = System.getProperty("years");
        String rangeValueLowerBound = System.getProperty("fromYear", "1968");
        String rangeValueUpperBound =
                System.getProperty("toYear", String.valueOf(LocalDate.now().getYear()));

        IntStream yearsStream;
        if (specificYears != null) {
            yearsStream = IntStream.of(parseYears(specificYears));
        } else {
            yearsStream =
                    IntStream.range(Integer.parseInt(rangeValueLowerBound), Integer.parseInt(rangeValueUpperBound));
        }
        int[] years = yearsStream.toArray();
        LOGGER.info("Fetching / Creating charts for {}", Arrays.toString(years));
        for (int year : years) {
            generate(year);
        }
    }

    private int[] parseYears(String specificYears) {
        return Arrays.stream(specificYears.split(","))
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .mapToInt(Integer::parseInt)
                .toArray();
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
        Playlist playlist;
        if (TopHitsGeneratorMode.INSTANCE.isDryRunEnabled()) {
            LOGGER.info("DRY-RUN. Not fetching playlist {}", year);
            playlist = new Playlist.Builder()
                    .setId(UUID.randomUUID().toString())
                    .setName(nameForYear(year))
                    .build();
        } else {
            ListManager listManager = new ListManager(spotifyApi);
            playlist = listManager.fetchPlaylist(year).orElseGet(() -> listManager.createPlaylist(year));
        }

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
