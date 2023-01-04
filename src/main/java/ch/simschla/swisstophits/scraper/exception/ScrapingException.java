package ch.simschla.swisstophits.scraper.exception;

import lombok.NonNull;

public class ScrapingException extends RuntimeException {

    public ScrapingException() {}

    public ScrapingException(String message) {
        super(message);
    }

    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScrapingException(Throwable cause) {
        super(cause);
    }

    public ScrapingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static ScrapingException wrap(@NonNull Exception parent) throws ScrapingException {
        throw new ScrapingException(parent);
    }
}
