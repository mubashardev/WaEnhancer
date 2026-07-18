package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import android.text.Html;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;
import java.util.concurrent.CountDownLatch;

import com.waenhancer.App;
import com.waenhancer.BuildConfig;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import dalvik.system.DexClassLoader;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Helper utility to bridge main set classes and pro submodule features cleanly,
 * preventing compilation failures when HAS_PRO_FEATURES is false.
 */
public class ProHelper {

    private static volatile boolean forceFree = false;
    private static java.lang.ref.WeakReference<Context> sContextRef = null;

    private static void saveContext(Context context) {
        if (context != null) {
            sContextRef = new java.lang.ref.WeakReference<>(context.getApplicationContext());
        }
    }

    private static Context getStaticContext() {
        if (sContextRef != null) {
            Context context = sContextRef.get();
            if (context != null) {
                return context;
            }
        }
        return App.getInstance();
    }

    private static final Object lfLock = new Object();
    private static JSONObject limitedFreeConfigCache = null;
    private static String lastLimitedFreeConfig = null;

    private static JSONObject decryptedConfigCache = null;
    private static String lastEncryptedConfig = null;

    private static ClassLoader companionPluginClassLoader = null;
    private static String companionPluginPath = null;
    private static boolean hasXposedLoaderCached = false;

    private static class PluginParentClassLoader extends ClassLoader {
        private final ClassLoader hostLoader;
        private final ClassLoader moduleLoader;
        private final ClassLoader xposedLoader;

        PluginParentClassLoader(ClassLoader hostLoader, ClassLoader moduleLoader, ClassLoader xposedLoader) {
            super(ClassLoader.getSystemClassLoader());
            this.hostLoader = hostLoader;
            this.moduleLoader = moduleLoader;
            this.xposedLoader = xposedLoader;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.waex.helper.")) {
                throw new ClassNotFoundException("Plugin class must be resolved from plugin dex: " + name);
            }

            if (name.startsWith("de.robv.android.xposed.")) {
                Class<?> knownXposedClass = loadKnownXposedClass(name);
                if (knownXposedClass != null) return knownXposedClass;
                Class<?> xposedClass = loadFromKnownLoader(name, xposedLoader);
                if (xposedClass != null) return xposedClass;
                xposedClass = loadFromKnownLoader(name, de.robv.android.xposed.XC_MethodHook.class.getClassLoader());
                if (xposedClass != null) return xposedClass;
                xposedClass = loadFromKnownLoader(name, moduleLoader);
                if (xposedClass != null) return xposedClass;
            }

