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

        int step = calculateSamplingStep(image);
        Set<Integer> colors = new HashSet<>();
        collectColors(image, step, colors);
        return Math.min(
                ConversionSettings.MAX_CUSTOM_COLORS,
                Math.max(1, colors.size()));
    }

    private static int calculateSamplingStep(BufferedImage image) {
        long pixelCount = (long) image.getWidth() * image.getHeight();
        return Math.max(1, (int) Math.ceil(
                (double) pixelCount / MAX_SAMPLED_PIXELS));
    }

    private static void collectColors(BufferedImage image, int step, Set<Integer> colors) {
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                colors.add(rgbWithoutAlpha(image, x, y));
                if (hasReachedColorLimit(colors)) {
                    return;
                }
            }
        }
    }

    private static int rgbWithoutAlpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0x00FFFFFF;
    }

    private static boolean hasReachedColorLimit(Set<Integer> colors) {
        return colors.size() > ConversionSettings.MAX_CUSTOM_COLORS;
    }
}
