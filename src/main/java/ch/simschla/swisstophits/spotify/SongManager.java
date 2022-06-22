package ch.simschla.swisstophits.spotify;

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
import se.michaelthelin.spotify.model_objects.special.SnapshotResult;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SongManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SongManager.class);

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
            LOGGER.info("Current tracks for {}: {}", chartInfo.getChartYear(), allCurrentTracks);

            if (allCurrentTracks.size() == chartInfo.getChartSongs().size() && !forceRecreate) {
                LOGGER.info("Playlist up to date, skipping.");
                return;
            }

            // delete before recreating
            if (!allCurrentTracks.isEmpty()) {
                LOGGER.info("Deleting current tracks: {}", allCurrentTracks);
            }
            deleteCurrentTracks(allCurrentTracks);

            // search
            List<Track> foundTracks = new ArrayList<>(chartInfo.getChartSongs().size());
            searchChartSongs(chartInfo, foundTracks);

            // set to playlist
            if (foundTracks.isEmpty()) {
                LOGGER.error("Could not find any tracks for chart year {}", chartInfo.getChartYear());

            }
            setToPlaylist(foundTracks);

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
                continue;
            }
            final Track track = tracks.get(0);
            LOGGER.info("Using {} for {}.", track, chartSong);
            foundTracks.add(track);
        }
    }

    private List<PlaylistTrack> fetchAllTracks() throws IOException, ParseException, SpotifyWebApiException {
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
    }

    private void deleteCurrentTracks(List<PlaylistTrack> tracks) throws IOException, ParseException, SpotifyWebApiException {
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
