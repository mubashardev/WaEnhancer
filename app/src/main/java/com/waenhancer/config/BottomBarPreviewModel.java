package com.waenhancer.config;

import java.util.Map;

/**
 * Every floating-bottom-bar value the editor's preview needs, resolved from a preference snapshot.
 *
 * <p>Kept free of Android types so the preview can be covered by JVM tests: the editor previously
 * read a handful of keys inline and silently ignored the rest, so most controls moved a slider
 * without changing anything on screen.
 */
public final class BottomBarPreviewModel {

    /** Radius used to mean "fully rounded"; larger than any pill can be. */
    public static final int FULLY_ROUNDED_RADIUS_DP = 1000;

    public final boolean barEnabled;
    public final boolean glassEnabled;
    public final int glassOpacity;
    public final int fillColor;
    public final int radiusDp;
    public final int sideMarginDp;
    public final int bottomMarginDp;
    public final int paddingVerticalDp;
    public final int iconSizeDp;
    public final int textSizeSp;
    public final int iconLabelSpacingDp;
    public final boolean manualHeight;
    public final int manualHeightDp;

    public final String fabMode;
    public final int fabOffsetDp;
    public final int minimalFabSizeDp;
    public final int minimalFabRadiusDp;
    public final int minimalFabOpacity;
    public final int minimalFabMarginDp;
    public final int minimalFabColor;
    public final int minimalFabIconColor;

    public final boolean indicatorVisible;
    public final boolean indicatorManualWidth;
    public final boolean indicatorManualHeight;
    public final int indicatorWidthDp;
    public final int indicatorHeightDp;
    public final int indicatorRadiusDp;
    public final int indicatorPaddingHorizontalDp;
    public final int indicatorPaddingVerticalDp;
    public final int indicatorOffsetDp;
    public final int indicatorOpacity;
    public final int indicatorColor;

    private BottomBarPreviewModel(Map<String, ?> snapshot) {
        barEnabled = bool(snapshot, "floating_bottom_bar", false);
        glassEnabled = bool(snapshot, "floating_bottom_bar_glass", true);
        glassOpacity = intOf(snapshot, "floating_bottom_bar_glass_opacity");
        fillColor = color(snapshot, "floating_bottom_bar_fill_color", 0);
        radiusDp = bool(snapshot, "floating_bottom_bar_fully_rounded", false)
                ? FULLY_ROUNDED_RADIUS_DP
                : intOf(snapshot, "floating_bottom_bar_radius");
        sideMarginDp = intOf(snapshot, "floating_bottom_bar_horizontal_margin");
        bottomMarginDp = intOf(snapshot, "floating_bottom_bar_bottom_margin");
        paddingVerticalDp = intOf(snapshot, "floating_bottom_bar_padding_vertical");
        iconSizeDp = intOf(snapshot, "floating_bottom_bar_icon_size");
        textSizeSp = intOf(snapshot, "floating_bottom_bar_text_size");
        iconLabelSpacingDp = intOf(snapshot, "floating_bottom_bar_icon_label_spacing");
        manualHeight = "manual".equals(str(snapshot, "floating_bottom_bar_height_mode",
                "automatic"));
        manualHeightDp = intOf(snapshot, "floating_bottom_bar_manual_height");

        fabMode = str(snapshot, "floating_bottom_bar_fab_mode", "default");
        fabOffsetDp = intOf(snapshot, "floating_bottom_bar_fab_offset");
        minimalFabSizeDp = intOf(snapshot, "floating_bottom_bar_minimal_fab_size");
        minimalFabRadiusDp = intOf(snapshot, "floating_bottom_bar_minimal_fab_radius");
        minimalFabOpacity = intOf(snapshot, "floating_bottom_bar_minimal_fab_opacity");
        minimalFabMarginDp = intOf(snapshot, "floating_bottom_bar_minimal_fab_margin");
        minimalFabColor = color(snapshot, "floating_bottom_bar_minimal_fab_color", 0);
        minimalFabIconColor = color(snapshot, "floating_bottom_bar_minimal_fab_icon_color",
                0xFFFFFFFF);

        indicatorVisible = bool(snapshot, "floating_bottom_bar_indicator_visible", false);
        indicatorManualWidth = "manual".equals(str(snapshot,
                "floating_bottom_bar_indicator_width_mode", "automatic"));
        indicatorManualHeight = "manual".equals(str(snapshot,
                "floating_bottom_bar_indicator_height_mode", "automatic"));
        indicatorWidthDp = intOf(snapshot, "floating_bottom_bar_indicator_width");
        indicatorHeightDp = intOf(snapshot, "floating_bottom_bar_indicator_height");
        indicatorRadiusDp = intOf(snapshot, "floating_bottom_bar_indicator_radius");
        indicatorPaddingHorizontalDp = intOf(snapshot,
                "floating_bottom_bar_indicator_padding_horizontal");
        indicatorPaddingVerticalDp = intOf(snapshot,
                "floating_bottom_bar_indicator_padding_vertical");
        indicatorOffsetDp = intOf(snapshot, "floating_bottom_bar_indicator_offset");
        indicatorOpacity = intOf(snapshot, "floating_bottom_bar_indicator_opacity");
        indicatorColor = color(snapshot, "floating_bottom_bar_indicator_color", 0);
    }

