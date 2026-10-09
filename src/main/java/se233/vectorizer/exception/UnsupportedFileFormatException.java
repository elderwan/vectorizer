package se233.vectorizer.exception;

/**
 * Reports a file whose format is not supported by the application.
 */
public class UnsupportedFileFormatException extends IllegalArgumentException {
    public UnsupportedFileFormatException(String message) {
        super(message);
    }

    public UnsupportedFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
