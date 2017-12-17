package de.diesner.ehzlogger.source;

public class SMLSourceException extends Exception {

    public SMLSourceException() {
    }

    public SMLSourceException(String message) {
        super(message);
    }

    public SMLSourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public SMLSourceException(Throwable cause) {
        super(cause);
    }

    public SMLSourceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
