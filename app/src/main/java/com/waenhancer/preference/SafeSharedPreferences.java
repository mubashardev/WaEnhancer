package com.waenhancer.preference;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public class SafeSharedPreferences implements SharedPreferences {

    private final SharedPreferences delegate;

    public SafeSharedPreferences(SharedPreferences delegate) {
        this.delegate = delegate;
    }

    @Override
    public Map<String, ?> getAll() {
        return delegate.getAll();
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        try {
            return delegate.getString(key, defValue);
        } catch (Exception e) {
            try {
                Object val = delegate.getAll().get(key);
                if (val instanceof Boolean) {
                    return ((Boolean) val) ? "1" : "0";
                }
                return val != null ? String.valueOf(val) : defValue;
            } catch (Exception ignored) {}
            return defValue;
        }
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        try {
            Set<String> value = delegate.getStringSet(key, defValues);
            return value != null ? value : (defValues != null ? defValues : new LinkedHashSet<>());
        } catch (Exception e) {
            try {
                Object val = delegate.getAll().get(key);
                if (val instanceof Set<?>) {
                    LinkedHashSet<String> result = new LinkedHashSet<>();
                    for (Object item : (Set<?>) val) {
                        if (item != null) {
                            result.add(String.valueOf(item));
                        }
                    }
                    return result;
                }
                if (val instanceof String s) {
                    LinkedHashSet<String> result = new LinkedHashSet<>();
                    String trimmed = s.trim();
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        String body = trimmed.substring(1, trimmed.length() - 1).trim();
                        if (!body.isEmpty()) {
                            for (String item : body.split(",")) {
                                String normalized = item.trim();
                                if (!normalized.isEmpty()) {
                                    result.add(normalized);
                                }
                            }
                        }
                    } else if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                    return result;
                }
            } catch (Exception ignored) {}
            return defValues != null ? defValues : new LinkedHashSet<>();
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        try {
            return delegate.getInt(key, defValue);
        } catch (Exception e) {
            try {
                Object val = delegate.getAll().get(key);
                if (val instanceof String) return Integer.parseInt((String) val);
                if (val instanceof Float) return ((Float) val).intValue();
                if (val instanceof Long) return ((Long) val).intValue();
            } catch (Exception ignored) {}
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return delegate.getLong(key, defValue);
        } catch (Exception e) {
            try {
                Object val = delegate.getAll().get(key);
                if (val instanceof String) return Long.parseLong((String) val);
                if (val instanceof Integer) return ((Integer) val).longValue();
                if (val instanceof Float) return ((Float) val).longValue();
            } catch (Exception ignored) {}
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return delegate.getFloat(key, defValue);
        } catch (Exception e) {
            try {
                Object val = delegate.getAll().get(key);
                if (val instanceof Integer) return ((Integer) val).floatValue();
                if (val instanceof String) return Float.parseFloat((String) val);
                if (val instanceof Long) return ((Long) val).floatValue();
                if (val instanceof Double) return ((Double) val).floatValue();
            } catch (Exception ignored) {}
            return defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        try {
            return delegate.getBoolean(key, defValue);
        } catch (Exception e) {
            try {
                Object val = delegate.getAll().get(key);
                if (val instanceof String) {
                    String s = (String) val;
                    return "1".equals(s) || "true".equalsIgnoreCase(s);
                }
                if (val instanceof Integer) return ((Integer) val) != 0;
            } catch (Exception ignored) {}
            return defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        return delegate.contains(key);
    }

    @Override
    public Editor edit() {
        return new EditorWrapper(delegate.edit());
    }

    private class EditorWrapper implements Editor {
        private final Editor delegateEditor;

        EditorWrapper(Editor delegateEditor) {
            this.delegateEditor = delegateEditor;
        }

        @Override
        public Editor putString(String key, @Nullable String value) {
            delegateEditor.putString(key, value);
            syncToProvider(key, "string", value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, @Nullable Set<String> values) {
            delegateEditor.putStringSet(key, values);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            delegateEditor.putInt(key, value);
            syncToProvider(key, "int", value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            delegateEditor.putLong(key, value);
            syncToProvider(key, "long", value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            delegateEditor.putFloat(key, value);
            syncToProvider(key, "float", value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            delegateEditor.putBoolean(key, value);
            syncToProvider(key, "boolean", value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            delegateEditor.remove(key);
            try {
                com.waenhancer.App app = com.waenhancer.App.getInstance();
                if (app != null) {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putString("key", key);
                    app.getContentResolver().call(
                            android.net.Uri.parse("content://com.waenhancer.hookprovider"),
                            "remove_preference", null, extras);
                }
            } catch (Throwable ignored) {}
            return this;
        }

        @Override
        public Editor clear() {
            delegateEditor.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return delegateEditor.commit();
        }

        @Override
        public void apply() {
            delegateEditor.apply();
        }

        private void syncToProvider(String key, String type, Object value) {
            try {
                com.waenhancer.App app = com.waenhancer.App.getInstance();
                if (app != null) {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putString("key", key);
                    extras.putString("type", type);
                    if (value instanceof Boolean) extras.putBoolean("value", (Boolean) value);
                    else if (value instanceof String) extras.putString("value", (String) value);
                    else if (value instanceof Integer) extras.putInt("value", (Integer) value);
                    else if (value instanceof Long) extras.putLong("value", (Long) value);
                    else if (value instanceof Float) extras.putFloat("value", (Float) value);
                    app.getContentResolver().call(
                            android.net.Uri.parse("content://com.waenhancer.hookprovider"),
                            "put_preference", null, extras);
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        delegate.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
