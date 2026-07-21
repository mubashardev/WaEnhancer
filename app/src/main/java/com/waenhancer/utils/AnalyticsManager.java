package com.waenhancer.utils;

import android.content.Context;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import com.waenhancer.App;
import com.waenhancer.BuildConfig;
import android.net.Uri;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsManager {

    private static final String TAG = "WAEX_Analytics";
    
    private static final String[] ANALYTICS_PROVIDER_AUTHORITIES = new String[] {
            BuildConfig.APPLICATION_ID + ".hookprovider",
            BuildConfig.APPLICATION_ID + ".provider"
    };

    /**
     * Log an event to Firebase Analytics.
     * Can be called from any process. No-op when {@link BuildConfig#FIREBASE_ENABLED} is false
     * (i.e. built without google-services.json) or when the user has not consented.
     */
    public static void logEvent(Context context, String eventName, Bundle params) {
        if (context == null || BuildConfig.DEBUG || !BuildConfig.FIREBASE_ENABLED) return;
        
        // If in main process, log directly
        if (context.getPackageName().equals(BuildConfig.APPLICATION_ID)) {
            // Check consent
            if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("enable_crash_analytics", false)) {
                return;
            }
            logDirectly(context, eventName, params);
        } else {
            // If in target process (WhatsApp), send via provider
            // The provider itself will also check the preference to be safe
            logViaProvider(context, eventName, params);
        }
    }


    private static void logDirectly(Context context, String eventName, Bundle params) {
        try {
            Class<?> firebaseAnalyticsClass = Class.forName("com.google.firebase.analytics.FirebaseAnalytics");
            Object instance = firebaseAnalyticsClass.getMethod("getInstance", Context.class).invoke(null, context);
            firebaseAnalyticsClass.getMethod("logEvent", String.class, Bundle.class).invoke(instance, eventName, params);
        } catch (Throwable ignored) {
            // Firebase might not be initialized or present
        }
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static void logViaProvider(Context context, String eventName, Bundle params) {
        // Capture context and params for the background task
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final Bundle eventParams = params != null ? new Bundle(params) : null;

        executor.execute(() -> {
            try {
                Bundle extras = new Bundle();
                extras.putString("event_name", eventName);
                extras.putBundle("params", eventParams);
                callProvider(appContext, "record_event", extras);
            } catch (Exception ignored) {}
        });
    }

    private static void callProvider(Context context, String method, Bundle extras) {
        for (String authority : ANALYTICS_PROVIDER_AUTHORITIES) {
            try {
                context.getContentResolver().call(
                        Uri.parse("content://" + authority), method, null, extras);
                return;
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Log a screen view event.
     */
    public static void logScreenView(Context context, String screenName) {
        Bundle params = new Bundle();
        params.putString("screen_name", screenName);
        logEvent(context, "screen_view", params);
    }
}