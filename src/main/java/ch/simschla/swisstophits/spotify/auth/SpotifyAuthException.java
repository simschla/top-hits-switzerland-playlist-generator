package ch.simschla.swisstophits.spotify.auth;

public class SpotifyAuthException extends RuntimeException {
    public SpotifyAuthException(String message) {
        super(message);
    }

    public SpotifyAuthException(Throwable cause) {
        super(cause);
    }
}
