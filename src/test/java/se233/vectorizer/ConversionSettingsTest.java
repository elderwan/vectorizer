package se233.vectorizer;

import org.junit.jupiter.api.Test;
import se233.vectorizer.model.ConversionSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ConversionSettingsTest {
    @Test
    void hasRequiredPhaseTwoDefaults() {
        ConversionSettings settings = new ConversionSettings();

        assertEquals(ConversionSettings.DetailLevel.MEDIUM, settings.getDetailLevel());
        assertEquals(ConversionSettings.ColorMode.CUSTOM, settings.getColorMode());
        assertEquals(2, settings.getCustomColors());
        assertFalse(settings.isRemoveBackground());
    }
}
