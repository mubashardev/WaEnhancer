package com.waenhancer.xposed.core;

import android.os.SystemClock;

import de.robv.android.xposed.XposedBridge;

public final class PerfLogger {
    private static final String TAG = "[WAE-PERF] ";
    private static final long DEFAULT_THRESHOLD_MS = 8L;
    private static volatile boolean enabled = false;

    private PerfLogger() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static long start() {
        if (!enabled) return 0L;
        return SystemClock.elapsedRealtimeNanos();
    }

    public static void end(String label, long startNs) {
        end(label, startNs, DEFAULT_THRESHOLD_MS);
    }

    public static void end(String label, long startNs, long thresholdMs) {
        if (!enabled || startNs <= 0L) return;
        long elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L;
        if (elapsedMs >= thresholdMs) {
            XposedBridge.log(TAG + label + " took " + elapsedMs + "ms");
        }
    }

    public static void log(String label, long elapsedMs, long thresholdMs) {
        if (!enabled || elapsedMs <= 0L) return;
        if (elapsedMs >= thresholdMs) {
            XposedBridge.log(TAG + label + " took " + elapsedMs + "ms");
        }
    }
}
