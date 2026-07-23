package com.waenhancer.xposed.bridge.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.waenhancer.BuildConfig;
import com.waenhancer.activities.ForceStartActivity;
import com.waenhancer.xposed.bridge.WaeIIFace;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.utils.Utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;

public class ProviderClient extends BaseClient {

    private final Context context;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Semaphore reconnectSemaphore = new Semaphore(1);
    private WaeIIFace service;
    private CompletableFuture<Boolean> continuation;

    public ProviderClient(Context context) {
        this.context = context;
    }

    @Override
    public WaeIIFace getService() {
        return service;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        if (service != null && service.asBinder().pingBinder()) {
            return CompletableFuture.completedFuture(true);
        }

        continuation = new CompletableFuture<>();

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            boolean connected = false;
            try {
                var currentActivity = WppCore.getCurrentActivity();
                if (currentActivity != null) {
                    var intent = new Intent();
                    intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, ForceStartActivity.class.getName()));
                    intent.putExtra("pkg", currentActivity.getPackageName());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    currentActivity.startActivity(intent);
                } else {
                    // Fallback: try to start from application context
                    var intent = new Intent();
                    intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, ForceStartActivity.class.getName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Utils.getApplication().startActivity(intent);
                }
            } catch (Exception e) {
                XposedBridge.log("ProviderClient: Failed to start ForceStartActivity: " + e.getMessage());
            }

            try {
                var resolver = Utils.getApplication().getContentResolver();
                var bundle = resolver.call(Settings.System.CONTENT_URI, "WaEnhancer X", "getHookBinder", null);
                var binder = bundle != null ? bundle.getBinder("binder") : null;
                if (binder != null) {
                    service = WaeIIFace.Stub.asInterface(binder);
                    if (service != null && service.asBinder().pingBinder()) {
                        ;

                        connected = true;
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("Error in Bridge: " + e.getMessage());
            }

            continuation.complete(connected);
            return continuation.join();
        });

        scheduler.schedule(() -> {
            if (!continuation.isDone()) {
                continuation.completeExceptionally(new Exception("Connection timed out"));
            }
        }, 3, TimeUnit.SECONDS);

        return future.exceptionally(ex -> false);
    }

    @Override
    public void tryReconnect() {
        if (service != null && service.asBinder().pingBinder()) return;
        reconnectSemaphore.acquireUninterruptibly();
        connect().whenComplete((canLoad, ex) -> {
            try {
                if (ex != null || !canLoad) {
                    /* Log removed */
                    Utils.doRestart(context);
                } else {
                    Utils.showToast("Reconnected to Bridge", Toast.LENGTH_SHORT);
                }
            } finally {
                reconnectSemaphore.release();
            }
        });
    }
}