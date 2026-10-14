package se233.vectorizer.model;

/**
 * Stores and validates the options used during image conversion.
 */
public class ConversionSettings {
    public static final int MIN_CUSTOM_COLORS = 1;
    public static final int MAX_CUSTOM_COLORS = 5;

    public enum DetailLevel {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High");

        private final String displayName;

        /**
         * Creates a detail level with the text shown in the user interface.
         *
         * @param displayName text displayed for this detail level
         */
        DetailLevel(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the user-friendly name of this detail level.
         *
         * @return display name
         */
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

        /**
         * Creates a naming rule with the text shown in the user interface.
         *
         * @param displayName text displayed for this naming rule
         */
        NamingRule(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the user-friendly name of this naming rule.
         *
         * @return display name
         */
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

    /**
     * Returns the selected image detail level.
     *
     * @return current detail level
     */
    public DetailLevel getDetailLevel() {
        return detailLevel;
    }

    /**
     * Changes the image detail level.
     *
     * @param detailLevel new detail level; must not be null
     * @throws IllegalArgumentException if detailLevel is null
     */
    public void setDetailLevel(DetailLevel detailLevel) {
        requireValue(detailLevel, "Detail level cannot be null");
        this.detailLevel = detailLevel;
    }

    /**
     * Returns the selected color mode.
     *
     * @return current color mode
     */
    public ColorMode getColorMode() {
        return colorMode;
    }

    /**
     * Changes the color mode.
     *
     * @param colorMode new color mode; must not be null
     * @throws IllegalArgumentException if colorMode is null
     */
    public void setColorMode(ColorMode colorMode) {
        requireValue(colorMode, "Color mode cannot be null");
        this.colorMode = colorMode;
    }

    /**
     * Returns the requested custom color count.
     *
     * @return current custom color count
     */
    public int getCustomColors() {
        return customColors;
    }

    /**
     * Changes the custom color count.
     *
     * @param customColors number of colors, from MIN_CUSTOM_COLORS to MAX_CUSTOM_COLORS
     * @throws IllegalArgumentException if the count is outside the supported range
     */
    public void setCustomColors(int customColors) {
        validateCustomColorCount(customColors);
        this.customColors = customColors;
    }

    /**
     * Reports whether background removal is enabled.
     *
     * @return true when background removal is enabled
     */
    public boolean isRemoveBackground() {
        return removeBackground;
    }

    /**
     * Enables or disables background removal.
     *
     * @param removeBackground true to remove the image background
     */
    public void setRemoveBackground(boolean removeBackground) {
        this.removeBackground = removeBackground;
    }

    /**
     * Returns the selected output naming rule.
     *
     * @return current naming rule
     */
    public NamingRule getNamingRule() {
        return namingRule;
    }

    /**
     * Changes the output naming rule.
     *
     * @param namingRule new naming rule; must not be null
     * @throws IllegalArgumentException if namingRule is null
     */
    public void setNamingRule(NamingRule namingRule) {
        requireValue(namingRule, "Naming rule cannot be null");
        this.namingRule = namingRule;
    }

    /**
     * Returns the custom output name or prefix.
     *
     * @return trimmed custom name
     */
    public String getCustomName() {
        return customName;
    }

    /**
     * Stores a custom output name or prefix.
     *
     * @param customName name or prefix; null is stored as an empty string
     */
    public void setCustomName(String customName) {
        this.customName = customName == null ? "" : customName.trim();
    }

    /**
     * Checks whether a custom color count is supported.
     *
     * @param colorCount count to validate
     * @throws IllegalArgumentException if the count is outside the supported range
     */
    private static void validateCustomColorCount(int colorCount) {
        if (colorCount >= MIN_CUSTOM_COLORS && colorCount <= MAX_CUSTOM_COLORS) {
            return;
        }
        throw new IllegalArgumentException("Custom colors must be between "
                + MIN_CUSTOM_COLORS + " and " + MAX_CUSTOM_COLORS);
    }

    /**
     * Checks that a required value is not null.
     *
     * @param value value to check
     * @param message error message used when the value is null
     * @param <T> value type
     * @throws IllegalArgumentException if value is null
     */
    private static <T> void requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversionSettings that = (ConversionSettings) o;
        return customColors == that.customColors
                && removeBackground == that.removeBackground
                && detailLevel == that.detailLevel
                && colorMode == that.colorMode;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(detailLevel, colorMode, customColors, removeBackground);
    }
}
