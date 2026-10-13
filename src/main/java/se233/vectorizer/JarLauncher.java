package se233.vectorizer;

/**
 * Entry point used by the packaged JAR.
 */
public final class JarLauncher {
    private JarLauncher() {
    }

    /**
     * Delegates packaged-JAR startup to the JavaFX launcher.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        // Delegate to the normal JavaFX launcher.
        Launcher.main(args);
    }
}
