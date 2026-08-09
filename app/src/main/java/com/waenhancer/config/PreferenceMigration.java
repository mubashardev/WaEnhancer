package com.waenhancer.config;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves values the hooked process must not be able to read out of the world-readable store.
 *
 * <p>The migration follows the sequence the plan mandates for anything touching stored data:
 * detect the old structure, take a snapshot, validate the source, write the new structure
 * <em>without deleting the old one</em>, verify the copy value by value, and only then record
 * the step as done. Nothing is ever removed before the copy has been proven, and
 * {@code clear()} is never used.</p>
 *
 * <p>The two steps are deliberately separate. {@link #copyPrivateValues} is safe to run on
 * every start and only ever adds data. {@link #removeMigratedSecrets} is the destructive half
 * and refuses to act unless the private copy has been verified and the caller confirms the
 * process boundary can serve the value another way.</p>
 */
public final class PreferenceMigration {

    /** Bumped when the migration itself changes. Stored in the private store. */
    public static final int VERSION = 1;
    public static final String KEY_VERSION = "migration_version";
    public static final String KEY_SECRETS_COPIED = "migration_secrets_copied";
    public static final String KEY_SECRETS_REMOVED = "migration_secrets_removed";

    private static final String SNAPSHOT_DIR = "migration_snapshots";
    private static final int SNAPSHOTS_KEPT = 5;

    private PreferenceMigration() {
    }

    public static final class Result {
        public final List<String> copied;
        public final List<String> mismatched;
        public final List<String> removed;
        public final String error;

        private Result(List<String> copied, List<String> mismatched, List<String> removed,
                       String error) {
            this.copied = Collections.unmodifiableList(new ArrayList<>(copied));
            this.mismatched = Collections.unmodifiableList(new ArrayList<>(mismatched));
            this.removed = Collections.unmodifiableList(new ArrayList<>(removed));
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null && mismatched.isEmpty();
        }
    }

    /**
     * Copies every key the schema assigns to the private store out of the public store,
     * leaving the public copy in place.
     *
     * <p>Additive by construction: a downgrade to a build that only knows the public store
     * still finds every value, and a failure part-way leaves the public store untouched.</p>
     */
    public static Result copyPrivateValues(Context context) {
        return copyPrivateValues(PreferenceStores.publicStore(context),
                PreferenceStores.privateStore(context), snapshotDirectory(context));
    }

    /**
     * Context-free core, so the whole sequence — snapshot, copy, verify, mark — is exercised by
     * ordinary JVM tests rather than only on a device.
     */
    public static Result copyPrivateValues(SharedPreferences publicStore,
                                           SharedPreferences privateStore, File snapshotDir) {
        Map<String, ?> source = publicStore.getAll();
        Map<String, Object> pending = new LinkedHashMap<>();
        for (Map.Entry<String, PreferenceSchema.Entry> entry : PreferenceSchema.all().entrySet()) {
            if (entry.getValue().store != PreferenceSchema.Store.PRIVATE) continue;
            String key = entry.getKey();
            Object value = source.get(key);
            if (value == null) continue;
            Object existing = privateStore.getAll().get(key);
            if (equalValues(existing, value)) continue;
            pending.put(key, value);
        }

        if (pending.isEmpty()) {
            markDone(privateStore, KEY_SECRETS_COPIED);
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), null);
        }

        String snapshotError = writeSnapshot(snapshotDir, source);
        if (snapshotError != null) {
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), snapshotError);
        }

        SharedPreferences.Editor editor = privateStore.edit();
        for (Map.Entry<String, Object> entry : pending.entrySet()) {
            put(editor, entry.getKey(), entry.getValue());
        }
        if (!editor.commit()) {
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), "The private store rejected the transaction.");
        }

        // Verify the copy before anything is considered migrated.
        List<String> copied = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        Map<String, ?> written = privateStore.getAll();
        for (Map.Entry<String, Object> entry : pending.entrySet()) {
            if (equalValues(written.get(entry.getKey()), entry.getValue())) {
                copied.add(entry.getKey());
            } else {
                mismatched.add(entry.getKey());
            }
        }
        if (mismatched.isEmpty()) markDone(privateStore, KEY_SECRETS_COPIED);
        return new Result(copied, mismatched, Collections.emptyList(), null);
    }

    /**
     * Removes secrets from the world-readable store, but only once the private copy has been
     * verified and the caller states that the hooked process can obtain the value another way.
     *
     * @param bridgeAvailable whether a UID-validated path exists to serve these values to the
     *                        hooked process; passing false makes this a no-op
     */
    public static Result removeMigratedSecrets(Context context, boolean bridgeAvailable) {
        return removeMigratedSecrets(PreferenceStores.publicStore(context),
                PreferenceStores.privateStore(context), snapshotDirectory(context),
                bridgeAvailable);
    }

    public static Result removeMigratedSecrets(SharedPreferences publicStore,
                                               SharedPreferences privateStore,
                                               File snapshotDir, boolean bridgeAvailable) {
        if (!bridgeAvailable) {
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(),
                    "Refusing to remove secrets while the hooked process cannot read them.");
        }
        if (!privateStore.getBoolean(KEY_SECRETS_COPIED, false)) {
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), "Refusing to remove secrets before the copy is verified.");
        }

        Map<String, ?> publicValues = publicStore.getAll();
        Map<String, ?> privateValues = privateStore.getAll();
        List<String> removable = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        for (String key : PreferenceSchema.secretKeys()) {
            Object current = publicValues.get(key);
            if (current == null) continue;
            if (equalValues(privateValues.get(key), current)) removable.add(key);
            else mismatched.add(key);
        }
        if (!mismatched.isEmpty()) {
            return new Result(Collections.emptyList(), mismatched, Collections.emptyList(),
                    "The private copy does not match the public value.");
        }
        if (removable.isEmpty()) {
            markDone(privateStore, KEY_SECRETS_REMOVED);
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), null);
        }

        String snapshotError = writeSnapshot(snapshotDir, publicValues);
        if (snapshotError != null) {
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), snapshotError);
        }

        SharedPreferences.Editor editor = publicStore.edit();
        for (String key : removable) editor.remove(key);
        if (!editor.commit()) {
            return new Result(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), "The public store rejected the removal.");
        }
        markDone(privateStore, KEY_SECRETS_REMOVED);
        return new Result(Collections.emptyList(), Collections.emptyList(), removable, null);
    }

    /** Restores a snapshot taken before a migration step. */
    public static boolean rollback(Context context, File snapshot) {
        return rollback(PreferenceStores.publicStore(context), snapshot);
    }

    public static boolean rollback(SharedPreferences publicStore, File snapshot) {
        if (snapshot == null || !snapshot.isFile()) return false;
        try {
            byte[] raw = new byte[(int) snapshot.length()];
            try (java.io.FileInputStream input = new java.io.FileInputStream(snapshot)) {
                if (input.read(raw) != raw.length) return false;
            }
            JSONObject root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            JSONObject values = root.optJSONObject("values");
            if (values == null) return false;

            SharedPreferences.Editor editor = publicStore.edit();
            for (java.util.Iterator<String> keys = values.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONObject holder = values.optJSONObject(key);
                if (holder == null) continue;
                Object value = decode(holder);
                if (value != null) put(editor, key, value);
            }
            // Restores by writing the recorded values back; it never clears the store first,
            // so a snapshot that is missing a key leaves the current value alone.
            return editor.commit();
        } catch (JSONException | java.io.IOException | RuntimeException ignored) {
            return false;
        }
    }

    public static File snapshotDirectory(Context context) {
        return new File(context.getFilesDir(), SNAPSHOT_DIR);
    }

    private static String writeSnapshot(File directory, Map<String, ?> values) {
        try {
            if (directory == null) return "No snapshot directory was provided.";
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return "Unable to create the snapshot directory.";
            }
            JSONObject root = new JSONObject();
            root.put("migrationVersion", VERSION);
            root.put("createdAt", System.currentTimeMillis());
            JSONObject encoded = new JSONObject();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                JSONObject holder = encode(entry.getValue());
                if (holder != null) encoded.put(entry.getKey(), holder);
            }
            root.put("values", encoded);

            File file = new File(directory, "public-store-" + System.currentTimeMillis() + ".json");
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            prune(directory);
            return null;
        } catch (JSONException | java.io.IOException | RuntimeException exception) {
            return "Unable to write the pre-migration snapshot.";
        }
    }

    private static void prune(File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length <= SNAPSHOTS_KEPT) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = SNAPSHOTS_KEPT; i < files.length; i++) files[i].delete();
    }

    private static void markDone(SharedPreferences privateStore, String key) {
        privateStore.edit().putBoolean(key, true).putInt(KEY_VERSION, VERSION).apply();
    }

    private static JSONObject encode(Object value) throws JSONException {
        JSONObject holder = new JSONObject();
        if (value instanceof Boolean) holder.put("t", "b").put("v", value);
        else if (value instanceof Integer) holder.put("t", "i").put("v", value);
        else if (value instanceof Long) holder.put("t", "l").put("v", value);
        else if (value instanceof Float) holder.put("t", "f").put("v", ((Float) value).doubleValue());
        else if (value instanceof String) holder.put("t", "s").put("v", value);
        else if (value instanceof Set<?>) {
            org.json.JSONArray array = new org.json.JSONArray();
            for (Object item : (Set<?>) value) array.put(String.valueOf(item));
            holder.put("t", "ss").put("v", array);
        } else return null;
        return holder;
    }

    private static Object decode(JSONObject holder) {
        String type = holder.optString("t", "");
        switch (type) {
            case "b": return holder.optBoolean("v");
            case "i": return holder.optInt("v");
            case "l": return holder.optLong("v");
            case "f": return (float) holder.optDouble("v");
            case "s": return holder.optString("v");
            case "ss":
                org.json.JSONArray array = holder.optJSONArray("v");
                if (array == null) return null;
                Set<String> set = new java.util.LinkedHashSet<>();
                for (int i = 0; i < array.length(); i++) set.add(array.optString(i));
                return set;
            default: return null;
        }
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Set<?>) {
            @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
            editor.putStringSet(key, set);
        } else editor.putString(key, String.valueOf(value));
    }

    private static boolean equalValues(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        return a.equals(b);
    }
}