    public static BottomBarPreviewModel from(Map<String, ?> snapshot) {
        return new BottomBarPreviewModel(snapshot);
    }

    public boolean isFullyRounded() {
        return radiusDp == FULLY_ROUNDED_RADIUS_DP;
    }

    public boolean isFabHidden() {
        return "hidden".equals(fabMode);
    }

    public boolean isFabMinimal() {
        return "minimal".equals(fabMode);
    }

    /**
     * Pill fill, with the glass opacity folded in.
     *
     * @param themeSurface colour to substitute when the user left the fill on "automatic" (0)
     */
    public int resolvedFillColor(int themeSurface) {
        int base = fillColor == 0 ? themeSurface : fillColor;
        return glassEnabled ? applyOpacity(base, glassOpacity) : opaque(base);
    }

    /** @param themePrimary colour to substitute when the FAB colour is left on "automatic" (0) */
    public int resolvedMinimalFabColor(int themePrimary) {
        return applyOpacity(minimalFabColor == 0 ? themePrimary : minimalFabColor,
                minimalFabOpacity);
    }

    /** @param themePrimary colour to substitute when the indicator colour is "automatic" (0) */
    public int resolvedIndicatorColor(int themePrimary) {
        return applyOpacity(indicatorColor == 0 ? themePrimary : indicatorColor,
                indicatorOpacity);
    }

    /**
     * Matches {@code FloatingBottomBar}: percentage scaled to 0-255 and packed as the alpha byte,
     * so the preview and the real bar agree.
     */
    public static int applyOpacity(int color, int percent) {
        int alpha = Math.max(0, Math.min(255, Math.round(percent * 2.55f)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static int intOf(Map<String, ?> snapshot, String key) {
        return BottomBarPreferenceSchema.readInt(snapshot, key);
    }

    private static boolean bool(Map<String, ?> snapshot, String key, boolean fallback) {
        Object raw = snapshot == null ? null : snapshot.get(key);
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof String) return Boolean.parseBoolean((String) raw);
        return fallback;
    }

    private static String str(Map<String, ?> snapshot, String key, String fallback) {
        Object raw = snapshot == null ? null : snapshot.get(key);
        return raw instanceof String && !((String) raw).isEmpty() ? (String) raw : fallback;
    }

    private static int color(Map<String, ?> snapshot, String key, int fallback) {
        Object raw = snapshot == null ? null : snapshot.get(key);
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof String) {
            Integer parsed = parseColor((String) raw);
            return parsed == null ? fallback : parsed;
        }
        return fallback;
    }

    /** Accepts {@code #RRGGBB}, {@code #AARRGGBB} and {@code 0} ("automatic"). */
    public static Integer parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "0".equals(value)) return 0;
        try {
            if (!value.startsWith("#")) value = "#" + value;
            if (value.length() == 7) value = "#FF" + value.substring(1);
            if (value.length() != 9) return null;
            return (int) Long.parseLong(value.substring(1), 16);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
