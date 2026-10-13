package se233.vectorizer.exception;

/**
 * Reports a file whose format is not supported by the application.
 */
public class UnsupportedFileFormatException extends IllegalArgumentException {
    /**
     * Creates an exception for an unsupported file format.
     *
     * @param message description of the invalid format
     */
    public UnsupportedFileFormatException(String message) {
        super(message);
    }

    /**
     * Creates an exception with an underlying parsing or I/O cause.
     *
     * @param message description of the invalid format
     * @param cause underlying failure
     */
    public UnsupportedFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
