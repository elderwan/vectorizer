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
    /**
     * Prevents creating this utility class.
     */
    private ZipHandler() {
    }

    /**
     * Extracts supported images into a new temporary directory.
     *
     * @param zipFile ZIP archive to read
     * @return extracted image files
     * @throws IOException if the archive cannot be read or extracted
     */
    public static List<File> extractImages(File zipFile) throws IOException {
        Path destination = Files.createTempDirectory("vectorizer-images-");
        return extractImages(zipFile, destination);
    }

    /**
     * Extracts supported images into the requested directory.
     * ZIP entries are checked to prevent files escaping that directory.
     *
     * @param zipFile ZIP archive to read
     * @param destination directory where images are written
     * @return extracted image files
     * @throws IOException if extraction fails or a ZIP entry is unsafe
     */
    public static List<File> extractImages(File zipFile, Path destination) throws IOException {
        validateArchive(zipFile);
        Files.createDirectories(destination);
        Path extractionRoot = normalize(destination);
        List<File> images = new ArrayList<>();
        boolean archiveContainsEntry = false;

        try (InputStream input = Files.newInputStream(zipFile.toPath());
             ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                archiveContainsEntry = true;
                if (entry.isDirectory()) {
                    continue;
                }
                Path output = resolveEntry(extractionRoot, entry);
                if (!FileValidator.isSupportedImageName(entry.getName())) {
                    continue;
                }
                Files.createDirectories(output.getParent());
                Files.copy(zipInput, output);
                images.add(output.toFile());
            }
            if (!archiveContainsEntry) {
                throw new UnsupportedFileFormatException("The ZIP archive is corrupted or empty.");
            }
        } catch (ZipException exception) {
            throw new UnsupportedFileFormatException("The ZIP archive is corrupted.", exception);
        }
        return images;
    }

    /**
     * Verifies that the input is a valid ZIP file by name and file type.
     *
     * @param zipFile archive to validate
     * @throws UnsupportedFileFormatException if the file is not a ZIP file
     */
    private static void validateArchive(File zipFile) {
        if (!FileValidator.isZip(zipFile)) {
            throw new UnsupportedFileFormatException("Expected a ZIP file");
        }
    }

    /**
     * Converts a path to an absolute normalized path.
     *
     * @param path path to normalize
     * @return normalized absolute path
     */
    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Resolves a ZIP entry below the extraction root.
     *
     * @param extractionRoot normalized extraction directory
     * @param entry ZIP entry to resolve
     * @return safe output path for the entry
     * @throws IOException if the entry would escape the extraction directory
     */
    private static Path resolveEntry(Path extractionRoot, ZipEntry entry) throws IOException {
        Path output = extractionRoot.resolve(entry.getName()).normalize();
        if (!output.startsWith(extractionRoot)) {
            throw new IOException("ZIP entry escapes extraction directory: " + entry.getName());
        }
        return output;
    }
}
