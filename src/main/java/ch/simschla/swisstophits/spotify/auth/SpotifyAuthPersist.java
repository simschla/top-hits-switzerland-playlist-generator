package ch.simschla.swisstophits.spotify.auth;

import lombok.experimental.FieldNameConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;

@FieldNameConstants
public class SpotifyAuthPersist {

    private String accessToken;

    private LocalDateTime accessTokenValidTo;

    private String refreshToken;

    private SpotifyAuthPersist() {
    }

    public String accessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        final String before = this.accessToken;
        this.accessToken = accessToken;
        if (!Objects.equals(before, accessToken)) {
            save();
        }
    }

    public boolean hasValidAccessToken() {
        return this.accessToken != null && this.accessTokenValidTo != null && this.accessTokenValidTo.isAfter(LocalDateTime.now());
    }

    public LocalDateTime authTokenValidTo() {
        return accessTokenValidTo;
    }

    public void setAccessTokenValidTo(LocalDateTime accessTokenValidTo) {
        final LocalDateTime before = this.accessTokenValidTo;
        this.accessTokenValidTo = accessTokenValidTo;
        if (!Objects.equals(before, accessTokenValidTo)) {
            save();
        }
    }

    public boolean hasValidRefreshToken() {
        return this.refreshToken != null;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        final String before = this.refreshToken;
        this.refreshToken = refreshToken;
        if (!Objects.equals(before, refreshToken)) {
            save();
        }
    }


    // ---- persisting

    private synchronized void save() {
        Properties props = new Properties(4);
        saveOrRemove(props, Fields.accessToken, accessToken);
        saveOrRemove(props, Fields.accessTokenValidTo, accessTokenValidTo != null ? accessTokenValidTo.toString() : null);
        saveOrRemove(props, Fields.refreshToken, refreshToken);
        try (OutputStream out = new FileOutputStream("spotify.auth")) {
            props.storeToXML(out, "SpotifyAuthTokens", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistException(e);
        }
    }

    private void saveOrRemove(Properties props, String key, String value) {
        if (value == null) {
            props.remove(key);
        } else {
            props.put(key, value);
        }
    }

    private synchronized void load() {
        if (!new File("spotify.auth").exists()) {
            return; // nothing to load
        }
        try (InputStream in = new FileInputStream("spotify.auth")) {
            Properties props = new Properties(4);
            props.loadFromXML(in);

            this.accessToken = props.getProperty(Fields.accessToken);
            String authTokenValidToString = props.getProperty(Fields.accessTokenValidTo);
            if (authTokenValidToString == null) {
                this.accessTokenValidTo = null;
            } else {
                this.accessTokenValidTo = LocalDateTime.parse(authTokenValidToString);
            }
            this.refreshToken = props.getProperty(Fields.refreshToken);
        } catch (IOException e) {
            throw new PersistException(e);
        }
    }

    // ---- create

    public static SpotifyAuthPersist open() {
        SpotifyAuthPersist spotifyAuthPersist = new SpotifyAuthPersist();
        spotifyAuthPersist.load();
        return spotifyAuthPersist;
    }

}
