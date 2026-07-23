package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.waenhancer.BuildConfig;
import android.util.Log;
import com.waenhancer.xposed.utils.Utils;

/**
 * Safely manages access to XSharedPreferences without triggering NoClassDefFoundError
 * for classes that implement Xposed interfaces.
 */
public class XPrefManager {

    private static SharedPreferences pref;
    private static volatile boolean xprefsUnavailable = false;

    public static SharedPreferences getPref() {
        if (pref != null) return pref;
        if (Utils.xprefs != null) {
            pref = Utils.xprefs;
            return pref;
        }
        if (xprefsUnavailable) return null;

        try {
            // Use reflection to avoid direct dependency on XSharedPreferences in this class's load time
            Class<?> xPrefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
            pref = (SharedPreferences) xPrefsClass.getConstructor(String.class, String.class)
                    .newInstance(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + "_preferences");
            
            // Call makeWorldReadable and reload via reflection
            try {
                xPrefsClass.getMethod("makeWorldReadable").invoke(pref);
                xPrefsClass.getMethod("reload").invoke(pref);
            } catch (Throwable ignored) {}
            
            return pref;
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException) && !(t.getCause() != null && t.getCause() instanceof ClassNotFoundException)) {
                Log.e("WaE-XPrefManager", "Failed to initialize XSharedPreferences", t);
            }
            xprefsUnavailable = true;
            return null;
        }
    }

    public static void reload() {
        if (pref != null) {
            try {
                Class<?> xPrefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
                if (xPrefsClass.isInstance(pref)) {
                    xPrefsClass.getMethod("reload").invoke(pref);
                } else {
                    try {
                        pref.getClass().getMethod("reload").invoke(pref);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        if (Utils.xprefs != null) {
            try {
                Class<?> xPrefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
                if (xPrefsClass.isInstance(Utils.xprefs)) {
                    xPrefsClass.getMethod("reload").invoke(Utils.xprefs);
                } else {
                    try {
                        Utils.xprefs.getClass().getMethod("reload").invoke(Utils.xprefs);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    public static SharedPreferences getPref(Context context) {
        SharedPreferences xposedPref = getPref();
        if (xposedPref != null) return xposedPref;
        
        // Fallback to provider-based or local prefs
        return context.getSharedPreferences(BuildConfig.APPLICATION_ID + "_preferences", Context.MODE_PRIVATE);
    }
}