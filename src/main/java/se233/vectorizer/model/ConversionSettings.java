package se233.vectorizer.model;

public class ConversionSettings {
    public static final int MIN_CUSTOM_COLORS = 1;
    public static final int MAX_CUSTOM_COLORS = 5;

    public enum DetailLevel {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High");

        private final String label;

        DetailLevel(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ColorMode {
        UNLIMITED,
        CUSTOM
    }

    public enum NamingRule {
        ORIGINAL("Original Name"),
        PREFIX_NUMBER("Custom Prefix + Number");

        private final String label;

        NamingRule(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
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
        if (detailLevel == null) {
            throw new IllegalArgumentException("Detail level cannot be null");
        }
        this.detailLevel = detailLevel;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode colorMode) {
        if (colorMode == null) {
            throw new IllegalArgumentException("Color mode cannot be null");
        }
        this.colorMode = colorMode;
    }

    public int getCustomColors() {
        return customColors;
    }

    public void setCustomColors(int customColors) {
        if (customColors < MIN_CUSTOM_COLORS || customColors > MAX_CUSTOM_COLORS) {
            throw new IllegalArgumentException("Custom colors must be between "
                    + MIN_CUSTOM_COLORS + " and " + MAX_CUSTOM_COLORS);
        }
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
        if (namingRule == null) {
            throw new IllegalArgumentException("Naming rule cannot be null");
        }
        this.namingRule = namingRule;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName == null ? "" : customName.trim();
    }
}
