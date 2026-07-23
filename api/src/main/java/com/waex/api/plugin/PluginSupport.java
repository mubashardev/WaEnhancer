package com.waex.api.plugin;

import android.content.SharedPreferences;
import com.waex.api.plugin.IPluginContext;

public class PluginSupport {
    private final IPluginContext context;
    private final ClassLoader classLoader;
    private final SharedPreferences prefs;

    public PluginSupport(IPluginContext context, ClassLoader classLoader, SharedPreferences prefs) {
        this.context = context;
        this.classLoader = classLoader;
        this.prefs = prefs;
    }

    public IPluginContext getContext() {
        return context;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    // Safe utility helpers
    public boolean getBooleanPreference(String key, boolean defaultValue) {
        return prefs != null && prefs.getBoolean(key, defaultValue);
    }

    public String getStringPreference(String key, String defaultValue) {
        return prefs != null ? prefs.getString(key, defaultValue) : defaultValue;
    }

    public String getModuleString(String key) {
        if (context != null && context.getCoreBridge() != null) {
            return context.getCoreBridge().getModuleString(key);
        }
        return null;
    }

    public int getIdentifier(String name, String defType) {
        if (context != null && context.getCoreBridge() != null) {
            return context.getCoreBridge().getIdentifier(name, defType);
        }
        return 0;
    }
}
