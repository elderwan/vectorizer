package se233.vectorizer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class Launcher extends Application {
    private static final Logger LOGGER = LogManager.getLogger(Launcher.class);

    @Override
    public void start(Stage stage) {
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

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            LOGGER.error("Uncaught exception on thread {}", thread.getName(), exception);
            javafx.application.Platform.runLater(() ->
                    new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR,
                            exception.getMessage() == null
                                    ? "An unexpected error occurred."
                                    : exception.getMessage(),
                            javafx.scene.control.ButtonType.OK).showAndWait());
        });
        launch(args);
    }
}
