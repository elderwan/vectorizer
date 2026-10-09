package se233.vectorizer.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

import java.nio.file.Files;
import java.nio.file.Path;

public class PreviewPane extends BorderPane {
    private static final double ZOOM_IN_FACTOR = 1.2;
    private static final double ZOOM_OUT_FACTOR = 0.8;
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 10.0;

    private final ImageView originalImageView = createImageView();
    private final WebView vectorizedWebView = new WebView();
    private final StackPane originalContent = new StackPane(originalImageView);
    private final StackPane vectorizedContent = new StackPane(vectorizedWebView);
    private final ScrollPane originalScrollPane = createScrollPane(originalContent);
    private final ScrollPane vectorizedScrollPane = createScrollPane(vectorizedContent);
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private double scale = 1.0;

    public PreviewPane() {
        setPadding(new Insets(12));
        vectorizedWebView.setContextMenuEnabled(false);
        vectorizedWebView.setPrefSize(800, 600);
        vectorizedWebView.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        SplitPane splitPane = new SplitPane(
                createPreviewPanel("Original Image", originalScrollPane),
                createPreviewPanel("Vectorized Result", vectorizedScrollPane));
        splitPane.setDividerPositions(0.5);

        StackPane previewStack = new StackPane(splitPane, progressIndicator);
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(48, 48);
        setCenter(previewStack);
    }

    public void setImages(Image original, Path vectorizedSvg) {
        originalImageView.setImage(original);
        setImageSize(originalImageView, original);
        loadSvg(vectorizedSvg);
        scale = 1.0;
        Platform.runLater(this::zoomToFit);
    }

    public void zoomIn() {
        setScale(scale * ZOOM_IN_FACTOR);
    }

    public void zoomOut() {
        setScale(scale * ZOOM_OUT_FACTOR);
    }

    public void zoomToFit() {
        Image image = originalImageView.getImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            setScale(1.0);
            return;
        }
        double width = originalScrollPane.getViewportBounds().getWidth() - 24;
        double height = originalScrollPane.getViewportBounds().getHeight() - 24;
        if (width <= 0 || height <= 0) {
            setScale(1.0);
            return;
        }
        setScale(clamp(Math.min(width / image.getWidth(), height / image.getHeight()),
                MIN_SCALE, MAX_SCALE));
    }

    public void setBusy(boolean busy) {
        progressIndicator.setVisible(busy);
    }

    public ProgressIndicator getProgressIndicator() {
        return progressIndicator;
    }

    private void loadSvg(Path svg) {
        if (svg != null && Files.isRegularFile(svg)) {
            vectorizedWebView.getEngine().load(svg.toUri().toString());
        } else {
            vectorizedWebView.getEngine().loadContent(
                    "<html><body><p>Vectorized preview unavailable</p></body></html>");
        }
    }

    private void setScale(double requestedScale) {
        scale = clamp(requestedScale, MIN_SCALE, MAX_SCALE);
        resizeImage(originalImageView, scale);
        vectorizedWebView.setZoom(scale);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static ImageView createImageView() {
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }

    private static ScrollPane createScrollPane(StackPane content) {
        content.setAlignment(Pos.CENTER);
        content.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        return scrollPane;
    }

    private static void setImageSize(ImageView imageView, Image image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            imageView.setFitWidth(0);
            imageView.setFitHeight(0);
            return;
        }
        imageView.setFitWidth(image.getWidth());
        imageView.setFitHeight(image.getHeight());
    }

    private static void resizeImage(ImageView imageView, double scale) {
        Image image = imageView.getImage();
        if (image != null) {
            imageView.setFitWidth(image.getWidth() * scale);
            imageView.setFitHeight(image.getHeight() * scale);
        }
    }

    private static VBox createPreviewPanel(String title, ScrollPane content) {
        VBox panel = new VBox(8, new Label(title), content);
        panel.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);
        return panel;
    }
}
