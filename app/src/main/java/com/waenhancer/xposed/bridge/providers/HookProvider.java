package com.waenhancer.xposed.bridge.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.waenhancer.xposed.bridge.service.HookBinder;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.util.Log;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.waenhancer.BuildConfig;
import com.waenhancer.utils.AnalyticsManager;
import com.waenhancer.utils.WhatsAppCrashException;
import com.waenhancer.xposed.utils.LicenseManager;
import java.io.File;

public class HookProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    private SharedPreferences getPrefs() {
        Context context = getContext();
        if (context == null) return null;
        // Explicitly target the default preferences used by the UI to ensure 100% parity
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        ;
        
        int callingUid = Binder.getCallingUid();
        long token = Binder.clearCallingIdentity();
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                /* Log removed */
                return null;
            }
            if (method.equals("getHookBinder")) {
                Bundle result = new Bundle();
                result.putBinder("binder", HookBinder.getInstance());
                return result;
            }
            var context = getContext();
            if (context == null) {
                return null;
            }
            if ("record_event".equals(method) && extras != null) {
                if (!prefs.getBoolean("enable_crash_analytics", false)) {
                    return Bundle.EMPTY;
                }
                String eventName = extras.getString("event_name");
                Bundle params = extras.getBundle("params");
                if (eventName != null) {
                    AnalyticsManager.logEvent(context, eventName, params);
                }
                return Bundle.EMPTY;
            }
            if ("record_crash".equals(method) && extras != null) {
                if (BuildConfig.DEBUG || !prefs.getBoolean("enable_crash_analytics", false)) {
                    return Bundle.EMPTY;
                }
                String stacktrace = extras.getString("stacktrace");
                if (stacktrace != null && !stacktrace.isEmpty()) {
                    try {
                        // Dynamically check if Firebase is initialized first
                        Class<?> firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp");
                        try {
                            firebaseAppClass.getMethod("getInstance").invoke(null);
                        } catch (Exception e) {
                            // Try to initialize it if not initialized
                            firebaseAppClass.getMethod("initializeApp", Context.class).invoke(null, context.getApplicationContext());
                        }

                        /* Log removed */
                        FirebaseCrashlytics.getInstance().recordException(
                                new WhatsAppCrashException("WhatsApp Crash: \n" + stacktrace));
                    } catch (Throwable t) {
                        Log.e("WAEX_Provider", "Failed to record crash to Crashlytics: " + t.getMessage());
                    }
                }
                return Bundle.EMPTY;
            }

            if ("get_preference".equals(method) && extras != null) {
                String key = extras.getString("key");
                Bundle result = new Bundle();
                if (key != null) {
                    Object value = prefs.getAll().get(key);
                    if (value instanceof Boolean) result.putBoolean("value", (Boolean) value);
                    else if (value instanceof String) result.putString("value", (String) value);
                    else if (value instanceof Integer) result.putInt("value", (Integer) value);
                    else if (value instanceof Long) result.putLong("value", (Long) value);
                    else if (value instanceof Float) result.putFloat("value", (Float) value);
                }
                return result;
            }
            if ("get_pro_plugin_info".equals(method)) {
                Bundle result = new Bundle();
                try {
                    ApplicationInfo info =
                            context.getPackageManager().getApplicationInfo("com.waex.helper", PackageManager.GET_META_DATA);
                    if (info.sourceDir != null && new File(info.sourceDir).exists()) {
                        result.putString("sourceDir", info.sourceDir);
                        result.putString("nativeLibraryDir", info.nativeLibraryDir);
                        int minVersion = info.metaData != null ? info.metaData.getInt("min_waex_version", 0) : 0;
                        result.putInt("min_waex_version", minVersion);
                        prefs.edit()
                                .putString("pro_plugin_path", info.sourceDir)
                                .putString("pro_plugin_lib_path", info.nativeLibraryDir)
                                .putInt("min_waex_version", minVersion)
                                .commit();
                    }
                } catch (Throwable t) {
                    Log.e("WAEX_Provider", "Failed to resolve pro plugin info", t);
                }
                return result;
            }
            if (method.equals("get_all_preferences")) {
                var all = prefs.getAll();
                ;
                // Dump keys for diagnosis
                for (String k : all.keySet()) {
                    ;
                }
                Bundle result = new Bundle();
                result.putSerializable("prefs", new HashMap<>(all));
                return result;
            }
            if ("put_preference".equals(method) && extras != null) {
                String key = extras.getString("key");
                String type = extras.getString("type");
                ;
                if (key == null || type == null) {
                    return null;
                }
                var editor = prefs.edit();
                switch (type) {
                    case "string":
                        editor.putString(key, extras.getString("value"));
                        break;
                    case "string_set":
                        var values = extras.getStringArrayList("value");
                        editor.putStringSet(key, values == null ? null : new HashSet<>(values));
                        break;
                    case "boolean":
                        editor.putBoolean(key, extras.getBoolean("value"));
                        break;
                    case "int":
                        editor.putInt(key, extras.getInt("value"));
                        break;
                    case "long":
                        editor.putLong(key, extras.getLong("value"));
                        break;
                    case "float":
                        editor.putFloat(key, extras.getFloat("value"));
                        break;
                    default:
                        return null;
                }
                editor.commit();
                fixPermissions();
                context.getContentResolver().notifyChange(Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), null);
                return Bundle.EMPTY;
            }
            if ("remove_preference".equals(method) && extras != null) {
                String key = extras.getString("key");
                if (key != null) {
                    prefs.edit().remove(key).commit();
                    fixPermissions();
                    context.getContentResolver().notifyChange(Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), null);
                    return Bundle.EMPTY;
                }
            }
            if ("clear_preferences".equals(method)) {
                prefs.edit().clear().commit();
                fixPermissions();
                context.getContentResolver().notifyChange(Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), null);
                return Bundle.EMPTY;
            }
            if ("verify_license".equals(method) && extras != null) {
                String licenseKey = extras.getString("license_key");
                Bundle result = new Bundle();
                if (licenseKey != null && !licenseKey.isEmpty()) {
                    // Synchronous/blocking verification logic within the background thread of the ContentProvider
                    final Object lock = new Object();
                    final Bundle verificationResult = new Bundle();
                    
                    LicenseManager.verifyLicense(context, licenseKey, new LicenseManager.LicenseCallback() {
                        @Override
                        public void onSuccess(String encryptedConfig) {
                            synchronized (lock) {
                                verificationResult.putBoolean("success", true);
                                verificationResult.putString("encrypted_config", encryptedConfig);
                                lock.notify();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            synchronized (lock) {
                                verificationResult.putBoolean("success", false);
                                verificationResult.putString("message", message);
                                lock.notify();
                            }
                        }
                    });
                    
                    synchronized (lock) {
                        try {
                            lock.wait(20000); // Wait up to 20 seconds for API response
                        } catch (InterruptedException ignored) {}
                    }
                    
                    result.putAll(verificationResult);
                } else {
                    result.putBoolean("success", false);
                    result.putString("message", "License key is empty.");
                }
                return result;
            }
            if ("unlink_device".equals(method)) {
                final Object lock = new Object();
                final Bundle unlinkResult = new Bundle();
                
                LicenseManager.unlinkDevice(context, new LicenseManager.UnlinkCallback() {
                    @Override
                    public void onSuccess() {
                        synchronized (lock) {
                            unlinkResult.putBoolean("success", true);
                            lock.notify();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        synchronized (lock) {
                            unlinkResult.putBoolean("success", false);
                            unlinkResult.putString("message", message);
                            lock.notify();
                        }
                    }
                });
                
                synchronized (lock) {
                    try {
                        lock.wait(20000);
                    } catch (InterruptedException ignored) {}
                }
                
                return unlinkResult;
            }
            return null;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    private void fixPermissions() {
        try {
            Context context = getContext();
            if (context == null) return;
            File dataDir = new File(context.getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, context.getPackageName() + "_preferences.xml");
            
            dataDir.setExecutable(true, false);
            dataDir.setReadable(true, false);
            
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            
            prefsFile.setReadable(true, false);
        } catch (Throwable t) {
            Log.e("WAEX_HookProvider", "Failed to fix permissions", t);
        }
    }
}