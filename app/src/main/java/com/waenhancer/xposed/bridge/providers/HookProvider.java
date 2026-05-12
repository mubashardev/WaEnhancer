package com.waenhancer.xposed.bridge.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.waenhancer.xposed.bridge.service.HookBinder;

public class HookProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    private SharedPreferences getPrefs() {
        Context context = getContext();
        if (context == null) return null;
        // Explicitly target the default preferences file used by the UI
        String prefsName = context.getPackageName() + "_preferences";
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        ;
        
        int callingUid = android.os.Binder.getCallingUid();
        long token = android.os.Binder.clearCallingIdentity();
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                android.util.Log.e("WAE_Provider", "Failed to get SharedPreferences in HookProvider");
                return null;
            }
            if (method.equals("getHookBinder")) {
                Bundle result = new Bundle();
                result.putBinder("binder", HookBinder.getInstance());
                return result;
            }
            var context = getContext();
            if (context == null) {
                return null;
            }
            if ("add_log".equals(method) && extras != null) {
                if (!prefs.getBoolean("logging_enabled", false)) {
                    return Bundle.EMPTY;
                }
                String pkg = extras.getString("package");
                String msg = extras.getString("message");
                if (pkg != null && msg != null) {
                    com.waenhancer.utils.LogManager.addLog(context, pkg, msg);
                }
                return Bundle.EMPTY;
            }
            if ("record_event".equals(method) && extras != null) {
                String eventName = extras.getString("event_name");
                Bundle params = extras.getBundle("params");
                ;
                if (eventName != null) {
                    com.waenhancer.utils.AnalyticsManager.logEvent(context, eventName, params);
                }
                return Bundle.EMPTY;
            }
            if ("record_crash".equals(method) && extras != null) {
                String stacktrace = extras.getString("stacktrace");
                if (stacktrace != null && !stacktrace.isEmpty()) {
                    try {
                        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("WhatsApp Crash:\n" + stacktrace);
                        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(
                                new com.waenhancer.utils.WhatsAppCrashException("WhatsApp Crash: \n" + stacktrace));
                    } catch (Exception e) {
                        android.util.Log.e("WAE_Provider", "Failed to record crash to Crashlytics: " + e.getMessage());
                    }
                }
                return Bundle.EMPTY;
            }
            if ("get_preference".equals(method) && extras != null) {
                String key = extras.getString("key");
                Bundle result = new Bundle();
                if (key != null) {
                    Object value = prefs.getAll().get(key);
                    if (value instanceof Boolean) result.putBoolean("value", (Boolean) value);
                    else if (value instanceof String) result.putString("value", (String) value);
                    else if (value instanceof Integer) result.putInt("value", (Integer) value);
                    else if (value instanceof Long) result.putLong("value", (Long) value);
                    else if (value instanceof Float) result.putFloat("value", (Float) value);
                }
                return result;
            }
            if (method.equals("get_all_preferences")) {
                var all = prefs.getAll();
                ;
                // Dump keys for diagnosis
                for (String k : all.keySet()) {
                    ;
                }
                Bundle result = new Bundle();
                result.putSerializable("prefs", new HashMap<>(all));
                return result;
            }
            if ("put_preference".equals(method) && extras != null) {
                String key = extras.getString("key");
                String type = extras.getString("type");
                ;
                if (key == null || type == null) {
                    return null;
                }
                var editor = prefs.edit();
                switch (type) {
                    case "string":
                        editor.putString(key, extras.getString("value"));
                        break;
                    case "string_set":
                        var values = extras.getStringArrayList("value");
                        editor.putStringSet(key, values == null ? null : new HashSet<>(values));
                        break;
                    case "boolean":
                        editor.putBoolean(key, extras.getBoolean("value"));
                        break;
                    case "int":
                        editor.putInt(key, extras.getInt("value"));
                        break;
                    case "long":
                        editor.putLong(key, extras.getLong("value"));
                        break;
                    case "float":
                        editor.putFloat(key, extras.getFloat("value"));
                        break;
                    default:
                        return null;
                }
                editor.commit();
                context.getContentResolver().notifyChange(Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), null);
                return Bundle.EMPTY;
            }
            if ("remove_preference".equals(method) && extras != null) {
                String key = extras.getString("key");
                if (key != null) {
                    prefs.edit().remove(key).commit();
                    context.getContentResolver().notifyChange(Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), null);
                    return Bundle.EMPTY;
                }
            }
            if ("clear_preferences".equals(method)) {
                prefs.edit().clear().commit();
                context.getContentResolver().notifyChange(Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider/preferences"), null);
                return Bundle.EMPTY;
            }
            return null;
        } finally {
            android.os.Binder.restoreCallingIdentity(token);
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
