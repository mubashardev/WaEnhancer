package com.waenhancer.config;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import java.util.Map;
import java.util.Set;

/**
 * Preference access that behaves correctly in both processes.
 *
 * <p><strong>Reading</strong> is tolerant of legacy value types. Settings written by older
 * builds are frequently stored under a different primitive than the current code expects — an
 * {@code Integer} where a {@code Float} is read, a {@code String} where a {@code Boolean} is
 * read — and a raw {@code getX} call throws {@link ClassCastException} for those. Every reader
 * here converts instead of throwing, and falls back to the supplied default.</p>
 *
 * <p><strong>Writing</strong> respects the fact that the module runs in two processes with
 * different capabilities. Inside the hooked WhatsApp process the store is an
 * {@code XSharedPreferences}, whose {@code edit()} throws
 * {@code UnsupportedOperationException("read-only implementation")}. Code that runs in both
 * processes must therefore never call {@code edit()} directly: it calls {@link #put} here,
 * which writes directly when the store is writable and otherwise routes the write to the
 * module process through the preference provider.</p>
 *
 * <p>This is not a theoretical concern. Arming the CSS failure counter from
 * {@code CustomThemeV2.doHook()} and {@code CustomView.doHook()} called {@code edit()} on the
 * read-only store, so the exception propagated out of {@code doHook} and disabled both theme
 * features at startup.</p>
 */
public final class SafePrefs {

    private SafePrefs() {
    }

    // ---------------------------------------------------------------- reading

    public static boolean getBoolean(SharedPreferences preferences, String key, boolean fallback) {
        Object value = raw(preferences, key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.equalsIgnoreCase("true") || text.equals("1")) return true;
            if (text.equalsIgnoreCase("false") || text.equals("0")) return false;
        }
        return fallback;
    }

    public static String getString(SharedPreferences preferences, String key, String fallback) {
        Object value = raw(preferences, key);
        if (value == null) return fallback;
        if (value instanceof String) return (String) value;
        if (value instanceof Boolean) return ((Boolean) value) ? "1" : "0";
        if (value instanceof Number) return String.valueOf(value);
        return fallback;
    }

    public static int getInt(SharedPreferences preferences, String key, int fallback) {
        Object value = raw(preferences, key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
        if (value instanceof String) {
            try {
                return (int) Float.parseFloat(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static long getLong(SharedPreferences preferences, String key, long fallback) {
        Object value = raw(preferences, key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static float getFloat(SharedPreferences preferences, String key, float fallback) {
        Object value = raw(preferences, key);
        if (value instanceof Number) return ((Number) value).floatValue();
        if (value instanceof String) {
            try {
                return Float.parseFloat(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getStringSet(SharedPreferences preferences, String key,
                                           Set<String> fallback) {
        Object value = raw(preferences, key);
        if (value instanceof Set<?>) {
            for (Object item : (Set<?>) value) {
                if (!(item instanceof String)) return fallback;
            }
            return (Set<String>) value;
        }
        return fallback;
    }

    /**
     * Reads through {@code getAll()} rather than the typed getters so that a value stored under
     * an unexpected type is converted rather than throwing.
     */
    private static Object raw(SharedPreferences preferences, String key) {
        if (preferences == null || key == null) return null;
        try {
            Map<String, ?> all = preferences.getAll();
            return all == null ? null : all.get(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------- writing

    /** True when this store accepts writes, i.e. we are not holding an XSharedPreferences. */
    public static boolean isWritable(SharedPreferences preferences) {
        if (preferences == null) return false;
        try {
            preferences.edit();
            return true;
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
    }

    /**
     * Applies a batch of values, choosing the transport that works in the current process.
     *
     * @param context may be null in the module process; required to reach the provider from the
     *                hooked process
     * @return true when the values were persisted
     */
    public static boolean put(Context context, SharedPreferences preferences,
                              Map<String, Object> values) {
        if (values == null || values.isEmpty()) return true;
        if (isWritable(preferences)) {
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                apply(editor, entry.getKey(), entry.getValue());
            }
            return editor.commit();
        }
        return putThroughProvider(context, values);
    }

    private static void apply(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) editor.remove(key);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Double) editor.putFloat(key, ((Double) value).floatValue());
        else if (value instanceof Set<?>) {
            @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
            editor.putStringSet(key, set);
        } else editor.putString(key, String.valueOf(value));
    }

    /**
     * Routes a write to the module process. The hooked process holds a read-only view of the
     * preference file, so the module app performs the write on its behalf.
     */
    private static boolean putThroughProvider(Context context, Map<String, Object> values) {
        if (context == null) return false;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID
                    + ".hookprovider");
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Bundle extras = new Bundle();
                extras.putString("key", entry.getKey());
                Object value = entry.getValue();
                if (value == null) {
                    resolver.call(uri, "remove_preference", null, extras);
                    continue;
                }
                if (value instanceof Boolean) {
                    extras.putString("type", "boolean");
                    extras.putBoolean("value", (Boolean) value);
                } else if (value instanceof Integer) {
                    extras.putString("type", "int");
                    extras.putInt("value", (Integer) value);
                } else if (value instanceof Long) {
                    extras.putString("type", "long");
                    extras.putLong("value", (Long) value);
                } else if (value instanceof Float || value instanceof Double) {
                    extras.putString("type", "float");
                    extras.putFloat("value", ((Number) value).floatValue());
                } else {
                    extras.putString("type", "string");
                    extras.putString("value", String.valueOf(value));
                }
                resolver.call(uri, "put_preference", null, extras);
            }
            return true;
        } catch (RuntimeException ignored) {
            // A failed bookkeeping write must never take down the calling feature.
            return false;
        }
    }
}
