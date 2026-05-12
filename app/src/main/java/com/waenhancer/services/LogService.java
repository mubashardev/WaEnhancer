package com.waenhancer.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.waenhancer.R;
import com.waenhancer.activities.LogsActivity;
import com.waenhancer.utils.LogManager;
import com.waenhancer.xposed.core.FeatureLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogService extends Service {
    private static final String TAG = "WAE_LogService";
    private static final String CHANNEL_ID = "log_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_STOP = "com.waenhancer.ACTION_STOP_LOGGING";

    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    private Process currentLogcatProcess = null;
    
    private static final Pattern LOGCAT_LINE_PATTERN = Pattern.compile("^([VDIWEAF])/([\\w\\.\\$]+)\\s*\\(\\s*(\\d+)\\):\\s?(.*)$");

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ;
            LogManager.setLoggingEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            isRunning = true;
            startForeground(NOTIFICATION_ID, createNotification());
            startStreaming();
            ;
        }

        return START_STICKY;
    }

    private void startStreaming() {
        executorService.execute(this::runLogcatStream);
    }

    private void stopStreaming() {
        isRunning = false;
        if (currentLogcatProcess != null) {
            currentLogcatProcess.destroy();
            currentLogcatProcess = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void runLogcatStream() {
        LogManager.appendLogsDirectly(FeatureLoader.PACKAGE_WPP, "[ui][I] === Capture Service Started ===\n");
        
        while (isRunning) {
            try {
                updatePids();
                currentLogcatProcess = new ProcessBuilder("su", "-c", "logcat -v brief -T 1").start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentLogcatProcess.getInputStream()))) {
                    String line;
                    while (isRunning && (line = reader.readLine()) != null) {
                        processLogLine(line);
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    LogManager.appendLogsDirectly(FeatureLoader.PACKAGE_WPP, "[ui][E] Service Error: " + e.getMessage() + "\n");
                }
            } finally {
                if (currentLogcatProcess != null) {
                    currentLogcatProcess.destroy();
                    currentLogcatProcess = null;
                }
            }
            
            if (isRunning) {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
        LogManager.appendLogsDirectly(FeatureLoader.PACKAGE_WPP, "[ui][I] === Capture Service Stopped ===\n");
    }

    private final Set<String> wppPids = new HashSet<>();
    private final Set<String> businessPids = new HashSet<>();
    private long lastPidUpdate = 0;

    private void processLogLine(String line) {
        if (line == null || line.trim().isEmpty() || line.startsWith("---------")) return;

        if (System.currentTimeMillis() - lastPidUpdate > 15000) {
            updatePids();
        }

        Matcher matcher = LOGCAT_LINE_PATTERN.matcher(line.trim());
        if (matcher.matches()) {
            String level = matcher.group(1);
            String tag = matcher.group(2);
            String pid = matcher.group(3);
            String message = matcher.group(4);

            String formattedLine = "[logcat][" + level + "][" + tag + "] " + message;

            if (wppPids.contains(pid)) {
                LogManager.appendLogsDirectly(FeatureLoader.PACKAGE_WPP, formattedLine + "\n");
            } else if (businessPids.contains(pid)) {
                LogManager.appendLogsDirectly(FeatureLoader.PACKAGE_BUSINESS, formattedLine + "\n");
            }
        } else {
            String lowerLine = line.toLowerCase();
            if (lowerLine.contains("com.whatsapp") && !lowerLine.contains("waenhancer")) {
                LogManager.appendLogsDirectly(FeatureLoader.PACKAGE_WPP, "[logcat][?] " + line + "\n");
            }
        }
    }

    private void updatePids() {
        wppPids.clear();
        businessPids.clear();
        addPidsFromCommand("pidof " + FeatureLoader.PACKAGE_WPP, wppPids);
        addPidsFromCommand("pidof " + FeatureLoader.PACKAGE_BUSINESS, businessPids);
        if (wppPids.isEmpty()) addPidsFromCommand("pgrep -f " + FeatureLoader.PACKAGE_WPP, wppPids);
        if (businessPids.isEmpty()) addPidsFromCommand("pgrep -f " + FeatureLoader.PACKAGE_BUSINESS, businessPids);
        lastPidUpdate = System.currentTimeMillis();
    }

    private void addPidsFromCommand(String command, Set<String> pidSet) {
        try {
            Process p = new ProcessBuilder("su", "-c", command).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    for (String part : line.split("\\s+")) {
                        if (part.matches("\\d+")) pidSet.add(part);
                    }
                }
            }
            p.destroy();
        } catch (Exception ignored) {}
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, LogsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, LogService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.log_service_title))
                .setContentText(getString(R.string.log_service_desc))
                .setSmallIcon(R.drawable.ic_logs)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_close, getString(R.string.stop_logging), stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Log Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }
}
