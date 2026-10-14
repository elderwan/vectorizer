package se233.vectorizer.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se233.vectorizer.exception.CliToolNotFoundException;
import se233.vectorizer.exception.VectorizationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Converts raster images to SVG through Potrace.
 *
 * The service quantizes image colors, creates one monochrome mask per color,
 * traces each mask with Potrace, and merges the generated SVG layers.
 */
/**
 * Converts raster images into layered SVG files through Potrace.
 */
public class VectorizationService {
    private static final Logger LOGGER = LogManager.getLogger(VectorizationService.class);
    private static final String DEFAULT_EXECUTABLE = "potrace";
    private static final int MAX_SAMPLE_PIXELS = 100_000;
    private static final int K_MEANS_ITERATIONS = 8;
    /*
     * Unlimited keeps the original palette when it is small. A bounded palette
     * is used for photographs so preview and export remain responsive.
     */
    private static final int MAX_UNLIMITED_COLORS = 16;
    private static volatile Boolean potraceAvailable;

    private final List<String> commandPrefix;

    /**
     * Creates a service using the default Potrace executable.
     */
    public VectorizationService() {
        this(DEFAULT_EXECUTABLE);
    }

    /**
     * Creates a service using a custom Potrace executable name.
     *
     * @param executable executable command used to start Potrace
     */
    public VectorizationService(String executable) {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("Potrace executable cannot be blank");
        }
        commandPrefix = List.of(executable);
    }

    /**
     * Creates a service using a complete command prefix.
     *
     * @param commandPrefix command and fixed arguments used for Potrace
     */
    public VectorizationService(List<String> commandPrefix) {
        if (commandPrefix == null || commandPrefix.isEmpty()
                || commandPrefix.get(0) == null || commandPrefix.get(0).isBlank()) {
            throw new IllegalArgumentException("Potrace command cannot be empty");
        }
        this.commandPrefix = List.copyOf(commandPrefix);
    }

    /**
     * Checks whether the default Potrace command can be started.
     *
     * @return true when Potrace is available
     */
    public static boolean checkPotraceAvailable() {
        Boolean cached = potraceAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (VectorizationService.class) {
            if (potraceAvailable == null) {
                potraceAvailable = canStart(DEFAULT_EXECUTABLE);
            }
            return potraceAvailable;
        }
    }

    /**
     * Clears the cached Potrace availability result.
     */
    public static void resetPotraceAvailabilityCache() {
        potraceAvailable = null;
    }

    /**
     * Converts an image asynchronously so the caller thread remains responsive.
     *
     * @param item image and image-specific settings to convert
     * @param settings conversion settings to apply
     * @return future containing the generated SVG path
     */
    public CompletableFuture<Path> convertAsync(ImageItem item, ConversionSettings settings) {
        // Run the potentially slow image conversion away from the JavaFX thread.
        if (item == null || settings == null) {
            return CompletableFuture.failedFuture(
                    new VectorizationException("Image item and settings are required"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convert(item, settings);
            } catch (IOException | RuntimeException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private Path convert(ImageItem item, ConversionSettings settings) throws IOException {
        LOGGER.info("Vectorization started: {}", item.getFile().getName());
        BufferedImage image = readImage(item);
        Path directory = Files.createTempDirectory("vectorizer-potrace-");
        Path output = directory.resolve(stripExtension(item.getFile().getName()) + ".svg");
        try {
            int colorLimit = settings.getColorMode() == ConversionSettings.ColorMode.CUSTOM
                    ? settings.getCustomColors() : MAX_UNLIMITED_COLORS;
            boolean preservePalette =
                    settings.getColorMode() == ConversionSettings.ColorMode.UNLIMITED;
            convertColorLayers(image, settings, directory, output, colorLimit,
                    preservePalette);
            LOGGER.info("Vectorization finished: {}", output);
            return output;
        } catch (CliToolNotFoundException | VectorizationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new VectorizationException(
                    "Unable to vectorize " + item.getFile().getName(), exception);
        }
    }

    private static BufferedImage readImage(ImageItem item) throws IOException {
        BufferedImage image = ImageIO.read(item.getFile());
        if (image == null) {
            throw new VectorizationException(
                    "Input image is corrupted or unsupported: " + item.getFile().getName());
        }
        return image;
    }

    private void convertColorLayers(
            BufferedImage image,
            ConversionSettings settings,
            Path directory,
            Path output,
            int colorLimit,
            boolean preservePalette) throws IOException {
        // Separate the image into color layers before invoking monochrome Potrace.
        QuantizedImage quantized = quantize(image, colorLimit, preservePalette);
        List<Path> layerSvgs = new ArrayList<>();
        List<Integer> layerColors = new ArrayList<>();
        Set<Integer> backgroundLayers = settings.isRemoveBackground()
                ? detectBackgroundLayers(quantized) : Set.of();

        for (int layer = 0; layer < quantized.palette().size(); layer++) {
            if (quantized.pixelCount(layer) == 0 || backgroundLayers.contains(layer)) {
                continue;
            }
            Path input = directory.resolve("layer-" + layer + ".ppm");
            Path layerSvg = directory.resolve("layer-" + layer + ".svg");
            writeMaskPnm(quantized.assignments(), quantized.width(), quantized.height(), layer, input);
            runPotrace(input, layerSvg, settings, toHex(quantized.palette().get(layer)));
            layerSvgs.add(layerSvg);
            layerColors.add(quantized.palette().get(layer));
        }
        if (layerSvgs.isEmpty()) {
            throw new VectorizationException("No vectorizable color layers were found.");
        }
        mergeSvgLayers(layerSvgs, layerColors, output, image.getWidth(), image.getHeight(),
                !settings.isRemoveBackground());
    }

    private void runPotrace(
            Path input,
            Path output,
            ConversionSettings settings,
            String color) throws IOException {
        // Execute Potrace for one mask and convert process failures into app errors.
        List<String> command = new ArrayList<>(commandPrefix);
        command.addAll(List.of(
                "-s",
                "-o", output.toString(),
                "-t", Integer.toString(turdSize(settings)),
                "-C", color,
                input.toString()));
        LOGGER.debug("Potrace command line: {}", command);

        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException exception) {
            throw new CliToolNotFoundException(
                    "Potrace CLI not found. Please install it and add it to PATH.", exception);
        }
        String processOutput = readOutput(process.getInputStream());
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VectorizationException("Potrace conversion was interrupted", exception);
        }
        if (exitCode != 0) {
            throw new VectorizationException(
                    "Potrace failed with exit code " + exitCode + ": " + processOutput);
        }
    }

    private static QuantizedImage quantize(
            BufferedImage image,
            int requestedColors,
            boolean preservePalette) {
        // Sample pixels and use a bounded palette so large images stay responsive.
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, (int) Math.ceil(
                Math.sqrt((double) width * height / MAX_SAMPLE_PIXELS)));
        List<Integer> samples = new ArrayList<>();
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                samples.add(image.getRGB(x, y) & 0x00FFFFFF);
            }
        }

        List<Integer> distinctSamples = new ArrayList<>(new LinkedHashSet<>(samples));
        int colorCount = Math.max(1, Math.min(requestedColors, distinctSamples.size()));
        boolean canUseOriginalPalette = preservePalette && distinctSamples.size() <= requestedColors;
        int[] palette = initialPalette(samples, colorCount);
        for (int iteration = 0;
             iteration < K_MEANS_ITERATIONS && !canUseOriginalPalette;
             iteration++) {
            long[] red = new long[palette.length];
            long[] green = new long[palette.length];
            long[] blue = new long[palette.length];
            int[] counts = new int[palette.length];
            for (int color : samples) {
                int nearest = nearestPaletteIndex(color, palette);
                red[nearest] += (color >> 16) & 0xFF;
                green[nearest] += (color >> 8) & 0xFF;
                blue[nearest] += color & 0xFF;
                counts[nearest]++;
            }
            for (int index = 0; index < palette.length; index++) {
                if (counts[index] > 0) {
                    palette[index] = rgb(
                            (int) (red[index] / counts[index]),
                            (int) (green[index] / counts[index]),
                            (int) (blue[index] / counts[index]));
                }
            }
        }

        int[] assignments = new int[width * height];
        int[] pixelCounts = new int[palette.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int nearest = nearestPaletteIndex(image.getRGB(x, y) & 0x00FFFFFF, palette);
                assignments[y * width + x] = nearest;
                pixelCounts[nearest]++;
            }
        }
        List<Integer> distinctPalette = new ArrayList<>(
                new LinkedHashSet<>(toList(palette)));
        if (distinctPalette.size() == palette.length) {
            return new QuantizedImage(width, height, assignments, toList(palette), pixelCounts);
        }

        int[] remapped = new int[assignments.length];
        int[] distinctCounts = new int[distinctPalette.size()];
        for (int index = 0; index < assignments.length; index++) {
            int remappedIndex = distinctPalette.indexOf(palette[assignments[index]]);
            remapped[index] = remappedIndex;
            distinctCounts[remappedIndex]++;
        }
        return new QuantizedImage(width, height, remapped, distinctPalette, distinctCounts);
    }

    private static int[] initialPalette(List<Integer> samples, int colorCount) {
        List<Integer> distinctSamples = new ArrayList<>(new LinkedHashSet<>(samples));
        int paletteSize = Math.min(colorCount, Math.max(1, distinctSamples.size()));
        int[] palette = new int[paletteSize];
        for (int index = 0; index < paletteSize; index++) {
            int sampleIndex = paletteSize == 1
                    ? 0
                    : (int) ((long) index * (distinctSamples.size() - 1) / (paletteSize - 1));
            palette[index] = distinctSamples.get(sampleIndex);
        }
        return palette;
    }

    private static int nearestPaletteIndex(int color, int[] palette) {
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < palette.length; index++) {
            long distance = colorDistance(color, palette[index]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static long colorDistance(int first, int second) {
        int red = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int green = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int blue = (first & 0xFF) - (second & 0xFF);
        return (long) red * red + (long) green * green + (long) blue * blue;
    }

    private static void writeMaskPnm(
            int[] assignments,
            int width,
            int height,
            int selectedLayer,
            Path destination) throws IOException {
        try (BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(destination))) {
            output.write(("P6\n" + width + " " + height + "\n255\n")
                    .getBytes(StandardCharsets.US_ASCII));
            for (int assignment : assignments) {
                int value = assignment == selectedLayer ? 0 : 255;
                output.write(value);
                output.write(value);
                output.write(value);
            }
        }
    }

    private static void writePnm(BufferedImage image, Path destination) throws IOException {
        try (BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(destination))) {
            output.write(("P6\n" + image.getWidth() + " " + image.getHeight() + "\n255\n")
                    .getBytes(StandardCharsets.US_ASCII));
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    output.write((rgb >> 16) & 0xFF);
                    output.write((rgb >> 8) & 0xFF);
                    output.write(rgb & 0xFF);
                }
            }
        }
    }

    private static void mergeSvgLayers(
            List<Path> layers,
            List<Integer> colors,
            Path output,
            int width,
            int height,
            boolean includeBackground) throws IOException {
        StringBuilder svg = new StringBuilder();
        svg.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                """.formatted(width, height, width, height));
        if (includeBackground) {
            svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>");
        }
        for (int index = 0; index < layers.size(); index++) {
            String content = Files.readString(layers.get(index), StandardCharsets.UTF_8);
            int groupStart = content.indexOf("<g ");
            int groupEnd = content.lastIndexOf("</g>");
            if (groupStart >= 0 && groupEnd > groupStart) {
                String group = content.substring(groupStart, groupEnd + 4);
                group = group.replace("fill=\"#000000\"", "fill=\"" + toHex(colors.get(index)) + "\"");
                svg.append(group);
            }
        }
        svg.append("</svg>");
        Files.writeString(output, svg, StandardCharsets.UTF_8);
    }

    /**
     * Identifies background color layers to exclude during vectorization.
     * Supports solid backgrounds, multi-tone gradients, and perimeter boundaries.
     *
     * @param image the quantized image with color assignments and palette
     * @return a set of layer indices representing background colors
     */
    private static Set<Integer> detectBackgroundLayers(QuantizedImage image) {
        int colorCount = image.palette().size();
        if (colorCount <= 1) {
            return Collections.emptySet();
        }

        int width = image.width();
        int height = image.height();
        int[] assignments = image.assignments();
        int totalPixels = assignments.length;

        // Step 1: Count edge pixels and corner pixels for each color layer
        int[] edgeCounts = new int[colorCount];
        int[] cornerCounts = new int[colorCount];
        int totalEdgePixels = countEdgeAndCornerPixels(
                assignments, width, height, edgeCounts, cornerCounts);

        // Step 2: Detect initial background seed layers (dominant perimeter or corners)
        Set<Integer> backgroundLayers = findInitialBackgroundSeeds(
                edgeCounts, cornerCounts, totalEdgePixels, image.pixelCounts(), totalPixels, colorCount);

        // Step 3: Expand detection to multi-tone / gradient background layers
        expandGradientBackgroundLayers(
                backgroundLayers, edgeCounts, totalEdgePixels, image.pixelCounts(), totalPixels, image.palette());

        // Step 4: Safety guarantee: never remove all layers from an image
        if (backgroundLayers.size() >= colorCount) {
            int leastEdgeLayer = 0;
            int minEdge = Integer.MAX_VALUE;
            for (int layer = 0; layer < colorCount; layer++) {
                if (edgeCounts[layer] < minEdge) {
                    minEdge = edgeCounts[layer];
                    leastEdgeLayer = layer;
                }
            }
            backgroundLayers.remove(leastEdgeLayer);
        }

        return backgroundLayers;
    }

    /**
     * Counts how many times each color layer appears on the outer borders and 4 corners.
     */
    private static int countEdgeAndCornerPixels(
            int[] assignments,
            int width,
            int height,
            int[] edgeCounts,
            int[] cornerCounts) {
        int totalEdgePixels = 0;

        // Record the 4 corners of the image
        int topLeft = assignments[0];
        int topRight = assignments[width - 1];
        int bottomLeft = assignments[(height - 1) * width];
        int bottomRight = assignments[(height - 1) * width + width - 1];

        cornerCounts[topLeft]++;
        cornerCounts[topRight]++;
        cornerCounts[bottomLeft]++;
        cornerCounts[bottomRight]++;

        // Sample top and bottom rows
        for (int x = 0; x < width; x++) {
            edgeCounts[assignments[x]]++;
            edgeCounts[assignments[(height - 1) * width + x]]++;
            totalEdgePixels += 2;
        }

        // Sample left and right columns (excluding corners already sampled)
        for (int y = 1; y < height - 1; y++) {
            edgeCounts[assignments[y * width]]++;
            edgeCounts[assignments[y * width + (width - 1)]]++;
            totalEdgePixels += 2;
        }

        return totalEdgePixels;
    }

    /**
     * Finds seed background layers based on border dominance and corner occupancy.
     */
    private static Set<Integer> findInitialBackgroundSeeds(
            int[] edgeCounts,
            int[] cornerCounts,
            int totalEdgePixels,
            int[] pixelCounts,
            int totalPixels,
            int colorCount) {
        Set<Integer> seeds = new HashSet<>();

        // 1. Find the layer with the highest presence along the image perimeter
        int dominantEdgeLayer = -1;
        int maxEdgeCount = 0;
        for (int layer = 0; layer < colorCount; layer++) {
            if (edgeCounts[layer] > maxEdgeCount) {
                maxEdgeCount = edgeCounts[layer];
                dominantEdgeLayer = layer;
            }
        }

        double dominantEdgeRatio = (double) maxEdgeCount / totalEdgePixels;
        if (dominantEdgeLayer != -1 && dominantEdgeRatio >= 0.20 && pixelCounts[dominantEdgeLayer] >= totalPixels * 0.03) {
            seeds.add(dominantEdgeLayer);
        }

        // 2. Identify secondary layers that occupy multiple corners (only if primary does not overwhelmingly dominate >= 60%)
        if (dominantEdgeRatio < 0.60) {
            for (int layer = 0; layer < colorCount; layer++) {
                if (layer == dominantEdgeLayer) {
                    continue;
                }
                double edgeRatio = (double) edgeCounts[layer] / totalEdgePixels;
                double totalRatio = (double) pixelCounts[layer] / totalPixels;

                boolean multipleCorners = cornerCounts[layer] >= 2 && edgeRatio >= 0.15;
                if (multipleCorners && totalRatio >= 0.02) {
                    seeds.add(layer);
                }
            }
        }

        return seeds;
    }

    /**
     * Expands background layers to include multi-tone / gradient transitions
     * that share color similarity and border presence with initial seed layers.
     */
    private static void expandGradientBackgroundLayers(
            Set<Integer> backgroundLayers,
            int[] edgeCounts,
            int totalEdgePixels,
            int[] pixelCounts,
            int totalPixels,
            List<Integer> palette) {
        int colorCount = palette.size();
        boolean expanded = true;

        while (expanded) {
            expanded = false;
            for (int layer = 0; layer < colorCount; layer++) {
                if (backgroundLayers.contains(layer)) {
                    continue;
                }

                double edgeRatio = (double) edgeCounts[layer] / totalEdgePixels;
                double totalRatio = (double) pixelCounts[layer] / totalPixels;

                // Candidate gradient layer must touch the outer perimeter
                if (edgeRatio < 0.04 || totalRatio < 0.01) {
                    continue;
                }

                for (int bgLayer : new ArrayList<>(backgroundLayers)) {
                    double distance = calculateColorDistance(palette.get(layer), palette.get(bgLayer));
                    double maxAllowedDistance = (edgeRatio >= 0.12) ? 125.0 : 95.0;

                    if (distance <= maxAllowedDistance) {
                        int currentBackgroundPixels = 0;
                        for (int bl : backgroundLayers) {
                            currentBackgroundPixels += pixelCounts[bl];
                        }
                        // Safety threshold: keep at least 12% of the image as foreground subject
                        if ((currentBackgroundPixels + pixelCounts[layer]) <= totalPixels * 0.88) {
                            backgroundLayers.add(layer);
                            expanded = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculates Euclidean distance between two RGB colors.
     */
    private static double calculateColorDistance(int firstColor, int secondColor) {
        int redDifference = ((firstColor >> 16) & 0xFF) - ((secondColor >> 16) & 0xFF);
        int greenDifference = ((firstColor >> 8) & 0xFF) - ((secondColor >> 8) & 0xFF);
        int blueDifference = (firstColor & 0xFF) - (secondColor & 0xFF);
        return Math.sqrt(
                redDifference * redDifference +
                greenDifference * greenDifference +
                blueDifference * blueDifference);
    }

    private static int turdSize(ConversionSettings settings) {
        return switch (settings.getDetailLevel()) {
            case LOW -> 10;
            case MEDIUM -> 50;
            case HIGH -> 100;
        };
    }

    private static String readOutput(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static boolean canStart(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().close();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            LOGGER.debug("Potrace is not available", exception);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int rgb(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
    }

    private static String toHex(int color) {
        return "#%06X".formatted(color & 0x00FFFFFF);
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private static String stripExtension(String name) {
        int extensionIndex = name.lastIndexOf('.');
        return extensionIndex > 0 ? name.substring(0, extensionIndex) : name;
    }

    private record QuantizedImage(
            int width,
            int height,
            int[] assignments,
            List<Integer> palette,
            int[] pixelCounts) {
        private int pixelCount(int layer) {
            return pixelCounts[layer];
        }
    }
}
