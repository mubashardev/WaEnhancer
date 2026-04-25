package com.waenhancer.xposed.bridge.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.waenhancer.BuildConfig;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A SharedPreferences implementation that writes values back to the module via a ContentProvider.
 * This ensures that settings changed within the WhatsApp process are persisted in the module.
 */
public class ProviderSharedPreferences implements SharedPreferences {

    private final Context context;
    private final SharedPreferences localPrefs;
    private final String authority = BuildConfig.APPLICATION_ID + ".provider";

    public ProviderSharedPreferences(Context context, SharedPreferences localPrefs) {
        this.context = context;
        this.localPrefs = localPrefs;
        hydrateFromProvider();
    }

    @Override
    public Map<String, ?> getAll() { return localPrefs.getAll(); }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) { return localPrefs.getString(key, defValue); }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) { return localPrefs.getStringSet(key, defValues); }

    @Override
    public int getInt(String key, int defValue) { return localPrefs.getInt(key, defValue); }

    @Override
    public long getLong(String key, long defValue) { return localPrefs.getLong(key, defValue); }

    @Override
    public float getFloat(String key, float defValue) { return localPrefs.getFloat(key, defValue); }

    @Override
    public boolean getBoolean(String key, boolean defValue) { return localPrefs.getBoolean(key, defValue); }

    @Override
    public boolean contains(String key) { return localPrefs.contains(key); }

    @Override
    public Editor edit() {
        return new ProviderEditor(localPrefs.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        localPrefs.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        localPrefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @SuppressWarnings("unchecked")
    private void hydrateFromProvider() {
        try {
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + authority),
                    "get_all_preferences",
                    null,
                    null);
            if (result == null) {
                return;
            }
            Serializable serializable = result.getSerializable("prefs");
            if (!(serializable instanceof Map<?, ?> rawMap)) {
                return;
            }
            var editor = localPrefs.edit().clear();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof String stringValue) {
                    editor.putString(key, stringValue);
                } else if (value instanceof Boolean booleanValue) {
                    editor.putBoolean(key, booleanValue);
                } else if (value instanceof Integer intValue) {
                    editor.putInt(key, intValue);
                } else if (value instanceof Long longValue) {
                    editor.putLong(key, longValue);
                } else if (value instanceof Float floatValue) {
                    editor.putFloat(key, floatValue);
                } else if (value instanceof Set<?> setValue) {
                    var strings = new java.util.HashSet<String>();
                    boolean allStrings = true;
                    for (Object item : setValue) {
                        if (!(item instanceof String stringItem)) {
                            allStrings = false;
                            break;
                        }
                        strings.add(stringItem);
                    }
                    if (allStrings) {
                        editor.putStringSet(key, strings);
                    }
                }
            }
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    private class ProviderEditor implements Editor {
        private final Editor localEditor;

        public ProviderEditor(Editor localEditor) {
            this.localEditor = localEditor;
        }

        private void syncToProvider(String key, Object value) {
            try {
                Bundle extras = new Bundle();
                extras.putString("key", key);
                if (value instanceof String stringValue) {
                    extras.putString("type", "string");
                    extras.putString("value", stringValue);
                } else if (value instanceof Boolean booleanValue) {
                    extras.putString("type", "boolean");
                    extras.putBoolean("value", booleanValue);
                } else if (value instanceof Integer intValue) {
                    extras.putString("type", "int");
                    extras.putInt("value", intValue);
                } else if (value instanceof Long longValue) {
                    extras.putString("type", "long");
                    extras.putLong("value", longValue);
                } else if (value instanceof Float floatValue) {
                    extras.putString("type", "float");
                    extras.putFloat("value", floatValue);
                } else if (value instanceof Set<?> setValue) {
                    extras.putString("type", "string_set");
                    var list = new ArrayList<String>();
                    for (Object item : setValue) {
                        if (item instanceof String stringItem) {
                            list.add(stringItem);
                        }
                    }
                    extras.putStringArrayList("value", list);
                } else {
                    return;
                }

                context.getContentResolver().call(
                        Uri.parse("content://" + authority),
                        "put_preference",
                        null,
                        extras);
            } catch (Exception e) {
                // Log error
            }
        }

        @Override
        public Editor putString(String key, @Nullable String value) {
            localEditor.putString(key, value);
            syncToProvider(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, @Nullable Set<String> values) {
            localEditor.putStringSet(key, values);
            syncToProvider(key, values);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            localEditor.putInt(key, value);
            syncToProvider(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            localEditor.putLong(key, value);
            syncToProvider(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            localEditor.putFloat(key, value);
            syncToProvider(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            localEditor.putBoolean(key, value);
            syncToProvider(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            localEditor.remove(key);
            try {
                Bundle extras = new Bundle();
                extras.putString("key", key);
                context.getContentResolver().call(
                        Uri.parse("content://" + authority),
                        "remove_preference",
                        null,
                        extras);
            } catch (Exception ignored) {
            }
            return this;
        }

        @Override
        public Editor clear() {
            localEditor.clear();
            try {
                context.getContentResolver().call(
                        Uri.parse("content://" + authority),
                        "clear_preferences",
                        null,
                        null);
            } catch (Exception ignored) {
            }
            return this;
        }

        @Override
        public boolean commit() { return localEditor.commit(); }

        @Override
        public void apply() { localEditor.apply(); }
    }
}
