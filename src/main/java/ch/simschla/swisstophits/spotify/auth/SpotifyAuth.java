package ch.simschla.swisstophits.spotify.auth;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.AuthorizationScope;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import spark.Spark;

public class SpotifyAuth {

    private final SpotifyAuthPersist authPersist;

    public SpotifyAuth() {
        this.authPersist = SpotifyAuthPersist.open();
    }

    public void authorized(SpotifyApi spotifyApi) {
        if (authPersist.hasValidAccessToken()) {
            spotifyApi.setAccessToken(authPersist.accessToken());
            spotifyApi.setRefreshToken(authPersist.refreshToken());
            return;
        }
        // accessToken is invalid
        if (authPersist.hasValidRefreshToken()) {
            spotifyApi.setRefreshToken(authPersist.refreshToken());
            refreshAccessToken(spotifyApi);
            spotifyApi.setAccessToken(authPersist.accessToken());
            spotifyApi.setRefreshToken(authPersist.refreshToken()); // might have changed according to documentation
            return;
        }
        // no refresh token
        requestAccess(spotifyApi);
        spotifyApi.setAccessToken(authPersist.accessToken());
        spotifyApi.setRefreshToken(authPersist.refreshToken());
    }

    private void refreshAccessToken(@NonNull SpotifyApi spotifyApi) {
        try {
            AuthorizationCodePKCERefreshRequest pkceRefreshRequest =
                    spotifyApi.authorizationCodePKCERefresh().build();
            final AuthorizationCodeCredentials authorizationCodeCredentials = pkceRefreshRequest.execute();
            rememberCredentials(authorizationCodeCredentials);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyAuthException(e);
        }
    }

    private void rememberCredentials(@NonNull AuthorizationCodeCredentials authorizationCodeCredentials) {
        authPersist.setAccessToken(authorizationCodeCredentials.getAccessToken());
        authPersist.setAccessTokenValidTo(LocalDateTime.now().plusSeconds(authorizationCodeCredentials.getExpiresIn()));
        authPersist.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
    }

    private void requestAccess(@NonNull SpotifyApi spotifyApi) {
        try {
            final String codeVerifier =
                    UUID.randomUUID().toString() + UUID.randomUUID().toString();

            String codeChallenge = createCodeChallenge(codeVerifier);

            final String code = requestCodeAuthorizedByUser(spotifyApi, codeChallenge);

            final AuthorizationCodePKCERequest authorizationCodePKCERequest =
                    spotifyApi.authorizationCodePKCE(code, codeVerifier).build();

            AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodePKCERequest.execute();
            rememberCredentials(authorizationCodeCredentials);
        } catch (NoSuchAlgorithmException
                | IOException
                | InterruptedException
                | SpotifyWebApiException
                | ParseException e) {
            throw new SpotifyAuthException(e);
        }
    }

    private String requestCodeAuthorizedByUser(SpotifyApi spotifyApi, String codeChallenge)
            throws IOException, InterruptedException {
        AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi
                .authorizationCodePKCEUri(codeChallenge)
                .scope(AuthorizationScope.PLAYLIST_MODIFY_PUBLIC)
                .build();

        URI uri = authorizationCodeUriRequest.execute();

        final AtomicReference<String> codeRef = new AtomicReference<>();
        Spark.get("spotify-auth-redir", (req, resp) -> {
            codeRef.set(req.queryParams("code"));
            resp.status(200);
            return "";
        });
        Desktop.getDesktop().browse(uri);
        while (codeRef.get() == null) {
            Thread.sleep(100);
        }
        Spark.stop();
        final String code = codeRef.get();
        return code;
    }

    private String createCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));

        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(encodedhash);
        return codeChallenge;
    }

    public static void main(String[] args)
            throws URISyntaxException, NoSuchAlgorithmException, IOException, InterruptedException {

        final SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(System.getProperty("spotify.client_id"))
                .setClientSecret(System.getProperty("spotify.client_secret"))
                .setRedirectUri(new URI("http://localhost:4567/spotify-auth-redir"))
                .build();

        SpotifyAuth auth = new SpotifyAuth();
        auth.authorized(spotifyApi);

        System.out.println("accessToken: " + auth.authPersist.accessToken());
    }
}
