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
import se.michaelthelin.spotify.model_objects.special.SnapshotResult;
import se.michaelthelin.spotify.model_objects.specification.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        if (chartInfo.getChartSongs().size() != tracks.size()) {
            throw new IllegalStateException("Match results are not on par. Charts " + chartInfo.getChartSongs().size() + " elements, but tracks " + tracks.size() + " elements.");
        }
        LinkedHashMap<String, String> matchResults = new LinkedHashMap<>();
        int maxLengthSong = 0;
        int maxLengthMatch = 0;
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            SongInfo songInfo = chartInfo.getChartSongs().get(i);
            String songInfoShortDesc = songInfo.toShortDesc();
            String trackShortDesc = describeTrack(track);

            matchResults.put(songInfoShortDesc, trackShortDesc);

            maxLengthMatch = Math.max(maxLengthMatch, trackShortDesc.length());
            maxLengthSong = Math.max(maxLengthSong, songInfoShortDesc.length());
        }


        // print header
        int tableLength = 6 /* pos */ + maxLengthMatch + 2 + 3 /* sep */ + maxLengthSong + 2;

        final StringBuilder table = new StringBuilder();
        table.append(repeat("=", tableLength))
                .append("\n");
        table.append("| ")
                .append(String.format("%3s", "#"))
                .append(" | ")
                .append(String.format("%-" + maxLengthSong + "s", "Charts-Info"))
                .append(" | ")
                .append(String.format("%-" + maxLengthMatch + "s", "Spotify Match"))
                .append(" |\n");
        table.append(repeat("=", tableLength))
                .append("\n");

        int pos = 1;
        for (Map.Entry<String, String> entry : matchResults.entrySet()) {
            table.append("| ")
                    .append(String.format("%3d", pos++))
                    .append(" | ")
                    .append(String.format("%-" + maxLengthSong + "s", entry.getKey()))
                    .append(" | ")
                    .append(String.format("%-" + maxLengthMatch + "s", entry.getValue()))
                    .append(" |\n");
            table.append(repeat("-", tableLength))
                    .append("\n");
        }

        LOGGER.info("Match Results for year {}:\n{}", chartInfo.getChartYear(), table);
    }

    private String repeat(String s, int times) {
        return IntStream.range(0, times).mapToObj(i -> s).collect(Collectors.joining(""));
    }

    private String describeTrack(Track track) {
        if (track == null) {
            return "-";
        }
        return String.format("%s by %s, %s (%s)", track.getName(), Arrays.stream(track.getArtists()).map(ArtistSimplified::getName).collect(Collectors.toList()), track.getAlbum().getName(), track.getAlbum().getReleaseDate());
    }

    @NonNull
    private Optional<Track> selectTrack(SongInfo chartSong, List<Track> tracks) {
        SongMatcher songMatcher = new SongMatcher(chartSong);
        return songMatcher.selectBestMatchingTrack(tracks);
    }

    private boolean matchesKaraoke(SongInfo chartSong, Track t) {
        if (chartSong.getSong().toLowerCase().contains("karaoke")) {
            return false;
        }
        if (chartSong.getArtists().stream().anyMatch(s -> s.toLowerCase().contains("karaoke"))) {
            return false;
        }
        return t.getName().toLowerCase().contains("karaoke") || t.getAlbum().getName().contains("karaoke") || Arrays.stream(t.getArtists()).anyMatch(a -> a.getName().toLowerCase().contains("karaoke"));
    }

    private boolean songNamesMatch(SongInfo chartSong, Track t) {
        boolean hasSameWords = t.getName().toLowerCase().replaceAll("[^a-z0-9]", "").contains(chartSong.getSong().toLowerCase().replaceAll("[^a-z0-9]", ""));
        if (hasSameWords) {
            return true;
        }
        return false;
    }

    private boolean artistNamesMatch(SongInfo chartSong, Track t) {
        String trackArtists = Arrays.stream(t.getArtists()).map(s -> s.getName().toLowerCase().replaceAll("[^a-z0-9]", "")).collect(Collectors.joining(" "));
        for (String artist : chartSong.getArtists()) {
            List<String> chartArtists = Arrays.stream(artist.toLowerCase().split(" ")).map(s -> s.replaceAll("[^a-z0-9]", "")).toList();
            for (String chartArtist : chartArtists) {
                if (!trackArtists.contains(chartArtist)) {
                    return false;
                }

            }
        }
        return true;
    }

    private List<PlaylistTrack> fetchAllTracks() throws IOException, ParseException, SpotifyWebApiException {
        if (TopHitsGeneratorMode.INSTANCE.dryRun()) {
            LOGGER.info("DRY-RUN. Not fetching current state from playlist {}", playlist.getName());
            return Collections.emptyList();
        }
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
        if (TopHitsGeneratorMode.INSTANCE.dryRun()) {
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
        if (TopHitsGeneratorMode.INSTANCE.dryRun()) {
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
