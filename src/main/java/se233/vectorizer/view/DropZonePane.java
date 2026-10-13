package se233.vectorizer.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * UI area that accepts image files or a ZIP file through drag and drop.
 */
public class DropZonePane extends VBox {
    private final Consumer<List<File>> filesConsumer;

    public DropZonePane(Consumer<List<File>> filesConsumer) {
        // Keep file handling outside the view; the controller receives accepted files.
        this.filesConsumer = filesConsumer;
        getStyleClass().add("drop-zone");
        setAlignment(Pos.CENTER);
        setSpacing(12);

        Label icon = new Label("\u25a7");
        icon.getStyleClass().add("drop-zone-icon");
        Label message = new Label("Drag file(s) here");
        message.getStyleClass().add("drop-zone-message");
        getChildren().addAll(icon, message);

        setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragEntered(event -> {
            event.consume();
        });
        setOnDragExited(event -> {
            event.consume();
        });
        setOnDragDropped(event -> {
            // Forward dropped files and report whether the drop was accepted.
            Dragboard board = event.getDragboard();
            boolean accepted = board.hasFiles();
            if (accepted) {
                filesConsumer.accept(board.getFiles());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }
}
