package se233.vectorizer.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.util.List;

/**
 * Main application view containing the drop zone, editor, preview, and status bar.
 */
/**
 * Builds the main application layout and exposes its reusable UI components.
 */
public class MainView extends BorderPane {
    private final StackPane contentStack = new StackPane();
    private final DropZonePane dropZonePane;
    private final PreviewPane previewPane = new PreviewPane();
    private final ConfigPane configPane = new ConfigPane();
    private final BorderPane editorPane = new BorderPane();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label();

    public MainView() {
        // Compose the main layout and wire drag-and-drop/zoom behavior.
        getStyleClass().add("main-view");
        editorPane.getStyleClass().add("editor-pane");

            dropZonePane = new DropZonePane(files -> {
            if (onFilesAccepted != null) {
                onFilesAccepted.accept(files);
            }
        });

        Button zoomIn = createZoomButton("＋", "Zoom In", event -> previewPane.zoomIn());
        Button zoomOut = createZoomButton("－", "Zoom Out", event -> previewPane.zoomOut());
        Button zoomToFit = createZoomButton("⌂", "Zoom to Fit", event -> previewPane.zoomToFit());

        ToolBar zoomToolbar = new ToolBar(zoomIn, zoomOut, zoomToFit);
        zoomToolbar.getStyleClass().add("zoom-toolbar");
        zoomToolbar.setPadding(new Insets(8, 12, 8, 12));
        zoomToolbar.setFocusTraversable(false);

        editorPane.setTop(zoomToolbar);
        editorPane.setCenter(previewPane);
        editorPane.setRight(configPane);
        editorPane.addEventFilter(javafx.scene.input.DragEvent.DRAG_OVER, event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        editorPane.addEventFilter(javafx.scene.input.DragEvent.DRAG_DROPPED, event -> {
            Dragboard board = event.getDragboard();
            if (board.hasFiles()) {
                if (onFilesAccepted != null) {
                    onFilesAccepted.accept(board.getFiles());
                }
                event.setDropCompleted(true);
                event.consume();
            }
        });
        progressBar.setVisible(false);
        statusLabel.setVisible(false);
        statusLabel.getStyleClass().add("status-label");
        progressBar.getStyleClass().add("status-progress");
        BorderPane statusBar = new BorderPane();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setLeft(statusLabel);
        statusBar.setRight(progressBar);
        statusBar.setPadding(new Insets(6, 10, 6, 10));
        editorPane.setBottom(statusBar);

        contentStack.getChildren().add(dropZonePane);
        setCenter(contentStack);
        showDropZone();
    }

    private java.util.function.Consumer<List<File>> onFilesAccepted;

    /**
     * Registers the callback used when files are accepted by the drop area.
     *
     * @param action callback that receives the accepted files
     */
    public void setOnFilesAccepted(java.util.function.Consumer<List<File>> action) {
        onFilesAccepted = action;
    }

    private Button createZoomButton(String symbol, String label, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        // Create reusable buttons so all zoom controls share the same setup.
        Button button = new Button(symbol + "  " + label);
        button.getStyleClass().add("toolbar-button");
        button.setOnAction(action);
        button.setFocusTraversable(false);
        return button;
    }

    /**
     * Shows the initial file drop screen.
     */
    public void showDropZone() {
        // Show the initial screen before any image has been loaded.
        contentStack.getChildren().setAll(dropZonePane);
    }

    /**
     * Shows the editor containing preview and configuration controls.
     */
    public void showEditor() {
        // Replace the drop zone with the image editor after import.
        contentStack.getChildren().setAll(editorPane);
    }

    /**
     * Returns the preview component.
     *
     * @return preview pane used by the editor
     */
    public PreviewPane getPreviewPane() {
        return previewPane;
    }

    /**
     * Returns the settings component.
     *
     * @return configuration pane used by the editor
     */
    public ConfigPane getConfigPane() {
        return configPane;
    }

    /**
     * Returns the processing progress bar.
     *
     * @return progress bar shown in the status area
     */
    public ProgressBar getProgressBar() {
        return progressBar;
    }

    /**
     * Returns the processing status label.
     *
     * @return status label shown in the status area
     */
    public Label getStatusLabel() {
        return statusLabel;
    }

    /**
     * Shows or hides the processing controls.
     *
     * @param visible whether the status controls should be visible
     */
    public void setProcessingVisible(boolean visible) {
        progressBar.setVisible(visible);
        statusLabel.setVisible(visible);
    }
}
