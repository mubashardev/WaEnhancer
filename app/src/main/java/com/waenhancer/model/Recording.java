package com.waenhancer.model;

import android.annotation.SuppressLint;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Recording {

    private static final Pattern PHONE_PATTERN = Pattern.compile("Call_([+\\w\\s]+)_\\d{8}_\\d{6}.(wav|m4a)");

    private final File file;
    private final long date;
    private final long size;
    private String contactName;
    private long duration;

    public Recording(@NonNull File file) {
        this.file = file;
        this.date = file.lastModified();
        this.size = file.length();
        extractContactName();
        parseDuration();
    }

    private void extractContactName() {
        Matcher matcher = PHONE_PATTERN.matcher(file.getName());
        if (matcher.matches() && matcher.groupCount() >= 1) {
            String extracted = matcher.group(1);
            contactName = extracted != null && !extracted.isEmpty() ? extracted : "Unknown";
        } else {
            contactName = "Unknown";
        }
    }

    private void parseDuration() {
        if (!file.exists() || file.length() == 0L) {
            duration = 0L;
            return;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = timeStr == null || timeStr.isEmpty() ? 0L : Long.parseLong(timeStr);
        } catch (Exception ignored) {
            duration = 0L;
        } finally {
            try {
                retriever.close();
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    public String getFormattedDuration() {
        long seconds = duration / 1000L;
        long minutes = seconds / 60L;
        seconds = seconds % 60L;

        if (minutes >= 60L) {
            long hours = minutes / 60L;
            minutes = minutes % 60L;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    public String getFormattedSize() {
        if (size < 1024L) {
            return size + " B";
        }
        if (size < 1024L * 1024L) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    @NonNull
    public File getFile() {
        return file;
    }

    @NonNull
    public String getContactName() {
        return contactName;
    }

    public long getDuration() {
        return duration;
    }

    public long getDate() {
        return date;
    }

    public long getSize() {
        return size;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Recording other)) {
            return false;
        }
        return file.equals(other.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}
