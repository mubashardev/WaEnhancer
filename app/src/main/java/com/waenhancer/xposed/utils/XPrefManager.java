package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.waenhancer.BuildConfig;

/**
 * Safely manages access to XSharedPreferences without triggering NoClassDefFoundError
 * for classes that implement Xposed interfaces.
 */
public class XPrefManager {

    private static SharedPreferences pref;

    public static SharedPreferences getPref() {
        if (pref != null) return pref;

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
            android.util.Log.e("WaE-XPrefManager", "Failed to initialize XSharedPreferences reflectively", t);
            // Fallback to standard SharedPreferences if XSharedPreferences is not available
            return null;
        }
    }

    public static void reload() {
        if (pref != null) {
            try {
                Class<?> xPrefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
                if (xPrefsClass.isInstance(pref)) {
                    xPrefsClass.getMethod("reload").invoke(pref);
                }
            } catch (Throwable ignored) {}
        }
        if (com.waenhancer.xposed.utils.Utils.xprefs != null) {
            try {
                Class<?> xPrefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
                if (xPrefsClass.isInstance(com.waenhancer.xposed.utils.Utils.xprefs)) {
                    xPrefsClass.getMethod("reload").invoke(com.waenhancer.xposed.utils.Utils.xprefs);
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
