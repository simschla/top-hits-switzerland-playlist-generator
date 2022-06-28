package ch.simschla.swisstophits.spotify;

import ch.simschla.swisstophits.model.ChartInfo;
import ch.simschla.swisstophits.model.SongInfo;
import lombok.NonNull;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SongMatchingResultPrinter {

    public String printMatchTable(@NonNull ChartInfo chartInfo, @NonNull List<Track> tracks) {
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

        return table.toString();
    }

    private String describeTrack(Track track) {
        if (track == null) {
            return "-";
        }
        return String.format("%s %s, %s (%s)", track.getName(), Arrays.stream(track.getArtists()).map(ArtistSimplified::getName).collect(Collectors.toList()), track.getAlbum().getName(), track.getAlbum().getReleaseDate());
    }

    private String repeat(String s, int times) {
        return IntStream.range(0, times).mapToObj(i -> s).collect(Collectors.joining(""));
    }
}
