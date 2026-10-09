package se233.vectorizer.exception;

/**
 * Reports that an external command-line dependency cannot be started.
 */
public class CliToolNotFoundException extends RuntimeException {
    public CliToolNotFoundException(String message) {
        super(message);
    }

    public CliToolNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
