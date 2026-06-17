package com.waenhancer.xposed.core.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import com.waex.api.plugin.IPlugin;
import dalvik.system.DexClassLoader;
import de.robv.android.xposed.XposedBridge;
import java.io.File;

public class PluginLoader {

    public static void loadPlugins(Context context, ClassLoader hostClassLoader, SharedPreferences pref) {
        String apkPath = locatePluginApk(context, pref);
        if (apkPath == null) {
            XposedBridge.log("[WAEX] Pro plugin APK not found. Skipping dynamic loading.");
            return;
        }

        XposedBridge.log("[WAEX] Pro plugin APK found at: " + apkPath + ". Initializing loading...");

        try {
            ClassLoader pluginClassLoader = createClassLoader(apkPath, context, hostClassLoader);
            initAndRunPlugin(pluginClassLoader, hostClassLoader, context, pref);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to load Pro plugin dynamically: " + t.toString());
            XposedBridge.log(t);
        }
    }

    private static String locatePluginApk(Context context, SharedPreferences pref) {
        // 1. Try finding installed Pro plugin package
        try {
            var pm = context.getPackageManager();
            var info = pm.getApplicationInfo("com.waex.pro", 0);
            if (info.sourceDir != null && new File(info.sourceDir).exists()) {
                return info.sourceDir;
            }
        } catch (Throwable ignored) {}

        // 2. Fallback to custom developer path from preferences
        if (pref != null) {
            String customPath = pref.getString("pro_plugin_path", null);
            if (customPath != null && new File(customPath).exists()) {
                return customPath;
            }
        }

        return null;
    }

    private static ClassLoader createClassLoader(String apkPath, Context context, ClassLoader hostClassLoader) {
        File codeCacheDir = context.getCodeCacheDir();
        ClassLoader isolatedParent = new IsolatedParentClassLoader(hostClassLoader);
        return new DexClassLoader(
            apkPath,
            codeCacheDir.getAbsolutePath(),
            null,
            isolatedParent
        );
    }

    private static void initAndRunPlugin(ClassLoader pluginClassLoader, ClassLoader hostClassLoader, Context context, SharedPreferences pref) throws Throwable {
        Class<?> entryClass = pluginClassLoader.loadClass("com.waex.pro.PluginEntry");
        if (!IPlugin.class.isAssignableFrom(entryClass)) {
            throw new IllegalArgumentException("Plugin entry class com.waex.pro.PluginEntry does not implement IPlugin");
        }

        IPlugin plugin = (IPlugin) entryClass.getDeclaredConstructor().newInstance();

        Context moduleContext = null;
        try {
            moduleContext = context.createPackageContext("com.waex.pro", Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to create package context for com.waex.pro: " + t.toString());
        }

        PluginContextImpl pluginContext = new PluginContextImpl(hostClassLoader, moduleContext, pref);
        plugin.load();
        plugin.attachContext(pluginContext);
        plugin.init();
        plugin.execute();

        XposedBridge.log("[WAEX] Dynamic plugin " + plugin.getName() + " (v" + plugin.getVersion() + ") loaded successfully!");
    }
}
