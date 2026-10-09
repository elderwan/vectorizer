package se233.vectorizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se233.vectorizer.util.FileValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileValidatorTest {
    @TempDir
    Path tempDirectory;

    @Test
    void acceptsSupportedImageExtensionsCaseInsensitively() throws IOException {
        File jpg = createFile("photo.JPG");
        File jpeg = createFile("photo.jpeg");
        File png = createFile("photo.png");

        assertTrue(FileValidator.isSupportedImage(jpg));
        assertTrue(FileValidator.isSupportedImage(jpeg));
        assertTrue(FileValidator.isSupportedImage(png));
    }

    @Test
    void rejectsUnsupportedExtensions() throws IOException {
        assertFalse(FileValidator.isSupportedImage(createFile("photo.gif")));
        assertFalse(FileValidator.isSupportedInput(createFile("document.pdf")));
    }

    @Test
    void recognizesZipFiles() throws IOException {
        assertTrue(FileValidator.isZip(createFile("images.ZIP")));
    }

    private File createFile(String name) throws IOException {
        return Files.createFile(tempDirectory.resolve(name)).toFile();
    }
}
