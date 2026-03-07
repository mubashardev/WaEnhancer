package com.wmods.wppenhacer.xposed.features.media;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallRecording extends Feature {

    private static final String TAG = "WaEnhancer_CallRecord";
    private final AtomicBoolean isCallConnected = new AtomicBoolean(false);

    // Single-threaded executor to handle disk IO sequentially without blocking the
    // audio engine
    private final ExecutorService diskIoExecutor = Executors.newSingleThreadExecutor();

    // File output streams
    private FileOutputStream incomingFos = null;
    private FileOutputStream outgoingFos = null;

    public CallRecording(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("call_recording_enable", false)) {
            XposedBridge.log("WaEnhancer: Call Recording is disabled");
            return;
        }

        XposedBridge.log("WaEnhancer: Initialize low-level Call Recording...");

        try {
            hookAudioTrack(classLoader);
            hookAudioRecord(classLoader);
            hookCallStateChanges();
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Error initializing Audio hooks - " + e.getMessage());
        }
    }

    private void hookAudioTrack(ClassLoader classLoader) {
        Class<?> audioTrackClass = XposedHelpers.findClass("android.media.AudioTrack", classLoader);

        XC_MethodHook writeHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (!isCallConnected.get())
                        return;

                    AudioTrack audioTrack = (AudioTrack) param.thisObject;

                    // Filter: Only hook Voice Call streams (Stream Type 0)
                    if (audioTrack.getStreamType() != AudioManager.STREAM_VOICE_CALL)
                        return;

                    Object audioData = param.args[0];
                    int size = -1;

                    if (param.getResult() != null) {
                        size = (Integer) param.getResult();
                    } else if (param.args.length > 2 && param.args[2] instanceof Integer) {
                        size = (Integer) param.args[2];
                    }

                    if (size <= 0)
                        return;

                    byte[] bytesToWrite = null;

                    if (audioData instanceof byte[]) {
                        byte[] data = (byte[]) audioData;
                        int offset = (Integer) param.args[1];
                        bytesToWrite = new byte[size];
                        System.arraycopy(data, offset, bytesToWrite, 0, size);
                    } else if (audioData instanceof short[]) {
                        short[] data = (short[]) audioData;
                        int offset = (Integer) param.args[1];
                        bytesToWrite = shortArrayToByteArray(data, offset, size);
                    } else if (audioData instanceof float[]) {
                        float[] data = (float[]) audioData;
                        int offset = (Integer) param.args[1];
                        bytesToWrite = floatArrayToByteArray(data, offset, size);
                    } else if (audioData instanceof ByteBuffer) {
                        ByteBuffer data = (ByteBuffer) audioData;
                        bytesToWrite = new byte[size];
                        int originalPosition = data.position();
                        data.get(bytesToWrite, 0, size);
                        data.position(originalPosition); // Restore position
                    }

                    if (bytesToWrite != null) {
                        writeIncomingPcm(bytesToWrite);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "AudioTrack hook error", e);
                }
            }
        };

        XposedBridge.hookAllMethods(audioTrackClass, "write", writeHook);
    }

    private void hookAudioRecord(ClassLoader classLoader) {
        Class<?> audioRecordClass = XposedHelpers.findClass("android.media.AudioRecord", classLoader);

        XC_MethodHook readHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!isCallConnected.get())
                        return;

                    Object result = param.getResult();
                    if (result == null)
                        return;

                    int bytesRead = (Integer) result;
                    if (bytesRead <= 0)
                        return;

                    Object audioData = param.args[0];
                    byte[] bytesToWrite = null;

                    if (audioData instanceof byte[]) {
                        byte[] data = (byte[]) audioData;
                        int offset = (Integer) param.args[1];
                        bytesToWrite = new byte[bytesRead];
                        System.arraycopy(data, offset, bytesToWrite, 0, bytesRead);
                    } else if (audioData instanceof short[]) {
                        short[] data = (short[]) audioData;
                        int offset = (Integer) param.args[1];
                        bytesToWrite = shortArrayToByteArray(data, offset, bytesRead);
                    } else if (audioData instanceof float[]) {
                        float[] data = (float[]) audioData;
                        int offset = (Integer) param.args[1];
                        bytesToWrite = floatArrayToByteArray(data, offset, bytesRead);
                    } else if (audioData instanceof ByteBuffer) {
                        ByteBuffer data = (ByteBuffer) audioData;
                        bytesToWrite = new byte[bytesRead];
                        int originalPosition = data.position();
                        data.position(0);
                        data.get(bytesToWrite, 0, bytesRead);
                        data.position(originalPosition);
                    }

                    if (bytesToWrite != null) {
                        writeOutgoingPcm(bytesToWrite);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "AudioRecord hook error", e);
                }
            }
        };

        XposedBridge.hookAllMethods(audioRecordClass, "read", readHook);
    }

    private void writeIncomingPcm(byte[] data) {
        diskIoExecutor.execute(() -> {
            try {
                if (incomingFos == null) {
                    File cacheDir = new File(FeatureLoader.mApp.getCacheDir(), "call_records");
                    cacheDir.mkdirs();
                    File file = new File(cacheDir, "incoming.pcm");
                    incomingFos = new FileOutputStream(file, true);
                }
                incomingFos.write(data);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write incoming PCM", e);
            }
        });
    }

    private void writeOutgoingPcm(byte[] data) {
        diskIoExecutor.execute(() -> {
            try {
                if (outgoingFos == null) {
                    File cacheDir = new File(FeatureLoader.mApp.getCacheDir(), "call_records");
                    cacheDir.mkdirs();
                    File file = new File(cacheDir, "outgoing.pcm");
                    outgoingFos = new FileOutputStream(file, true);
                }
                outgoingFos.write(data);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write outgoing PCM", e);
            }
        });
    }

    private void closeStreams() {
        diskIoExecutor.execute(() -> {
            try {
                if (incomingFos != null) {
                    incomingFos.flush();
                    incomingFos.close();
                    incomingFos = null;
                }
                if (outgoingFos != null) {
                    outgoingFos.flush();
                    outgoingFos.close();
                    outgoingFos = null;
                }

                File cacheDir = new File(FeatureLoader.mApp.getCacheDir(), "call_records");
                File incomingFile = new File(cacheDir, "incoming.pcm");
                File outgoingFile = new File(cacheDir, "outgoing.pcm");

                if (incomingFile.exists() || outgoingFile.exists()) {
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                            .format(new java.util.Date());
                    String fileName = "Call_Recording_" + timestamp + ".wav";
                    File wavFile = new File(cacheDir, fileName);

                    mixPcmFiles(incomingFile, outgoingFile, wavFile);

                    if (incomingFile.exists())
                        incomingFile.delete();
                    if (outgoingFile.exists())
                        outgoingFile.delete();

                    moveWavToPublic(wavFile);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to close and mix PCM streams", e);
            }
        });
    }

    private void mixPcmFiles(File incoming, File outgoing, File outputWav) throws java.io.IOException {
        java.io.FileInputStream inStream = incoming.exists() ? new java.io.FileInputStream(incoming) : null;
        java.io.FileInputStream outStream = outgoing.exists() ? new java.io.FileInputStream(outgoing) : null;
        FileOutputStream wavStream = new FileOutputStream(outputWav);

        // Placeholder for WAV header (44 bytes)
        wavStream.write(new byte[44]);

        byte[] inBuffer = new byte[2048];
        byte[] outBuffer = new byte[2048];

        int inRead = 0, outRead = 0;
        long totalDataLen = 0;

        while (true) {
            inRead = (inStream != null) ? inStream.read(inBuffer) : -1;
            outRead = (outStream != null) ? outStream.read(outBuffer) : -1;

            if (inRead == -1 && outRead == -1) {
                break;
            }

            int maxRead = Math.max(inRead > 0 ? inRead : 0, outRead > 0 ? outRead : 0);
            byte[] mixedBuffer = new byte[maxRead];

            for (int i = 0; i < maxRead; i += 2) {
                short inSample = 0;
                short outSample = 0;

                if (i + 1 < inRead) {
                    inSample = (short) ((inBuffer[i] & 0xFF) | (inBuffer[i + 1] << 8));
                }
                if (i + 1 < outRead) {
                    outSample = (short) ((outBuffer[i] & 0xFF) | (outBuffer[i + 1] << 8));
                }

                int mixedSample = inSample + outSample;

                // Hard clipping
                if (mixedSample > 32767)
                    mixedSample = 32767;
                else if (mixedSample < -32768)
                    mixedSample = -32768;

                mixedBuffer[i] = (byte) mixedSample;
                if (i + 1 < maxRead) {
                    mixedBuffer[i + 1] = (byte) (mixedSample >> 8);
                }
            }

            wavStream.write(mixedBuffer);
            totalDataLen += maxRead;
        }

        if (inStream != null)
            inStream.close();
        if (outStream != null)
            outStream.close();

        // Write actual WAV header
        writeWavHeader(wavStream, totalDataLen, 48000, 1, 16);
        wavStream.close();
    }

    private void writeWavHeader(FileOutputStream out, long totalAudioLen, int sampleRate, int channels, int bitRate)
            throws java.io.IOException {
        long totalDataLen = totalAudioLen + 36;
        long byteRate = sampleRate * channels * bitRate / 8;

        out.getChannel().position(0);

        byte[] header = new byte[44];
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitRate / 8);
        header[33] = 0;
        header[34] = (byte) bitRate;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header);
    }

    private void moveWavToPublic(File wavFile) {
        try {
            String settingsPath = prefs.getString("call_recording_path", android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            File parentDir = new File(settingsPath, "WA Call Recordings");
            if (!parentDir.exists())
                parentDir.mkdirs();

            File finalFile = new File(parentDir, wavFile.getName());

            java.io.FileInputStream in = new java.io.FileInputStream(wavFile);
            FileOutputStream out = new FileOutputStream(finalFile);

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            wavFile.delete();
            XposedBridge.log("WaEnhancer: Moved WAV to " + finalFile.getAbsolutePath());

            if (prefs.getBoolean("call_recording_toast", false)) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Utils.showToast("Call recording saved to " + finalFile.getName(), Toast.LENGTH_SHORT);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to move WAV to public dir", e);
        }
    }

    private byte[] shortArrayToByteArray(short[] shortArray, int offset, int sizeInShorts) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(sizeInShorts * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = offset; i < offset + sizeInShorts; i++) {
            byteBuffer.putShort(shortArray[i]);
        }
        return byteBuffer.array();
    }

    private byte[] floatArrayToByteArray(float[] floatArray, int offset, int sizeInFloats) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(sizeInFloats * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = offset; i < offset + sizeInFloats; i++) {
            byteBuffer.putFloat(floatArray[i]);
        }
        return byteBuffer.array();
    }

    private void hookCallStateChanges() {
        try {
            var clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                    "VoiceServiceEventCallback");
            if (clsCallEventCallback != null) {
                XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        handleCallEnded("fieldstatsReady");
                    }
                });

                XposedBridge.hookAllMethods(clsCallEventCallback, "soundPortCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("WaEnhancer: VoIP Call Connected. Starting PCM capture.");
                        isCallConnected.set(true);
                    }
                });
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoiceServiceEventCallback: " + e.getMessage());
        }

        try {
            var voipActivityClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.Contains,
                    "VoipActivity");
            if (voipActivityClass != null && Activity.class.isAssignableFrom(voipActivityClass)) {
                XposedBridge.hookAllMethods(voipActivityClass, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handleCallEnded("VoipActivity.onDestroy");
                    }
                });
            }
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: Could not hook VoipActivity: " + e.getMessage());
        }
    }

    private void handleCallEnded(@NonNull String reason) {
        if (isCallConnected.getAndSet(false)) {
            XposedBridge.log("WaEnhancer: Call ended by " + reason + ". Closing PCM streams and mixing.");
            closeStreams();
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Call Recording PCM Hook";
    }
}
