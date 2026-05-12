package com.waenhancer.xposed.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.waenhancer.xposed.utils.Utils;

public class MessageDeviceSourceStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "MessageDeviceSource.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "message_device_source";
    private static final String COL_MESSAGE_ID = "message_id";
    private static final String COL_DEVICE_ID = "device_id";
    private static final int CACHE_SIZE = 1000;
    private static final int CACHE_MISS = -2;

    private static volatile MessageDeviceSourceStore instance;
    private SQLiteDatabase dbWrite;
    private final LruCache<String, Integer> deviceCache = new LruCache<>(CACHE_SIZE);

    private MessageDeviceSourceStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static MessageDeviceSourceStore getInstance() {
        if (instance == null) {
            synchronized (MessageDeviceSourceStore.class) {
                if (instance == null) {
                    instance = new MessageDeviceSourceStore(Utils.getApplication());
                    instance.dbWrite = instance.getWritableDatabase();
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                COL_MESSAGE_ID + " TEXT PRIMARY KEY, " +
                COL_DEVICE_ID + " INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Initial version only.
    }

    public void upsertDeviceId(@Nullable String messageId, int deviceId) {
        if (messageId == null || messageId.isEmpty() || deviceId < 0) return;

        Integer cached = deviceCache.get(messageId);
        if (cached != null && cached == deviceId) return;

        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put(COL_MESSAGE_ID, messageId);
            values.put(COL_DEVICE_ID, deviceId);
            dbWrite.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            deviceCache.put(messageId, deviceId);
        }
    }

    public int getDeviceId(@Nullable String messageId) {
        if (messageId == null || messageId.isEmpty()) return -1;

        Integer cached = deviceCache.get(messageId);
        if (cached != null) {
            return cached == CACHE_MISS ? -1 : cached;
        }

        try (Cursor cursor = getReadableDatabase().query(
                TABLE_NAME,
                new String[]{COL_DEVICE_ID},
                COL_MESSAGE_ID + "=?",
                new String[]{messageId},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                int deviceId = cursor.getInt(0);
                deviceCache.put(messageId, deviceId);
                return deviceId;
            }
        }

        deviceCache.put(messageId, CACHE_MISS);
        return -1;
    }
}
