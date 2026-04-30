package com.waenhancer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.waenhancer.App;
import com.waenhancer.xposed.core.FeatureLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.FileReader;

public class LogManager {
    public static final String PREF_LOGGING_ENABLED = "logging_enabled";
    private static final String LOG_FILE_WPP = "whatsapp.log";
    private static final String LOG_FILE_BUSINESS = "whatsapp_business.log";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static boolean isLoggingEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_LOGGING_ENABLED, false);
    }

    public static void setLoggingEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_LOGGING_ENABLED, enabled).apply();
    }

    public static void addLog(String packageName, String message) {
        addLog(null, packageName, message);
    }

    public static void addLog(Context context, String packageName, String message) {
        File cacheDir = (context != null) ? context.getCacheDir() : (App.getInstance() != null ? App.getInstance().getCacheDir() : null);
        if (cacheDir == null) return;

        File logFolder = new File(cacheDir, "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);

        String timestamp = dateFormat.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logLine);
        } catch (IOException e) {
            // Silently fail
        }
    }

    public static boolean isLoggingEnabledViaProvider(Context context) {
        if (context == null) return false;
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("key", PREF_LOGGING_ENABLED);
            android.os.Bundle result = context.getContentResolver().call(android.net.Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider"), "get_preference", null, extras);
            return result != null && result.getBoolean("value", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void addLogViaProvider(Context context, String packageName, String message) {
        if (context == null) return;
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("package", packageName);
            extras.putString("message", message);
            context.getContentResolver().call(android.net.Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider"), "add_log", null, extras);
        } catch (Exception e) {
            // Silently fail
        }
    }

    public static String getLogs(String packageName) {
        File logFolder = new File(App.getInstance().getCacheDir(), "logs");
        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);

        if (!logFile.exists()) {
            return "";
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            return "Error reading logs: " + e.getMessage();
        }
        return content.toString();
    }

    public static void clearLogs(String packageName) {
        File logFolder = new File(App.getInstance().getCacheDir(), "logs");
        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);
        if (logFile.exists()) {
            logFile.delete();
        }
    }
}
