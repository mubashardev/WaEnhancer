package com.waenhancer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.waenhancer.App;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogManager {
    public static final String PREF_LOGGING_ENABLED = "logging_enabled";
    private static final String LOG_FILE_WPP = "whatsapp.log";
    private static final String LOG_FILE_BUSINESS = "whatsapp_business.log";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final String[] LOG_PROVIDER_AUTHORITIES = new String[] {
            com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider",
            com.waenhancer.BuildConfig.APPLICATION_ID + ".provider"
    };
    private static final Pattern LOGCAT_LINE_PATTERN = Pattern.compile("^([VDIWEAF])/([^\\(]+)\\(\\s*\\d+\\):\\s?(.*)$");

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
            android.os.Bundle result = callProvider(context, "get_preference", extras);
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
            callProvider(context, "add_log", extras);
        } catch (Exception e) {
            // Silently fail
        }
    }

    private static android.os.Bundle callProvider(Context context, String method, android.os.Bundle extras) {
        for (String authority : LOG_PROVIDER_AUTHORITIES) {
            try {
                android.os.Bundle result = context.getContentResolver().call(
                        android.net.Uri.parse("content://" + authority), method, null, extras);
                if (result != null) {
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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

    public static boolean hasRootAccess() {
        String output = runRootCommand("id");
        return output != null && output.contains("uid=0");
    }

    public static void clearRootLogcatBuffer() {
        runRootCommand("logcat -c");
    }

    public static void captureRootLogs(String packageName) {
        String targetPackage = FeatureLoader.PACKAGE_BUSINESS.equals(packageName)
                ? FeatureLoader.PACKAGE_BUSINESS
                : FeatureLoader.PACKAGE_WPP;
        String pidOutput = runRootCommand("pidof " + targetPackage);
        if (pidOutput == null) {
            return;
        }

        String[] pids = pidOutput.trim().split("\\s+");
        if (pids.length == 0 || pids[0].isEmpty()) {
            return;
        }
        String pid = pids[0];

        String rawLogcat = runRootCommand("logcat -d -v brief --pid=" + pid);
        if (rawLogcat == null) {
            return;
        }

        String normalizedLogs = normalizeLogcat(rawLogcat);
        replaceLogs(packageName, normalizedLogs);
    }

    private static String normalizeLogcat(String rawLogcat) {
        if (rawLogcat == null || rawLogcat.isEmpty()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        String[] lines = rawLogcat.split("\n");
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("---------")) continue;

            Matcher matcher = LOGCAT_LINE_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                String level = matcher.group(1);
                String tag = matcher.group(2).trim();
                String message = matcher.group(3);
                normalized.append("[logcat][").append(level).append("][").append(tag).append("] ")
                        .append(message).append("\n");
            } else {
                normalized.append("[logcat][I][RAW] ").append(trimmed).append("\n");
            }
        }
        return normalized.toString().trim();
    }

    private static void replaceLogs(String packageName, String content) {
        File cacheDir = App.getInstance() != null ? App.getInstance().getCacheDir() : null;
        if (cacheDir == null) return;

        File logFolder = new File(cacheDir, "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        String fileName = FeatureLoader.PACKAGE_BUSINESS.equals(packageName) ? LOG_FILE_BUSINESS : LOG_FILE_WPP;
        File logFile = new File(logFolder, fileName);
        try (FileWriter writer = new FileWriter(logFile, false)) {
            writer.write(content == null ? "" : content);
        } catch (IOException ignored) {
        }
    }

    private static String runRootCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(12, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 && output.length() == 0) {
                return null;
            }
            return output.toString().trim();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
