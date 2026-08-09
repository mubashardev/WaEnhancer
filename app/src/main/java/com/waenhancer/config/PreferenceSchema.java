package com.waenhancer.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The definitive preference schema: the single source of truth for every key the module
 * persists.
 *
 * <p>Two independent dimensions are recorded per key. {@link Sensitivity} describes the
 * <em>content</em> and decides whether a key may leave the device in a settings backup.
 * {@link Store} describes the <em>transport</em> and decides which file the key lives in:
 * a key is {@link Store#PUBLIC} only when a hook running inside the WhatsApp process must
 * read it, and never when it holds a secret.</p>
 *
 * <p>The backup allowlist is derived from this table rather than maintained as a second
 * hand-written list. The previous hand-written list had drifted from the implementation:
 * it named keys that do not exist and misspelled others, so most settings were silently
 * dropped by both export and import.</p>
 */
public final class PreferenceSchema {

    public enum Type { BOOLEAN, STRING, STRING_SET, INT, LONG, FLOAT }

    /** What the value is, which decides whether it may be exported. */
    public enum Sensitivity {
        /** A user setting that a hook reads. Exported. */
        PUBLIC_SETTING,
        /** A user setting only the module app reads, or a device-local path. Exported. */
        PRIVATE_SETTING,
        /** A user secret. Never exported, never world-readable. */
        SECRET,
        /** Regenerable local state. Never exported. */
        CACHE,
        /** Internal operational state. Never exported. */
        RUNTIME
    }

    /** Which preference file the value lives in. */
    public enum Store {
        /** Readable by the hooked WhatsApp process. Never holds a secret. */
        PUBLIC,
        /** Module process only. */
        PRIVATE
    }

    public static final class Entry {
        public final String key;
        public final Type type;
        public final Sensitivity sensitivity;
        public final Store store;

        private Entry(String key, Type type, Sensitivity sensitivity, Store store) {
            this.key = key;
            this.type = type;
            this.sensitivity = sensitivity;
            this.store = store;
        }

        public boolean isExportable() {
            return sensitivity == Sensitivity.PUBLIC_SETTING
                    || sensitivity == Sensitivity.PRIVATE_SETTING;
        }
    }

    private static final Map<String, Entry> ENTRIES;
    private static final Set<String> EXPORTABLE;
    private static final Set<String> PUBLIC_KEYS;
    private static final Set<String> SECRET_KEYS;

    static {
        Map<String, Entry> entries = new LinkedHashMap<>();
        add(entries, "active_xposed_api_version", Type.INT, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "admin_emoji", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "admin_grp", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "alertsticker", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "always_online", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "always_typing_contacts", Type.STRING, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "always_typing_global", Type.BOOLEAN, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "always_typing_global_mode", Type.STRING, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "always_typing_global_target", Type.STRING, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "ampm", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "animation_emojis", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "animation_list", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "antidisappearing", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "antieditmessages", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "antirevoke", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "antirevokestatus", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "assemblyai_key", Type.STRING, Sensitivity.SECRET, Store.PRIVATE);
        add(entries, "audio_transcription", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "audio_type", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "auto_status_forward", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "auto_status_forward_rules_json", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "auto_status_forward_rules_pref", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "autonext_status", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "background_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "backup_privacy_notice_seen", Type.BOOLEAN, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "blueonreply", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bootloader_spoofer", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bootloader_spoofer_custom", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bootloader_spoofer_xml", Type.STRING, Sensitivity.SECRET, Store.PRIVATE);
        add(entries, "broadcast_tag", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bubble_color", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bubble_left", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bubble_right", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "bypass_version_check", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_block_contacts", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_info", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_privacy", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_recording_blacklist", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_recording_enable", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_recording_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_recording_path", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_recording_toast", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_recording_whitelist", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_type", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "call_white_contacts", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "calltype", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "change_dpi", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "changecolor", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "changecolor_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "channels", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "chatfilter", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "contributors_json", Type.STRING, Sensitivity.CACHE, Store.PRIVATE);
        add(entries, "copystatus", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "css_failure_count", Type.INT, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "css_last_valid", Type.STRING, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "css_previous_valid", Type.STRING, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "css_safe_mode", Type.BOOLEAN, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "css_test_expires_at", Type.LONG, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "css_test_value", Type.STRING, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "css_theme", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "custom_css", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "custom_filters", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "custom_privacy_type", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "customforwardlimit", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "customize_supported_versions", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "deleted_message_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "disable_ads", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "disable_defemojis", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "disable_expiration", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "disable_profile_status", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "disable_sensor_proximity", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "dont_ask_optimize_db", Type.BOOLEAN, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "dotonline", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "doubletap2like", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "doubletap2like_emoji", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "download_local", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "download_video_note", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "downloadstatus", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "downloadviewonce", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "filter_group_members_messages", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "filter_items", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "filtergroups", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "filterseen", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_bottom_margin", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_fab_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_fab_offset", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_fill_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_fully_rounded", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_glass", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_glass_opacity", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_height_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_horizontal_margin", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_icon_label_spacing", Type.FLOAT, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "floating_bottom_bar_icon_size", Type.FLOAT, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "floating_bottom_bar_indicator_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_height", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_offset", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_opacity", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_padding_horizontal", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_padding_vertical", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_radius", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_visible", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_indicator_width", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_manual_height", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_minimal_fab_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_minimal_fab_icon_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_minimal_fab_margin", Type.FLOAT, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "floating_bottom_bar_minimal_fab_opacity", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_minimal_fab_radius", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_minimal_fab_size", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_padding_vertical", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_radius", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_scroll_hide_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "floating_bottom_bar_text_size", Type.FLOAT, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "floatingmenu", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "folder_theme", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "force_english", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "force_restore_backup_feature", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "freezelastseen", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "ghostmode", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "ghostmode_r", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "ghostmode_t", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "go_to_first_message", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "google_translate", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "groq_api_key", Type.STRING, Sensitivity.SECRET, Store.PRIVATE);
        add(entries, "hide_seen_view", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hideaudioseen", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hideonceseen", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hideread", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hideread_group", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hidereceipt", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hidestatusview", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hidetabs", Type.STRING_SET, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "hidetag", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "ignored_timestamp", Type.LONG, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "ignored_version", Type.STRING, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "igstatus", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "imagequality", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "init_prefs_creation", Type.BOOLEAN, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "keybox_verify_status", Type.STRING, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "keybox_verify_time", Type.LONG, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "last_fetch", Type.LONG, Sensitivity.CACHE, Store.PRIVATE);
        add(entries, "lite_mode", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "lockedchats_enhancer", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "media_preview", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "menuwicon", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "message_device_source", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "metaai", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "module_heartbeat", Type.LONG, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "need_restart", Type.BOOLEAN, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "newchat", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "novaconfig", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "oldstatus", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "open_settings_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "open_waex", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "pending_restart_changes", Type.STRING_SET, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "pinnedlimit", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "primary_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "proximity_audios", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "release_channel", Type.STRING, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "remove_status_bottom_tile", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "remove_status_heart_button", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "remove_status_quick_reactions", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "removechannel_rec", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "removeforwardlimit", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "removeseemore", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "repo_stats_json", Type.STRING, Sensitivity.CACHE, Store.PRIVATE);
        add(entries, "repo_stats_time", Type.LONG, Sensitivity.CACHE, Store.PRIVATE);
        add(entries, "restartbutton", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "revokeallmessages", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "secondstotime", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "seentick", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "segundos", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "selectable_message", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "send_video_as_video_note", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "separategroups", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "separategroups_counter_type", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "show_dndmode", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "show_freezeLastSeen", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "show_hidereceipt", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "show_home_menu", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "show_hook_toast", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "showbiohome", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "shownamehome", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "showonline", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "showonlinetext", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "stamp_copied_message", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "status_style", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "tasker", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "tasker_allowed_packages", Type.STRING_SET, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "tasker_broadcast_message_body", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "tasker_legacy_unauthenticated", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "tasker_secret", Type.STRING, Sensitivity.SECRET, Store.PRIVATE);
        add(entries, "text_color", Type.INT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "thememode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "toast_viewed_message", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "toast_viewed_status", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "toastdeleted", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "transcription_provider", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "typearchive", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "unlinked_reverted_to_stable", Type.BOOLEAN, Sensitivity.RUNTIME, Store.PRIVATE);
        add(entries, "updateTime", Type.LONG, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "update_alert_pref", Type.BOOLEAN, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "update_check", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "verify_blocked_contact", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "video_call_screen_rec", Type.BOOLEAN, Sensitivity.PRIVATE_SETTING, Store.PRIVATE);
        add(entries, "video_limit_size", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "video_maxfps", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "video_real_resolution", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "videoquality", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "viewonce", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "voicenote_speed", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wae_color_mode", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wae_color_preset", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wae_version_name", Type.STRING, Sensitivity.RUNTIME, Store.PUBLIC);
        add(entries, "wallpaper", Type.BOOLEAN, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wallpaper_alpha", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wallpaper_alpha_navigation", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wallpaper_alpha_toolbar", Type.FLOAT, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);
        add(entries, "wallpaper_file", Type.STRING, Sensitivity.PUBLIC_SETTING, Store.PUBLIC);

        ENTRIES = Collections.unmodifiableMap(entries);

        Set<String> exportable = new LinkedHashSet<>();
        Set<String> publicKeys = new LinkedHashSet<>();
        Set<String> secretKeys = new LinkedHashSet<>();
        for (Entry entry : entries.values()) {
            if (entry.isExportable()) exportable.add(entry.key);
            if (entry.store == Store.PUBLIC) publicKeys.add(entry.key);
            if (entry.sensitivity == Sensitivity.SECRET) secretKeys.add(entry.key);
        }
        EXPORTABLE = Collections.unmodifiableSet(exportable);
        PUBLIC_KEYS = Collections.unmodifiableSet(publicKeys);
        SECRET_KEYS = Collections.unmodifiableSet(secretKeys);
    }

    private PreferenceSchema() {
    }

    private static void add(Map<String, Entry> entries, String key, Type type,
                            Sensitivity sensitivity, Store store) {
        if (sensitivity == Sensitivity.SECRET && store == Store.PUBLIC) {
            throw new IllegalStateException("A secret may never live in the public store: " + key);
        }
        entries.put(key, new Entry(key, type, sensitivity, store));
    }

    public static Map<String, Entry> all() {
        return ENTRIES;
    }

    public static Entry entry(String key) {
        return ENTRIES.get(key);
    }

    public static boolean isKnown(String key) {
        return ENTRIES.containsKey(key);
    }

    public static boolean isExportable(String key) {
        Entry entry = ENTRIES.get(key);
        return entry != null && entry.isExportable();
    }

    public static boolean isSecret(String key) {
        Entry entry = ENTRIES.get(key);
        return entry != null && entry.sensitivity == Sensitivity.SECRET;
    }

    /** Keys that belong in the world-readable file the hooked process reads. */
    public static Set<String> publicKeys() {
        return PUBLIC_KEYS;
    }

    /** Keys a settings backup may carry. */
    public static Set<String> exportableKeys() {
        return EXPORTABLE;
    }

    public static Set<String> secretKeys() {
        return SECRET_KEYS;
    }
}
