package se233.vectorizer.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se233.vectorizer.model.ConversionSettings;
import se233.vectorizer.model.ImageItem;
import se233.vectorizer.model.VectorizationService;
import se233.vectorizer.util.ColorAnalyzer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Processes image batches asynchronously and reports progress and failures.
 *
 * Multiple images may be processed concurrently when the selected settings
 * make parallel conversion safe and useful.
 */
public class BatchProcessor {
    private static final Logger LOGGER = LogManager.getLogger(BatchProcessor.class);

    private final VectorizationService vectorizationService;

    /**
     * Creates a batch processor with the conversion service it should use.
     *
     * @param vectorizationService service used for image conversion
     */
    public BatchProcessor(VectorizationService vectorizationService) {
        this.vectorizationService = vectorizationService;
    }

    /**
     * Processes all items without blocking the calling JavaFX thread.
     *
     * @param items images to process
     * @param settings conversion settings
     * @param outputDirectory destination directory
     * @param progressListener receives completed count and total count
     * @return future containing successes and individual failures
     */
    public CompletableFuture<BatchResult> processAsync(
            List<ImageItem> items,
            ConversionSettings settings,
            Path outputDirectory,
            Consumer<BatchProgress> progressListener) {
        // Copy the list so later UI changes cannot modify the running batch.
        List<ImageItem> snapshot = List.copyOf(items);
        return CompletableFuture.supplyAsync(() ->
                process(snapshot, settings, outputDirectory, progressListener));
    }

    /**
     * Detects colors and creates the initial preview for every imported image.
     * Multiple images are processed concurrently.
     *
     * @param items images to preprocess
     * @param progressListener receives completed count and total count
     * @return future containing preview paths, detected colors, and failures
     */
    /**
     * Preprocesses images concurrently and reports each completed item.
     *
     * @param items images to preprocess
     * @param progressListener receives completed count and total count
     * @param itemListener receives each prepared image
     * @return future containing preview results and failures
     */
    public CompletableFuture<PreprocessResult> preprocessAsync(
            List<ImageItem> items,
            Consumer<BatchProgress> progressListener) {
        return preprocessAsync(items, progressListener, null);
    }

    public CompletableFuture<PreprocessResult> preprocessAsync(
            List<ImageItem> items,
            Consumer<BatchProgress> progressListener,
            Consumer<PreprocessedItem> itemListener) {
        List<ImageItem> snapshot = List.copyOf(items);
        return CompletableFuture.supplyAsync(() ->
                preprocess(snapshot, progressListener, itemListener));
    }

    /**
     * Determines whether the current batch qualifies for parallel processing.
     *
     * @param items images in the batch
     * @param settings conversion settings selected by the user
     * @return true when parallel processing should be used
     */
    public static boolean shouldProcessInParallel(List<ImageItem> items, ConversionSettings settings) {
        // Parallel mode is enabled only for the supported multi-image case.
        return items.size() > 1
                && settings.getDetailLevel() == ConversionSettings.DetailLevel.MEDIUM
                && settings.getColorMode() == ConversionSettings.ColorMode.CUSTOM
                && settings.getCustomColors() <= ConversionSettings.MAX_CUSTOM_COLORS;
    }

    private BatchResult process(
            List<ImageItem> items,
            ConversionSettings settings,
            Path outputDirectory,
            Consumer<BatchProgress> progressListener) {
        boolean parallel = shouldProcessInParallel(items, settings);
        LOGGER.info("Batch size: {}, parallel: {}", items.size(), parallel);
        List<Path> successes = new ArrayList<>();
        List<BatchFailure> failures = new ArrayList<>();
        ExecutorService executor = createBatchExecutor(parallel);
        try {
            Files.createDirectories(outputDirectory);
            List<String> outputNames = createOutputNames(items, settings);
            CompletionService<Path> completionService = new ExecutorCompletionService<>(executor);
            submitExportTasks(items, settings, outputDirectory, outputNames, completionService);
            collectExportResults(items.size(), completionService, successes, failures, progressListener);
        } catch (IOException exception) {
            LOGGER.error("Unable to prepare batch output directory", exception);
            for (ImageItem item : items) {
                failures.add(new BatchFailure(item.getFile().getName(), exception));
            }
        } finally {
            executor.shutdownNow();
        }
        return new BatchResult(successes, failures, parallel);
    }

