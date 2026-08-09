package com.waenhancer.config;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A read-only view of one store with a single extra key laid over it.
 *
 * <p>Used where a decision needs both public settings and one value that lives in the private
 * store — the automation token, for instance. It lets the deciding code read one consistent
 * view without the private value ever being written into the world-readable file.</p>
 *
 * <p>Writes are refused: this is a view, not a store.</p>
 */
public class OverlayPreferences implements SharedPreferences {

    private final SharedPreferences delegate;
    private final String overlayKey;
    private final Object overlayValue;

    public OverlayPreferences(SharedPreferences delegate, String overlayKey, Object overlayValue) {
        this.delegate = delegate;
        this.overlayKey = overlayKey;
        this.overlayValue = overlayValue;
    }

    @Override
    public Map<String, ?> getAll() {
        Map<String, Object> all = new LinkedHashMap<>(delegate.getAll());
        if (overlayValue != null) all.put(overlayKey, overlayValue);
        else all.remove(overlayKey);
        return all;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        if (overlayKey.equals(key)) {
            return overlayValue instanceof String ? (String) overlayValue : defValue;
        }
        return delegate.getString(key, defValue);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return overlayKey.equals(key) ? defValues : delegate.getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        return overlayKey.equals(key) ? defValue : delegate.getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return overlayKey.equals(key) ? defValue : delegate.getLong(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return overlayKey.equals(key) ? defValue : delegate.getFloat(key, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return overlayKey.equals(key) ? defValue : delegate.getBoolean(key, defValue);
    }

    @Override
    public boolean contains(String key) {
        return overlayKey.equals(key) ? overlayValue != null : delegate.contains(key);
    }

    @Override
    public Editor edit() {
        throw new UnsupportedOperationException("OverlayPreferences is a read-only view");
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        delegate.registerOnSharedPreferenceChangeListener(l);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        delegate.unregisterOnSharedPreferenceChangeListener(l);
    }
}
