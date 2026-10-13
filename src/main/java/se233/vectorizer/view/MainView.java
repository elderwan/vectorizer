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

public class MainView extends BorderPane {
    private final StackPane contentStack = new StackPane();
    private final DropZonePane dropZonePane;
    private final PreviewPane previewPane = new PreviewPane();
    private final ConfigPane configPane = new ConfigPane();
    private final BorderPane editorPane = new BorderPane();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label();

    public MainView() {
        getStyleClass().add("main-view");
        editorPane.getStyleClass().add("editor-pane");

        dropZonePane = new DropZonePane(files -> {
            if (onFilesAccepted != null) {
                onFilesAccepted.accept(files);
            }
        });

        Button zoomIn = createZoomButton("＋", "Zoom In", previewPane::zoomIn);
        Button zoomOut = createZoomButton("－", "Zoom Out", previewPane::zoomOut);
        Button zoomToFit = createZoomButton("⌂", "Zoom to Fit", previewPane::zoomToFit);

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

    public void setOnFilesAccepted(java.util.function.Consumer<List<File>> action) {
        onFilesAccepted = action;
    }

    private Button createZoomButton(String symbol, String label, Runnable action) {
        Button button = new Button(symbol + "  " + label);
        button.getStyleClass().add("toolbar-button");
        button.setOnAction(event -> action.run());
        button.setFocusTraversable(false);
        return button;
    }

    public void showDropZone() {
        contentStack.getChildren().setAll(dropZonePane);
    }

    public void showEditor() {
        contentStack.getChildren().setAll(editorPane);
    }

    public PreviewPane getPreviewPane() {
        return previewPane;
    }

    public ConfigPane getConfigPane() {
        return configPane;
    }

    public ProgressBar getProgressBar() {
        return progressBar;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public void setProcessingVisible(boolean visible) {
        progressBar.setVisible(visible);
        statusLabel.setVisible(visible);
    }
}
