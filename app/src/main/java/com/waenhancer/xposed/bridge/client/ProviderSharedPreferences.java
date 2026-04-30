package com.waenhancer.xposed.bridge.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
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
    private static final String[] AUTHORITIES = new String[] {
            BuildConfig.APPLICATION_ID + ".hookprovider",
            BuildConfig.APPLICATION_ID + ".provider"
    };

    public ProviderSharedPreferences(Context context, SharedPreferences localPrefs) {
        this.context = context;
        this.localPrefs = localPrefs;
        hydrateFromProvider();
    }

    @Override
    public Map<String, ?> getAll() { return localPrefs.getAll(); }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        try {
            return localPrefs.getString(key, defValue);
        } catch (Exception e) {
            // Fallback: try to get any value as string
            try {
                Object val = localPrefs.getAll().get(key);
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
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) { return localPrefs.getStringSet(key, defValues); }

    @Override
    public int getInt(String key, int defValue) { return localPrefs.getInt(key, defValue); }

    @Override
    public long getLong(String key, long defValue) { return localPrefs.getLong(key, defValue); }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return localPrefs.getFloat(key, defValue);
        } catch (Exception e) {
            try {
                Object val = localPrefs.getAll().get(key);
                if (val instanceof Integer) return ((Integer) val).floatValue();
                if (val instanceof String) return Float.parseFloat((String) val);
                if (val instanceof Long) return ((Long) val).floatValue();
                if (val instanceof Double) return ((Double) val).floatValue();
            } catch (Exception ignored) {}
            return defValue;
        }
    }

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
            Bundle result = callProvider("get_all_preferences", null);
            if (result == null) {
                return;
            }
            Serializable serializable = result.getSerializable("prefs");
            if (!(serializable instanceof Map)) {
                return;
            }
            Map<?, ?> rawMap = (Map<?, ?>) serializable;
            
            android.util.Log.i("WAE", "Hydrating " + rawMap.size() + " preferences from provider");
            var editor = localPrefs.edit().clear();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    continue;
                }
                String key = (String) entry.getKey();
                Object value = entry.getValue();
                
                // Specific migrations during hydration
                if ("open_wae".equals(key) && value instanceof Boolean) {
                    editor.putString(key, ((Boolean) value) ? "1" : "0");
                    continue;
                }

                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Double) {
                    editor.putFloat(key, ((Double) value).floatValue());
                } else if (value instanceof Set<?>) {
                    Set<?> setValue = (Set<?>) value;
                    var strings = new java.util.HashSet<String>();
                    boolean allStrings = true;
                    for (Object item : setValue) {
                        if (!(item instanceof String)) {
                            allStrings = false;
                            break;
                        }
                        strings.add((String) item);
                    }
                    if (allStrings) {
                        editor.putStringSet(key, strings);
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            android.util.Log.e("WAE", "Hydration failed: " + e.getMessage());
        }
    }

    private class ProviderEditor implements Editor {
        private final Editor localEditor;

        public ProviderEditor(Editor localEditor) {
            this.localEditor = localEditor;
        }

        private void syncToProvider(String key, Object value) {
            Bundle extras = new Bundle();
            extras.putString("key", key);
            if (value instanceof String) {
                extras.putString("type", "string");
                extras.putString("value", (String) value);
            } else if (value instanceof Boolean) {
                extras.putString("type", "boolean");
                extras.putBoolean("value", (Boolean) value);
            } else if (value instanceof Integer) {
                extras.putString("type", "int");
                extras.putInt("value", (Integer) value);
            } else if (value instanceof Long) {
                extras.putString("type", "long");
                extras.putLong("value", (Long) value);
            } else if (value instanceof Float) {
                extras.putString("type", "float");
                extras.putFloat("value", (Float) value);
            } else if (value instanceof Set<?>) {
                extras.putString("type", "string_set");
                var list = new ArrayList<String>();
                for (Object item : (Set<?>) value) {
                    if (item instanceof String) {
                        list.add((String) item);
                    }
                }
                extras.putStringArrayList("value", list);
            } else {
                return;
            }
            callProvider("put_preference", extras);
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
                callProvider("remove_preference", extras);
            } catch (Exception ignored) {
            }
            return this;
        }

        @Override
        public Editor clear() {
            localEditor.clear();
            try {
                callProvider("clear_preferences", null);
            } catch (Exception ignored) {
            }
            return this;
        }

        @Override
        public boolean commit() { return localEditor.commit(); }

        @Override
        public void apply() { localEditor.apply(); }
    }

    @Nullable
    private Bundle callProvider(@NonNull String method, @Nullable Bundle extras) {
        for (String authority : AUTHORITIES) {
            try {
                Bundle result = context.getContentResolver().call(
                        Uri.parse("content://" + authority),
                        method,
                        null,
                        extras);
                if (result != null) {
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
