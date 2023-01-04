package ch.simschla.swisstophits;

public class TopHitsGeneratorException extends RuntimeException {

    public TopHitsGeneratorException(String message) {
        super(message);
    }

    public TopHitsGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }

    public TopHitsGeneratorException(Throwable cause) {
        super(cause);
    }

    public TopHitsGeneratorException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
