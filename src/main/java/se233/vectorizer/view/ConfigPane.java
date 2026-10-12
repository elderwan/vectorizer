package se233.vectorizer.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import se233.vectorizer.model.ConversionSettings;

import java.util.function.Consumer;

public class ConfigPane extends VBox {
    private final Button exportAllButton = new Button("Export All");
    private final Button resetButton = new Button("Reset");
    private final ComboBox<ConversionSettings.DetailLevel> detailComboBox = new ComboBox<>();
    private final ComboBox<ConversionSettings.NamingRule> namingRuleComboBox = new ComboBox<>();
    private final TextField customNameField = new TextField();
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
        getStyleClass().add("config-pane");
        setPadding(new Insets(16));
        setSpacing(12);
        setPrefWidth(240);

        detailComboBox.getItems().setAll(ConversionSettings.DetailLevel.values());
        detailComboBox.setValue(ConversionSettings.DetailLevel.MEDIUM);
        detailComboBox.setMaxWidth(Double.MAX_VALUE);

        namingRuleComboBox.getItems().setAll(ConversionSettings.NamingRule.values());
        namingRuleComboBox.setValue(ConversionSettings.NamingRule.ORIGINAL);
        namingRuleComboBox.setMaxWidth(Double.MAX_VALUE);
        customNameField.setPromptText("Enter custom prefix");
        customNameField.setMaxWidth(Double.MAX_VALUE);
        customNameField.getStyleClass().add("settings-control");
        namingRuleComboBox.getStyleClass().add("settings-control");
        namingRuleComboBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> updateCustomNameVisibility());
        updateCustomNameVisibility();

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

        exportAllButton.getStyleClass().add("primary-button");
        resetButton.getStyleClass().add("secondary-button");
        detailComboBox.getStyleClass().add("settings-control");
        colorsSpinner.getStyleClass().add("settings-control");
        removeBackgroundCheckBox.getStyleClass().add("settings-check");

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(
                exportAllButton,
                resetButton,
                createSection("File naming", new VBox(8,
                        namingRuleComboBox,
                        customNameField)),
                createSection("Detail", detailComboBox),
                createSection("Colors", new VBox(8,
                        new HBox(10, unlimitedRadio, customRadio),
                        colorsSpinner)),
                createSection("Background", removeBackgroundCheckBox),
                spacer,
                createSection("Navigation", createNavigation()));
    }

    public ConversionSettings readSettings() {
        ConversionSettings settings = new ConversionSettings();
        settings.setDetailLevel(detailComboBox.getValue());
        settings.setColorMode(customRadio.isSelected()
                ? ConversionSettings.ColorMode.CUSTOM
                : ConversionSettings.ColorMode.UNLIMITED);
        settings.setCustomColors(colorsSpinner.getValue());
        settings.setRemoveBackground(removeBackgroundCheckBox.isSelected());
        settings.setNamingRule(namingRuleComboBox.getValue());
        settings.setCustomName(customNameField.getText());
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
            namingRuleComboBox.setValue(settings.getNamingRule());
            customNameField.setText(settings.getCustomName());
            updateCustomNameVisibility();
        } finally {
            updating = false;
        }
    }

    public void setOnExportAll(Runnable action) {
        exportAllButton.setOnAction(event -> action.run());
    }

    public void setOnReset(Runnable action) {
        resetButton.setOnAction(event -> action.run());
    }

    public void setOnBack(Runnable action) {
        backButton.setOnAction(event -> action.run());
    }

    public void setOnNext(Runnable action) {
        nextButton.setOnAction(event -> action.run());
    }

    public void setOnSettingsChanged(Consumer<ConversionSettings> action) {
        detailComboBox.valueProperty().addListener((observable, oldValue, newValue) -> notifySettingsChanged(action));
        namingRuleComboBox.valueProperty().addListener((observable, oldValue, newValue) ->
                notifySettingsChanged(action));
        customNameField.textProperty().addListener((observable, oldValue, newValue) ->
                notifySettingsChanged(action));
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

    private void updateCustomNameVisibility() {
        boolean customNameRequired = namingRuleComboBox.getValue()
                != ConversionSettings.NamingRule.ORIGINAL;
        customNameField.setVisible(customNameRequired);
        customNameField.setManaged(customNameRequired);
    }

    private HBox createNavigation() {
        HBox navigation = new HBox(8, backButton, nextButton);
        navigation.getStyleClass().add("navigation-box");
        backButton.getStyleClass().add("secondary-button");
        nextButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);
        nextButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(backButton, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(nextButton, javafx.scene.layout.Priority.ALWAYS);
        return navigation;
    }

    private static VBox createSection(String title, javafx.scene.Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        VBox section = new VBox(7, titleLabel, content);
        section.getStyleClass().add("config-section");
        return section;
    }
}
