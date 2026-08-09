package com.waenhancer.testing;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link SharedPreferences} for JVM unit tests.
 *
 * <p>Applies edits with the same semantics as the platform implementation: {@code clear()} is
 * evaluated before the staged writes, and the whole editor lands as one atomic step.</p>
 */
public final class FakeSharedPreferences implements SharedPreferences {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean rejectCommit;

    /** Makes every subsequent {@code commit()} fail, as Android does when storage is unusable. */
    public void rejectCommits(boolean reject) {
        this.rejectCommit = reject;
    }

    @Override
    public Map<String, ?> getAll() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object value = values.get(key);
        return value instanceof Set ? (Set<String>) value : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object value = values.get(key);
        if (value == null) return defValue;
        if (!(value instanceof Integer)) throw new ClassCastException(key + " is not an int");
        return (Integer) value;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = values.get(key);
        if (value == null) return defValue;
        if (!(value instanceof Long)) throw new ClassCastException(key + " is not a long");
        return (Long) value;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        if (value == null) return defValue;
        if (!(value instanceof Float)) throw new ClassCastException(key + " is not a float");
        return (Float) value;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        if (value == null) return defValue;
        if (!(value instanceof Boolean)) throw new ClassCastException(key + " is not a boolean");
        return (Boolean) value;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    private final class FakeEditor implements Editor {
        private final Map<String, Object> staged = new LinkedHashMap<>();
        private final Set<String> removed = new LinkedHashSet<>();
        private boolean clearRequested;

        private Editor stage(String key, Object value) {
            staged.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override
        public Editor putString(String key, String value) {
            return stage(key, value);
        }

        @Override
        public Editor putStringSet(String key, Set<String> value) {
            return stage(key, value == null ? null : new LinkedHashSet<>(value));
        }

        @Override
        public Editor putInt(String key, int value) {
            return stage(key, value);
        }

        @Override
        public Editor putLong(String key, long value) {
            return stage(key, value);
        }

        @Override
        public Editor putFloat(String key, float value) {
            return stage(key, value);
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            return stage(key, value);
        }

        @Override
        public Editor remove(String key) {
            removed.add(key);
            staged.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            clearRequested = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (rejectCommit) return false;
            if (clearRequested) values.clear();
            for (String key : removed) values.remove(key);
            values.putAll(staged);
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(values));
    }
}
