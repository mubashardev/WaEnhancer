package com.waenhancer.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.xposed.core.db.DelMessageStore;

public class DeletedMessagesProvider extends ContentProvider {

    public static final String AUTHORITY = "com.waenhancer.provider";
    public static final String PATH_DELETED_MESSAGES = "deleted_messages";
    public static final String PATH_PREFERENCES = "preferences";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_DELETED_MESSAGES);
    public static final Uri PREF_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_PREFERENCES);

    private static final int DELETED_MESSAGES = 1;
    private static final int PREFERENCES = 2;
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, PATH_DELETED_MESSAGES, DELETED_MESSAGES);
        uriMatcher.addURI(AUTHORITY, PATH_PREFERENCES, PREFERENCES);
    }

    private DelMessageStore dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = DelMessageStore.getInstance(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // Not needed for now, but good practice to implement basic query if UI needs it later
        // or just return null if we only use it for insertion from Xposed
        return null; 
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (uriMatcher.match(uri) == DELETED_MESSAGES && values != null) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            
            // --- NEW: Propagate Contact Name to all messages in this chat ---
            String chatJid = values.getAsString("chat_jid");
            String contactName = values.getAsString("contact_name");
            
            if (chatJid != null && contactName != null && !contactName.isEmpty()) {
                ContentValues updateValues = new ContentValues();
                updateValues.put("contact_name", contactName);
                db.update(DelMessageStore.TABLE_DELETED_FOR_ME, updateValues, "chat_jid = ?", new String[]{chatJid});
            }
            // ----------------------------------------------------------------
            
            long id = db.insertWithOnConflict(DelMessageStore.TABLE_DELETED_FOR_ME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            if (id > 0) {
                return Uri.withAppendedPath(CONTENT_URI, String.valueOf(id));
            }
        }
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

    @Nullable
    @Override
    public android.os.Bundle call(@NonNull String method, @Nullable String arg, @Nullable android.os.Bundle extras) {
        var context = getContext();
        if (context == null) {
            return super.call(method, arg, extras);
        }

        var prefs = context.getSharedPreferences(context.getPackageName() + "_preferences",
                android.content.Context.MODE_PRIVATE);

        if ("get_preference".equals(method) && extras != null) {
            String key = extras.getString("key");
            android.os.Bundle result = new android.os.Bundle();
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

        if ("add_log".equals(method) && extras != null) {
            if (!prefs.getBoolean("logging_enabled", false)) {
                return android.os.Bundle.EMPTY;
            }
            String pkg = extras.getString("package");
            String msg = extras.getString("message");
            if (pkg != null && msg != null) {
                com.waenhancer.utils.LogManager.addLog(context, pkg, msg);
            }
            return android.os.Bundle.EMPTY;
        }

        if ("put_preference".equals(method) && extras != null) {
            String key = extras.getString("key");
            Object value = extras.get("value");
            if (key != null) {
                var editor = prefs.edit();
                if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                editor.apply();
                
                // Also update the XSharedPreferences by making them readable if possible
                // or rely on Xposed reloading them.
                return android.os.Bundle.EMPTY;
            }
        }
        return super.call(method, arg, extras);
    }
}
