package se233.vectorizer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Starts the JavaFX application, loads the main view, and applies the UI theme.
 */
public class Launcher extends Application {
    private static final Logger LOGGER = LogManager.getLogger(Launcher.class);

    /**
     * Creates and displays the primary application window.
     *
     * @param stage JavaFX stage used as the application window
     */
    @Override
    public void start(Stage stage) {
        // Build the scene from FXML, attach CSS, and show the application window.
        try {
            FXMLLoader loader = new FXMLLoader(
                    Launcher.class.getResource("/se233/vectorizer/main-view.fxml"));
            Scene scene = new Scene(loader.load(), 1280, 760);
            scene.getRoot().getStyleClass().add("app-root");
            String stylesheet = Launcher.class
                    .getResource("/se233/vectorizer/app.css")
                    .toExternalForm();
            scene.getStylesheets().add(stylesheet);

            stage.setTitle("Vectorizer");
            stage.setMinWidth(960);
            stage.setMinHeight(600);
            stage.setScene(scene);
            stage.show();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Unable to start the application", exception);
            throw new IllegalStateException("Unable to start the application", exception);
        }
    }

    /**
     * Starts the JavaFX application and installs the global error handler.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> handleUncaughtException(thread, exception));
        launch(args);
    }

    private static void handleUncaughtException(Thread thread, Throwable exception) {
        LOGGER.error("Uncaught exception on thread {}", thread.getName(), exception);
        javafx.application.Platform.runLater(() -> {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = "An unexpected error occurred.";
            }
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    message,
                    javafx.scene.control.ButtonType.OK
            );
            alert.showAndWait();
        });
    }
}
