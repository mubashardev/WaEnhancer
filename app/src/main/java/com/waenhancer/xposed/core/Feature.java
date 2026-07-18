package com.waenhancer.xposed.core;

import android.util.Log;

import androidx.annotation.NonNull;

import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Feature {

    public final ClassLoader classLoader;
    public final SharedPreferences prefs;
    public static boolean DEBUG = false;

    // Global tracking to prevent hook leaks if doHook is called multiple times
    private static final Set<Member> hookedMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected boolean markHooked(Member member) {
        if (member == null) return false;
        return hookedMethods.add(member);
    }

    public Feature(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        this.classLoader = classLoader;
        this.prefs = preferences;
    }

    public abstract void doHook() throws Throwable;

    @NonNull
    public abstract String getPluginName();

    public void logDebug(Object object) {
    }

    public void logDebug(String title, Object object) {
    }


    public void log(Object object) {
    }

    public void logError(Object object) {
        if (object instanceof Throwable) {
            // XposedBridge.log(String.format("[%s] CRITICAL ERROR:", this.getPluginName()));
            // XposedBridge.log((Throwable) object);
        } else {
            // XposedBridge.log(String.format("[%s] CRITICAL ERROR: %s", this.getPluginName(), object));
        }
    }

    protected void reloadPrefs() {
        if (prefs instanceof XSharedPreferences) {
            ((XSharedPreferences) prefs).reload();
        } else if (prefs.getClass().getName().contains("ProviderSharedPreferences")) {
            try {
                Method reload = prefs.getClass().getMethod("reload");
                reload.invoke(prefs);
            } catch (Exception ignored) {}
        }
    }

    protected String getSafeString(String key, String defaultValue) {
        try {
            reloadPrefs();
            Object val = prefs.getAll().get(key);
            if (val == null) return defaultValue;
            if (val instanceof String) return (String) val;
            if (val instanceof Boolean) return (Boolean) val ? "1" : "0";
            return String.valueOf(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected float getSafeFloat(String key, float defaultValue) {
        try {
            Object val = prefs.getAll().get(key);
            if (val == null) return defaultValue;
            if (val instanceof Float) return (Float) val;
            if (val instanceof Integer) return ((Integer) val).floatValue();
            if (val instanceof String) return Float.parseFloat((String) val);
            if (val instanceof Double) return ((Double) val).floatValue();
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}