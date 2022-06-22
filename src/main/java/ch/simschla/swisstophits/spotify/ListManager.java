package ch.simschla.swisstophits.spotify;

import com.neovisionaries.i18n.CountryCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ListManager {

    public static final String TARGET_LIST_NAME_PREFIX = "Top Hits Schweiz";

    public static final Pattern TARGET_LIST_NAME_PATTERN = Pattern.compile("^\\Q" + TARGET_LIST_NAME_PREFIX + "\\E (197\\d|198\\d|199\\d|20\\d\\d)$");

    public static final String TARGET_LIST_DESCRIPTION_PREFIX = "Die grössten Hits aus dem Jahr";
    public static final String TARGET_LIST_DESCRIPTION_SUFFIX = "(Rohdaten von hitparade.ch)";
    @NonNull
    private final SpotifyApi spotifyApi;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final List<Playlist> playlists = fetchAllExistingLists();

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final User currentUser = fetchCurrentUser();

    public ListManager(@NonNull SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    public Optional<Playlist> fetchPlaylist(@NonNull Integer year) {
        Pattern patternForYear = patternForYear(year);
        return getPlaylists().stream().filter(playlists -> patternForYear.matcher(playlists.getName()).matches()).findFirst();
    }

    public Playlist createPlaylist(@NonNull Integer year) {
        final String targetListName = nameForYear(year);
        if (fetchPlaylist(year).isPresent()) {
            throw new SpotifyException(year + ": " + targetListName + " already exists!");
        }

        try {
            Playlist playlist = this.spotifyApi.createPlaylist(getCurrentUser().getId(), targetListName)
                    .description(descriptionForYear(year))
                    .collaborative(false)
                    .public_(true)
                    .build()
                    .execute();
            getPlaylists().add(playlist); // remember for next time
            return playlist;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyException(e);
        }

    }

    private List<Playlist> fetchAllExistingLists() {
        try {
            final int fetchSize = 50;
            List<Playlist> playlists = new ArrayList<>(fetchSize);
            Paging<PlaylistSimplified> lastResult;
            do {
                lastResult = this.spotifyApi.getListOfCurrentUsersPlaylists().limit(50).build().execute();
                Arrays.stream(lastResult.getItems())
                        .map(this::fetchPlaylist)
                        .forEachOrdered(playlists::add);
            } while (lastResult.getNext() != null);
            return playlists;
        } catch (IOException | ParseException | SpotifyWebApiException e) {
            throw new SpotifyException(e);
        }
    }

    private Playlist fetchPlaylist(@NonNull PlaylistSimplified simplified) {
        try {
            return this.spotifyApi.getPlaylist(simplified.getId())
                    .market(CountryCode.CH)
                    .build()
                    .execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyException(e);
        }
    }

    private User fetchCurrentUser() {
        try {
            return this.spotifyApi.getCurrentUsersProfile().build().execute();
        } catch (SpotifyWebApiException | IOException | ParseException e) {
            throw new SpotifyException(e);
        }
    }


    private boolean isMatchingNamePattern(PlaylistSimplified playlist) {
        return TARGET_LIST_NAME_PATTERN.matcher(playlist.getName()).matches();
    }


    private static Pattern patternForYear(@NonNull Integer year) {
        return Pattern.compile("^\\Q" + TARGET_LIST_NAME_PREFIX + "\\E " + year + "$");
    }

    private static String nameForYear(@NonNull Integer year) {
        return TARGET_LIST_NAME_PREFIX + " " + year;
    }

    private static String descriptionForYear(@NonNull Integer year) {
        return TARGET_LIST_DESCRIPTION_PREFIX + " " + year + ". " + TARGET_LIST_DESCRIPTION_SUFFIX;
    }
}