            if (name.startsWith("com.waenhancer.") || name.startsWith("com.waex.api.")) {
                Class<?> moduleClass = loadFromKnownLoader(name, moduleLoader);
                if (moduleClass != null) return moduleClass;
            }

            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException ignored) {
                Class<?> hostClass = loadFromKnownLoader(name, hostLoader);
                if (hostClass != null) return hostClass;
                Class<?> moduleClass = loadFromKnownLoader(name, moduleLoader);
                if (moduleClass != null) return moduleClass;
                Class<?> xposedClass = loadFromKnownLoader(name, xposedLoader);
                if (xposedClass != null) return xposedClass;
                throw ignored;
            }
        }

        private Class<?> loadKnownXposedClass(String name) {
            if ("de.robv.android.xposed.XposedBridge".equals(name)) return de.robv.android.xposed.XposedBridge.class;
            if ("de.robv.android.xposed.XposedHelpers".equals(name)) return de.robv.android.xposed.XposedHelpers.class;
            if ("de.robv.android.xposed.XC_MethodHook".equals(name)) return de.robv.android.xposed.XC_MethodHook.class;
            if ("de.robv.android.xposed.XC_MethodHook$MethodHookParam".equals(name)) return de.robv.android.xposed.XC_MethodHook.MethodHookParam.class;
            if ("de.robv.android.xposed.XC_MethodHook$Unhook".equals(name)) return de.robv.android.xposed.XC_MethodHook.Unhook.class;
            if ("de.robv.android.xposed.XSharedPreferences".equals(name)) return de.robv.android.xposed.XSharedPreferences.class;
            return null;
        }

        private Class<?> loadFromKnownLoader(String name, ClassLoader loader) {
            if (loader == null) return null;
            try {
                return loader.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
    }

    public static synchronized ClassLoader getPluginClassLoader(Context context) {
        return getPluginClassLoader(context, null, null);
    }

    public static synchronized ClassLoader getPluginClassLoader(Context context, ClassLoader parentLoader) {
        return getPluginClassLoader(context, parentLoader, null);
    }

    public static synchronized ClassLoader getPluginClassLoader(Context context, ClassLoader parentLoader, ClassLoader xposedLoader) {
        saveContext(context);
        if (!isPluginInstalled(context)) {
            companionPluginClassLoader = null;
            companionPluginPath = null;
            return null;
        }
        String cachedPath = null;
        String cachedLibPath = null;
        if (companionPluginClassLoader != null
                && companionPluginPath != null
                && new java.io.File(companionPluginPath).exists()) {
            return companionPluginClassLoader;
        }

        ClassLoader proHelperLoader = ProHelper.class.getClassLoader();
        
        // 1. Try querying the HookProvider ContentProvider first to get the most fresh path from package manager.
        if (context != null) {
            try {
                android.os.Bundle pluginInfo = context.getContentResolver().call(
                        android.net.Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
                        "get_pro_plugin_info",
                        null,
                        null
                );
                if (pluginInfo != null) {
                    String providerPath = pluginInfo.getString("sourceDir", null);
                    String providerLibPath = pluginInfo.getString("nativeLibraryDir", null);
                    if (providerPath != null && !providerPath.trim().isEmpty()
                            && new java.io.File(providerPath).exists()) {
                        cachedPath = providerPath;
                        cachedLibPath = providerLibPath;
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("WaeX-ClassDebug", "Failed to query live Pro plugin path from HookProvider", t);
            }
        }

        // 2. If HookProvider failed or returned null/invalid, try reading package manager directly
        if (cachedPath == null || cachedPath.trim().isEmpty() || !new java.io.File(cachedPath).exists()) {
            if (context == null) {
                context = App.getInstance();
            }
            if (context != null) {
                try {
                    android.content.pm.ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo("com.waex.helper", 0);
                    if (appInfo.sourceDir != null && new java.io.File(appInfo.sourceDir).exists()) {
                        cachedPath = appInfo.sourceDir;
                        cachedLibPath = appInfo.nativeLibraryDir;
                    }
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }

        // 3. Fallback to cached path from preferences
        if (cachedPath == null || cachedPath.trim().isEmpty() || !new java.io.File(cachedPath).exists()) {
            try {
                SharedPreferences prefs = getPrefs();
                cachedPath = prefs != null ? prefs.getString("pro_plugin_path", null) : null;
                cachedLibPath = prefs != null ? prefs.getString("pro_plugin_lib_path", null) : null;
            } catch (Throwable t) {
                // Ignore
            }
        }

        if (cachedPath != null && !cachedPath.trim().isEmpty() && new java.io.File(cachedPath).exists()) {
            /* Log removed */
            
            // 1. Try to get nativeLibraryDir from package manager if cachedLibPath is missing
            if (cachedLibPath == null || cachedLibPath.trim().isEmpty()) {
                if (context != null) {
                    try {
                        android.content.pm.ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo("com.waex.helper", 0);
                        cachedLibPath = appInfo.nativeLibraryDir;
                        /* Log removed */
                    } catch (Throwable t) {
                        android.util.Log.w("WaeX-ClassDebug", "Could not get nativeLibraryDir from package manager: " + t.toString());
                    }
                }
            }

            // 2. Fallback/auto-resolve library path checking APK parent directory using device-supported ABIs
            if (cachedLibPath == null || cachedLibPath.trim().isEmpty()) {
                java.io.File apkFile = new java.io.File(cachedPath);
                java.io.File parentDir = apkFile.getParentFile();
                if (parentDir != null && parentDir.exists()) {
                    java.io.File libDir = new java.io.File(parentDir, "lib");
                    if (libDir.exists()) {
                        // Use device-supported ABIs if available, otherwise fallback to standard list
                        String[] abis = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            abis = android.os.Build.SUPPORTED_ABIS;
                        }
                        if (abis == null || abis.length == 0) {
                            abis = new String[]{"arm64-v8a", "armeabi-v7a", "arm64", "arm", "x86_64", "x86"};
                        }
                        for (String abi : abis) {
                            java.io.File abiDir = new java.io.File(libDir, abi);
                            if (new java.io.File(abiDir, "libpro_native.so").exists()) {
                                cachedLibPath = abiDir.getAbsolutePath();
                                /* Log removed */
                                break;
                            }
                        }
                    }
                }
            }

            // 3. Construct a multi-path library search path to cover all bases
            StringBuilder libPathBuilder = new StringBuilder();
            if (cachedLibPath != null && !cachedLibPath.trim().isEmpty()) {
                libPathBuilder.append(cachedLibPath);
            }
            
            // Add the APK's directory and the APK path itself as fallbacks
            java.io.File apkFile = new java.io.File(cachedPath);
            String apkDir = apkFile.getParent();
            if (apkDir != null) {
                if (libPathBuilder.length() > 0) {
                    libPathBuilder.append(java.io.File.pathSeparator);
                }
                libPathBuilder.append(apkDir);
            }
            if (libPathBuilder.length() > 0) {
                libPathBuilder.append(java.io.File.pathSeparator);
            }
            libPathBuilder.append(cachedPath);

            String finalLibPath = libPathBuilder.toString();
            /* Log removed */

            java.io.File optimizedDir = null;
            if (context != null) {
                optimizedDir = new java.io.File(context.getCodeCacheDir(), "waex_pro_dex");
                if (!optimizedDir.exists() && !optimizedDir.mkdirs()) {
                    optimizedDir = context.getCodeCacheDir();
                }
            }
            if (optimizedDir == null) {
                optimizedDir = new java.io.File(System.getProperty("java.io.tmpdir"), "waex_pro_dex");
                if (!optimizedDir.exists()) {
                    optimizedDir.mkdirs();
                }
            }

            // Instead of creating a new DexClassLoader (which isolates the plugin and prevents LSPosed from rewriting its Xposed references,
            // causing NoClassDefFoundError name mismatches), we append the pro plugin APK directly to the host module ClassLoader.
            // Since the host ClassLoader is registered with LSPosed, /* Log removed */
            
            appendDexPath(proHelperLoader, cachedPath, finalLibPath);
            companionPluginClassLoader = proHelperLoader;
            companionPluginPath = cachedPath;
            
            // Explicitly load the native library using System.load to bypass namespace restrictions
            try {
                String soName = "libpro_native.so";
                java.io.File soFile = null;
                if (cachedLibPath != null && !cachedLibPath.trim().isEmpty()) {
                    soFile = new java.io.File(cachedLibPath, soName);
                }
                if (soFile == null || !soFile.exists()) {
                    // Fallback to searching the constructed finalLibPath
                    for (String path : finalLibPath.split(java.io.File.pathSeparator)) {
                        java.io.File f = new java.io.File(path, soName);
                        if (f.exists()) {
                            soFile = f;
                            break;
                        }
                    }
                }
                if (soFile != null && soFile.exists()) {
                    /* Log removed */
                    System.load(soFile.getAbsolutePath());
                    /* Log removed */
                } else {
                    /* Log removed */
                }
            } catch (Throwable t) {
                android.util.Log.e("WaeX-ClassDebug", "Failed to explicitly load pro native library: " + t.toString(), t);
            }
            
            try {
                Class.forName("com.waex.helper.ProFeature", true, companionPluginClassLoader);
                /* Log removed */
            } catch (Throwable t) {
                android.util.Log.e("WaeX-ClassDebug", "Failed to initialize ProFeature with plugin classloader: " + t.toString(), t);
            }
            return companionPluginClassLoader;
        }

        return null;
    }

    private static void appendDexPath(ClassLoader classLoader, String apkPath, String libraryPath) {
        try {
            // 1. Find the pathList field in BaseDexClassLoader
            java.lang.reflect.Field pathListField = null;
            Class<?> curr = classLoader.getClass();
            while (curr != null) {
                try {
                    pathListField = curr.getDeclaredField("pathList");
                    break;
                } catch (NoSuchFieldException e) {
                    curr = curr.getSuperclass();
                }
            }
            if (pathListField == null) {
                /* Log removed */
                return;
            }
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(classLoader);
            if (pathList == null) {
                /* Log removed */
                return;
            }

            // 2. Try using the public/private addDexPath method on DexPathList
            boolean dexAppended = false;
            try {
                java.lang.reflect.Method addDexPathMethod = pathList.getClass().getDeclaredMethod(
                    "addDexPath", String.class, java.io.File.class
                );
                addDexPathMethod.setAccessible(true);
                addDexPathMethod.invoke(pathList, apkPath, null);
                /* Log removed */
                dexAppended = true;
            } catch (Throwable ignored) {
                // Method might not exist or signature changed, fallback to manual elements array manipulation
            }

            // 3. Find makePathElements / makeDexElements method for fallback and for native library elements
            java.lang.reflect.Method makePathElementsMethod = null;
            Class<?> pathListClass = pathList.getClass();
            while (pathListClass != null) {
                for (java.lang.reflect.Method m : pathListClass.getDeclaredMethods()) {
                    if (m.getName().equals("makePathElements") || m.getName().equals("makeDexElements")) {
                        makePathElementsMethod = m;
                        break;
                    }
                }
                if (makePathElementsMethod != null) break;
                pathListClass = pathListClass.getSuperclass();
            }
            if (makePathElementsMethod == null) {
                /* Log removed */
                return;
            }
            makePathElementsMethod.setAccessible(true);

            java.util.List<java.io.IOException> suppressedExceptions = new java.util.ArrayList<>();
            Class<?>[] paramTypes = makePathElementsMethod.getParameterTypes();

            if (!dexAppended) {
                java.util.List<java.io.File> files = new java.util.ArrayList<>();
                files.add(new java.io.File(apkPath));
                Object[] newElements = null;
                if (makePathElementsMethod.getName().equals("makePathElements")) {
                    if (paramTypes.length == 3) {
                        if (paramTypes[2] == ClassLoader.class) {
                            newElements = (Object[]) makePathElementsMethod.invoke(null, files, suppressedExceptions, classLoader);
                        } else {
                            newElements = (Object[]) makePathElementsMethod.invoke(null, files, null, suppressedExceptions);
                        }
                    } else if (paramTypes.length == 4) {
                        newElements = (Object[]) makePathElementsMethod.invoke(null, files, null, suppressedExceptions, classLoader);
                    } else {
                        newElements = (Object[]) makePathElementsMethod.invoke(null, files, suppressedExceptions);
                    }
                } else {
                    newElements = (Object[]) makePathElementsMethod.invoke(null, files, null, suppressedExceptions, classLoader);
                }

                if (newElements != null && newElements.length > 0) {
                    java.lang.reflect.Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
                    dexElementsField.setAccessible(true);
                    Object[] originalElements = (Object[]) dexElementsField.get(pathList);

                    Object[] combinedElements = (Object[]) java.lang.reflect.Array.newInstance(
                        originalElements.getClass().getComponentType(),
                        originalElements.length + newElements.length
                    );
                    System.arraycopy(originalElements, 0, combinedElements, 0, originalElements.length);
                    System.arraycopy(newElements, 0, combinedElements, originalElements.length, newElements.length);
                    dexElementsField.set(pathList, combinedElements);
                    /* Log removed */
                } else {
                    /* Log removed */
                }
            }

            // 4. Append native library paths
            if (libraryPath != null && !libraryPath.isEmpty()) {
                String[] paths = libraryPath.split(java.io.File.pathSeparator);
                java.util.List<java.io.File> newDirsList = new java.util.ArrayList<>();
                for (String path : paths) {
                    if (!path.trim().isEmpty()) {
                        newDirsList.add(new java.io.File(path));
                    }
                }

                if (!newDirsList.isEmpty()) {
                    // Update nativeLibraryDirectories (which can be a List<File> or File[])
                    try {
                        java.lang.reflect.Field nativeLibraryDirectoriesField = pathList.getClass().getDeclaredField("nativeLibraryDirectories");
                        nativeLibraryDirectoriesField.setAccessible(true);
                        Object origDirsObj = nativeLibraryDirectoriesField.get(pathList);
                        if (origDirsObj instanceof java.util.List) {
                            java.util.List<java.io.File> origDirsList = (java.util.List<java.io.File>) origDirsObj;
                            java.util.List<java.io.File> updatedDirsList = new java.util.ArrayList<>(origDirsList);
                            for (java.io.File dir : newDirsList) {
                                if (!updatedDirsList.contains(dir)) {
                                    updatedDirsList.add(dir);
                                }
                            }
                            nativeLibraryDirectoriesField.set(pathList, updatedDirsList);
                            /* Log removed */
                        } else if (origDirsObj instanceof java.io.File[]) {
                            java.io.File[] origDirsArr = (java.io.File[]) origDirsObj;
                            java.util.List<java.io.File> updatedDirsList = new java.util.ArrayList<>(java.util.Arrays.asList(origDirsArr));
                            for (java.io.File dir : newDirsList) {
                                if (!updatedDirsList.contains(dir)) {
                                    updatedDirsList.add(dir);
                                }
                            }
                            java.io.File[] combinedDirsArr = updatedDirsList.toArray(new java.io.File[0]);
                            nativeLibraryDirectoriesField.set(pathList, combinedDirsArr);
                            /* Log removed */
                        }
                    } catch (Throwable t) {
                        android.util.Log.e("WaeX-ClassDebug", "Failed to update nativeLibraryDirectories Field: " + t);
                    }

                    // Update nativeLibraryPathElements (array of NativeLibraryElement or Element)
                    try {
                        java.lang.reflect.Field nativeLibraryPathElementsField = pathList.getClass().getDeclaredField("nativeLibraryPathElements");
                        nativeLibraryPathElementsField.setAccessible(true);
                        Object[] originalLibElements = (Object[]) nativeLibraryPathElementsField.get(pathList);

                        Class<?> componentType = originalLibElements.getClass().getComponentType();
                        java.util.List<Object> newLibElementsList = new java.util.ArrayList<>();

                        for (java.io.File dir : newDirsList) {
                            Object elementObj = null;
                            for (java.lang.reflect.Constructor<?> ctor : componentType.getDeclaredConstructors()) {
                                ctor.setAccessible(true);
                                Class<?>[] params = ctor.getParameterTypes();
                                try {
                                    if (params.length == 1 && params[0] == java.io.File.class) {
                                        elementObj = ctor.newInstance(dir);
                                        break;
                                    } else if (params.length == 2 && params[0] == java.io.File.class && params[1] == boolean.class) {
                                        elementObj = ctor.newInstance(dir, true);
                                        break;
                                    } else if (params.length == 3 && params[0] == java.io.File.class && params[1] == boolean.class && params[2] == java.io.File.class) {
                                        elementObj = ctor.newInstance(dir, true, null);
                                        break;
                                    } else if (params.length == 4 && params[0] == java.io.File.class && params[1] == boolean.class && params[2] == java.io.File.class && params[3] == dalvik.system.DexFile.class) {
                                        elementObj = ctor.newInstance(dir, true, null, null);
                                        break;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (elementObj != null) {
                                newLibElementsList.add(elementObj);
                            } else {
                                /* Log removed */
                            }
                        }

                        if (!newLibElementsList.isEmpty()) {
                            Object[] combinedLibElements = (Object[]) java.lang.reflect.Array.newInstance(
                                componentType,
                                originalLibElements.length + newLibElementsList.size()
                            );
                            System.arraycopy(originalLibElements, 0, combinedLibElements, 0, originalLibElements.length);
                            for (int i = 0; i < newLibElementsList.size(); i++) {
                                combinedLibElements[originalLibElements.length + i] = newLibElementsList.get(i);
                            }
                            nativeLibraryPathElementsField.set(pathList, combinedLibElements);
                            /* Log removed */
                        } else {
                            /* Log removed */
                        }
                    } catch (Throwable t) {
                        android.util.Log.e("WaeX-ClassDebug", "Failed to update nativeLibraryPathElements Field: " + t);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-ClassDebug", "Failed to append dex path: " + t);
        }
    }

    public static void setForceFree(boolean force) {
        forceFree = force;
    }

    private static SharedPreferences getPrefs() {
        if (Utils.xprefs != null) {
            return Utils.xprefs;
        }
        Context context = App.getInstance();
        if (context != null) {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
        return null;
    }

    private static String decrypt(String encryptedBase64) {
        try {
            byte[] keyBytes = new byte[]{
                'W','a','E','n','h','a','n','c','e','r','X','_',
                'S','u','p','e','r','_','S','e','c','r','e','t','_',
                'K','e','y','_','1','2','3'
            };
            byte[] ivBytes = new byte[]{
                'W','a','E','n','h','a','n','c','e','r','X','_',
                'I','V','_','_'
            };
            byte[] cipherText = Base64.decode(encryptedBase64, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized JSONObject getDecryptedConfig() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return null;
        
        String encryptedConfig = prefs.getString("encrypted_config", null);
        if (encryptedConfig == null || encryptedConfig.trim().isEmpty()) {
            decryptedConfigCache = null;
            lastEncryptedConfig = null;
            return null;
        }
        
        if (encryptedConfig.equals(lastEncryptedConfig) && decryptedConfigCache != null) {
            return decryptedConfigCache;
        }
        
        try {
            String decrypted = decrypt(encryptedConfig);
            if (decrypted != null && !decrypted.isEmpty()) {
                decryptedConfigCache = new JSONObject(decrypted);
                lastEncryptedConfig = encryptedConfig;
                return decryptedConfigCache;
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to decrypt/parse config", t);
        }
        
        decryptedConfigCache = null;
        lastEncryptedConfig = null;
        return null;
    }

    private static synchronized JSONObject getLimitedFreeConfig() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return null;

        String encryptedConfig = prefs.getString("limited_free_config_cache", null);
        if (encryptedConfig == null || encryptedConfig.trim().isEmpty()) {
            limitedFreeConfigCache = null;
            lastLimitedFreeConfig = null;
            return null;
        }

        if (encryptedConfig.equals(lastLimitedFreeConfig) && limitedFreeConfigCache != null) {
            return limitedFreeConfigCache;
        }

        try {
            String decrypted = decrypt(encryptedConfig);
            if (decrypted != null && !decrypted.isEmpty()) {
                limitedFreeConfigCache = new JSONObject(decrypted);
                lastLimitedFreeConfig = encryptedConfig;
                return limitedFreeConfigCache;
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to decrypt/parse limited free config", t);
        }

        limitedFreeConfigCache = null;
        lastLimitedFreeConfig = null;
        return null;
    }

    public static boolean isLimitedFreeHookEnabled(String key) {
        if (!isPluginInstalled(null)) return false;
        if (key == null) return false;
        JSONObject config = getLimitedFreeConfig();
        if (config == null) return false;
        JSONObject hooks = config.optJSONObject("hooks");
        if (hooks == null) return false;
        String val = hooks.optString(key, null);
        return val != null && !val.trim().isEmpty();
    }

    public static String getLimitedFreeHookString(String key) {
        if (!isPluginInstalled(null)) return null;
        if (key == null) return null;
        JSONObject config = getLimitedFreeConfig();
        if (config == null) return null;
        JSONObject hooks = config.optJSONObject("hooks");
        if (hooks == null) return null;
        return hooks.optString(key, null);
    }

    public static boolean isLimitedFreePreferenceEnabled(String prefKey) {
        if (!isPluginInstalled(null)) return false;
        if (prefKey == null) return false;
        String hookKey = null;
        if (prefKey.equals("file_size_spoofer")) {
            hookKey = "file_size_spoofer";
        } else if (prefKey.equals("message_bomber")) {
            hookKey = "message_bomber";
        } else if (prefKey.equals("delete_message_file") || prefKey.equals("delete_message_file_sent")) {
            hookKey = "delete_message_file";
        } else if (prefKey.equals("pro_status_splitter")) {
            hookKey = "pro_status_splitter";
        } else if (prefKey.equals("remove_status_bottom_tile")
                || prefKey.equals("remove_status_quick_reactions")
                || prefKey.equals("remove_status_heart_button")
                || prefKey.equals("status_bottom_play_pause_button")
                || prefKey.equals("add_status_reply_menu_item")
                || prefKey.equals("status_video_fast_gesture")
                || prefKey.equals("status_video_fast_speed")
                || prefKey.equals("disable_status_swipe_up")) {
            hookKey = "customize_status_control_class";
        } else if (prefKey.equals("always_typing_global")
                || prefKey.equals("always_typing_global_target")
                || prefKey.equals("always_typing_global_mode")
                || prefKey.equals("always_typing_contacts")
                || prefKey.equals("always_typing_global_type")) {
            hookKey = "always_typing_global";
        } else if (prefKey.equals("send_audio_as_voice_status")) {
            hookKey = "send_audio_as_voice_status";
        } else if (prefKey.equals("filter_group_members_messages")) {
            hookKey = "filter_group_members_messages";
        } else if (prefKey.equals("unlock_premium_customization")) {
            hookKey = "unlock_premium_customization";
        }

        if (hookKey != null) {
            return isLimitedFreeHookEnabled(hookKey);
        }
        return false;
    }

    public static void initLimitedFree(final Context context, final SharedPreferences prefs) {
        saveContext(context);
        if (prefs == null) return;
        // Load cached config first
        try {
            String cachedEncrypted = prefs.getString("limited_free_config_cache", null);
            if (cachedEncrypted != null && !cachedEncrypted.trim().isEmpty()) {
                String decrypted = decrypt(cachedEncrypted);
                if (decrypted != null && !decrypted.isEmpty()) {
                    synchronized (lfLock) {
                        limitedFreeConfigCache = new JSONObject(decrypted);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to load cached limited free config", t);
        }

        // Fetch latest configuration in background
        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("https://waex.mubashar.dev/limited_free_features.txt")
                    .header("User-Agent", "WPPro-App")
                    .build();

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {}

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    try (response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String encryptedBody = response.body().string().trim();
                            String decrypted = decrypt(encryptedBody);
                            if (decrypted != null && !decrypted.isEmpty()) {
                                // Validate JSON structure
                                new JSONObject(decrypted);
                                
                                // Cache locally
                                prefs.edit().putString("limited_free_config_cache", encryptedBody).apply();
                                
                                // Update in memory
                                synchronized (lfLock) {
                                    limitedFreeConfigCache = new JSONObject(decrypted);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Checks if the Pro licensing status is currently active.
     */
    public static boolean isProEnabled() {
        if (!isPluginInstalled(getStaticContext())) {
            return false;
        }
        return "ACTIVE".equalsIgnoreCase(getProStatus());
    }

    /**
     * Checks if the Pro pill design is enabled in the decrypted config.
     */
    public static boolean isPillDesignProEnabled() {
        if (!isProEnabled()) {
            return false;
        }
        JSONObject config = getDecryptedConfig();
        if (config == null) {
            return false;
        }
        return config.optBoolean("pill_design_pro_enabled", false);
    }

    /**
     * Checks if the Filter Items Pro hook is enabled in the decrypted server configuration.
     */
    public static boolean isFilterItemsProEnabled() {
        String hookClass = getHookStringSafely("filter_items");
        return hookClass != null && !hookClass.trim().isEmpty();
    }

    /**
     * Triggers a silent check/config refresh in the background, invoking the callback upon completion.
     */
    public static void silentCheck(final Context context, final Runnable callback) {
        com.waenhancer.xposed.utils.LicenseManager.silentCheck(context, new LicenseManager.SilentCheckListener() {
            @Override
            public void onStatusChanged() {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }

    /**
     * Retrieves the plan name matching active, expired, or free states.
     */
    public static String getProPlanName() {
        String status = getProStatus();
        if ("ACTIVE".equalsIgnoreCase(status)) {
            SharedPreferences prefs = getPrefs();
            String plan = prefs != null ? prefs.getString("plan_name", "") : "";
            return plan.isEmpty() ? "Pro Active" : plan;
        } else if ("EXPIRED".equalsIgnoreCase(status)) {
            return "Pro Expired";
        } else {
            return "Free";
        }
    }

    /**
     * Gets the current pro status string ("ACTIVE", "EXPIRED", "FREE").
     */
    public static String getProStatus() {
        if (forceFree) {
            return "FREE";
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return "FREE";
        }
        String licenseKey = prefs.getString("license_key", "").trim();
        boolean isVerified = prefs.getBoolean("is_pro_verified", false);
        if (!isVerified || licenseKey.isEmpty()) {
            return "FREE";
        }
        long expiresAt = 0;
        try {
            expiresAt = prefs.getLong("expires_at", 0);
        } catch (ClassCastException e) {
            try {
                String expiresStr = prefs.getString("expires_at", "0");
                expiresAt = Long.parseLong(expiresStr);
            } catch (Exception ignored) {}
        }
        if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    public static void navigateToPluginPack(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent();
            intent.setClassName("com.waenhancer", "com.waenhancer.activities.MainActivity");
            intent.putExtra("navigate_to_fragment", 0);
            intent.putExtra("scroll_to_preference", "unlock_limited_free");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Throwable t) {
            android.widget.Toast.makeText(context, "Failed to open Extended Plugin Pack settings.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Recursively traverses and locks down Pro features in a preference list if not verified.
     */
    public static void updatePreferences(Context context, PreferenceGroup group) {
        if (group == null) return;
        saveContext(context);
        boolean pluginInstalled = isPluginInstalled(context);
        boolean proActive = pluginInstalled && isProEnabled();

        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);

            String prefKey = pref.getKey();
            if (prefKey != null) {
                boolean isFree = isLimitedFreePreferenceEnabled(prefKey);

                if (isFree) {
                    CharSequence title = pref.getTitle();
                    if (title != null) {
                        String titleStr = title.toString();
                        // Remove the [Pro] badge if it was added by ProSwitchPreference
                        titleStr = titleStr.replace("[Pro]", "").trim();
                        titleStr = titleStr.replace("  ", " ");
                        if (!titleStr.contains("Limited Free")) {
                            String coloredBadge = " <font color='#02C697'><b>[Limited Free]</b></font>";
                            pref.setTitle(Html.fromHtml(titleStr + coloredBadge, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            pref.setTitle(Html.fromHtml(titleStr, Html.FROM_HTML_MODE_LEGACY));
                        }
                    }
                }
            }

            if (pref instanceof PreferenceGroup) {
                PreferenceGroup prefGroup = (PreferenceGroup) pref;
                String activationKey = "pro_activation_link_" + prefGroup.getKey();
                Preference activationPref = prefGroup.findPreference(activationKey);

                if (isProGroup(pref) && !proActive) {
                    prefGroup.setEnabled(true);
                    uncheckTwoStatePreferences(prefGroup);
                    disableChildrenOfProGroupExceptActivation(prefGroup, activationKey);

                    if (activationPref == null) {
                        activationPref = new Preference(context);
                        activationPref.setKey(activationKey);
                        activationPref.setOrder(-1);
                        prefGroup.addPreference(activationPref);
                    }

                    if (!pluginInstalled) {
                        if (isPluginPackageInstalled(context)) {
                            // Plugin is installed, but unsupported (since pluginInstalled is false)
                            int minVersion = getPluginMinWaexVersion(context);
                            String minVersionName = getVersionNameFromCode(minVersion);
                            String titleHtml = "<b><font color='#D32F2F'>🔑 v" + minVersionName + " Required</font></b>";
                            activationPref.setTitle(Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY));
                            activationPref.setSummary("Plugin requires a newer version of the main app. Tap to view updates.");
                            activationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(@NonNull Preference preference) {
                                    try {
                                        Intent intent = new Intent(context, Class.forName("com.waenhancer.activities.ChangelogActivity"));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        context.startActivity(intent);
                                    } catch (Exception ignored) {}
                                    return true;
                                }
                            });
                        } else {
                            // Plugin package not installed at all
                            if ("FREE".equalsIgnoreCase(getProStatus())) {
                                // User is a free user. Show regular verify text, do NOT show plugin required.
                                String titleHtml = "<b><font color='#8B5CF6'>🔑 Tap here to verify license key & unlock</font></b>";
                                activationPref.setTitle(Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY));
                                activationPref.setSummary("This category is locked. Verify your WAEX Helper license to unlock all features.");
                                activationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                                    @Override
                                    public boolean onPreferenceClick(@NonNull Preference preference) {
                                        try {
                                            Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                                            Intent intent = new Intent(context, clazz);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(intent);
                                        } catch (Throwable t) {
                                            android.widget.Toast.makeText(context, "Pro features are not available.", android.widget.Toast.LENGTH_SHORT).show();
                                        }
                                        return true;
                                    }
                                });
                            } else {
                                // Pro license configured (ACTIVE or EXPIRED) but plugin is missing
                                String titleHtml = "<b><font color='#D32F2F'>🔑 Plugin Required</font></b>";
                                activationPref.setTitle(Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY));
                                activationPref.setSummary("Companion plugin is missing. Tap to install Extended Plugin Pack.");
                                activationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                                    @Override
                                    public boolean onPreferenceClick(@NonNull Preference preference) {
                                        navigateToPluginPack(context);
                                        return true;
                                    }
                                });
                            }
                        }
                    } else {
                        String titleHtml = "<b><font color='#8B5CF6'>🔑 Tap here to verify license key & unlock</font></b>";
                        activationPref.setTitle(Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY));
                        activationPref.setSummary("This category is locked. Verify your WAEX Helper license to unlock all features.");
                        activationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(@NonNull Preference preference) {
                                try {
                                    Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                                    Intent intent = new Intent(context, clazz);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                } catch (Throwable t) {
                                    android.widget.Toast.makeText(context, "Pro features are not available.", android.widget.Toast.LENGTH_SHORT).show();
                                }
                                return true;
                            }
                        });
                    }
                } else {
                    if (activationPref != null) {
                        prefGroup.removePreference(activationPref);
                    }
                    if (isProGroup(pref) && proActive) {
                        String key = pref.getKey();
                        if ("customize_status_view_category".equals(key)) {
                            String hookClass = getHookStringSafely("customize_status_control_class");
                            if (hookClass == null || hookClass.trim().isEmpty()) {
                                disableAndUncheckGroupFromServer(prefGroup, "(Disabled by Server)");
                                continue;
                            }
                        }
                    }
                    updatePreferences(context, prefGroup);
                }
            } else {
                if (isProFeature(pref) && !proActive) {
                    boolean limitedFree = isLimitedFreePreferenceEnabled(pref.getKey());

                    if (!limitedFree) {
                        if (pref.getClass().getName().contains("ProSwitchPreference")) {
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                        } else {
                            pref.setEnabled(false);
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                        }
                    }
                } else if (isProFeature(pref) && proActive) {
                    String key = pref.getKey();
                    String hookKey = getHookKeyForPref(key);
                    if (hookKey != null) {
                        String hookClass = getHookStringSafely(hookKey);
                        if (hookClass == null || hookClass.trim().isEmpty()) {
                            pref.setEnabled(false);
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                            CharSequence summary = pref.getSummary();
                            if (summary == null || !summary.toString().contains("Disabled by Server")) {
                                pref.setSummary("Disabled by Server");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void disableChildrenOfProGroupExceptActivation(PreferenceGroup group, String activationKey) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (activationKey.equals(pref.getKey())) {
                pref.setEnabled(true);
                continue;
            }
            if (pref.getClass().getName().contains("ProSwitchPreference")) {
                if (pref instanceof TwoStatePreference) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            } else {
                pref.setEnabled(false);
                if (pref instanceof TwoStatePreference) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            }
            if (pref instanceof PreferenceGroup) {
                disableChildrenOfProGroupExceptActivation((PreferenceGroup) pref, activationKey);
            }
        }
    }

    private static void disableAndUncheckGroupFromServer(PreferenceGroup group, String suffix) {
        if (group == null) return;
        group.setEnabled(false);
        CharSequence title = group.getTitle();
        if (title != null && !title.toString().contains(suffix)) {
            group.setTitle(title + " " + suffix);
        }
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            pref.setEnabled(false);
            if (pref instanceof TwoStatePreference) {
                ((TwoStatePreference) pref).setChecked(false);
            }
            if (pref instanceof androidx.preference.ListPreference || pref instanceof TwoStatePreference) {
                CharSequence summary = pref.getSummary();
                if (summary == null || !summary.toString().contains("Disabled by Server")) {
                    pref.setSummary("Disabled by Server");
                }
            }
            if (pref instanceof PreferenceGroup) {
                disableAndUncheckGroupFromServer((PreferenceGroup) pref, suffix);
            }
        }
    }

    private static boolean isProGroup(Preference pref) {
        if (pref == null) return false;
        String className = pref.getClass().getName();
        if (className.contains("ProPreferenceCategory")) {
            return true;
        }
        String key = pref.getKey();
        if (key != null) {
            return key.equals("customize_status_view_category");
        }
        return false;
    }

    private static void uncheckTwoStatePreferences(PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof TwoStatePreference) {
                ((TwoStatePreference) pref).setChecked(false);
            }
            if (pref instanceof PreferenceGroup) {
                uncheckTwoStatePreferences((PreferenceGroup) pref);
            }
        }
    }

    public static boolean isProFeature(String key) {
        if (key == null) return false;
        return key.equals("message_bomber") 
                || key.equals("license_verify") 
                || key.equals("delete_message_file") 
                || key.equals("delete_message_file_sent") 
                || key.equals("pro_status_splitter")
                || key.equals("remove_status_bottom_tile")
                || key.equals("remove_status_quick_reactions")
                || key.equals("remove_status_heart_button")
                || key.equals("status_bottom_play_pause_button")
                || key.equals("add_status_reply_menu_item")
                || key.equals("status_video_fast_gesture")
                || key.equals("status_video_fast_speed")
                || key.equals("disable_status_swipe_up")
                || key.equals("always_typing_global")
                || key.equals("always_typing_global_target")
                || key.equals("always_typing_global_mode")
                || key.equals("always_typing_contacts")
                || key.equals("always_typing_global_type")
                || key.equals("send_audio_as_voice_status")
                || key.equals("file_size_spoofer")
                || key.equals("filter_group_members_messages")
                || key.equals("unlock_premium_customization")
                || key.equals("recover_deleted_media");
    }

    private static boolean isProFeature(Preference pref) {
        if (pref == null) return false;

        String className = pref.getClass().getName();
        if (className.contains("ProSwitchPreference")) {
            return true;
        }

        return isProFeature(pref.getKey());
    }

    private static String getHookKeyForPref(String key) {
        if (key == null) return null;
        if (key.equals("message_bomber")) {
            return "message_bomber";
        }
        if (key.equals("delete_message_file") || key.equals("delete_message_file_sent")) {
            return "delete_message_file";
        }
        if (key.equals("pro_status_splitter")) {
            return "pro_status_splitter";
        }
        if (key.equals("remove_status_bottom_tile")
                || key.equals("remove_status_quick_reactions")
                || key.equals("remove_status_heart_button")
                || key.equals("status_bottom_play_pause_button")
                || key.equals("add_status_reply_menu_item")
                || key.equals("status_video_fast_gesture")
                || key.equals("status_video_fast_speed")
                || key.equals("disable_status_swipe_up")) {
            return "customize_status_control_class";
        }
        if (key.equals("always_typing_global")
                || key.equals("always_typing_global_target")
                || key.equals("always_typing_global_mode")
                || key.equals("always_typing_contacts")
                || key.equals("always_typing_global_type")) {
            return "always_typing_global";
        }
        if (key.equals("send_audio_as_voice_status")) {
            return "send_audio_as_voice_status";
        }
        if (key.equals("file_size_spoofer")) {
            return "file_size_spoofer";
        }
        if (key.equals("filter_group_members_messages")) {
            return "filter_group_members_messages";
        }
        if (key.equals("unlock_premium_customization")) {
            return "unlock_premium_customization";
        }
        return null;
    }

    private static String getHookStringSafely(String hookKey) {
        if (isLimitedFreeHookEnabled(hookKey)) {
            return getLimitedFreeHookString(hookKey);
        }
        if (!isProEnabled()) {
            return null;
        }
        JSONObject config = getDecryptedConfig();
        if (config == null) return null;
        JSONObject hooks = config.optJSONObject("hooks");
        if (hooks == null) return null;
        return hooks.optString(hookKey, null);
    }

    public static java.io.File convertAudioToOpus(Context context, android.net.Uri uri) {
        if (context == null || uri == null) return null;
        
        ParcelFileDescriptor inputPfd = null;
        try {
            inputPfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (inputPfd == null) return null;
        } catch (Exception e) {
            android.util.Log.e("WaeX-Helper", "Failed to open input URI for transcoding: " + e.toString());
            return null;
        }

        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.waex.helper", "com.waex.helper.services.ProService"));

        final CountDownLatch latch = new CountDownLatch(1);
        final com.waex.helper.IProService[] serviceHolder = new com.waex.helper.IProService[1];
        java.util.concurrent.ExecutorService connectionExecutor = null;

        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceHolder[0] = com.waex.helper.IProService.Stub.asInterface(service);
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                latch.countDown();
            }
        };

        java.io.File outFile = null;
        try {
            boolean bound;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                connectionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
                bound = context.bindService(intent, Context.BIND_AUTO_CREATE, connectionExecutor, conn);
            } else {
                bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            }
            if (!bound) {
                try { inputPfd.close(); } catch (Exception ignored) {}
                return null;
            }

            try {
                boolean connected = latch.await(4, TimeUnit.SECONDS);
                com.waex.helper.IProService service = serviceHolder[0];
                if (connected && service != null) {
                    ParcelFileDescriptor outputPfd = service.convertAudioToOpus(inputPfd);
                    if (outputPfd != null) {
                        outFile = new java.io.File(context.getCacheDir(), "VoiceStatus-" + System.currentTimeMillis() + ".opus");
                        try (java.io.InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(outputPfd);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                            fos.flush();
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("WaeX-Helper", "IPC convertAudioToOpus failed: " + e.toString());
            } finally {
                context.unbindService(conn);
                if (connectionExecutor != null) {
                    connectionExecutor.shutdownNow();
                }
                try { inputPfd.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            android.util.Log.e("WaeX-Helper", "Failed to bind for transcoding: " + e.toString());
            if (connectionExecutor != null) {
                connectionExecutor.shutdownNow();
            }
            try { inputPfd.close(); } catch (Exception ignored) {}
        }

        return (outFile != null && outFile.exists()) ? outFile : null;
    }

    public static void showKeyboxVerificationDialog(androidx.preference.PreferenceFragmentCompat fragment) {
        com.waenhancer.utils.KeyboxVerification.showDialog(fragment);
    }

    public static boolean isPluginPackageInstalled(Context context) {
        if (context == null) return false;
        
        // If we are in the main app, use PackageManager directly
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
            try {
                context.getPackageManager().getPackageInfo("com.waex.helper", 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        // Otherwise (we are in WhatsApp/Xposed process), query the HookProvider
        try {
            android.os.Bundle pluginInfo = context.getContentResolver().call(
                    android.net.Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
                    "get_pro_plugin_info",
                    null,
                    null
            );
            if (pluginInfo != null && pluginInfo.getString("sourceDir") != null) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static int getPluginMinWaexVersion(Context context) {
        if (context == null) return 0;
        boolean isXposed = !BuildConfig.APPLICATION_ID.equals(context.getPackageName());
        if (isXposed) {
            try {
                android.os.Bundle pluginInfo = context.getContentResolver().call(
                        android.net.Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
                        "get_pro_plugin_info",
                        null,
                        null
                );
                if (pluginInfo != null) {
                    return pluginInfo.getInt("min_waex_version", 0);
                }
            } catch (Throwable ignored) {}
            return 0;
        }
        try {
            android.content.pm.ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                "com.waex.helper", android.content.pm.PackageManager.GET_META_DATA
            );
            if (appInfo != null && appInfo.metaData != null) {
                return appInfo.metaData.getInt("min_waex_version", 0);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static String getVersionNameFromCode(long versionCode) {
        if (versionCode <= 0) {
            return "unknown";
        }
        long base = versionCode / 100;
        long tail = versionCode % 100;
        
        long patch = base % 10;
        long minor = (base / 10) % 10;
        long major = base / 100;
        
        if (tail == 99) {
            return major + "." + minor + "." + patch;
        } else {
            return major + "." + minor + "." + patch + "-beta-" + tail;
        }
    }

    public static boolean isPluginInstalled(Context context) {
        saveContext(context);
        Context ctx = context != null ? context : getStaticContext();
        if (ctx == null) {
            if (companionPluginPath != null) {
                return new java.io.File(companionPluginPath).exists();
            }
            return false;
        }

        boolean isXposed = !BuildConfig.APPLICATION_ID.equals(ctx.getPackageName());
        int minVersion = 0;
        boolean exists = false;

        if (isXposed) {
            try {
                android.os.Bundle pluginInfo = ctx.getContentResolver().call(
                        android.net.Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
                        "get_pro_plugin_info",
                        null,
                        null
                );
                if (pluginInfo != null) {
                    String sourceDir = pluginInfo.getString("sourceDir");
                    if (sourceDir != null && !sourceDir.trim().isEmpty() && new java.io.File(sourceDir).exists()) {
                        exists = true;
                        minVersion = pluginInfo.getInt("min_waex_version", 0);
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("WaeX-Helper", "Failed to query Pro plugin from HookProvider in isPluginInstalled", t);
            }
        } else {
            try {
                android.content.pm.ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(
                    "com.waex.helper", android.content.pm.PackageManager.GET_META_DATA
                );
                if (appInfo != null && appInfo.sourceDir != null && new java.io.File(appInfo.sourceDir).exists()) {
                    exists = true;
                    if (appInfo.metaData != null) {
                        minVersion = appInfo.metaData.getInt("min_waex_version", 0);
                    }
                }
            } catch (Throwable t) {
                exists = false;
            }
        }

        if (!exists) {
            return false;
        }

        if (minVersion > 0) {
            try {
                android.content.pm.PackageInfo myInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                long myVersionCode;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    myVersionCode = myInfo.getLongVersionCode();
                } else {
                    myVersionCode = myInfo.versionCode;
                }
                if (myVersionCode < minVersion) {
                    /* Log removed */
                    return false;
                }
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public static void checkRootAndInstallPlugin(final Activity activity, final Runnable onConsentAgreed) {
        if (activity == null) return;
        
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(activity);
        progressDialog.setMessage("Requesting root permissions...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            boolean hasRoot = com.waenhancer.utils.RootUtils.hasRootAccess();
            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                if (hasRoot) {
                    if (onConsentAgreed != null) {
                        onConsentAgreed.run();
                    }
                    Toast.makeText(activity, "Root access granted. Downloading latest plugin...", Toast.LENGTH_SHORT).show();
                    startProDownloadAndInstallSilent(activity);
                } else {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setTitle("Root Required")
                        .setMessage("Root access is required to download and install the plugin silently. Please grant root permission.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
            });
        }).start();
    }

    public static void startProDownloadAndInstallSilent(final Activity activity) {
        if (activity == null) return;

        if (!com.waenhancer.utils.RootUtils.hasRootAccess()) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "Root access is needed. Please grant Root/Superuser permission to WAEX Helper in your root manager (e.g. KernelSU/Magisk).", Toast.LENGTH_LONG).show();
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("Root Access Required")
                    .setMessage("WAEX Helper requires Root/Superuser permission to install/update the Helper app silently.\n\nPlease open your Root Manager, grant root access to WAEX Helper, and try again.")
                    .setPositiveButton("OK", null)
                    .show();
            });
            return;
        }

        File cacheDir = activity.getCacheDir();
        File apkFile = new File(cacheDir, "helper.apk");
        if (apkFile.exists() && apkFile.length() > 1024) {
            Toast.makeText(activity, "Installing plugin...", Toast.LENGTH_SHORT).show();
            installProApkWithRoot(activity, apkFile);
            return;
        }

        String url = Config.getBaseUrl() + "/api/v1/plugin/latest";
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "Plugin download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Plugin request failed: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                try {
                    String jsonStr = response.body().string();
                    org.json.JSONObject jsonObj = new org.json.JSONObject(jsonStr);
                    String downloadUrl = jsonObj.getString("download_url");

                    okhttp3.Request downloadRequest = new okhttp3.Request.Builder()
                            .url(downloadUrl)
                            .build();

                    client.newCall(downloadRequest).enqueue(new okhttp3.Callback() {
                        @Override
                        public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, "Plugin download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                activity.runOnUiThread(() -> {
                                    Toast.makeText(activity, "Plugin download failed: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                                });
                                return;
                            }

                            File cacheDir = activity.getCacheDir();
                            File apkFile = new File(cacheDir, "helper.apk");
                            File tmpFile = new File(cacheDir, "helper.apk.tmp");

                            try (InputStream is = response.body().byteStream();
                                 FileOutputStream fos = new FileOutputStream(tmpFile)) {

                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, read);
                                }
                                fos.flush();

                                if (tmpFile.renameTo(apkFile)) {
                                    activity.runOnUiThread(() -> {
                                        Toast.makeText(activity, "Plugin downloaded. Installing silently...", Toast.LENGTH_SHORT).show();
                                        installProApkWithRoot(activity, apkFile);
                                    });
                                } else {
                                    throw new IOException("Failed to rename temporary file");
                                }
                            } catch (Exception e) {
                                if (tmpFile.exists()) tmpFile.delete();
                                activity.runOnUiThread(() -> {
                                    Toast.makeText(activity, "Plugin download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Plugin resolution failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    public static void startProDownloadAndInstall(final Activity activity) {
        if (activity == null) return;

        if (!com.waenhancer.utils.RootUtils.hasRootAccess()) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "Root access is needed. Please grant Root/Superuser permission to WaEnhancer in your root manager (e.g. KernelSU/Magisk).", Toast.LENGTH_LONG).show();
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("Root Access Required")
                    .setMessage("WaEnhancer requires Root/Superuser permission to install/update the Helper app silently.\n\nPlease open your Root Manager, grant root access to WaEnhancer, and try again.")
                    .setPositiveButton("OK", null)
                    .show();
            });
            return;
        }

        File cacheDir = activity.getCacheDir();
        File apkFile = new File(cacheDir, "helper.apk");
        if (apkFile.exists() && apkFile.length() > 1024) {
            installProApkWithRoot(activity, apkFile);
            return;
        }

        Context modContext = activity;
        boolean isXposed = !BuildConfig.APPLICATION_ID.equals(activity.getPackageName());
        
        if (isXposed) {
            try {
                modContext = activity.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
            } catch (Exception e) {
                android.util.Log.e("WaeX-Helper", "Error creating package context: " + e.getMessage());
            }
        }

        int layoutId = isXposed ? modContext.getResources().getIdentifier("bottom_sheet_update_progress", "layout", BuildConfig.APPLICATION_ID) : com.waenhancer.R.layout.bottom_sheet_update_progress;
        int bsTitleId = isXposed ? modContext.getResources().getIdentifier("bs_title", "id", BuildConfig.APPLICATION_ID) : com.waenhancer.R.id.bs_title;
        int progressBarId = isXposed ? modContext.getResources().getIdentifier("update_progress_bar", "id", BuildConfig.APPLICATION_ID) : com.waenhancer.R.id.update_progress_bar;
        int statusTextId = isXposed ? modContext.getResources().getIdentifier("update_status_text", "id", BuildConfig.APPLICATION_ID) : com.waenhancer.R.id.update_status_text;
        int cancelBtnId = isXposed ? modContext.getResources().getIdentifier("bs_cancel_btn", "id", BuildConfig.APPLICATION_ID) : com.waenhancer.R.id.bs_cancel_btn;

        if (layoutId == 0) {
            Toast.makeText(activity, "Error: Could not load progress layout", Toast.LENGTH_SHORT).show();
            return;
        }

        android.view.View dialogView = android.view.LayoutInflater.from(modContext).inflate(layoutId, null);
        var bsTitle = (com.google.android.material.textview.MaterialTextView) dialogView.findViewById(bsTitleId);
        var progressBar = (com.google.android.material.progressindicator.LinearProgressIndicator) dialogView.findViewById(progressBarId);
        var statusText = (com.google.android.material.textview.MaterialTextView) dialogView.findViewById(statusTextId);
        var cancelBtn = (com.google.android.material.button.MaterialButton) dialogView.findViewById(cancelBtnId);

        if (bsTitle != null) {
            bsTitle.setText(modContext.getString(com.waenhancer.R.string.downloading_plugin));
        }
        if (statusText != null) {
            statusText.setText("Resolving plugin download URL...");
        }

        final okhttp3.Call[] currentCall = {null};
        final com.google.android.material.bottomsheet.BottomSheetDialog dialog = com.waenhancer.ui.helpers.BottomSheetHelper.createStyledDialog(activity);
        dialog.setContentView(dialogView);
        dialog.setCanceledOnTouchOutside(false);

        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> {
                if (currentCall[0] != null) currentCall[0].cancel();
                dialog.dismiss();
            });
        }

        dialog.show();

        String url = Config.getBaseUrl() + "/api/v1/plugin/latest";
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .build();

        currentCall[0] = client.newCall(request);
        currentCall[0].enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                activity.runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                if (call.isCanceled()) return;
                if (!response.isSuccessful()) {
                    activity.runOnUiThread(() -> {
                        if (dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, "Unexpected HTTP code " + response.code()), Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                try {
                    String jsonStr = response.body().string();
                    org.json.JSONObject jsonObj = new org.json.JSONObject(jsonStr);
                    String downloadUrl = jsonObj.getString("download_url");

                    activity.runOnUiThread(() -> {
                        if (statusText != null) statusText.setText("Downloading plugin...");
                    });

                    okhttp3.Request downloadRequest = new okhttp3.Request.Builder()
                            .url(downloadUrl)
                            .build();

                    currentCall[0] = client.newCall(downloadRequest);
                    currentCall[0].enqueue(new okhttp3.Callback() {
                        @Override
                        public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                            if (call.isCanceled()) return;
                            activity.runOnUiThread(() -> {
                                if (dialog.isShowing()) dialog.dismiss();
                                Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                            if (call.isCanceled()) return;
                            if (!response.isSuccessful()) {
                                activity.runOnUiThread(() -> {
                                    if (dialog.isShowing()) dialog.dismiss();
                                    Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, "Unexpected HTTP code " + response.code()), Toast.LENGTH_LONG).show();
                                });
                                return;
                            }

                            File cacheDir = activity.getCacheDir();
                            File apkFile = new File(cacheDir, "helper.apk");
                            File tmpFile = new File(cacheDir, "helper.apk.tmp");

                            try (InputStream is = response.body().byteStream();
                                 FileOutputStream fos = new FileOutputStream(tmpFile)) {

                                long totalBytes = response.body().contentLength();
                                byte[] buffer = new byte[8192];
                                int read;
                                long currentBytes = 0;

                                while ((read = is.read(buffer)) != -1) {
                                    if (call.isCanceled()) return;
                                    fos.write(buffer, 0, read);
                                    currentBytes += read;
                                    int progress = (int) (currentBytes * 100 / (totalBytes > 0 ? totalBytes : 1));
                                    long finalCurrentBytes = currentBytes;
                                    activity.runOnUiThread(() -> {
                                        if (progressBar != null) progressBar.setProgress(progress);
                                        String sizeInfo = String.format(java.util.Locale.US, "%.1f MB / %.1f MB", 
                                            finalCurrentBytes / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0));
                                        if (statusText != null) statusText.setText(sizeInfo + " (" + progress + "%)");
                                    });
                                }
                                fos.flush();

                                if (tmpFile.renameTo(apkFile)) {
                                    activity.runOnUiThread(() -> {
                                        if (dialog.isShowing()) dialog.dismiss();
                                        installProApkWithRoot(activity, apkFile);
                                    });
                                } else {
                                    throw new IOException("Failed to rename temporary file");
                                }
                            } catch (Exception e) {
                                if (tmpFile.exists()) tmpFile.delete();
                                activity.runOnUiThread(() -> {
                                    if (dialog.isShowing()) dialog.dismiss();
                                    Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        if (dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, "Failed to resolve download URL: " + e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private static void installProApkWithRoot(final Activity activity, final File apkFile) {
        if (activity == null) return;
        
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(com.waenhancer.R.string.installing_plugin));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            String apkPath = apkFile.getAbsolutePath();
            String tmpPath = "/data/local/tmp/helper.apk";
            
            String copyCmd = "cp \"" + apkPath + "\" " + tmpPath + " && chmod 666 " + tmpPath;
            com.waenhancer.utils.RootUtils.runRootCommand(copyCmd);

            String cmd = "pm install -r -d --user 0 " + tmpPath;
            String result = com.waenhancer.utils.RootUtils.runRootCommand(cmd);
            
            com.waenhancer.utils.RootUtils.runRootCommand("rm " + tmpPath);

            boolean success = result != null && (result.toLowerCase().contains("success") || result.toLowerCase().contains("pkg:"));
            
            if (success) {
                com.waenhancer.utils.RootUtils.runRootCommand("pm grant com.waex.helper android.permission.POST_NOTIFICATIONS");
                com.waenhancer.utils.RootUtils.runRootCommand("appops set com.waex.helper POST_NOTIFICATION allow");
                com.waenhancer.utils.RootUtils.runRootCommand("am force-stop com.whatsapp");
                com.waenhancer.utils.RootUtils.runRootCommand("am force-stop com.whatsapp.w4b");
            }

            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                if (success) {
                    Toast.makeText(activity, com.waenhancer.R.string.install_success_restart, Toast.LENGTH_LONG).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            activity.startActivity(intent);
                        }
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(0);
                    }, 2000);
                } else {
                    String error = (result != null && !result.isEmpty()) ? result.trim() : "Unknown error";
                    Toast.makeText(activity, activity.getString(com.waenhancer.R.string.install_failed, error), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
