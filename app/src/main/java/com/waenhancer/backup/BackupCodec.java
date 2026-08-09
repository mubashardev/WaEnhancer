package com.waenhancer.backup;

import android.content.Context;
import android.content.SharedPreferences;

import com.waenhancer.config.BottomBarPreferenceSchema;
import com.waenhancer.config.PreferenceSchema;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Versioned, allowlisted settings backup. Database/media backup belongs to Block D. */
public final class BackupCodec {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    /**
     * The set of exportable keys is derived from {@link PreferenceSchema}, which is the single
     * source of truth for what the module persists.
     *
     * <p>This replaces a hand-written list that had drifted from the implementation. That list
     * named 43 keys the app never defines and misspelled others — {@code showname} for
     * {@code shownamehome}, {@code separate_groups} for {@code separategroups},
     * {@code statusdowload} for {@code downloadstatus}, {@code toast_viewer} for
     * {@code toast_viewed_message} — while omitting most real settings. Export wrote a small,
     * partly non-existent subset and import restored the same, so a restored backup appeared to
     * do nothing.</p>
     */
    private static final Set<String> SAFE_KEYS = PreferenceSchema.exportableKeys();

    private static final Set<String> BLOCKED_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "keybox", "keybox_xml", "private_key", "certificate", "certificates",
                    "license_key", "license_token", "tg_username", "encrypted_config",
                    "is_pro_verified", "expires_at", "pro_plugin_path", "pro_plugin_lib_path",
                    "github_token", "gh_public_token", "tasker_secret", "installation_id",
                    "module_heartbeat", "last_crash", "crash_stacktrace")));

    private static final Map<String, String> LEGACY_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("floating_bottom_bar_scroll_hide", "floating_bottom_bar_scroll_hide_mode");
        aliases.put("colors_customization", "changecolor");
        aliases.put("custom_primary_color", "primary_color");
        // Names the previous hand-written allowlist used, which never matched the
        // implementation. Files exported by that build carry them, so they are mapped back
        // onto the real keys instead of being discarded as unknown.
        aliases.put("showname", "shownamehome");
        aliases.put("showbio", "showbiohome");
        aliases.put("separate_groups", "separategroups");
        aliases.put("separate_groups_counter", "separategroups_counter_type");
        aliases.put("statusdowload", "downloadstatus");
        aliases.put("locked_chats_enhancer", "lockedchats_enhancer");
        aliases.put("show_message_device_source", "message_device_source");
        aliases.put("toast_viewer", "toast_viewed_message");
        aliases.put("audio_transcript", "audio_transcription");
        LEGACY_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private BackupCodec() {
    }

    public static String exportSettings(SharedPreferences preferences, String appVersion)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("appVersion", appVersion == null ? "unknown" : appVersion);
        root.put("createdAt", OffsetDateTime.now().toString());

        Map<String, ?> stored = preferences.getAll();
        JSONObject settings = new JSONObject();
        for (String key : SAFE_KEYS) {
            if (isSensitive(key)) continue;
            Object value = stored.get(key);
            if (value == null || !isSupportedValue(value)) continue;
            settings.put(key, encode(value));
        }
        root.put("settings", settings);
        return root.toString(2);
    }

    /**
     * Names the values the user has set that this file deliberately leaves behind, so the export
     * can say what it is not carrying instead of dropping it silently.
     *
     * <p>Secrets are excluded because a settings backup is a plain JSON file the user may share.
     * Carrying them safely needs a password-encrypted container, which belongs to the full
     * backup work rather than here.</p>
     */
    public static ExcludedSummary excludedFrom(SharedPreferences preferences) {
        Map<String, ?> stored = preferences == null
                ? Collections.emptyMap() : preferences.getAll();
        List<String> secrets = new ArrayList<>();
        List<String> internal = new ArrayList<>();
        for (Map.Entry<String, PreferenceSchema.Entry> entry
                : PreferenceSchema.all().entrySet()) {
            if (entry.getValue().isExportable()) continue;
            Object value = stored.get(entry.getKey());
            if (value == null) continue;
            if (value instanceof String && ((String) value).isEmpty()) continue;
            if (entry.getValue().sensitivity == PreferenceSchema.Sensitivity.SECRET) {
                secrets.add(entry.getKey());
            } else {
                internal.add(entry.getKey());
            }
        }
        return new ExcludedSummary(secrets, internal);
    }

    public static final class ExcludedSummary {
        /** User secrets that are set but never written to a settings backup. */
        public final List<String> secrets;
        /** Cache and runtime state, which is regenerated rather than restored. */
        public final List<String> internal;

        private ExcludedSummary(List<String> secrets, List<String> internal) {
            this.secrets = Collections.unmodifiableList(new ArrayList<>(secrets));
            this.internal = Collections.unmodifiableList(new ArrayList<>(internal));
        }

        public boolean hasSecrets() {
            return !secrets.isEmpty();
        }

        /** Plain-language notice naming the excluded secrets, or null when there are none. */
        public String secretsNotice() {
            if (secrets.isEmpty()) return null;
            return "This file does not contain your " + join(secrets)
                    + ". Settings backups are plain, shareable files, so secrets are never"
                    + " written to them. Keep a copy of those values yourself: reinstalling"
                    + " will not restore them.";
        }

        private static String join(List<String> keys) {
            List<String> labels = new ArrayList<>();
            for (String key : keys) labels.add(label(key));
            if (labels.size() == 1) return labels.get(0);
            String last = labels.remove(labels.size() - 1);
            return String.join(", ", labels) + " and " + last;
        }

        private static String label(String key) {
            switch (key) {
                case "groq_api_key":
                    return "Groq API key";
                case "assemblyai_key":
                    return "AssemblyAI API key";
                case "bootloader_spoofer_xml":
                    return "imported keybox";
                default:
                    return key;
            }
        }
    }

    public static ImportPlan parseAndValidate(byte[] data) throws BackupException {
        if (data == null || data.length == 0) throw new BackupException("Backup is empty.");
        if (data.length > MAX_BYTES) throw new BackupException("Backup exceeds the 2 MB limit.");

        final JSONObject root;
        try {
            root = new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (JSONException exception) {
            throw new BackupException("Backup is not valid JSON.", exception);
        }

        boolean legacy = !root.has("schemaVersion");
        JSONObject source;
        int schemaVersion;
        if (legacy) {
            source = root;
            schemaVersion = 0;
        } else {
            schemaVersion = root.optInt("schemaVersion", -1);
            if (schemaVersion != SCHEMA_VERSION) {
                throw new BackupException("Unsupported backup schema: " + schemaVersion);
            }
            source = root.optJSONObject("settings");
            if (source == null) throw new BackupException("Backup does not contain settings.");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        List<String> sensitive = new ArrayList<>();
        List<String> normalized = new ArrayList<>();

        int total = 0;
        for (String originalKey : iterable(source.keys())) {
            total++;
            String key = LEGACY_ALIASES.getOrDefault(originalKey, originalKey);
            if (isSensitive(key)) {
                sensitive.add(originalKey);
                continue;
            }
            if (!SAFE_KEYS.contains(key)) {
                unknown.add(originalKey);
                continue;
            }

            try {
                Object decoded = decode(source.get(originalKey), legacy);
                Object safe = normalizeValue(key, decoded, normalized);
                if (safe != null) values.put(key, safe);
            } catch (JSONException | IllegalArgumentException exception) {
                throw new BackupException("Invalid value for setting: " + originalKey, exception);
            }
        }

        return new ImportPlan(schemaVersion, legacy, total, values, unknown, sensitive, normalized);
    }

    public static ImportReport apply(Context context, SharedPreferences preferences, ImportPlan plan)
            throws BackupException {
        if (plan == null) throw new BackupException("Import plan is missing.");
        writeSnapshot(context, preferences);

        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : plan.values.entrySet()) {
            put(editor, entry.getKey(), entry.getValue());
        }

        // commit() applies the whole editor atomically. A false result means nothing was written,
        // so the existing preferences are still intact and must not be cleared and rebuilt.
        if (!editor.commit()) {
            throw new BackupException("Android rejected the settings transaction. "
                    + "No setting was changed.");
        }
        return new ImportReport(plan.values.size(), plan.totalKeys, plan.unknownKeys,
                plan.sensitiveKeys, plan.normalizedKeys, plan.legacy);
    }

    public static Set<String> safeKeys() {
        return SAFE_KEYS;
    }

    public static boolean isSensitive(String key) {
        if (key == null) return true;
        // The schema is authoritative: anything it classifies as a secret is refused and
        // reported as such, rather than falling through to the name heuristic below.
        if (PreferenceSchema.isSecret(key)) return true;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (BLOCKED_KEYS.contains(normalized)) return true;
        return normalized.contains("private_key")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("certificate")
                || normalized.contains("keybox")
                || normalized.contains("license")
                || normalized.contains("plugin_path")
                || normalized.contains("classloader")
                || normalized.contains("heartbeat")
                || normalized.contains("crash_stack");
    }

    private static JSONObject encode(Object value) throws JSONException {
        JSONObject encoded = new JSONObject();
        if (value instanceof Boolean) {
            encoded.put("type", "boolean");
            encoded.put("value", value);
        } else if (value instanceof Integer) {
            encoded.put("type", "int");
            encoded.put("value", value);
        } else if (value instanceof Long) {
            encoded.put("type", "long");
            encoded.put("value", value);
        } else if (value instanceof Float || value instanceof Double) {
            encoded.put("type", "float");
            encoded.put("value", ((Number) value).doubleValue());
        } else if (value instanceof Set<?>) {
            encoded.put("type", "string_set");
            encoded.put("value", new JSONArray((Set<?>) value));
        } else {
            encoded.put("type", "string");
            encoded.put("value", String.valueOf(value));
        }
        return encoded;
    }

    private static Object decode(Object raw, boolean legacy) throws JSONException {
        if (!(raw instanceof JSONObject)) {
            if (!legacy) throw new JSONException("Versioned setting must be an object.");
            return raw;
        }
        JSONObject encoded = (JSONObject) raw;
        String type = encoded.optString("type", "");
        Object value = encoded.get("value");
        switch (type.toLowerCase(Locale.ROOT)) {
            case "boolean":
                if (!(value instanceof Boolean)) throw new JSONException("Expected boolean");
                return value;
            case "integer":
            case "int":
                return asNumber(value).intValue();
            case "long":
                return asNumber(value).longValue();
            case "double":
            case "float":
                return asNumber(value).floatValue();
            case "hashset":
            case "string_set":
            case "jsonarray":
                return toStringSet(value);
            case "string":
                return String.valueOf(value);
            default:
                if (legacy) return value;
                throw new JSONException("Unsupported type: " + type);
        }
    }

    private static Object normalizeValue(String key, Object value, List<String> normalized) {
        if (BottomBarPreferenceSchema.all().containsKey(key)) {
            float safe = BottomBarPreferenceSchema.normalize(key, value);
            if (!(value instanceof Float) || Float.compare(((Number) value).floatValue(), safe) != 0) {
                normalized.add(key);
            }
            return safe;
        }
        if ("floating_bottom_bar_scroll_hide_mode".equals(key) && value instanceof Boolean) {
            normalized.add(key);
            return (Boolean) value ? "tabs" : "off";
        }
        if (!isSupportedValue(value)) {
            throw new IllegalArgumentException("Unsupported value type");
        }
        return value;
    }

    private static boolean isSupportedValue(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double) {
            return true;
        }
        if (value instanceof Set<?>) {
            for (Object item : (Set<?>) value) if (!(item instanceof String)) return false;
            return true;
        }
        return false;
    }

    private static Number asNumber(Object value) throws JSONException {
        if (value instanceof Number) return (Number) value;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new JSONException("Expected number");
        }
    }

    private static Set<String> toStringSet(Object value) throws JSONException {
        JSONArray array;
        if (value instanceof JSONArray) array = (JSONArray) value;
        else throw new JSONException("Expected array");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        return result;
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Double) editor.putFloat(key, ((Double) value).floatValue());
        else if (value instanceof Set<?>) {
            @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
            editor.putStringSet(key, new LinkedHashSet<>(set));
        } else editor.putString(key, String.valueOf(value));
    }


    private static void writeSnapshot(Context context, SharedPreferences preferences)
            throws BackupException {
        if (context == null) {
            throw new BackupException("Cannot import without a context to store the rollback snapshot.");
        }
        try {
            File directory = new File(context.getFilesDir(), "migration_snapshots");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Unable to create snapshot directory");
            }
            File snapshot = new File(directory,
                    "settings-before-import-" + System.currentTimeMillis() + ".json");
            try (FileOutputStream output = new FileOutputStream(snapshot)) {
                output.write(exportSettings(preferences, "pre-import-snapshot")
                        .getBytes(StandardCharsets.UTF_8));
            }
            pruneSnapshots(directory, 5);
        } catch (Exception exception) {
            throw new BackupException("Unable to create the pre-import snapshot.", exception);
        }
    }

    private static void pruneSnapshots(File directory, int keep) {
        File[] files = directory.listFiles();
        if (files == null || files.length <= keep) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < files.length; i++) files[i].delete();
    }

    private static <T> Iterable<T> iterable(final java.util.Iterator<T> iterator) {
        return () -> iterator;
    }

    public static final class ImportPlan {
        public final int schemaVersion;
        public final boolean legacy;
        /** How many entries the file contained, regardless of whether they were applied. */
        public final int totalKeys;
        public final Map<String, Object> values;
        public final List<String> unknownKeys;
        public final List<String> sensitiveKeys;
        public final List<String> normalizedKeys;

        private ImportPlan(int schemaVersion, boolean legacy, int totalKeys,
                           Map<String, Object> values,
                           List<String> unknownKeys, List<String> sensitiveKeys,
                           List<String> normalizedKeys) {
            this.schemaVersion = schemaVersion;
            this.legacy = legacy;
            this.totalKeys = totalKeys;
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.unknownKeys = Collections.unmodifiableList(new ArrayList<>(unknownKeys));
            this.sensitiveKeys = Collections.unmodifiableList(new ArrayList<>(sensitiveKeys));
            this.normalizedKeys = Collections.unmodifiableList(new ArrayList<>(normalizedKeys));
        }
    }

    public static final class ImportReport {
        public final int applied;
        public final int total;
        public final List<String> skippedUnknown;
        public final List<String> skippedSensitive;
        public final List<String> normalized;
        public final boolean legacy;

        private ImportReport(int applied, int total, List<String> skippedUnknown,
                             List<String> skippedSensitive, List<String> normalized,
                             boolean legacy) {
            this.applied = applied;
            this.total = total;
            this.skippedUnknown = skippedUnknown;
            this.skippedSensitive = skippedSensitive;
            this.normalized = normalized;
            this.legacy = legacy;
        }

        /**
         * States applied-against-total explicitly. The previous wording reported only the
         * applied count, so an import that recognised almost nothing read as a success.
         */
        public String summary() {
            StringBuilder text = new StringBuilder();
            text.append("Applied ").append(applied).append(" of ").append(total)
                    .append(total == 1 ? " setting" : " settings")
                    .append(legacy ? " from a legacy backup." : ".");
            if (!skippedUnknown.isEmpty()) {
                text.append("\nNot recognised: ").append(skippedUnknown.size())
                        .append(" (these belong to a different version and were ignored).");
            }
            if (!skippedSensitive.isEmpty()) {
                text.append("\nRefused as sensitive: ").append(skippedSensitive.size())
                        .append(" (keys, tokens and certificates are never imported).");
            }
            if (!normalized.isEmpty()) {
                text.append("\nClamped to a valid range: ").append(normalized.size()).append(".");
            }
            if (applied == 0) {
                text.append("\nNothing changed.");
            }
            return text.toString();
        }
    }

    public static class BackupException extends Exception {
        public BackupException(String message) {
            super(message);
        }

        public BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
