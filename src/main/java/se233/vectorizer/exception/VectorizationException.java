package se233.vectorizer.exception;

import java.io.IOException;

/**
 * Reports an input, process, or output failure during raster-to-vector conversion.
 */
public class VectorizationException extends IOException {
    /**
     * Creates an exception for a vectorization failure.
     *
     * @param message description of the conversion failure
     */
    public VectorizationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with an underlying cause.
     *
     * @param message description of the conversion failure
     * @param cause underlying conversion failure
     */
    public VectorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
