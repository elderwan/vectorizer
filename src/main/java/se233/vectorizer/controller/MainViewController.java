package se233.vectorizer.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se233.vectorizer.exception.CliToolNotFoundException;
import se233.vectorizer.exception.UnsupportedFileFormatException;
import se233.vectorizer.model.ConversionSettings;
import se233.vectorizer.model.ImageItem;
import se233.vectorizer.model.VectorizationService;
import se233.vectorizer.util.ColorAnalyzer;
import se233.vectorizer.util.FileValidator;
import se233.vectorizer.util.ZipHandler;
import se233.vectorizer.view.MainView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletionException;

/**
 * Coordinates file loading, asynchronous previews, navigation, and export.
 */
public class MainViewController {
    private static final Logger LOGGER = LogManager.getLogger(MainViewController.class);

    @FXML
    private MainView mainView;

    private final List<ImageItem> imageItems = new ArrayList<>();
    private final VectorizationService vectorizationService;
    private final BatchProcessor batchProcessor;
    private final ScheduledExecutorService previewScheduler =
            Executors.newSingleThreadScheduledExecutor(MainViewController::daemonThread);
    private final ExecutorService previewExecutor =
            Executors.newSingleThreadExecutor(MainViewController::daemonThread);
    private final AtomicLong previewGeneration = new AtomicLong();
    private final AtomicLong dataGeneration = new AtomicLong();
    private ScheduledFuture<?> pendingPreview;
    private Task<Void> currentPreviewTask;
    private final Set<ImageItem> preprocessingItems = new HashSet<>();
    private int currentIndex;

    public MainViewController() {
        this(new VectorizationService());
    }

    public MainViewController(VectorizationService vectorizationService) {
        this.vectorizationService = vectorizationService;
        this.batchProcessor = new BatchProcessor(vectorizationService);
    }

    @FXML
    private void initialize() {
        AllEventHandlers.install(this, mainView);
        mainView.showDropZone();
    }

    public void loadFiles(List<File> files) {
        LOGGER.info("Files dropped: {}", files == null ? 0 : files.size());
        try {
            List<File> imageFiles = resolveImageFiles(files);
            if (imageFiles.isEmpty()) {
                showError("No supported JPG or PNG images were found.");
                return;
            }

            List<ImageItem> loadedItems = new ArrayList<>();
            for (File file : imageFiles) {
                if (imageItems.stream().anyMatch(item -> sameFile(item.getFile(), file))) {
                    LOGGER.info("Skipping already loaded image: {}", file.getName());
                    continue;
                }
                ImageItem item = new ImageItem(file);
                loadedItems.add(item);
                LOGGER.info("Image loaded: {}", file.getName());
            }
            if (loadedItems.isEmpty()) {
                showError("The selected images are already loaded.");
                return;
            }
            boolean firstLoad = imageItems.isEmpty();
            imageItems.addAll(loadedItems);
            preprocessingItems.addAll(loadedItems);
            if (firstLoad) {
                currentIndex = 0;
            }
            prepareInitialSettings(loadedItems);
            mainView.showEditor();
            refreshPreview();
            startInitialPreprocessing(loadedItems);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Unable to load selected files", exception);
            showError(userMessage(exception, "The selected files could not be loaded."));
        }
    }

    public void resetAll() {
        LOGGER.info("Resetting all loaded images");
        previewGeneration.incrementAndGet();
        dataGeneration.incrementAndGet();
        if (pendingPreview != null) {
            pendingPreview.cancel(false);
            pendingPreview = null;
        }
        if (currentPreviewTask != null && currentPreviewTask.isRunning()) {
            currentPreviewTask.cancel();
        }
        imageItems.clear();
        preprocessingItems.clear();
        currentIndex = 0;
        mainView.getPreviewPane().setBusy(false);
        mainView.setProcessingVisible(false);
        mainView.showDropZone();
    }

