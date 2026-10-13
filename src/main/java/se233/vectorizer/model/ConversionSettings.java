package se233.vectorizer.model;

public class ConversionSettings {
    public static final int MIN_CUSTOM_COLORS = 1;
    public static final int MAX_CUSTOM_COLORS = 5;

    public enum DetailLevel {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High");

        private final String displayName;

        DetailLevel(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum ColorMode {
        UNLIMITED,
        CUSTOM
    }

    public enum NamingRule {
        ORIGINAL("Original Name"),
        PREFIX_NUMBER("Custom Prefix + Number");

        private final String displayName;

        NamingRule(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private DetailLevel detailLevel = DetailLevel.MEDIUM;
    private ColorMode colorMode = ColorMode.CUSTOM;
    private int customColors = 2;
    private boolean removeBackground;
    private NamingRule namingRule = NamingRule.ORIGINAL;
    private String customName = "";

    public DetailLevel getDetailLevel() {
        return detailLevel;
    }

    public void setDetailLevel(DetailLevel detailLevel) {
        requireValue(detailLevel, "Detail level cannot be null");
        this.detailLevel = detailLevel;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode colorMode) {
        requireValue(colorMode, "Color mode cannot be null");
        this.colorMode = colorMode;
    }

    public int getCustomColors() {
        return customColors;
    }

    public void setCustomColors(int customColors) {
        validateCustomColorCount(customColors);
        this.customColors = customColors;
    }

    public boolean isRemoveBackground() {
        return removeBackground;
    }

    public void setRemoveBackground(boolean removeBackground) {
        this.removeBackground = removeBackground;
    }

    public NamingRule getNamingRule() {
        return namingRule;
    }

    public void setNamingRule(NamingRule namingRule) {
        requireValue(namingRule, "Naming rule cannot be null");
        this.namingRule = namingRule;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName == null ? "" : customName.trim();
    }

    private static void validateCustomColorCount(int colorCount) {
        if (colorCount >= MIN_CUSTOM_COLORS && colorCount <= MAX_CUSTOM_COLORS) {
            return;
        }
        throw new IllegalArgumentException("Custom colors must be between "
                + MIN_CUSTOM_COLORS + " and " + MAX_CUSTOM_COLORS);
    }

    private static <T> void requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
