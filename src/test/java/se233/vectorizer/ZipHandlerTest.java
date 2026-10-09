package se233.vectorizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se233.vectorizer.util.ZipHandler;
import se233.vectorizer.exception.UnsupportedFileFormatException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZipHandlerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void extractsOnlySupportedImagesAndPreservesDirectories() throws IOException {
        Path archive = tempDirectory.resolve("images.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/photo.png"));
            output.write(new byte[]{1, 2, 3});
            output.closeEntry();
            output.putNextEntry(new ZipEntry("notes.txt"));
            output.write(new byte[]{4, 5});
            output.closeEntry();
        }

        Path destination = tempDirectory.resolve("extracted");
        List<File> extracted = ZipHandler.extractImages(archive.toFile(), destination);

        assertEquals(1, extracted.size());
        assertEquals(destination.resolve("nested/photo.png").toFile(), extracted.get(0));
        assertTrue(Files.exists(destination.resolve("nested/photo.png")));
        assertEquals(3, Files.size(destination.resolve("nested/photo.png")));
    }

    @Test
    void rejectsZipSlipEntries() throws IOException {
        Path archive = tempDirectory.resolve("unsafe.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("../outside.png"));
            output.write(new byte[]{1});
            output.closeEntry();
        }

        try {
            ZipHandler.extractImages(archive.toFile(), tempDirectory.resolve("extracted"));
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("escapes"));
            return;
        }
        throw new AssertionError("Expected ZIP slip entry to be rejected");
    }

    @Test
    void extractsMixedJpgAndPngEntries() throws IOException {
        Path archive = tempDirectory.resolve("mixed.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : List.of("photo.jpg", "photo.png")) {
                output.putNextEntry(new ZipEntry(name));
                output.write(new byte[]{1, 2, 3});
                output.closeEntry();
            }
        }

        List<File> extracted = ZipHandler.extractImages(
                archive.toFile(), tempDirectory.resolve("mixed-extracted"));

        assertEquals(2, extracted.size());
    }

    @Test
    void rejectsCorruptedZip() throws IOException {
        Path archive = tempDirectory.resolve("corrupted.zip");
        Files.write(archive, new byte[]{1, 2, 3, 4});

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedFileFormatException.class,
                () -> ZipHandler.extractImages(archive.toFile(), tempDirectory.resolve("bad")));
    }
}
