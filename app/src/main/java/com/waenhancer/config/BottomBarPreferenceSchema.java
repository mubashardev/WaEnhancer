package com.waenhancer.config;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Central schema for every numeric Floating Bottom Bar setting. */
public final class BottomBarPreferenceSchema {

    public static final class Spec {
        public final float defaultValue;
        public final float min;
        public final float max;
        public final float step;

        private Spec(float defaultValue, float min, float max, float step) {
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.step = step;
        }
    }

    private static final Map<String, Spec> SPECS;

    static {
        Map<String, Spec> specs = new LinkedHashMap<>();
        add(specs, "floating_bottom_bar_radius", 28, 0, 64, 1);
        add(specs, "floating_bottom_bar_bottom_margin", 22, 0, 64, 1);
        add(specs, "floating_bottom_bar_horizontal_margin", 16, 0, 48, 1);
        add(specs, "floating_bottom_bar_fab_offset", 80, -32, 128, 1);
        add(specs, "floating_bottom_bar_glass_opacity", 35, 10, 100, 5);
        add(specs, "floating_bottom_bar_icon_size", 24, 16, 40, 1);
        add(specs, "floating_bottom_bar_text_size", 12, 8, 18, 1);
        add(specs, "floating_bottom_bar_padding_vertical", 6, 0, 24, 1);
        add(specs, "floating_bottom_bar_icon_label_spacing", 2, 0, 16, 1);
        add(specs, "floating_bottom_bar_manual_height", 64, 48, 96, 1);
        add(specs, "floating_bottom_bar_minimal_fab_size", 48, 32, 72, 1);
        add(specs, "floating_bottom_bar_minimal_fab_radius", 24, 0, 36, 1);
        add(specs, "floating_bottom_bar_minimal_fab_opacity", 100, 10, 100, 5);
        add(specs, "floating_bottom_bar_minimal_fab_margin", 16, 0, 48, 1);
        add(specs, "floating_bottom_bar_indicator_width", 40, 16, 96, 1);
        add(specs, "floating_bottom_bar_indicator_height", 32, 2, 56, 1);
        add(specs, "floating_bottom_bar_indicator_radius", 18, 0, 32, 1);
        add(specs, "floating_bottom_bar_indicator_padding_horizontal", 8, 0, 24, 1);
        add(specs, "floating_bottom_bar_indicator_padding_vertical", 4, 0, 16, 1);
        add(specs, "floating_bottom_bar_indicator_offset", 0, -24, 24, 1);
        add(specs, "floating_bottom_bar_indicator_opacity", 18, 0, 100, 1);
        SPECS = Collections.unmodifiableMap(specs);
    }

    private BottomBarPreferenceSchema() {
    }

    private static void add(Map<String, Spec> specs, String key, float defaultValue,
                            float min, float max, float step) {
        specs.put(key, new Spec(defaultValue, min, max, step));
    }

    public static Map<String, Spec> all() {
        return SPECS;
    }

    public static Spec spec(String key) {
        Spec spec = SPECS.get(key);
        if (spec == null) throw new IllegalArgumentException("Unknown bottom bar key: " + key);
        return spec;
    }

    public static float normalize(String key, Object rawValue) {
        Spec spec = spec(key);
        float value = parse(rawValue, spec.defaultValue);
        if (!Float.isFinite(value)) value = spec.defaultValue;
        value = Math.max(spec.min, Math.min(spec.max, value));
        float steps = Math.round((value - spec.min) / spec.step);
        float aligned = spec.min + (steps * spec.step);
        return Math.max(spec.min, Math.min(spec.max, aligned));
    }

    public static float read(SharedPreferences preferences, String key) {
        return normalize(key, preferences.getAll().get(key));
    }

    /**
     * Reads from a caller-supplied {@link SharedPreferences#getAll()} snapshot. Prefer this inside
     * the hooked WhatsApp process: {@code getAll()} copies the whole preference map on every call.
     */
    public static float read(Map<String, ?> snapshot, String key) {
        return normalize(key, snapshot == null ? null : snapshot.get(key));
    }

    public static int readInt(SharedPreferences preferences, String key) {
        return Math.round(read(preferences, key));
    }

    public static int readInt(Map<String, ?> snapshot, String key) {
        return Math.round(read(snapshot, key));
    }

    public static void normalizePersistedValues(SharedPreferences preferences) {
        Map<String, ?> stored = preferences.getAll();
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (Map.Entry<String, Spec> entry : SPECS.entrySet()) {
            String key = entry.getKey();
            Object raw = stored.get(key);
            float normalized = normalize(key, raw);
            if (!(raw instanceof Float) || Float.compare((Float) raw, normalized) != 0) {
                editor.putFloat(key, normalized);
                changed = true;
            }
        }
        if (changed) editor.apply();
    }

    public static Map<String, Float> preset(String presetName) {
        String preset = presetName == null ? "stable_glass"
                : presetName.toLowerCase(Locale.ROOT);
        Map<String, Float> values = new LinkedHashMap<>();
        switch (preset) {
            case "compact":
                values.put("floating_bottom_bar_radius", 24f);
                values.put("floating_bottom_bar_bottom_margin", 12f);
                values.put("floating_bottom_bar_horizontal_margin", 12f);
                values.put("floating_bottom_bar_icon_size", 21f);
                values.put("floating_bottom_bar_text_size", 10f);
                values.put("floating_bottom_bar_padding_vertical", 3f);
                values.put("floating_bottom_bar_icon_label_spacing", 1f);
                values.put("floating_bottom_bar_manual_height", 52f);
                break;
            case "accessibility":
                values.put("floating_bottom_bar_radius", 32f);
                values.put("floating_bottom_bar_bottom_margin", 20f);
                values.put("floating_bottom_bar_horizontal_margin", 10f);
                values.put("floating_bottom_bar_icon_size", 30f);
                values.put("floating_bottom_bar_text_size", 15f);
                values.put("floating_bottom_bar_padding_vertical", 10f);
                values.put("floating_bottom_bar_icon_label_spacing", 5f);
                values.put("floating_bottom_bar_manual_height", 82f);
                values.put("floating_bottom_bar_indicator_height", 42f);
                break;
            case "stable_glass":
            default:
                values.put("floating_bottom_bar_radius", 28f);
                values.put("floating_bottom_bar_bottom_margin", 22f);
                values.put("floating_bottom_bar_horizontal_margin", 16f);
                values.put("floating_bottom_bar_icon_size", 24f);
                values.put("floating_bottom_bar_text_size", 12f);
                values.put("floating_bottom_bar_padding_vertical", 6f);
                values.put("floating_bottom_bar_icon_label_spacing", 2f);
                values.put("floating_bottom_bar_manual_height", 64f);
                values.put("floating_bottom_bar_glass_opacity", 35f);
                break;
        }
        return Collections.unmodifiableMap(values);
    }

    private static float parse(Object raw, float fallback) {
        if (raw instanceof Number) return ((Number) raw).floatValue();
        if (raw instanceof String) {
            try {
                return Float.parseFloat(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
