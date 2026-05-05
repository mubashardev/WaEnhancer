package com.waenhancer.xposed.utils;

public class ResId {

    public static class drawable {

        public static int eye_disabled;
        public static int eye_enabled;
        public static int admin;

        public static int preview_eye;
        public static int refresh;
        public static int ghost_disabled;
        public static int ghost_enabled;
        public static int airplane_enabled;
        public static int airplane_disabled;
        public static int online;
        public static int deleted;
        public static int download;
        public static int camera;
        public static int edit2;
        public static int ic_privacy;
        public static int user_foreground;
    }

    public static class string {
        public static int edited_history;
        public static int dnd_message;
        public static int dnd_mode_title;
        public static int freezelastseen_message;
        public static int freezelastseen_title;
        public static int activate;
        public static int cancel;
        public static int message_original;
        public static int new_chat;
        public static int number_with_country_code;
        public static int message;
        public static int download;
        public static int error_when_saving_try_again;
        public static int msg_text_status_not_downloadable;
        public static int saved_to;
        public static int restart_whatsapp;
        public static int restart_wpp;
        public static int send_blue_tick;
        public static int sending_read_blue_tick;
        public static int send;
        public static int send_sticker;
        public static int do_you_want_to_send_sticker;
        public static int whatsapp_call;
        public static int phone_call;
        public static int yes;
        public static int no;
        public static int version_error;
        public static int copy_to_clipboard;
        public static int check_for_latest_version;
        public static int already_have_latest;
        public static int contact_developer;
        public static int copied_to_clipboard;
        public static int error_detected;
        public static int rebooting;
        public static int deleted_status;
        public static int deleted_message;
        public static int toast_online;
        public static int message_removed_on;
        public static int loading;
        public static int delete_for_me;
        public static int share_as_status;
        public static int viewed_your_status;
        public static int viewed_your_message;
        public static int select_status_type;
        public static int open_camera;
        public static int edit_text;
        public static int select_a_color;
        public static int read_all_mark_as_read;
        public static int grant_permission;
        public static int expiration;
        public static int deleted_a_message_in_group;
        public static int allow;
        public static int invalid_folder;
        public static int uri_permission;
        public static int ghost_mode;
        public static int ghost_mode_message;
        public static int disable;
        public static int enable;
        public static int ghost_mode_s;
        public static int starting_cache;
        public static int bridge_error;
        public static int not_available;
        public static int app_name;
        public static int hideread;
        public static int hidestatusview;
        public static int hidereceipt;
        public static int ghostmode;
        public static int ghostmode_r;
        public static int custom_privacy;
        public static int custom_privacy_sum;
        public static int block_call;
        public static int contact_s;
        public static int phone_number_s;
        public static int country_s;
        public static int city_s;
        public static int ip_s;
        public static int platform_s;
        public static int wpp_version_s;
        public static int call_information;
        public static int ask_download_folder;
        public static int download_folder_permission;
        public static int no_contact_with_custom_privacy;
        public static int select_contacts;
        public static int download_not_available;
        public static int block_not_detected;
        public static int possible_block_detected;
        public static int checking_if_the_contact_is_blocked;
        public static int block_unverified;
        public static int warning_restore;
        public static int force_restore_backup_experimental;
        public static int force_restore_backup;
        public static int contact_probably_not_added;
        public static int separate_groups_sum;
        public static int separate_groups_unsupported_sum;
        public static int waenhancer_settings;
    }

    public static class array {
        public static int supported_versions_wpp;
        public static int supported_versions_business;
    }

    public static class xml {
        public static int fragment_general;
        public static int fragment_privacy;
        public static int fragment_media;
        public static int fragment_customization;
        public static int embedded_settings_root;
        public static int embedded_settings_general;
        public static int embedded_settings_home_screen;
        public static int embedded_settings_conversation;
        public static int embedded_settings_privacy;
        public static int embedded_settings_status;
        public static int embedded_settings_calls;
        public static int embedded_settings_media;
        public static int embedded_settings_audio;
        public static int embedded_settings_appearance;
        public static int embedded_settings_advanced;
    }

    public static class style {
        public static int Theme;
        public static int Theme_Light;
    }

    public static class attr {
    }

    public static class layout {
    }

    public static class color {
    }

    public static class dimen {
    }

    public static void initLocal(android.content.Context context) {
        if (string.app_name != 0) return; // Already initialized
        try {
            Class<?> rString = com.waenhancer.R.string.class;
            for (java.lang.reflect.Field field : string.class.getFields()) {
                try {
                    java.lang.reflect.Field rField = rString.getField(field.getName());
                    field.set(null, rField.getInt(null));
                } catch (Exception ignored) {}
            }
            Class<?> rDrawable = com.waenhancer.R.drawable.class;
            for (java.lang.reflect.Field field : drawable.class.getFields()) {
                try {
                    java.lang.reflect.Field rField = rDrawable.getField(field.getName());
                    field.set(null, rField.getInt(null));
                } catch (Exception ignored) {}
            }
            Class<?> rXml = com.waenhancer.R.xml.class;
            for (java.lang.reflect.Field field : xml.class.getFields()) {
                try {
                    java.lang.reflect.Field rField = rXml.getField(field.getName());
                    field.set(null, rField.getInt(null));
                } catch (Exception ignored) {}
            }
            Class<?> rStyle = com.waenhancer.R.style.class;
            for (java.lang.reflect.Field field : style.class.getFields()) {
                try {
                    java.lang.reflect.Field rField = rStyle.getField(field.getName());
                    field.set(null, rField.getInt(null));
                } catch (Exception ignored) {}
            }
            Class<?> rArray = com.waenhancer.R.array.class;
            for (java.lang.reflect.Field field : array.class.getFields()) {
                try {
                    java.lang.reflect.Field rField = rArray.getField(field.getName());
                    field.set(null, rField.getInt(null));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            android.util.Log.e("WAE", "Local ResId init failed: " + e.getMessage());
        }
    }
}
