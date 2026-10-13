package se233.vectorizer.util;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Provides validation helpers for supported image and archive files.
 */
public final class FileValidator {
    /**
     * Prevents creating this utility class.
     */
    private FileValidator() {
    }

    /**
     * Checks whether a real file has a supported image extension.
     *
     * @param file file to inspect
     * @return true for an existing JPG, JPEG, or PNG file
     */
    public static boolean isSupportedImage(File file) {
        return isRegularFile(file) && isSupportedImageName(file.getName());
    }

    /**
     * Checks whether a file name has a supported image extension.
     *
     * @param fileName file name to inspect
     * @return true for a JPG, JPEG, or PNG name, ignoring case
     */
    /**
     * Checks whether a filename has a supported raster-image extension.
     *
     * @param fileName filename to inspect
     * @return true for JPG, JPEG, or PNG names
     */
    public static boolean isSupportedImageName(String fileName) {
        return fileName != null && hasExtension(fileName, ".jpg", ".jpeg", ".png");
    }

    /**
     * Checks whether a real file has a ZIP extension.
     *
     * @param file file to inspect
     * @return true for an existing ZIP file
     */
    /**
     * Checks whether a regular file has a ZIP extension.
     *
     * @param file file to inspect
     * @return true when the file is a ZIP archive
     */
    public static boolean isZip(File file) {
        return isRegularFile(file) && hasExtension(file, ".zip");
    }

    /**
     * Checks whether a file can be used as an application input.
     *
     * @param file file to inspect
     * @return true for a supported image or ZIP file
     */
    /**
     * Checks whether a file is an accepted application input.
     *
     * @param file file to inspect
     * @return true for a supported image or ZIP archive
     */
    public static boolean isSupportedInput(File file) {
        return isSupportedImage(file) || isZip(file);
    }

    /**
     * Checks a file's name against one or more extensions.
     *
     * @param file file whose name is checked
     * @param extensions accepted extensions
     * @return true when the file name ends with one of the extensions
     */
    private static boolean hasExtension(File file, String... extensions) {
        return hasExtension(file.getName(), extensions);
    }

    /**
     * Checks a file-name string against one or more extensions.
     *
     * @param fileName file name to check
     * @param extensions accepted extensions
     * @return true when the name ends with one of the extensions
     */
    private static boolean hasExtension(String fileName, String... extensions) {
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(extensions).anyMatch(normalizedName::endsWith);
    }

    /**
     * Checks whether a non-null path points to a regular file.
     *
     * @param file file to inspect
     * @return true when file exists and is not a directory
     */
    private static boolean isRegularFile(File file) {
        return file != null && file.isFile();
    }
}
