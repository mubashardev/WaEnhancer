package com.waenhancer.xposed.bridge.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.utils.Utils;

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
    private static final String AUTHORITY_SUFFIX = ".hookprovider";
    private static final String AUTHORITY_LEGACY = "com.waenhancer.hookprovider";

    private final SharedPreferences fallbackPrefs;

    public ProviderSharedPreferences(Context context, SharedPreferences localPrefs, SharedPreferences fallbackPrefs) {
        Utils.log("[WAE] ProviderSharedPreferences: Initializing...");
        this.context = context;
        this.localPrefs = localPrefs;
        this.fallbackPrefs = fallbackPrefs;
        try {
            hydrateFromProvider();
        } catch (Throwable t) {
            Utils.log("[WAE] ProviderSharedPreferences: Hydration critical failure: " + t.getMessage());
        }
        try {
            registerObserver();
        } catch (Throwable t) {
            Utils.log("[WAE] ProviderSharedPreferences: Observer registration critical failure: " + t.getMessage());
        }
        Utils.log("[WAE] ProviderSharedPreferences: Initialization complete. Local cache size: " + localPrefs.getAll().size());
    }

    private void registerObserver() {
        String authority = BuildConfig.APPLICATION_ID + AUTHORITY_SUFFIX;
        try {
            Utils.log("[WAE] ProviderSharedPreferences: Registering observer for authority: " + authority);
            context.getContentResolver().registerContentObserver(
                    Uri.parse("content://" + authority + "/preferences"),
                    true,
                    new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                        private long lastHydration = 0;
                        @Override
                        public void onChange(boolean selfChange) {
                            long now = System.currentTimeMillis();
                            if (now - lastHydration > 500) {
                                lastHydration = now;
                                Utils.log("[WAE] ProviderSharedPreferences: Preferences changed, re-hydrating...");
                                hydrateFromProvider();
                            }
                        }
                    }
            );
            Utils.log("[WAE] ProviderSharedPreferences: Observer registered successfully");
        } catch (Throwable e) {
            Utils.log("[WAE] ProviderSharedPreferences: Failed to register observer: " + e.getMessage());
            // Try legacy
            if (!authority.equals(AUTHORITY_LEGACY)) {
                try {
                    context.getContentResolver().registerContentObserver(
                        Uri.parse("content://" + AUTHORITY_LEGACY + "/preferences"),
                        true,
                        new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                            private long lastHydration = 0;
                            @Override
                            public void onChange(boolean selfChange) {
                                long now = System.currentTimeMillis();
                                if (now - lastHydration > 500) {
                                    lastHydration = now;
                                    hydrateFromProvider();
                                }
                            }
                        }
                    );
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public Map<String, ?> getAll() { return localPrefs.getAll(); }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        if (localPrefs.contains(key)) return localPrefs.getString(key, defValue);
        if (fallbackPrefs != null && fallbackPrefs.contains(key)) return fallbackPrefs.getString(key, defValue);
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
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        if (localPrefs.contains(key)) return localPrefs.getStringSet(key, defValues);
        if (fallbackPrefs != null && fallbackPrefs.contains(key)) return fallbackPrefs.getStringSet(key, defValues);
        return localPrefs.getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        if (localPrefs.contains(key)) return localPrefs.getInt(key, defValue);
        if (fallbackPrefs != null && fallbackPrefs.contains(key)) return fallbackPrefs.getInt(key, defValue);
        return localPrefs.getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        if (localPrefs.contains(key)) return localPrefs.getLong(key, defValue);
        if (fallbackPrefs != null && fallbackPrefs.contains(key)) return fallbackPrefs.getLong(key, defValue);
        return localPrefs.getLong(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (localPrefs.contains(key)) return localPrefs.getFloat(key, defValue);
        if (fallbackPrefs != null && fallbackPrefs.contains(key)) return fallbackPrefs.getFloat(key, defValue);
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
    public boolean getBoolean(String key, boolean defValue) {
        if (localPrefs.contains(key)) return localPrefs.getBoolean(key, defValue);
        if (fallbackPrefs != null && fallbackPrefs.contains(key)) return fallbackPrefs.getBoolean(key, defValue);
        return localPrefs.getBoolean(key, defValue);
    }

    @Override
    public boolean contains(String key) {
        return localPrefs.contains(key) || (fallbackPrefs != null && fallbackPrefs.contains(key));
    }

    public void reload() {
        Utils.log("[WAE] ProviderSharedPreferences: Manual reload requested");
        hydrateFromProvider();
    }

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

    private long lastHydrationTime = 0;

    @SuppressWarnings("unchecked")
    private void hydrateFromProvider() {
        long now = System.currentTimeMillis();
        if (now - lastHydrationTime < 500) {
            // Skip rapid re-hydration to avoid loops
            return;
        }
        lastHydrationTime = now;
        try {
            Utils.log("[WAE] ProviderSharedPreferences: Starting hydration...");
            Bundle result = callProvider("get_all_preferences", null);
            if (result == null) {
                Utils.log("[WAE] ProviderSharedPreferences: Hydration failed (null result). Using fallback.");
                Utils.log("[WAE] ProviderSharedPreferences: Fallback cache size: " + fallbackPrefs.getAll().size());
                var editor = localPrefs.edit().clear();
                for (Map.Entry<String, ?> entry : fallbackPrefs.getAll().entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                    else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                    else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                    else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
                    else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
                }
                editor.commit();
                return;
            }
            Utils.log("[WAE] ProviderSharedPreferences: Received preferences bundle from provider");
            Serializable serializable = result.getSerializable("prefs");
            if (!(serializable instanceof Map)) {
                android.util.Log.e("WAE", "Hydration failed: result 'prefs' is not a Map (type: " + (serializable != null ? serializable.getClass().getName() : "null") + ")");
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
            editor.commit();
            android.util.Log.i("WAE", "Hydration completed successfully");
        } catch (Exception e) {
            android.util.Log.e("WAE", "Hydration failed with exception: " + e.getMessage(), e);
        }
    }

    private class ProviderEditor implements Editor {
        private final Editor localEditor;

        public ProviderEditor(Editor localEditor) {
            this.localEditor = localEditor;
        }

        private void syncToProvider(String key, Object value) {
            de.robv.android.xposed.XposedBridge.log("[WAE] ProviderSharedPreferences: Putting preference: " + key + " = " + value);
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
        String[] authorities = new String[] { BuildConfig.APPLICATION_ID + AUTHORITY_SUFFIX, AUTHORITY_LEGACY, "com.waenhancer.provider" };
        for (String authority : authorities) {
            try {
                Utils.log("[WAE] ProviderSharedPreferences: Calling provider: " + method + " (Authority: " + authority + ")");
                Bundle result = context.getContentResolver().call(
                        Uri.parse("content://" + authority),
                        method,
                        null,
                        extras);
                if (result != null) {
                    Utils.log("[WAE] ProviderSharedPreferences: Call successful for: " + authority);
                    return result;
                } else {
                    Utils.log("[WAE] ProviderSharedPreferences: Call returned null for: " + authority);
                }
            } catch (Throwable e) {
                Utils.log("[WAE] ProviderSharedPreferences: Call error (" + authority + "): " + e.getMessage());
            }
        }
        return null;
    }
}
