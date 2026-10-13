package se233.vectorizer.exception;

/**
 * Reports that an external command-line dependency cannot be started.
 */
public class CliToolNotFoundException extends RuntimeException {
    /**
     * Creates an exception with a descriptive message.
     *
     * @param message description of the missing command-line tool
     */
    public CliToolNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message description of the missing command-line tool
     * @param cause underlying process-start failure
     */
    public CliToolNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