    public void showPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            refreshPreview();
            analyzeCurrentImage();
            schedulePreviewConversion();
        }
    }

    public void showNext() {
        if (currentIndex < imageItems.size() - 1) {
            currentIndex++;
            refreshPreview();
            analyzeCurrentImage();
            schedulePreviewConversion();
        }
    }

    public void applySettings(ConversionSettings settings) {
        if (imageItems.isEmpty()) {
            return;
        }
        ImageItem item = imageItems.get(currentIndex);
        ConversionSettings previous = copyOf(item.getSettings());
        copySettings(settings, item.getSettings());
        LOGGER.debug("Settings changed: detail={}, colors={}, customColors={}, removeBackground={}",
                settings.getDetailLevel(), settings.getColorMode(), settings.getCustomColors(),
                settings.isRemoveBackground());
        if (vectorizationSettingsChanged(previous, settings)) {
            schedulePreviewConversion();
        }
    }

    public void exportAll() {
        if (imageItems.isEmpty()) {
            return;
        }
        ConversionSettings settings = copyOf(imageItems.get(currentIndex).getSettings());
        if (usesCustomNaming(settings) && settings.getCustomName().isBlank()) {
            showError("Enter a custom prefix or name before exporting.");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose an export folder");
        File directory = chooser.showDialog(getWindow());
        if (directory == null) {
            return;
        }

        mainView.setProcessingVisible(true);
        mainView.getProgressBar().setProgress(0);
        mainView.getStatusLabel().setText("Exporting...");
        LOGGER.info("Export started: {} image(s)", imageItems.size());

        batchProcessor.processAsync(imageItems, settings, directory.toPath(), progress ->
                Platform.runLater(() -> {
                    mainView.getProgressBar().setProgress(
                            (double) progress.completed() / progress.total());
                    mainView.getStatusLabel().setText(
                            "Exporting " + progress.completed() + "/" + progress.total());
                })).whenComplete((result, throwable) -> Platform.runLater(() -> {
            mainView.setProcessingVisible(false);
            if (throwable != null) {
                LOGGER.error("Export failed", unwrap(throwable));
                showError(userMessage(unwrap(throwable), "The images could not be exported."));
                return;
            }
            LOGGER.info("Export finished: {} succeeded, {} failed",
                    result.successes().size(), result.failures().size());
            showInformation("Export complete: " + result.successes().size()
                    + " succeeded, " + result.failures().size() + " failed.");
        }));
    }

    private void schedulePreviewConversion() {
        if (imageItems.isEmpty()) {
            return;
        }
        if (pendingPreview != null) {
            pendingPreview.cancel(false);
        }
        long generation = previewGeneration.incrementAndGet();
        pendingPreview = previewScheduler.schedule(
                () -> Platform.runLater(() -> startPreviewConversion(generation)),
                300, TimeUnit.MILLISECONDS);
    }

    private void startPreviewConversion(long generation) {
        if (generation != previewGeneration.get() || imageItems.isEmpty()) {
            return;
        }
        if (currentPreviewTask != null && currentPreviewTask.isRunning()) {
            currentPreviewTask.cancel();
        }
        ImageItem item = imageItems.get(currentIndex);
        if (preprocessingItems.contains(item)) {
            return;
        }
        ConversionSettings settings = copyOf(item.getSettings());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                updateProgress(0, 1);
                Path svg = vectorizationService.convertAsync(item, settings).join();
                item.setVectorizedPath(svg);
                updateProgress(1, 1);
                return null;
            }
        };
        currentPreviewTask = task;
        mainView.getPreviewPane().setBusy(true);
        task.setOnSucceeded(event -> {
            if (generation == previewGeneration.get() && item == imageItems.get(currentIndex)) {
                mainView.getPreviewPane().setBusy(false);
                refreshPreview();
            }
        });
        task.setOnFailed(event -> {
            if (generation == previewGeneration.get()) {
                mainView.getPreviewPane().setBusy(false);
                Throwable exception = unwrap(task.getException());
                LOGGER.error("Preview vectorization failed", exception);
                showError(userMessage(exception, "The image could not be vectorized."));
            }
        });
        task.setOnCancelled(event -> mainView.getPreviewPane().setBusy(false));
        if (mainView.getPreviewPane().getProgressIndicator().progressProperty().isBound()) {
            mainView.getPreviewPane().getProgressIndicator().progressProperty().unbind();
        }
        mainView.getPreviewPane().getProgressIndicator().progressProperty()
                .bind(task.progressProperty());
        previewExecutor.submit(task);
    }

    private void startInitialPreprocessing(List<ImageItem> itemsToProcess) {
        long generation = dataGeneration.get();
        mainView.setProcessingVisible(true);
        mainView.getProgressBar().setProgress(0);
        mainView.getStatusLabel().setText("Preparing previews...");
        mainView.getPreviewPane().setBusy(true);

        batchProcessor.preprocessAsync(itemsToProcess, progress ->
                Platform.runLater(() -> {
                    if (generation != dataGeneration.get()) {
                        return;
                    }
                    mainView.getProgressBar().setProgress(
                            (double) progress.completed() / progress.total());
                    mainView.getStatusLabel().setText(
                            "Preparing " + progress.completed() + "/" + progress.total());
                }), prepared -> Platform.runLater(() -> {
                    if (generation != dataGeneration.get()) {
                        return;
                    }
                    preprocessingItems.remove(prepared.item());
                    prepared.item().setVectorizedPath(prepared.svg());
                    if (!imageItems.isEmpty()
                            && prepared.item() == imageItems.get(currentIndex)) {
                        mainView.getConfigPane().setColorLimit(prepared.detectedColors());
                        refreshPreview();
                    }
                })).whenComplete((result, throwable) -> Platform.runLater(() -> {
            if (generation != dataGeneration.get()) {
                return;
            }
            for (ImageItem item : itemsToProcess) {
                preprocessingItems.remove(item);
            }
            boolean preprocessingStillRunning = !preprocessingItems.isEmpty();
            mainView.setProcessingVisible(preprocessingStillRunning);
            mainView.getPreviewPane().setBusy(preprocessingStillRunning);
            if (throwable != null) {
                LOGGER.error("Initial preprocessing failed", unwrap(throwable));
                showError(userMessage(unwrap(throwable),
                        "The images could not be prepared."));
                return;
            }
            if (!imageItems.isEmpty()) {
                refreshPreview();
                BatchProcessor.PreprocessedItem current = result.successes().stream()
                        .filter(item -> item.item() == imageItems.get(currentIndex))
                        .findFirst()
                        .orElse(null);
                if (current != null) {
                    mainView.getConfigPane().setColorLimit(current.detectedColors());
                }
            }
            if (!result.failures().isEmpty()) {
                LOGGER.error("Some initial previews failed: {}", result.failures().size());
                showError(result.failures().size()
                        + " image(s) could not be prepared.");
            }
        }));
    }

    private void analyzeCurrentImage() {
        if (imageItems.isEmpty()) {
            return;
        }
        ImageItem item = imageItems.get(currentIndex);
        CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage image = ImageIO.read(item.getFile());
                if (image == null) {
                    throw new IOException("Input image is corrupted.");
                }
                return ColorAnalyzer.detectColorCount(image);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenAccept(colorCount -> Platform.runLater(() -> {
            LOGGER.debug("Color count detected for {}: {}", item.getFile().getName(), colorCount);
            if (item == imageItems.get(currentIndex)) {
                mainView.getConfigPane().setColorLimit(colorCount);
            }
        })).exceptionally(throwable -> {
            LOGGER.error("Unable to analyze image colors", unwrap(throwable));
            return null;
        });
    }

    private void prepareInitialSettings(List<ImageItem> items) {
        for (ImageItem item : items) {
            ConversionSettings settings = item.getSettings();
            settings.setDetailLevel(ConversionSettings.DetailLevel.MEDIUM);
            settings.setColorMode(ConversionSettings.ColorMode.CUSTOM);
            settings.setCustomColors(2);
            settings.setRemoveBackground(false);
            settings.setNamingRule(ConversionSettings.NamingRule.ORIGINAL);
            settings.setCustomName("");
        }
    }

    private void refreshPreview() {
        if (imageItems.isEmpty()) {
            return;
        }
        ImageItem item = imageItems.get(currentIndex);
        mainView.getPreviewPane().setImages(item.getOriginalImage(), item.getVectorizedPath());
        mainView.getConfigPane().showSettings(item.getSettings());
        mainView.getConfigPane().setNavigationDisabled(imageItems.size() <= 1);
    }

    private static Thread daemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "vectorizer-preview");
        thread.setDaemon(true);
        return thread;
    }

    private List<File> resolveImageFiles(List<File> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() == 1 && FileValidator.isZip(files.get(0))) {
            return ZipHandler.extractImages(files.get(0));
        }
        if (files.stream().anyMatch(FileValidator::isZip)) {
            throw new UnsupportedFileFormatException("Drop either images or one ZIP file, not both.");
        }
        if (files.stream().anyMatch(file -> !FileValidator.isSupportedImage(file))) {
            throw new UnsupportedFileFormatException("Only JPG and PNG files are supported.");
        }
        return files.stream()
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static ConversionSettings copyOf(ConversionSettings source) {
        ConversionSettings copy = new ConversionSettings();
        copySettings(source, copy);
        return copy;
    }

    private static void copySettings(ConversionSettings source, ConversionSettings target) {
        target.setDetailLevel(source.getDetailLevel());
        target.setColorMode(source.getColorMode());
        target.setCustomColors(source.getCustomColors());
        target.setRemoveBackground(source.isRemoveBackground());
        target.setNamingRule(source.getNamingRule());
        target.setCustomName(source.getCustomName());
    }

    private static boolean sameFile(File first, File second) {
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (IOException exception) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
    }

    private static boolean usesCustomNaming(ConversionSettings settings) {
        return settings.getNamingRule() != ConversionSettings.NamingRule.ORIGINAL;
    }

    private static boolean vectorizationSettingsChanged(
            ConversionSettings previous,
            ConversionSettings current) {
        return previous.getDetailLevel() != current.getDetailLevel()
                || previous.getColorMode() != current.getColorMode()
                || previous.getCustomColors() != current.getCustomColors()
                || previous.isRemoveBackground() != current.isRemoveBackground();
    }

    private Window getWindow() {
        return mainView.getScene() == null ? null : mainView.getScene().getWindow();
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    private void showInformation(String message) {
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait();
    }

    private static String userMessage(Throwable exception, String fallback) {
        if (exception instanceof CliToolNotFoundException) {
            return "Potrace CLI not found. Please install it and add it to PATH.";
        }
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback : exception.getMessage();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable result = throwable;
        while ((result instanceof CompletionException
                || result instanceof java.util.concurrent.ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