    private ExecutorService createBatchExecutor(boolean parallel) {
        if (parallel) {
            int cores = Runtime.getRuntime().availableProcessors();
            return Executors.newFixedThreadPool(cores);
        } else {
            return Executors.newSingleThreadExecutor();
        }
    }

    private void submitExportTasks(
            List<ImageItem> items,
            ConversionSettings settings,
            Path outputDirectory,
            List<String> outputNames,
            CompletionService<Path> completionService) {
        for (int index = 0; index < items.size(); index++) {
            ImageItem item = items.get(index);
            String outputName = outputNames.get(index);
            completionService.submit(new ExportSingleTask(item, settings, outputDirectory, outputName));
        }
    }

    private class ExportSingleTask implements Callable<Path> {
        private final ImageItem item;
        private final ConversionSettings settings;
        private final Path outputDirectory;
        private final String outputName;

        ExportSingleTask(ImageItem item, ConversionSettings settings, Path outputDirectory, String outputName) {
            this.item = item;
            this.settings = settings;
            this.outputDirectory = outputDirectory;
            this.outputName = outputName;
        }

        @Override
        public Path call() throws Exception {
            Path temporarySvg = vectorizationService.convertAsync(item, settings).join();
            Path destination = outputDirectory.resolve(outputName);
            Files.copy(temporarySvg, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        }
    }

    private void collectExportResults(
            int totalCount,
            CompletionService<Path> completionService,
            List<Path> successes,
            List<BatchFailure> failures,
            Consumer<BatchProgress> progressListener) {
        for (int completed = 1; completed <= totalCount; completed++) {
            try {
                Path result = completionService.take().get();
                successes.add(result);
            } catch (Exception exception) {
                Throwable cause = unwrap(exception);
                LOGGER.error("Batch item failed", cause);
                failures.add(new BatchFailure(cause.getMessage(), cause));
            }
            if (progressListener != null) {
                progressListener.accept(new BatchProgress(completed, totalCount));
            }
        }
    }

    private static List<String> createOutputNames(
            List<ImageItem> items,
            ConversionSettings settings) {
        // Preserve original names or create numbered names from a custom prefix.
        ConversionSettings.NamingRule namingRule = settings.getNamingRule();
        if (namingRule == ConversionSettings.NamingRule.ORIGINAL) {
            List<String> names = new ArrayList<>(items.size());
            for (ImageItem item : items) {
                String baseName = stripExtension(item.getFile().getName());
                names.add(baseName + ".svg");
            }
            return names;
        }

        String customBase = sanitizeCustomName(settings.getCustomName());
        List<String> names = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            names.add(customBase + "_" + (index + 1) + ".svg");
        }
        return names;
    }

