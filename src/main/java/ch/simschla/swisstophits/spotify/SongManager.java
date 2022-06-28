package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.mode.TopHitsGeneratorMode;
import ch.simschla.swisstophits.model.ChartInfo;
import ch.simschla.swisstophits.model.SongInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.NonNull;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.special.SnapshotResult;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

public class SongManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongManager.class);

    private static final SongMatchingResultPrinter MATCHING_RESULT_PRINTER = new SongMatchingResultPrinter();

    private final SpotifyApi spotifyApi;
    private final Playlist playlist;

    public SongManager(@NonNull SpotifyApi spotifyApi, @NonNull Playlist playlist) {
        this.spotifyApi = spotifyApi;
        this.playlist = playlist;
    }


    public void setTrackList(ChartInfo chartInfo, boolean forceRecreate) {
        try {
            // current state
            List<PlaylistTrack> allCurrentTracks = fetchAllTracks();
            LOGGER.debug("Current tracks for {}: {}", chartInfo.getChartYear(), allCurrentTracks);

            if (allCurrentTracks.size() == chartInfo.getChartSongs().size() && !forceRecreate) {
                LOGGER.info("Playlist up to date, skipping.");
                return;
            }

            // search
            LOGGER.info("Searching {} songs for year {}.", chartInfo.getChartSongs().size(), chartInfo.getChartYear());
            List<Track> foundTracks = new ArrayList<>(chartInfo.getChartSongs().size());
            searchChartSongs(chartInfo, foundTracks);

            // set to playlist
            if (foundTracks.isEmpty()) {
                LOGGER.error("Could not find any tracks for chart year {}", chartInfo.getChartYear());
                return;
            }
            printMatchResult(chartInfo, foundTracks);

            // delete before recreating
            if (!allCurrentTracks.isEmpty()) {
                deleteCurrentTracks(allCurrentTracks);
            }

            List<Track> tracksToSave = foundTracks.stream().filter(Objects::nonNull).toList();
            setToPlaylist(tracksToSave);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyException(e);
        }
    }

    private void searchChartSongs(ChartInfo chartInfo, List<Track> foundTracks) {
        SongSearcher searcher = new SongSearcher(this.spotifyApi);
        for (SongInfo chartSong : chartInfo.getChartSongs()) {
            LOGGER.debug("Searching for song: {}", chartSong);

            List<Track> tracks = searcher.search(chartSong);
            if (tracks.isEmpty()) {
                LOGGER.warn("Could not find any track matching {} -- skipping.", chartSong);
                foundTracks.add(null);
                continue;
            }

            final Optional<Track> track = selectTrack(chartSong, tracks);
            if (track.isEmpty()) {
                LOGGER.warn("Could not select matching tracking for {}. Available: {}", chartSong, tracks);
                foundTracks.add(null);
                continue;
            }
            LOGGER.debug("Using {} for {}.", track.get(), chartSong);
            LOGGER.debug("First 5: {}", tracks.subList(0, Math.min(5, tracks.size())).stream().map(t -> "\n- " + t).collect(Collectors.joining("\n")));
            foundTracks.add(track.get());
        }
    }

    private void printMatchResult(ChartInfo chartInfo, List<Track> tracks) {
        String table = MATCHING_RESULT_PRINTER.printMatchTable(chartInfo, tracks);
        LOGGER.info("Match Results for year {}:\n{}", chartInfo.getChartYear(), table);
        writeToFile(chartInfo, table);
    }

    private void writeToFile(ChartInfo chartInfo, String table) {
        String print = "# Spotify matches for charts *" + chartInfo.getChartYear() + "*\n\n" + table;
        Path path = Paths.get("matching-results", "spotify", chartInfo.getChartYear() + ".md");
        path.toFile().getParentFile().mkdirs();
        try {
            Files.writeString(path, print, CREATE, WRITE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new SpotifyException(e);
        }
    }

    @NonNull
    private Optional<Track> selectTrack(SongInfo chartSong, List<Track> tracks) {
        SongMatcher songMatcher = new SongMatcher(chartSong);
        return songMatcher.selectBestMatchingTrack(tracks);
    }

    private List<PlaylistTrack> fetchAllTracks() throws IOException, ParseException, SpotifyWebApiException {
        if (TopHitsGeneratorMode.INSTANCE.isDryRunEnabled()) {
            LOGGER.info("DRY-RUN. Not fetching current state from playlist {}", playlist.getName());
            return Collections.emptyList();
        }
        try {
            final int fetchSize = 50;
            int offset = 0;
            final List<PlaylistTrack> tracks = new ArrayList<>(fetchSize);
            Paging<PlaylistTrack> cur;
            do {
                cur = spotifyApi.getPlaylistsItems(playlist.getId())
                        .limit(50)
                        .offset(offset)
                        .build()
                        .execute();
                tracks.addAll(Arrays.asList(cur.getItems()));
                offset += fetchSize;
            } while (cur.getNext() != null);
            return tracks;
        } catch (NotFoundException e) {
            // can happen if playlist has just been created, so it is probably empty anyway
            LOGGER.info("Could not find tracks for {}. Ignoring since that means it is empty anyway!", this.playlist);
            return Collections.emptyList();
        }
    }

    private void deleteCurrentTracks(List<PlaylistTrack> tracks) throws IOException, ParseException, SpotifyWebApiException {
        if (TopHitsGeneratorMode.INSTANCE.isDryRunEnabled()) {
            LOGGER.info("DRY-RUN. Not deleting from playlist {}", playlist.getName());
            return;
        }
        LOGGER.info("Deleting {} current tracks for recreation of playlist {}.", tracks.size(), playlist.getName());
        inChunks(tracks, 100, curList -> {
            JsonArray tracksArray = new JsonArray(curList.size());
            curList.stream()
                    .map(track -> {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("uri", track.getTrack().getUri());
                        return jsonObject;
                    })
                    .forEach(tracksArray::add);
            SnapshotResult result = this.spotifyApi.removeItemsFromPlaylist(playlist.getId(), tracksArray)
                    .build()
                    .execute();
        });
    }

    private void setToPlaylist(List<Track> tracks) throws IOException, ParseException, SpotifyWebApiException {
        if (TopHitsGeneratorMode.INSTANCE.isDryRunEnabled()) {
            LOGGER.info("DRY-RUN. Not saving to playlist {}", playlist.getName());
            return;
        }
        LOGGER.info("Saving {} tracks to playlist {}", tracks.size(), playlist.getName());
        AtomicInteger offset = new AtomicInteger(0);
        JsonArray jsonArray = new JsonArray(tracks.size());
        for (Track track : tracks) {
            jsonArray.add(track.getUri());
        }

        this.spotifyApi.addItemsToPlaylist(playlist.getId(), jsonArray)
                .build()
                .execute();
    }


    private static <T> void inChunks(List<T> elements, Integer chunkSize, ChunkConsumer<List<T>> chunkConsumer) throws IOException, ParseException, SpotifyWebApiException {
        if (elements.isEmpty()) {
            return;
        }
        List<T> current = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            T t = elements.get(i);
            current.add(t);
            if (current.size() == chunkSize) {
                chunkConsumer.accept(current);
                current.clear();
            }
        }
        // maybe one last time
        if (!current.isEmpty()) {
            chunkConsumer.accept(current);
        }
    }

    @FunctionalInterface
    private interface ChunkConsumer<T extends List<?>> {
        void accept(T chunk) throws IOException, ParseException, SpotifyWebApiException;
    }
}
