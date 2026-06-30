package com.waenhancer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.material.app.LocaleDelegate;

public class App extends Application {

    private static App instance;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Handler MainHandler = new Handler(Looper.getMainLooper());

    public static void showRequestStoragePermission(Activity activity) {
        com.waenhancer.ui.helpers.BottomSheetHelper.showConfirmation(
                activity,
                activity.getString(R.string.storage_permission),
                activity.getString(R.string.permission_storage),
                activity.getString(R.string.allow),
                false,
                () -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                        activity.startActivity(intent);
                    } else {
                        ActivityCompat.requestPermissions(activity,
                                new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE },
                                0);
                    }
                });
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        if (Application.getProcessName().equals(BuildConfig.APPLICATION_ID)) {
            if (!BuildConfig.DEBUG) {
                try {
                    boolean enableCrashAnalytics = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("enable_crash_analytics", false);
                    if (enableCrashAnalytics) {
                        Class<?> firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp");
                        firebaseAppClass.getMethod("initializeApp", Context.class).invoke(null, App.this);
                        
                        Class<?> firebaseAnalyticsClass = Class.forName("com.google.firebase.analytics.FirebaseAnalytics");
                        Object analyticsInstance = firebaseAnalyticsClass.getMethod("getInstance", Context.class).invoke(null, App.this);
                        firebaseAnalyticsClass.getMethod("setAnalyticsCollectionEnabled", boolean.class).invoke(analyticsInstance, true);
                        
                        Class<?> firebaseCrashlyticsClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics");
                        Object crashlyticsInstance = firebaseCrashlyticsClass.getMethod("getInstance").invoke(null);
                        firebaseCrashlyticsClass.getMethod("setCrashlyticsCollectionEnabled", boolean.class).invoke(crashlyticsInstance, true);
                    }
                } catch (Throwable ignored) {
                }
            }

            // Local expiration check (offline fail-safe)
            try {
                var sharedPrefs = PreferenceManager.getDefaultSharedPreferences(App.this);
                long expiresAt = 0;
                try {
                    expiresAt = sharedPrefs.getLong("expires_at", 0);
                } catch (ClassCastException e) {
                    try {
                        String expiresStr = sharedPrefs.getString("expires_at", "0");
                        expiresAt = Long.parseLong(expiresStr);
                    } catch (Exception ignored) {}
                }
                boolean isProVerified = sharedPrefs.getBoolean("is_pro_verified", false);
                if (isProVerified && expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
                    sharedPrefs.edit()
                            .putBoolean("is_pro_verified", false)
                            .remove("encrypted_config")
                            .putBoolean("message_bomber", false)
                            .putBoolean("delete_message_file", false)
                            .putBoolean("delete_message_file_sent", false)
                            .putString("floating_bottom_bar_pill_design", "regular")
                            .commit();
                    com.waenhancer.xposed.utils.ProHelper.setForceFree(true);
                    
                    com.waenhancer.xposed.utils.Utils.handleSubscriptionDowngrade(App.this, "Your subscription plan has expired.");
                    
                    try {
                        com.waenhancer.xposed.utils.LicenseManager.makePrefsWorldReadable(App.this);
                    } catch (Exception ignored) {}

                    try {
                        restartApp("com.whatsapp");
                    } catch (Exception ignored) {}
                    try {
                        restartApp("com.whatsapp.w4b");
                    } catch (Exception ignored) {}
                }
            } catch (Throwable ignored) {}

            // Perform silent background license re-verification at startup
            try {
                com.waenhancer.xposed.utils.LicenseManager.silentCheck(App.this);
            } catch (Exception e) {
                android.util.Log.e("WaeX-App", "Failed to invoke silentCheck", e);
            }
        }
        
        var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean("verify_blocked_contact", false)) {
            sharedPreferences.edit().putBoolean("verify_blocked_contact", false).apply();
        }

        // Resolve and store the Pro plugin APK path so that Xposed hooks can load it
        try {
            var pm = getPackageManager();
            var info = pm.getApplicationInfo("com.waex.helper", 0);
            if (info.sourceDir != null && new java.io.File(info.sourceDir).exists()) {
                sharedPreferences.edit()
                    .putString("pro_plugin_path", info.sourceDir)
                    .putString("pro_plugin_lib_path", info.nativeLibraryDir)
                    .apply();
                try {
                    com.waenhancer.xposed.utils.LicenseManager.makePrefsWorldReadable(this);
                } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-App", "Failed to resolve companion APK path", t);
        }

        // Initialize limited-free feature config. Run unconditionally — the pro plugin is now a
        // separate APK (com.waex.helper), so HAS_PRO_FEATURES may be false even when it is installed.
        // initLimitedFree() handles the "not available" case gracefully.
        try {
            com.waenhancer.xposed.utils.ProHelper.initLimitedFree(this, sharedPreferences);
        } catch (Throwable t) {
            android.util.Log.e("WaeX-App", "Failed to initialize LimitedFreeManager", t);
        }
        
        // Force create the preferences file if it doesn't exist so LSPosed file watcher doesn't fail
        File prefFile = new File(getApplicationInfo().dataDir, "shared_prefs/" + getPackageName() + "_preferences.xml");
        if (!prefFile.exists()) {
            sharedPreferences.edit().putBoolean("init_prefs_creation", true).commit();
        }
        
        var mode = Integer.parseInt(sharedPreferences.getString("thememode", "0"));
        setThemeMode(mode);
        changeLanguage(this);
        
        // Notify ContentProvider when preferences change locally
        sharedPreferences.registerOnSharedPreferenceChangeListener((prefs, key) -> {
            try {
                getContentResolver().notifyChange(
                    Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), 
                    null
                );
            } catch (Exception ignored) {}
        });

        // Suppress DeadSystemException/DeadSystemRuntimeException crashes from system server restarts
        final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Throwable t = throwable;
            boolean isDeadSystem = false;
            while (t != null) {
                String className = t.getClass().getName();
                if ("android.os.DeadSystemRuntimeException".equals(className) ||
                    "android.os.DeadSystemException".equals(className) ||
                    "android.os.DeadObjectException".equals(className)) {
                    isDeadSystem = true;
                    break;
                }
                t = t.getCause();
            }

            if (isDeadSystem) {
                android.util.Log.e("App", "Quietly ignoring DeadSystemException/DeadObjectException to prevent polluting Crashlytics", throwable);
                System.exit(0);
                return;
            }

            if (originalHandler != null) {
                originalHandler.uncaughtException(thread, throwable);
            }
        });
    }

    public static void setThemeMode(int mode) {
        switch (mode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    public static App getInstance() {
        return instance;
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    public static Handler getMainHandler() {
        return MainHandler;
    }

    public void restartApp(String packageWpp) {
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
        intent.putExtra("PKG", packageWpp);
        sendBroadcast(intent);
    }

    public static void changeLanguage(Context context) {
        var force = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("force_english", false);
        LocaleDelegate.setDefaultLocale(force ? Locale.ENGLISH : Locale.getDefault());
        var res = context.getResources();
        var config = res.getConfiguration();
        config.setLocale(LocaleDelegate.getDefaultLocale());
        // noinspection deprecation
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    public static File getWaEnhancerFolder() {
        var download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        var waEnhancerFolder = new File(download, "WaEnhancerX");
        if (!waEnhancerFolder.exists())
            waEnhancerFolder.mkdirs();
        return waEnhancerFolder;
    }

    public static boolean isOriginalPackage() {
        // Allow the official package and any debug build
        return BuildConfig.APPLICATION_ID.equals("com.waenhancer") || BuildConfig.DEBUG;
    }

}
