package com.waenhancer.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.waenhancer.model.TaskerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight SQLite-backed history of Tasker automation events.
 * Thread-safe singleton — usable from both the main app process and the
 * Xposed (WhatsApp) process since each gets its own DB file.
 * <p>
 * Retains at most {@link #MAX_EVENTS} rows; older entries are pruned
 * automatically on every insert.
 */
public class TaskerHistoryManager extends SQLiteOpenHelper {

    private static final String DB_NAME = "tasker_history.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_EVENTS = 100;

    private static final String TABLE = "events";
    private static final String COL_ID = "_id";
    private static final String COL_TYPE = "type";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_NUMBER = "target_number";
    private static final String COL_PREVIEW = "message_preview";

    private static volatile TaskerHistoryManager instance;

    private TaskerHistoryManager(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * Obtain (or create) the singleton instance.
     * Pass any valid {@link Context} — application context is preferred.
     */
    public static TaskerHistoryManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TaskerHistoryManager.class) {
                if (instance == null) {
                    instance = new TaskerHistoryManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ── Schema ──────────────────────────────────────────────────────────

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TYPE + " TEXT NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_NUMBER + " TEXT, " +
                COL_PREVIEW + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Record an automation event. Trims the table to {@link #MAX_EVENTS} rows.
     *
     * @param type          {@link TaskerEvent#TYPE_INCOMING} or {@link TaskerEvent#TYPE_OUTGOING}
     * @param targetNumber  phone number involved
     * @param messagePreview first portion of the message body (may be truncated)
     */
    public void logEvent(String type, String targetNumber, String messagePreview) {
        if (messagePreview != null && messagePreview.length() > 200) {
            messagePreview = messagePreview.substring(0, 200) + "…";
        }

        ContentValues cv = new ContentValues(4);
        cv.put(COL_TYPE, type);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        cv.put(COL_NUMBER, targetNumber);
        cv.put(COL_PREVIEW, messagePreview);

        SQLiteDatabase db = getWritableDatabase();
        db.insert(TABLE, null, cv);
        pruneOldEvents(db);
    }

    /**
     * @return up to {@link #MAX_EVENTS} recent events, newest first.
     */
    public List<TaskerEvent> getRecentEvents() {
        List<TaskerEvent> events = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.query(TABLE, null, null, null, null, null,
                COL_TIMESTAMP + " DESC", String.valueOf(MAX_EVENTS))) {
            while (c.moveToNext()) {
                TaskerEvent e = new TaskerEvent();
                e.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                e.type = c.getString(c.getColumnIndexOrThrow(COL_TYPE));
                e.timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                e.targetNumber = c.getString(c.getColumnIndexOrThrow(COL_NUMBER));
                e.messagePreview = c.getString(c.getColumnIndexOrThrow(COL_PREVIEW));
                events.add(e);
            }
        }
        return events;
    }

    /**
     * Delete all history entries.
     */
    public void clearHistory() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    // ── Internals ───────────────────────────────────────────────────────

    private void pruneOldEvents(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + TABLE +
                " WHERE " + COL_ID + " NOT IN (" +
                "SELECT " + COL_ID + " FROM " + TABLE +
                " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT " + MAX_EVENTS + ")");
    }
}
