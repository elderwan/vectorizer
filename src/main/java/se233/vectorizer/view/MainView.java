package se233.vectorizer.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
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
        dropZonePane = new DropZonePane(files -> {
            if (onFilesAccepted != null) {
                onFilesAccepted.accept(files);
            }
        });

        Button zoomIn = new Button("Zoom In");
        Button zoomOut = new Button("Zoom Out");
        Button zoomToFit = new Button("Zoom to Fit");
        zoomIn.setOnAction(event -> previewPane.zoomIn());
        zoomOut.setOnAction(event -> previewPane.zoomOut());
        zoomToFit.setOnAction(event -> previewPane.zoomToFit());

        ToolBar zoomToolbar = new ToolBar(zoomIn, zoomOut, zoomToFit);
        zoomToolbar.setPadding(new Insets(10));

        editorPane.setTop(zoomToolbar);
        editorPane.setCenter(previewPane);
        editorPane.setRight(configPane);
        progressBar.setVisible(false);
        statusLabel.setVisible(false);
        BorderPane statusBar = new BorderPane();
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
