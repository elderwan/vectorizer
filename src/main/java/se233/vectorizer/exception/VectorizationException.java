package se233.vectorizer.exception;

import java.io.IOException;

/**
 * Reports an input, process, or output failure during raster-to-vector conversion.
 */
public class VectorizationException extends IOException {
    public VectorizationException(String message) {
        super(message);
    }

    public VectorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
