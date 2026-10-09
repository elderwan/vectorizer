package se233.vectorizer.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import se233.vectorizer.exception.UnsupportedFileFormatException;

public final class ZipHandler {
    private ZipHandler() {
    }

    public static List<File> extractImages(File zipFile) throws IOException {
        Path destination = Files.createTempDirectory("vectorizer-images-");
        return extractImages(zipFile, destination);
    }

    public static List<File> extractImages(File zipFile, Path destination) throws IOException {
        if (!FileValidator.isZip(zipFile)) {
            throw new UnsupportedFileFormatException("Expected a ZIP file");
        }
        Files.createDirectories(destination);
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        List<File> images = new ArrayList<>();
        boolean sawEntry = false;

        try (InputStream input = Files.newInputStream(zipFile.toPath());
             ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                sawEntry = true;
                if (entry.isDirectory()) {
                    continue;
                }
                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedDestination)) {
                    throw new IOException("ZIP entry escapes extraction directory: " + entry.getName());
                }
                if (!FileValidator.isSupportedImageName(entry.getName())) {
                    continue;
                }
                Files.createDirectories(output.getParent());
                Files.copy(zipInput, output);
                images.add(output.toFile());
            }
            if (!sawEntry) {
                throw new UnsupportedFileFormatException("The ZIP archive is corrupted or empty.");
            }
        } catch (ZipException exception) {
            throw new UnsupportedFileFormatException("The ZIP archive is corrupted.", exception);
        }
        return images;
    }
}
