package com.waenhancer.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Generates semantic ARGB tokens from one accent without replacing green pixels blindly. */
public final class SemanticTheme {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    public static final class Tokens {
        private final Map<String, Integer> values;

        private Tokens(Map<String, Integer> values) {
            this.values = Collections.unmodifiableMap(values);
        }

        public int get(String token) {
            Integer value = values.get(token);
            if (value == null) throw new IllegalArgumentException("Unknown token: " + token);
            return value;
        }

        public Map<String, Integer> asMap() {
            return values;
        }
    }

    private static final Map<String, Integer> PRESETS;

    static {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("green", rgb(79, 175, 80));
        colors.put("blue", rgb(59, 130, 246));
        colors.put("cyan", rgb(6, 182, 212));
        colors.put("purple", rgb(139, 92, 246));
        colors.put("orange", rgb(249, 115, 22));
        colors.put("red", rgb(239, 68, 68));
        colors.put("pink", rgb(236, 72, 153));
        PRESETS = Collections.unmodifiableMap(colors);
    }

    private SemanticTheme() {
    }

    public static Map<String, Integer> presets() {
        return PRESETS;
    }

    public static int presetColor(String name) {
        if (name == null) return PRESETS.get("green");
        return PRESETS.getOrDefault(name.toLowerCase(Locale.ROOT), PRESETS.get("green"));
    }

    public static Tokens fromPreset(String name, boolean dark) {
        return generate(presetColor(name), dark);
    }

    public static Tokens generate(int accent, boolean dark) {
        int surface = dark ? rgb(18, 18, 18) : WHITE;
        int primary = ensureControlContrast(accent, surface, 3.0);
        int onPrimary = bestTextColor(primary);
        int primaryContainer = blend(primary, dark ? BLACK : WHITE, dark ? 0.58f : 0.78f);
        int surfaceVariant = dark ? rgb(43, 47, 49) : rgb(238, 241, 242);
        int onSurface = dark ? rgb(245, 247, 248) : rgb(28, 31, 32);
        int outline = ensureControlContrast(blend(primary, onSurface, 0.65f), surface, 3.0);

        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("primary", primary);
        map.put("onPrimary", onPrimary);
        map.put("primaryContainer", primaryContainer);
        map.put("onPrimaryContainer", bestTextColor(primaryContainer));
        map.put("secondary", rotateHue(primary, 24f));
        map.put("surface", surface);
        map.put("surfaceVariant", surfaceVariant);
        map.put("onSurface", onSurface);
        map.put("outline", outline);
        map.put("link", ensureTextContrast(primary, surface, 4.5));
        map.put("selection", withAlpha(primary, 0.24f));
        map.put("fab", primary);
        map.put("activeIndicator", withAlpha(primary, dark ? 0.30f : 0.18f));
        map.put("unreadBadge", primary);
        map.put("outgoingBubble", blend(primary, dark ? BLACK : WHITE, dark ? 0.50f : 0.80f));
        map.put("incomingBubble", surfaceVariant);
        map.put("onOutgoingBubble", bestTextColor(map.get("outgoingBubble")));
        map.put("onIncomingBubble", bestTextColor(surfaceVariant));
        map.put("success", rgb(34, 197, 94));
        map.put("warning", rgb(245, 158, 11));
        map.put("error", rgb(220, 38, 38));
        return new Tokens(map);
    }

    public static int bestTextColor(int background) {
        return contrastRatio(BLACK, background) >= contrastRatio(WHITE, background)
                ? BLACK : WHITE;
    }

    public static int ensureTextContrast(int foreground, int background, double targetRatio) {
        if (contrastRatio(foreground, background) >= targetRatio) return foreground;
        int target = bestTextColor(background);
        for (int i = 1; i <= 20; i++) {
            int candidate = blend(foreground, target, i / 20f);
            if (contrastRatio(candidate, background) >= targetRatio) return candidate;
        }
        return target;
    }

    public static int ensureControlContrast(int foreground, int background, double targetRatio) {
        return ensureTextContrast(foreground, background, targetRatio);
    }

    public static double contrastRatio(int first, int second) {
        double l1 = luminance(first);
        double l2 = luminance(second);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        double r = linear(red(color) / 255.0);
        double g = linear(green(color) / 255.0);
        double b = linear(blue(color) / 255.0);
        return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    public static int blend(int first, int second, float amountSecond) {
        float t = Math.max(0f, Math.min(1f, amountSecond));
        int a = Math.round(alpha(first) * (1f - t) + alpha(second) * t);
        int r = Math.round(red(first) * (1f - t) + red(second) * t);
        int g = Math.round(green(first) * (1f - t) + green(second) * t);
        int b = Math.round(blue(first) * (1f - t) + blue(second) * t);
        return argb(a, r, g, b);
    }

    public static int withAlpha(int color, float alphaFraction) {
        return argb(Math.round(Math.max(0f, Math.min(1f, alphaFraction)) * 255),
                red(color), green(color), blue(color));
    }

    private static int rotateHue(int color, float degrees) {
        float[] hsv = rgbToHsv(red(color), green(color), blue(color));
        hsv[0] = (hsv[0] + degrees) % 360f;
        return hsvToColor(alpha(color), hsv[0], hsv[1], hsv[2]);
    }

    private static float[] rgbToHsv(int red, int green, int blue) {
        float r = red / 255f;
        float g = green / 255f;
        float b = blue / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0f) hue = 0f;
        else if (max == r) hue = 60f * (((g - b) / delta) % 6f);
        else if (max == g) hue = 60f * (((b - r) / delta) + 2f);
        else hue = 60f * (((r - g) / delta) + 4f);
        if (hue < 0f) hue += 360f;
        float saturation = max == 0f ? 0f : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static int hsvToColor(int alpha, float hue, float saturation, float value) {
        float chroma = value * saturation;
        float x = chroma * (1f - Math.abs((hue / 60f) % 2f - 1f));
        float m = value - chroma;
        float r1;
        float g1;
        float b1;
        if (hue < 60f) { r1 = chroma; g1 = x; b1 = 0f; }
        else if (hue < 120f) { r1 = x; g1 = chroma; b1 = 0f; }
        else if (hue < 180f) { r1 = 0f; g1 = chroma; b1 = x; }
        else if (hue < 240f) { r1 = 0f; g1 = x; b1 = chroma; }
        else if (hue < 300f) { r1 = x; g1 = 0f; b1 = chroma; }
        else { r1 = chroma; g1 = 0f; b1 = x; }
        return argb(alpha,
                Math.round((r1 + m) * 255f),
                Math.round((g1 + m) * 255f),
                Math.round((b1 + m) * 255f));
    }

    private static int rgb(int red, int green, int blue) {
        return argb(255, red, green, blue);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
    }

    private static int alpha(int color) { return (color >>> 24) & 0xFF; }
    private static int red(int color) { return (color >>> 16) & 0xFF; }
    private static int green(int color) { return (color >>> 8) & 0xFF; }
    private static int blue(int color) { return color & 0xFF; }
}