    private static String sanitizeCustomName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.regionMatches(true, Math.max(0, name.length() - 4), ".svg", 0, 4)) {
            name = name.substring(0, name.length() - 4).trim();
        }

        StringBuilder sanitized = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isISOControl(character)
                    || "\\/:*?\"<>|".indexOf(character) >= 0) {
                sanitized.append('_');
            } else {
                sanitized.append(character);
            }
        }

        name = sanitized.toString().stripTrailing();
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1).stripTrailing();
        }
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException(
                    "Enter a valid custom export name before exporting.");
        }
        return name;
    }

    private PreprocessResult preprocess(
            List<ImageItem> items,
            Consumer<BatchProgress> progressListener,
            Consumer<PreprocessedItem> itemListener) {
        LOGGER.info("Preprocessing batch size: {}", items.size());
        List<PreprocessedItem> successes = new ArrayList<>();
        List<BatchFailure> failures = new ArrayList<>();
        if (items.isEmpty()) {
            return new PreprocessResult(successes, failures, false);
        }

        int maxThreads = Math.min(Runtime.getRuntime().availableProcessors(), items.size());
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        try {
            CompletionService<PreprocessedItem> completionService =
                    new ExecutorCompletionService<>(executor);
            submitPreprocessTasks(items, completionService);
            collectPreprocessResults(items.size(), completionService, successes, failures, progressListener, itemListener);
        } finally {
            executor.shutdownNow();
        }
        return new PreprocessResult(successes, failures, items.size() > 1);
    }

    private void submitPreprocessTasks(
            List<ImageItem> items,
            CompletionService<PreprocessedItem> completionService) {
        for (ImageItem item : items) {
            completionService.submit(new PreprocessSingleTask(item));
        }
    }

    private class PreprocessSingleTask implements Callable<PreprocessedItem> {
        private final ImageItem item;

        PreprocessSingleTask(ImageItem item) {
            this.item = item;
        }

        @Override
        public PreprocessedItem call() throws Exception {
            return preprocessSingleImage(item);
        }
    }

    private PreprocessedItem preprocessSingleImage(ImageItem item) throws IOException {
        BufferedImage image = ImageIO.read(item.getFile());
        if (image == null) {
            throw new IOException("Input image is corrupted: " + item.getFile().getName());
        }
        int colorCount = ColorAnalyzer.detectColorCount(image);
        Path svg = vectorizationService.convertAsync(item, item.getSettings()).join();
        return new PreprocessedItem(item, svg, colorCount);
    }

    private void collectPreprocessResults(
            int totalCount,
            CompletionService<PreprocessedItem> completionService,
            List<PreprocessedItem> successes,
            List<BatchFailure> failures,
            Consumer<BatchProgress> progressListener,
            Consumer<PreprocessedItem> itemListener) {
        for (int completed = 1; completed <= totalCount; completed++) {
            try {
                PreprocessedItem result = completionService.take().get();
                successes.add(result);
                if (itemListener != null) {
                    itemListener.accept(result);
                }
            } catch (Exception exception) {
                Throwable cause = unwrap(exception);
                LOGGER.error("Preprocessing item failed", cause);
                failures.add(new BatchFailure(cause.getMessage(), cause));
            }
            notifyProgress(progressListener, completed, totalCount);
        }
    }

    private static void notifyProgress(
            Consumer<BatchProgress> progressListener,
            int completed,
            int total) {
        if (progressListener != null) {
            progressListener.accept(new BatchProgress(completed, total));
        }
    }

    private static Throwable unwrap(Throwable exception) {
        Throwable cause = exception;
        while ((cause instanceof CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String stripExtension(String name) {
        int extensionIndex = name.lastIndexOf('.');
        return extensionIndex > 0 ? name.substring(0, extensionIndex) : name;
    }

    /**
     * Reports the number of completed items in a batch.
     *
     * @param completed completed item count
     * @param total total item count
     */
    public record BatchProgress(int completed, int total) {
    }

    /**
     * Describes one failed batch item.
     *
     * @param message user-facing failure message
     * @param cause underlying failure
     */
    public record BatchFailure(String message, Throwable cause) {
    }

    /**
     * Contains successful outputs, failures, and the processing mode.
     *
     * @param successes generated output paths
     * @param failures failed items
     * @param parallel whether parallel processing was used
     */
    public record BatchResult(List<Path> successes, List<BatchFailure> failures, boolean parallel) {
        public BatchResult {
            successes = List.copyOf(successes);
            failures = List.copyOf(failures);
        }
    }

    /**
     * Contains one prepared image and its detected color count.
     *
     * @param item prepared image item
     * @param svg generated preview path
     * @param detectedColors detected color count
     */
    public record PreprocessedItem(ImageItem item, Path svg, int detectedColors) {
    }

    /**
     * Contains all preprocessing successes and failures.
     *
     * @param successes prepared image results
     * @param failures preprocessing failures
     * @param parallel whether preprocessing used parallel workers
     */
    public record PreprocessResult(
            List<PreprocessedItem> successes,
            List<BatchFailure> failures,
            boolean parallel) {
        public PreprocessResult {
            successes = List.copyOf(successes);
            failures = List.copyOf(failures);
        }
    }
}
