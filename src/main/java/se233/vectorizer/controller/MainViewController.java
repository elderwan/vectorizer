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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
/**
 * Coordinates file loading, settings, previews, navigation, and export.
 */
public class MainViewController {
    private static final Logger LOGGER = LogManager.getLogger(MainViewController.class);

    @FXML
    private MainView mainView;

    private final List<ImageItem> imageItems = new ArrayList<>();
    private final VectorizationService vectorizationService;
    private final BatchProcessor batchProcessor;
    private final ScheduledExecutorService previewScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> createDaemonThread(runnable));
    private final ExecutorService previewExecutor =
            Executors.newSingleThreadExecutor(runnable -> createDaemonThread(runnable));
    private final AtomicLong previewGeneration = new AtomicLong();
    private final AtomicLong dataGeneration = new AtomicLong();
    private ScheduledFuture<?> pendingPreview;
    private Task<Void> currentPreviewTask;
    private final Set<ImageItem> preprocessingItems = new HashSet<>();
    private int currentIndex;

    public MainViewController() {
        this(new VectorizationService());
    }

    /**
     * Creates a controller using the supplied vectorization service.
     *
     * @param vectorizationService service used to convert images
     */
    public MainViewController(VectorizationService vectorizationService) {
        this.vectorizationService = vectorizationService;
        this.batchProcessor = new BatchProcessor(vectorizationService);
    }

    @FXML
    private void initialize() {
        // Connect view events to controller actions when FXML creates the view.
        AllEventHandlers.install(this, mainView);
        mainView.showDropZone();
    }

    /**
     * Loads supported images, displays the editor, and starts preprocessing.
     *
     * @param files files accepted from the user interface
     */
    public void loadFiles(List<File> files) {
        // Validate imports, create ImageItems, then start preview preparation.
        LOGGER.info("Files dropped: {}", files == null ? 0 : files.size());
        try {
            List<File> imageFiles = resolveImageFiles(files);
            if (imageFiles.isEmpty()) {
                showError("No supported JPG or PNG images were found.");
                return;
            }

            List<ImageItem> loadedItems = new ArrayList<>();
            for (File file : imageFiles) {
                if (isAlreadyLoaded(file)) {
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

    /**
     * Handles the [Reset] button action.
     * Cancels all pending conversion tasks, increments generation counters to invalidate
     * outdated worker callbacks, clears the imported image list, and resets the UI back
     * to the initial file drop zone screen.
     */
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

    /**
     * Handles the [Back] button action.
     * Selects and displays the previous imported image in the list.
     * Uses the cached SVG if the same settings were already converted.
     */
    public void showPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            refreshPreview();
            analyzeCurrentImage();
            checkAndSchedulePreview();
        }
    }

    /**
     * Handles the [Next] button action.
     * Selects and displays the next imported image in the list.
     * Uses the cached SVG if the same settings were already converted.
     */
    public void showNext() {
        if (currentIndex < imageItems.size() - 1) {
            currentIndex++;
            refreshPreview();
            analyzeCurrentImage();
            checkAndSchedulePreview();
        }
    }

    private void checkAndSchedulePreview() {
        ImageItem item = imageItems.get(currentIndex);
        Path cached = item.getCachedSvg(item.getSettings());
        if (cached != null && Files.isRegularFile(cached)) {
            item.setVectorizedPath(cached);
            mainView.getPreviewPane().setBusy(false);
            refreshPreview();
        } else {
            schedulePreviewConversion();
        }
    }

    /**
     * Handles settings modification (Detail, Color Mode, Colors Spinner, Background CheckBox).
     * Applies the newly selected settings to the current image and schedules a new debounced preview conversion.
     *
     * @param settings settings selected in the configuration pane
     */
    public void applySettings(ConversionSettings settings) {
        // Save settings for the current image and refresh only when conversion input changed.
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

    /**
     * Handles the [Export All] button action.
     * Displays a native directory chooser dialog, then starts an asynchronous
     * batch vectorization pipeline using BatchProcessor to export all loaded images.
     */
    public void exportAll() {
        // Export every loaded image asynchronously and update progress on the JavaFX thread.
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

        batchProcessor.processAsync(imageItems, settings, directory.toPath(), progress -> {
            Platform.runLater(() -> updateExportProgress(progress));
        }).whenComplete((result, throwable) -> {
            Platform.runLater(() -> handleExportComplete(result, throwable));
        });
    }

    private void updateExportProgress(BatchProcessor.BatchProgress progress) {
        double ratio = (double) progress.completed() / progress.total();
        mainView.getProgressBar().setProgress(ratio);
        mainView.getStatusLabel().setText("Exporting " + progress.completed() + "/" + progress.total());
    }

    private void handleExportComplete(BatchProcessor.BatchResult result, Throwable throwable) {
        mainView.setProcessingVisible(false);
        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            LOGGER.error("Export failed", cause);
            showError(userMessage(cause, "The images could not be exported."));
            return;
        }
        int successCount = result.successes().size();
        int failureCount = result.failures().size();
        LOGGER.info("Export finished: {} succeeded, {} failed", successCount, failureCount);
        showInformation("Export complete: " + successCount + " succeeded, " + failureCount + " failed.");
    }

    private void schedulePreviewConversion() {
        // Debounce rapid setting changes so previews are not converted repeatedly.
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
        Path cached = item.getCachedSvg(settings);
        if (cached != null && Files.isRegularFile(cached)) {
            item.setVectorizedPath(cached);
            mainView.getPreviewPane().setBusy(false);
            refreshPreview();
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Path svg = vectorizationService.convertAsync(item, settings).join();
                item.setVectorizedPath(svg);
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
        previewExecutor.submit(task);
    }

    private void startInitialPreprocessing(List<ImageItem> itemsToProcess) {
        long generation = dataGeneration.get();
        mainView.setProcessingVisible(true);
        mainView.getProgressBar().setProgress(0);
        mainView.getStatusLabel().setText("Preparing previews...");
        mainView.getPreviewPane().setBusy(true);

        batchProcessor.preprocessAsync(itemsToProcess,
                progress -> Platform.runLater(() -> updatePreprocessProgress(generation, progress)),
                prepared -> Platform.runLater(() -> handleItemPreprocessed(generation, prepared))
        ).whenComplete((result, throwable) -> {
            Platform.runLater(() -> handlePreprocessComplete(generation, itemsToProcess, result, throwable));
        });
    }

    private void updatePreprocessProgress(long generation, BatchProcessor.BatchProgress progress) {
        if (generation != dataGeneration.get()) {
            return;
        }
        double ratio = (double) progress.completed() / progress.total();
        mainView.getProgressBar().setProgress(ratio);
        mainView.getStatusLabel().setText("Preparing " + progress.completed() + "/" + progress.total());
    }

    private void handleItemPreprocessed(long generation, BatchProcessor.PreprocessedItem prepared) {
        if (generation != dataGeneration.get()) {
            return;
        }
        preprocessingItems.remove(prepared.item());
        prepared.item().setVectorizedPath(prepared.svg());
        if (!imageItems.isEmpty() && prepared.item() == imageItems.get(currentIndex)) {
            mainView.getConfigPane().setColorLimit(prepared.detectedColors());
            refreshPreview();
        }
    }

    private void handlePreprocessComplete(
            long generation,
            List<ImageItem> itemsToProcess,
            BatchProcessor.PreprocessResult result,
            Throwable throwable) {
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
            Throwable cause = unwrap(throwable);
            LOGGER.error("Initial preprocessing failed", cause);
            showError(userMessage(cause, "The images could not be prepared."));
            return;
        }
        if (!imageItems.isEmpty()) {
            refreshPreview();
            updateColorLimitForCurrent(result);
        }
        if (!result.failures().isEmpty()) {
            LOGGER.error("Some initial previews failed: {}", result.failures().size());
            showError(result.failures().size() + " image(s) could not be prepared.");
        }
    }

    private void updateColorLimitForCurrent(BatchProcessor.PreprocessResult result) {
        ImageItem currentImageItem = imageItems.get(currentIndex);
        for (BatchProcessor.PreprocessedItem successItem : result.successes()) {
            if (successItem.item() == currentImageItem) {
                mainView.getConfigPane().setColorLimit(successItem.detectedColors());
                break;
            }
        }
    }

    private void analyzeCurrentImage() {
        if (imageItems.isEmpty()) {
            return;
        }
        ImageItem item = imageItems.get(currentIndex);
        CompletableFuture.supplyAsync(() -> readAndCountColors(item))
                .thenAccept(colorCount -> {
                    Platform.runLater(() -> applyDetectedColorCount(item, colorCount));
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Unable to analyze image colors", unwrap(throwable));
                    return null;
                });
    }

    private int readAndCountColors(ImageItem item) {
        try {
            BufferedImage image = ImageIO.read(item.getFile());
            if (image == null) {
                throw new IOException("Input image is corrupted: " + item.getFile().getName());
            }
            return ColorAnalyzer.detectColorCount(image);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void applyDetectedColorCount(ImageItem item, int colorCount) {
        LOGGER.debug("Color count detected for {}: {}", item.getFile().getName(), colorCount);
        if (item == imageItems.get(currentIndex)) {
            mainView.getConfigPane().setColorLimit(colorCount);
        }
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

    private boolean isAlreadyLoaded(File targetFile) {
        for (ImageItem item : imageItems) {
            if (sameFile(item.getFile(), targetFile)) {
                return true;
            }
        }
        return false;
    }

    private static Thread createDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "vectorizer-preview");
        thread.setDaemon(true);
        return thread;
    }
    
    private List<File> resolveImageFiles(List<File> files) throws IOException {
        // Accept multiple images or one ZIP, but never a mixture of both.
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() == 1 && FileValidator.isZip(files.get(0))) {
            return ZipHandler.extractImages(files.get(0));
        }
        for (File file : files) {
            if (FileValidator.isZip(file)) {
                throw new UnsupportedFileFormatException("Drop either images or one ZIP file, not both.");
            }
            if (!FileValidator.isSupportedImage(file)) {
                throw new UnsupportedFileFormatException("Only JPG and PNG files are supported.");
            }
        }
        List<File> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort((file1, file2) -> file1.getName().compareToIgnoreCase(file2.getName()));
        return sortedFiles;
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
