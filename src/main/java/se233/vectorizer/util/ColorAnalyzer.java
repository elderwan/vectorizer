package se233.vectorizer.util;

import se233.vectorizer.model.ConversionSettings;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Estimates the number of distinct RGB colors in a raster image.
 */
public final class ColorAnalyzer {
    private static final int MAX_SAMPLED_PIXELS = 1_000_000;

    private ColorAnalyzer() {
    }

    /**
     * Counts distinct RGB values, capped at the supported custom color maximum.
     * Large images are sampled to keep analysis responsive.
     *
     * @param image image to inspect
     * @return detected color count, capped at the supported custom maximum
     */
    public static int detectColorCount(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        }
        long pixelCount = (long) image.getWidth() * image.getHeight();
        int step = Math.max(1, (int) Math.ceil((double) pixelCount / MAX_SAMPLED_PIXELS));
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                colors.add(image.getRGB(x, y) & 0x00FFFFFF);
                if (colors.size() > ConversionSettings.MAX_CUSTOM_COLORS) {
                    return ConversionSettings.MAX_CUSTOM_COLORS;
                }
            }
        }
        return Math.max(1, colors.size());
    }
}
