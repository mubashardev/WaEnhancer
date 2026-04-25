package com.waenhancer.xposed.bridge.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
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

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (method.equals("getHookBinder")) {
            Bundle result = new Bundle();
            result.putBinder("binder", HookBinder.getInstance());
            return result;
        }
        var context = getContext();
        if (context == null) {
            return null;
        }
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if ("get_all_preferences".equals(method)) {
            Bundle result = new Bundle();
            result.putSerializable("prefs", new HashMap<>(prefs.getAll()));
            return result;
        }
        if ("put_preference".equals(method) && extras != null) {
            String key = extras.getString("key");
            String type = extras.getString("type");
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
            editor.apply();
            return Bundle.EMPTY;
        }
        if ("remove_preference".equals(method) && extras != null) {
            String key = extras.getString("key");
            if (key != null) {
                prefs.edit().remove(key).apply();
                return Bundle.EMPTY;
            }
        }
        if ("clear_preferences".equals(method)) {
            prefs.edit().clear().apply();
            return Bundle.EMPTY;
        }
        return null;
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
