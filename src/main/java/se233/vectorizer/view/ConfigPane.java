package se233.vectorizer.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import se233.vectorizer.model.ConversionSettings;

import java.util.function.Consumer;

public class ConfigPane extends VBox {
    private final Button exportAllButton = new Button("Export All");
    private final ComboBox<ConversionSettings.DetailLevel> detailComboBox = new ComboBox<>();
    private final RadioButton unlimitedRadio = new RadioButton("Unlimited");
    private final RadioButton customRadio = new RadioButton("Custom");
    private final Spinner<Integer> colorsSpinner = new Spinner<>(
            2, ConversionSettings.MAX_CUSTOM_COLORS, 2);
    private final SpinnerValueFactory.IntegerSpinnerValueFactory colorsValueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    2, ConversionSettings.MAX_CUSTOM_COLORS, 2);
    private final CheckBox removeBackgroundCheckBox = new CheckBox("Remove background");
    private final Button backButton = new Button("Back");
    private final Button nextButton = new Button("Next");
    private boolean updating;

    public ConfigPane() {
        setPadding(new Insets(16));
        setSpacing(14);
        setPrefWidth(240);

        detailComboBox.getItems().setAll(ConversionSettings.DetailLevel.values());
        detailComboBox.setValue(ConversionSettings.DetailLevel.MEDIUM);
        detailComboBox.setMaxWidth(Double.MAX_VALUE);

        ToggleGroup colorGroup = new ToggleGroup();
        unlimitedRadio.setToggleGroup(colorGroup);
        customRadio.setToggleGroup(colorGroup);
        customRadio.setSelected(true);
        colorsSpinner.setEditable(false);
        colorsSpinner.setValueFactory(colorsValueFactory);
        colorsSpinner.setDisable(false);

        customRadio.selectedProperty().addListener((observable, oldValue, selected) -> {
            colorsSpinner.setDisable(!selected);
        });

        getChildren().addAll(
                exportAllButton,
                new Label("Detail level"),
                detailComboBox,
                new Label("Colors"),
                new HBox(10, unlimitedRadio, customRadio),
                colorsSpinner,
                removeBackgroundCheckBox,
                new VBox(),
                createNavigation());
        VBox.setVgrow(getChildren().get(7), javafx.scene.layout.Priority.ALWAYS);
    }

    public ConversionSettings readSettings() {
        ConversionSettings settings = new ConversionSettings();
        settings.setDetailLevel(detailComboBox.getValue());
        settings.setColorMode(customRadio.isSelected()
                ? ConversionSettings.ColorMode.CUSTOM
                : ConversionSettings.ColorMode.UNLIMITED);
        settings.setCustomColors(colorsSpinner.getValue());
        settings.setRemoveBackground(removeBackgroundCheckBox.isSelected());
        return settings;
    }

    public void showSettings(ConversionSettings settings) {
        updating = true;
        try {
            detailComboBox.setValue(settings.getDetailLevel());
            customRadio.setSelected(settings.getColorMode() == ConversionSettings.ColorMode.CUSTOM);
            unlimitedRadio.setSelected(settings.getColorMode() == ConversionSettings.ColorMode.UNLIMITED);
            colorsSpinner.getValueFactory().setValue(settings.getCustomColors());
            colorsSpinner.setDisable(settings.getColorMode() != ConversionSettings.ColorMode.CUSTOM);
            removeBackgroundCheckBox.setSelected(settings.isRemoveBackground());
        } finally {
            updating = false;
        }
    }

    public void setOnExportAll(Runnable action) {
        exportAllButton.setOnAction(event -> action.run());
    }

    public void setOnBack(Runnable action) {
        backButton.setOnAction(event -> action.run());
    }

    public void setOnNext(Runnable action) {
        nextButton.setOnAction(event -> action.run());
    }

    public void setOnSettingsChanged(Consumer<ConversionSettings> action) {
        detailComboBox.valueProperty().addListener((observable, oldValue, newValue) -> notifySettingsChanged(action));
        unlimitedRadio.selectedProperty().addListener((observable, oldValue, newValue) -> notifySettingsChanged(action));
        customRadio.selectedProperty().addListener((observable, oldValue, newValue) -> notifySettingsChanged(action));
        colorsSpinner.getValueFactory().valueProperty()
                .addListener((observable, oldValue, newValue) -> notifySettingsChanged(action));
        removeBackgroundCheckBox.selectedProperty()
                .addListener((observable, oldValue, newValue) -> notifySettingsChanged(action));
    }

    public void setNavigationDisabled(boolean disabled) {
        backButton.setDisable(disabled);
        nextButton.setDisable(disabled);
    }

    public void setColorLimit(int detectedColors) {
        int maximum = Math.max(2, Math.min(
                ConversionSettings.MAX_CUSTOM_COLORS, detectedColors));
        int current = Math.max(2, Math.min(maximum, colorsSpinner.getValue()));
        colorsValueFactory.setMax(maximum);
        colorsValueFactory.setValue(current);
    }

    private void notifySettingsChanged(Consumer<ConversionSettings> action) {
        if (!updating) {
            action.accept(readSettings());
        }
    }

    private HBox createNavigation() {
        HBox navigation = new HBox(8, backButton, nextButton);
        backButton.setMaxWidth(Double.MAX_VALUE);
        nextButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(backButton, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(nextButton, javafx.scene.layout.Priority.ALWAYS);
        return navigation;
    }
}
