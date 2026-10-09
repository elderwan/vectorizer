package se233.vectorizer.model;

import javafx.scene.image.Image;

import java.io.File;
import java.nio.file.Path;

public class ImageItem {
    private final File file;
    private final Image originalImage;
    private Path vectorizedPath;
    private final ConversionSettings settings = new ConversionSettings();

    public ImageItem(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Image file must exist");
        }
        this.file = file;
        this.originalImage = new Image(file.toURI().toString(), false);
        if (originalImage.isError()) {
            throw new IllegalArgumentException("Unable to load image: " + file.getName());
        }
    }

    public File getFile() {
        return file;
    }

    public Image getOriginalImage() {
        return originalImage;
    }

    public Path getVectorizedPath() {
        return vectorizedPath;
    }

    public void setVectorizedPath(Path vectorizedPath) {
        this.vectorizedPath = vectorizedPath;
    }

    public ConversionSettings getSettings() {
        return settings;
    }
}
