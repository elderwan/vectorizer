package se233.vectorizer.util;

import java.io.File;
import java.util.Locale;

public final class FileValidator {
    private FileValidator() {
    }

    public static boolean isSupportedImage(File file) {
        return file != null && file.isFile() && isSupportedImageName(file.getName());
    }

    public static boolean isSupportedImageName(String fileName) {
        return fileName != null && hasExtension(fileName, ".jpg", ".jpeg", ".png");
    }

    public static boolean isZip(File file) {
        return file != null && file.isFile() && hasExtension(file, ".zip");
    }

    public static boolean isSupportedInput(File file) {
        return isSupportedImage(file) || isZip(file);
    }

    private static boolean hasExtension(File file, String... extensions) {
        return hasExtension(file.getName(), extensions);
    }

    private static boolean hasExtension(String fileName, String... extensions) {
        String name = fileName.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
