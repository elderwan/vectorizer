package se233.vectorizer.model;

import javafx.scene.image.Image;

import java.io.File;
import java.nio.file.Path;

/**
 * Represents one imported image and the result/settings associated with it.
 */
/**
 * Represents an imported image, its settings, and its generated SVG path.
 */
public class ImageItem {
    private final File file;
    private final Image originalImage;
    private Path vectorizedPath;
    private final ConversionSettings settings = new ConversionSettings();

    public ImageItem(File file) {
        // Validate and load the original image once when the item is created.
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Image file must exist");
        }
        this.file = file;
        this.originalImage = new Image(file.toURI().toString(), false);
        if (originalImage.isError()) {
            throw new IllegalArgumentException("Unable to load image: " + file.getName());
        }
    }

    /**
     * Returns the imported image file.
     *
     * @return source image file
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns the image loaded for the original preview.
     *
     * @return original JavaFX image
     */
    public Image getOriginalImage() {
        return originalImage;
    }

    /**
     * Returns the generated SVG path.
     *
     * @return vectorized file path, or null before conversion
     */
    public Path getVectorizedPath() {
        return vectorizedPath;
    }

    private final java.util.Map<ConversionSettings, Path> cache = new java.util.HashMap<>();

    /**
     * Stores the generated SVG path.
     *
     * @param vectorizedPath generated SVG path
     */
    public void setVectorizedPath(Path vectorizedPath) {
        // Store the generated SVG so the preview can display it later.
        this.vectorizedPath = vectorizedPath;
        if (vectorizedPath != null) {
            ConversionSettings snapshot = new ConversionSettings();
            snapshot.setDetailLevel(settings.getDetailLevel());
            snapshot.setColorMode(settings.getColorMode());
            snapshot.setCustomColors(settings.getCustomColors());
            snapshot.setRemoveBackground(settings.isRemoveBackground());
            cache.put(snapshot, vectorizedPath);
        }
    }

    /**
     * Checks if a cached SVG exists for the specified settings.
     */
    public Path getCachedSvg(ConversionSettings currentSettings) {
        return cache.get(currentSettings);
    }

    /**
     * Returns the conversion settings for this image.
     *
     * @return image-specific conversion settings
     */
    public ConversionSettings getSettings() {
        return settings;
    }
}
