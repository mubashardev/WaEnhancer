package com.waenhancer.xposed.features.media;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;


import com.waenhancer.xposed.bridge.WaeIIFace;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONArray;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallRecording extends Feature {
    private static final int MENU_ID_MANAGE_RECORDINGS = 0x7EAD0011;
    private static final String CALLS_HISTORY_FRAGMENT = "CallsHistoryFragment";


    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isCallConnected = new AtomicBoolean(false);
    private final AtomicReference<MediaRecorder> mediaRecorderRef = new AtomicReference<>();
    private final AtomicReference<ParcelFileDescriptor> outputPfdRef = new AtomicReference<>();
    private final AtomicReference<FileOutputStream> outputStreamRef = new AtomicReference<>();
    private final AtomicReference<File> outputFileRef = new AtomicReference<>();
    private final AtomicReference<FMessageWpp.UserJid> currentUserJid = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> delayedStartFuture = new AtomicReference<>();

    private final ScheduledExecutorService delayedStartScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "WaEnhancer-CallDelayedStart");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean permissionGranted = new AtomicBoolean(false);

    public CallRecording(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        logDebug("WAEX: Call Recording feature initializing...");
        hookCallStateChanges();
    }

    private void openRecordingsManager(@NonNull Activity activity) {
        try {
            Intent intent = new Intent();
            intent.setClassName(com.waenhancer.BuildConfig.APPLICATION_ID, "com.waenhancer.activities.RecordingsActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            Utils.showToast("Failed to open Recordings Manager", Toast.LENGTH_SHORT);
            logDebug("WAEX: Failed to open recordings manager: " + t.getMessage());
        }
    }

    private void hookCallStateChanges() {
        int hooksInstalled = 0;

        try {
            Class<?> clsCallEventCallback = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "VoiceServiceEventCallback"
            );
            if (clsCallEventCallback != null) {
                logDebug("WAEX: Found VoiceServiceEventCallback: " + clsCallEventCallback.getName());

                try {
                    XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            handleCallEnded("fieldstatsReady");
                        }
                    });
                    hooksInstalled++;
                } catch (Throwable e) {
                    logDebug("WAEX: Could not hook fieldstatsReady: " + e.getMessage());
                }

                XposedBridge.hookAllMethods(clsCallEventCallback, "soundPortCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        reloadPrefs();
                        if (!prefs.getBoolean("call_recording_enable", false)) {
                            return;
                        }
                        logDebug("WAEX: soundPortCreated - will record after 3s");
                        extractUserJid(param.thisObject);
                        isCallConnected.set(true);
                        scheduleDelayedStart();
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            logDebug("WAEX: Could not hook VoiceServiceEventCallback: " + e.getMessage());
        }

        try {
            Class<?> voipActivityClass = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.Contains,
                    "VoipActivity"
            );
            if (voipActivityClass != null && android.app.Activity.class.isAssignableFrom(voipActivityClass)) {
                logDebug("WAEX: Found VoipActivity: " + voipActivityClass.getName());

                XposedBridge.hookAllMethods(voipActivityClass, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handleCallEnded("VoipActivity.onDestroy");
                    }
                });
                hooksInstalled++;
            }
        } catch (Throwable e) {
            logDebug("WAEX: Could not hook VoipActivity: " + e.getMessage());
        }

        // Intentionally avoid hooking HomeActivity's options menu here.
        // That path is exercised during native tab changes and reflective
        // fragment scanning in the menu hook was introducing visible jank
        // while swiping WhatsApp's home tabs.

        logDebug("WAEX: Call Recording initialized with " + hooksInstalled + " hooks");
    }

    private void handleCallEnded(@NonNull String reason) {
        logDebug("WAEX: Call ended by " + reason);
        isCallConnected.set(false);
        cancelDelayedStart();
        stopRecording();
    }

    private void scheduleDelayedStart() {
        cancelDelayedStart();
        ScheduledFuture<?> future = delayedStartScheduler.schedule(() -> {
            if (!isCallConnected.get()) {
                logDebug("WAEX: Delayed start cancelled, call not connected");
                return;
            }
            if (isRecording.get()) {
                logDebug("WAEX: Delayed start ignored, already recording");
                return;
            }
            startRecording();
        }, 3, TimeUnit.SECONDS);
        delayedStartFuture.set(future);
    }

    private void cancelDelayedStart() {
        ScheduledFuture<?> future = delayedStartFuture.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void extractUserJid(Object callback) {
        if (callback == null) {
            return;
        }

        try {
            Object callInfo = XposedHelpers.callMethod(callback, "getCallInfo");
            if (callInfo == null) {
                return;
            }

            Object peerJid = null;
            try {
                peerJid = XposedHelpers.getObjectField(callInfo, "peerJid");
            } catch (Throwable ignored) {
            }

            if (peerJid != null) {
                FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(peerJid);
                if (!userJid.isNull()) {
                    currentUserJid.set(userJid);
                    logDebug("WAEX: Found phone from UserJid: " + userJid.getPhoneNumber());
                    return;
                }
            }

            Object participantsObj = null;
            try {
                participantsObj = XposedHelpers.getObjectField(callInfo, "participants");
            } catch (Throwable ignored) {
            }

            if (participantsObj instanceof Map<?, ?> participants) {
                for (Object key : participants.keySet()) {
                    FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(key);
                    if (!userJid.isNull()) {
                        currentUserJid.set(userJid);
                        logDebug("WAEX: Found phone from single participant: " + userJid.getPhoneNumber());
                        return;
                    }
                }
            }
        } catch (Throwable e) {
            logDebug("WAEX: extractUserJid error: " + e.getMessage());
        }
    }

    private void grantVoiceCallPermission() {
        if (permissionGranted.get()) {
            return;
        }

        try {
            String packageName = FeatureLoader.mApp.getPackageName();
            logDebug("WAEX: Granting CAPTURE_AUDIO_OUTPUT via root");

            String[] commands = {
                    "pm grant " + packageName + " android.permission.CAPTURE_AUDIO_OUTPUT",
                    "appops set " + packageName + " RECORD_AUDIO allow",
            };

            for (String cmd : commands) {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    int exitCode = process.waitFor();
                    logDebug("WAEX: " + cmd + " exit: " + exitCode);
                } catch (Exception e) {
                    logDebug("WAEX: Root failed: " + e.getMessage());
                }
            }

            permissionGranted.set(true);
        } catch (Throwable e) {
            logDebug("WAEX: grantVoiceCallPermission error: " + e.getMessage());
        }
    }

    private synchronized void startRecording() {
        if (isRecording.get()) {
            logDebug("WAEX: Already recording");
            return;
        }

        FMessageWpp.UserJid userJid = currentUserJid.get();
        if (userJid != null && !shouldRecord(userJid.getPhoneNumber())) {
            logDebug("WAEX: Skipping recording due to privacy settings for: " + userJid.getPhoneNumber());
            return;
        }

        if (!isCallConnected.get()) {
            logDebug("WAEX: Skipping recording, call is not connected");
            return;
        }

        try {
            if (ContextCompat.checkSelfPermission(
                    FeatureLoader.mApp,
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {
                logDebug("WAEX: No RECORD_AUDIO permission");
                return;
            }

            WaeIIFace bridge = null;
            try {
                bridge = WppCore.getClientBridge();
            } catch (Throwable t) {
                logDebug("WAEX: Could not get client bridge: " + t.getMessage());
            }

            String packageName = FeatureLoader.mApp.getPackageName();
            String appName = packageName.contains("w4b") ? "WA Business" : "WhatsApp";
            String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String settingsPath = prefs.getString("call_recording_path", defaultPath);

            File parentDir = new File(settingsPath, "WA Call Recordings");
            File appDir = new File(parentDir, appName);
            if (bridge != null && !appDir.exists() && !appDir.mkdirs()) {
                boolean dirCreated = bridge.createDir(appDir.getAbsolutePath());
                if (!dirCreated && !appDir.exists()) {
                    throw new IOException("Could not create output directory: " + appDir.getAbsolutePath());
                }
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "Call_" + timestamp + ".m4a";
            if (userJid != null) {
                String contactName = WppCore.getContactName(userJid);
                fileName = contactName.isEmpty()
                        ? "Call_" + userJid.getPhoneNumber() + "_" + timestamp + ".m4a"
                        : "Call_" + contactName + "_" + timestamp + ".m4a";
            }

            OutputTarget outputTarget = openOutputTarget(bridge, appDir, fileName);
            outputFileRef.set(outputTarget.file);
            outputPfdRef.set(outputTarget.parcelFileDescriptor);
            outputStreamRef.set(outputTarget.outputStream);

            if (prefs.getBoolean("call_recording_use_root", false)) {
                grantVoiceCallPermission();
            }

            int[] audioSources = new int[]{
                    MediaRecorder.AudioSource.VOICE_CALL,
                    MediaRecorder.AudioSource.VOICE_UPLINK,
                    MediaRecorder.AudioSource.VOICE_DOWNLINK,
                    6,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC
            };
            String[] sourceNames = new String[]{
                    "VOICE_CALL",
                    "VOICE_UPLINK",
                    "VOICE_DOWNLINK",
                    "VOICE_RECOGNITION",
                    "VOICE_COMMUNICATION",
                    "MIC"
            };

            MediaRecorder selectedRecorder = null;
            String usedSource = "none";

            for (int i = 0; i < audioSources.length; i++) {
                MediaRecorder testRecorder = new MediaRecorder();
                try {
                    logDebug("WAEX: Trying " + sourceNames[i]);
                    testRecorder.setAudioSource(audioSources[i]);
                    testRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    testRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    testRecorder.setAudioEncodingBitRate(96000);
                    testRecorder.setAudioSamplingRate(44100);
                    testRecorder.setOutputFile(outputTarget.fd);
                    testRecorder.prepare();
                    testRecorder.start();

                    selectedRecorder = testRecorder;
                    usedSource = sourceNames[i];
                    logDebug("WAEX: SUCCESS " + sourceNames[i]);
                    break;
                } catch (Exception e) {
                    logDebug("WAEX: FAILED " + sourceNames[i] + ": " + e.getMessage());
                    try {
                        testRecorder.reset();
                        testRecorder.release();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (selectedRecorder == null) {
                logDebug("WAEX: All audio sources failed");
                closeOutputResources(false);
                return;
            }

            mediaRecorderRef.set(selectedRecorder);
            if (!isRecording.compareAndSet(false, true)) {
                try {
                    selectedRecorder.stop();
                } catch (RuntimeException ignored) {
                }
                selectedRecorder.reset();
                selectedRecorder.release();
                mediaRecorderRef.set(null);
                closeOutputResources(false);
                return;
            }

            logDebug("WAEX: Recording started (" + usedSource + "): " + outputTarget.file.getAbsolutePath());
            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast("Recording started", Toast.LENGTH_SHORT);
            }
        } catch (Exception e) {
            logDebug("WAEX: startRecording error: " + e.getMessage());
            isRecording.set(false);
            MediaRecorder recorder = mediaRecorderRef.getAndSet(null);
            if (recorder != null) {
                try {
                    recorder.reset();
                    recorder.release();
                } catch (Throwable ignored) {
                }
            }
            closeOutputResources(true);
        }
    }

    private synchronized void stopRecording() {
        cancelDelayedStart();
        if (!isRecording.getAndSet(false)) {
            return;
        }

        boolean saved = false;
        try {
            MediaRecorder recorder = mediaRecorderRef.getAndSet(null);
            if (recorder != null) {
                try {
                    recorder.stop();
                    saved = true;
                } catch (RuntimeException e) {
                    logDebug("WAEX: MediaRecorder stop exception (no valid audio data received)");
                } finally {
                    try {
                        recorder.reset();
                        recorder.release();
                    } catch (Exception ignored) {
                    }
                }
            }

            File outputFile = outputFileRef.getAndSet(null);
            closeOutputResources(!saved);
            logDebug("WAEX: Recording stopped, file=" + (outputFile != null ? outputFile.getAbsolutePath() : "unknown"));

            if (saved && outputFile != null) {
                Utils.scanFile(outputFile);
            }
            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast(saved ? "Recording saved!" : "Recording failed", Toast.LENGTH_SHORT);
            }

            currentUserJid.set(null);
        } catch (Exception e) {
            logDebug("WAEX: stopRecording error: " + e.getMessage());
            closeOutputResources(false);
            outputFileRef.set(null);
        }
    }

    private void closeOutputResources(boolean deleteOutputFile) {
        FileOutputStream stream = outputStreamRef.getAndSet(null);
        ParcelFileDescriptor pfd = outputPfdRef.getAndSet(null);
        File outputFile = outputFileRef.getAndSet(null);

        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
        }
        if (deleteOutputFile && outputFile != null && outputFile.exists() && !outputFile.delete()) {
            logDebug("WAEX: Could not delete incomplete recording: " + outputFile.getAbsolutePath());
        }
    }

    @NonNull
    private OutputTarget openOutputTarget(
            WaeIIFace bridge,
            @NonNull File preferredDir,
            @NonNull String fileName
    ) throws IOException {
        File preferredFile = new File(preferredDir, fileName);
        if (bridge != null) {
            try {
                ParcelFileDescriptor parcelFileDescriptor = bridge.openFile(preferredFile.getAbsolutePath(), true);
                if (parcelFileDescriptor != null) {
                    return new OutputTarget(
                            preferredFile,
                            parcelFileDescriptor,
                            null,
                            parcelFileDescriptor.getFileDescriptor()
                    );
                }
                logDebug("WAEX: Bridge openFile returned null, fallback to Android/data path");
            } catch (Throwable t) {
                logDebug("WAEX: Bridge openFile failed, fallback to Android/data path: " + t.getMessage());
            }
        }

        File appExternalDir = FeatureLoader.mApp.getExternalFilesDir(null);
        if (appExternalDir == null) {
            throw new IOException("Could not resolve app external files directory");
        }

        File fallbackDir = new File(appExternalDir, "Recordings");
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
            throw new IOException("Could not create fallback recording directory: " + fallbackDir.getAbsolutePath());
        }

        File fallbackFile = new File(fallbackDir, fileName);
        FileOutputStream fallbackStream = new FileOutputStream(fallbackFile);
        logDebug("WAEX: Recording fallback path in Android/data: " + fallbackFile.getAbsolutePath());
        return new OutputTarget(fallbackFile, null, fallbackStream, fallbackStream.getFD());
    }

    private boolean shouldRecord(String phoneNumber) {
        try {
            int mode = 0;
            try {
                mode = Integer.parseInt(prefs.getString("call_recording_mode", "0"));
            } catch (NumberFormatException ignored) {
            }

            if (mode == 0 || mode == 1) {
                return true;
            }

            String blacklist = prefs.getString("call_recording_blacklist", "[]");
            String whitelist = prefs.getString("call_recording_whitelist", "[]");
            if (mode == 2) {
                if (phoneNumber == null) {
                    return true;
                }
                String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
                return !isNumberInList(cleanPhone, blacklist);
            }

            if (mode == 3) {
                if (TextUtils.isEmpty(whitelist) || "[]".equals(whitelist)) {
                    return false;
                }
                if (phoneNumber == null) {
                    return false;
                }
                String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
                return isNumberInList(cleanPhone, whitelist);
            }
        } catch (Exception e) {
            logDebug("WAEX: shouldRecord check error: " + e.getMessage());
        }
        return true;
    }

    private boolean isNumberInList(String phone, String jsonList) {
        if (TextUtils.isEmpty(jsonList) || "[]".equals(jsonList)) {
            return false;
        }

        try {
            JSONArray array = new JSONArray(jsonList);
            for (int i = 0; i < array.length(); i++) {
                String num = array.getString(i).replaceAll("[^0-9]", "");
                if (num.equals(phone)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logDebug("WAEX: Error parsing list: " + e.getMessage());
        }
        return false;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Call Recording";
    }

    private static final class OutputTarget {
        @NonNull
        private final File file;
        private final ParcelFileDescriptor parcelFileDescriptor;
        private final FileOutputStream outputStream;
        @NonNull
        private final java.io.FileDescriptor fd;

        private OutputTarget(
                @NonNull File file,
                ParcelFileDescriptor parcelFileDescriptor,
                FileOutputStream outputStream,
                @NonNull java.io.FileDescriptor fd
        ) {
            this.file = file;
            this.parcelFileDescriptor = parcelFileDescriptor;
            this.outputStream = outputStream;
            this.fd = fd;
        }
    }
}
