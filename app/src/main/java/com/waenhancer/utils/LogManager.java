package com.waenhancer.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.waenhancer.App;
import com.waenhancer.services.LogService;
import com.waenhancer.xposed.core.FeatureLoader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class LogManager {
    private static final String TAG = "WAE_LogManager";
    public static final String PREF_LOGGING_ENABLED = "logging_enabled";
    private static final String LOG_FILE_WPP = "whatsapp.log";
    private static final String LOG_FILE_BUSINESS = "whatsapp_business.log";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    
    private static final String[] LOG_PROVIDER_AUTHORITIES = new String[] {
            com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider",
            com.waenhancer.BuildConfig.APPLICATION_ID + ".provider"
    };
    
    private static final Object fileLock = new Object();

    public static boolean isLoggingEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_LOGGING_ENABLED, false);
    }

    public static void setLoggingEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_LOGGING_ENABLED, enabled).apply();
        if (enabled) {
            startService(context);
        } else {
            stopService(context);
        }
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }


    public static void stopService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        intent.setAction(LogService.ACTION_STOP);
        // We use startService to send the STOP action command
        context.startService(intent);
    }

    public static void addLog(String packageName, String message) {
        addLog(null, packageName, message);
    }

    public static void addLog(Context context, String packageName, String message) {
        String timestamp = dateFormat.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";
        appendLogsDirectly(packageName, logLine);
    }

    public static void appendLogsDirectly(String packageName, String content) {
        if (content == null || content.isEmpty()) return;
        File logFolder = getLogFolder(null);
        if (logFolder == null) return;

        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);
        
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(content);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "Failed to append logs: " + e.getMessage());
            }
        }
    }

    public static void addLogViaProvider(Context context, String packageName, String message) {
        if (context == null) return;
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("package", packageName);
            extras.putString("message", message);
            callProvider(context, "add_log", extras);
        } catch (Exception ignored) {}
    }

    private static android.os.Bundle callProvider(Context context, String method, android.os.Bundle extras) {
        for (String authority : LOG_PROVIDER_AUTHORITIES) {
            try {
                android.os.Bundle result = context.getContentResolver().call(
                        android.net.Uri.parse("content://" + authority), method, null, extras);
                if (result != null) return result;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String getLogs(String packageName) {
        File logFolder = getLogFolder(null);
        if (logFolder == null) return "Log storage not available";
        
        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);

        if (!logFile.exists() || logFile.length() == 0) return "";

        StringBuilder content = new StringBuilder();
        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            } catch (IOException e) {
                return "Error reading logs: " + e.getMessage();
            }
        }
        return content.toString();
    }

    public static void clearLogs(String packageName) {
        File logFolder = getLogFolder(null);
        if (logFolder == null) return;
        
        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);
        synchronized (fileLock) {
            if (logFile.exists()) logFile.delete();
        }
    }

    public static boolean hasRootAccess() {
        String output = runRootCommand("id");
        return output != null && output.contains("uid=0");
    }

    public static void clearRootLogcatBuffer() {
        runRootCommand("logcat -c");
    }

    public static File getLogFolder(Context context) {
        Context targetContext = context != null ? context : App.getInstance();
        if (targetContext == null) return null;

        File baseDir = targetContext.getExternalFilesDir(null);
        if (baseDir == null) baseDir = targetContext.getFilesDir();
        
        if (baseDir == null) return null;
        
        File logFolder = new File(baseDir, "logs");
        if (!logFolder.exists()) logFolder.mkdirs();
        return logFolder;
    }

    public static String runRootCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            return output.toString().trim();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
