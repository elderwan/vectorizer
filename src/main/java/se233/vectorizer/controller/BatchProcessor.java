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
 * Runs one-image conversions sequentially or in parallel according to the
 * assignment's batch trigger condition.
 */
public class BatchProcessor {
    private static final Logger LOGGER = LogManager.getLogger(BatchProcessor.class);

    private final VectorizationService vectorizationService;

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
    public CompletableFuture<PreprocessResult> preprocessAsync(
            List<ImageItem> items,
            Consumer<BatchProgress> progressListener) {
        List<ImageItem> snapshot = List.copyOf(items);
        return CompletableFuture.supplyAsync(() ->
                preprocess(snapshot, progressListener));
    }

    public static boolean shouldProcessInParallel(List<ImageItem> items, ConversionSettings settings) {
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
        ExecutorService executor = parallel
                ? Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
                : Executors.newSingleThreadExecutor();
        try {
            Files.createDirectories(outputDirectory);
            CompletionService<Path> completionService = new ExecutorCompletionService<>(executor);
            for (ImageItem item : items) {
                Callable<Path> task = () -> {
                    Path temporarySvg = vectorizationService.convertAsync(item, settings).join();
                    Path destination = outputDirectory.resolve(
                            stripExtension(item.getFile().getName()) + ".svg");
                    Files.copy(temporarySvg, destination, StandardCopyOption.REPLACE_EXISTING);
                    return destination;
                };
                completionService.submit(task);
            }
            for (int completed = 1; completed <= items.size(); completed++) {
                try {
                    Path result = completionService.take().get();
                    successes.add(result);
                } catch (Exception exception) {
                    Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                            ? exception.getCause() : exception;
                    LOGGER.error("Batch item failed", cause);
                    failures.add(new BatchFailure(cause.getMessage(), cause));
                }
                if (progressListener != null) {
                    progressListener.accept(new BatchProgress(completed, items.size()));
                }
            }
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

    private PreprocessResult preprocess(
            List<ImageItem> items,
            Consumer<BatchProgress> progressListener) {
        LOGGER.info("Preprocessing batch size: {}", items.size());
        List<PreprocessedItem> successes = new ArrayList<>();
        List<BatchFailure> failures = new ArrayList<>();
        if (items.isEmpty()) {
            return new PreprocessResult(successes, failures, false);
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors(), items.size()));
        try {
            CompletionService<PreprocessedItem> completionService =
                    new ExecutorCompletionService<>(executor);
            for (ImageItem item : items) {
                completionService.submit(() -> {
                    BufferedImage image = ImageIO.read(item.getFile());
                    if (image == null) {
                        throw new IOException("Input image is corrupted: "
                                + item.getFile().getName());
                    }
                    int colorCount = ColorAnalyzer.detectColorCount(image);
                    Path svg = vectorizationService
                            .convertAsync(item, item.getSettings()).join();
                    return new PreprocessedItem(item, svg, colorCount);
                });
            }
            for (int completed = 1; completed <= items.size(); completed++) {
                try {
                    successes.add(completionService.take().get());
                } catch (Exception exception) {
                    Throwable cause = unwrap(exception);
                    LOGGER.error("Preprocessing item failed", cause);
                    failures.add(new BatchFailure(cause.getMessage(), cause));
                }
                notifyProgress(progressListener, completed, items.size());
            }
        } finally {
            executor.shutdownNow();
        }
        return new PreprocessResult(successes, failures, items.size() > 1);
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

    public record BatchProgress(int completed, int total) {
    }

    public record BatchFailure(String message, Throwable cause) {
    }

    public record BatchResult(List<Path> successes, List<BatchFailure> failures, boolean parallel) {
        public BatchResult {
            successes = List.copyOf(successes);
            failures = List.copyOf(failures);
        }
    }

    public record PreprocessedItem(ImageItem item, Path svg, int detectedColors) {
    }

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
