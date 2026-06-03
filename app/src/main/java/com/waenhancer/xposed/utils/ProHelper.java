package com.waenhancer.xposed.utils;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.waenhancer.BuildConfig;

/**
 * Helper utility to bridge main set classes and pro submodule features cleanly,
 * preventing compilation failures when HAS_PRO_FEATURES is false.
 */
public class ProHelper {

    /**
     * Checks if the Pro licensing status is currently active.
     */
    public static boolean isProEnabled() {
        if (!BuildConfig.HAS_PRO_FEATURES) {
            return false;
        }
        try {
            Class<?> managerClass = Class.forName("com.waenhancer.pro.utils.ProStatusManager");
            Boolean result = (Boolean) managerClass.getMethod("isFeatureEnabled").invoke(null);
            return result != null && result;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Retrieves the plan name matching active, expired, or free states.
     */
    public static String getProPlanName() {
        if (!BuildConfig.HAS_PRO_FEATURES) {
            return "Free";
        }
        try {
            Class<?> managerClass = Class.forName("com.waenhancer.pro.utils.ProStatusManager");
            Object planName = managerClass.getMethod("getPlanName").invoke(null);
            return planName != null ? planName.toString() : "Free";
        } catch (Throwable t) {
            return "Free";
        }
    }

    /**
     * Gets the current pro status string ("ACTIVE", "EXPIRED", "FREE").
     */
    public static String getProStatus() {
        if (!BuildConfig.HAS_PRO_FEATURES) {
            return "FREE";
        }
        try {
            Class<?> managerClass = Class.forName("com.waenhancer.pro.utils.ProStatusManager");
            Object statusEnum = managerClass.getMethod("getStatus").invoke(null);
            if (statusEnum != null) {
                return statusEnum.toString(); // "ACTIVE", "EXPIRED", "FREE"
            }
        } catch (Throwable t) {
            // fallback
        }
        return "FREE";
    }

    /**
     * Dynamic bridge to update whether to force the license status to FREE.
     */
    public static void setForceFree(boolean force) {
        if (!BuildConfig.HAS_PRO_FEATURES) {
            return;
        }
        try {
            Class<?> managerClass = Class.forName("com.waenhancer.pro.utils.ProStatusManager");
            managerClass.getMethod("setForceFree", boolean.class).invoke(null, force);
        } catch (Throwable t) {
            // ignored
        }
    }

    /**
     * Recursively traverses and locks down Pro features in a preference list if not verified.
     *
     * @param context The Android context.
     * @param group   The preference group to evaluate.
     */
    public static void updatePreferences(Context context, PreferenceGroup group) {
        if (group == null) return;
        boolean proActive = isProEnabled();

        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);

            if (pref instanceof PreferenceGroup) {
                if (isProGroup(pref) && !proActive) {
                    pref.setEnabled(false);
                    uncheckTwoStatePreferences((PreferenceGroup) pref);
                    disableChildrenOfProGroup((PreferenceGroup) pref);
                } else {
                    if (isProGroup(pref) && proActive) {
                        String key = pref.getKey();
                        if ("customize_status_view_category".equals(key)) {
                            String hookClass = getHookStringSafely("customize_status_control_class");
                            if (hookClass == null || hookClass.trim().isEmpty()) {
                                disableAndUncheckGroupFromServer((PreferenceGroup) pref, "(Disabled by Server)");
                                continue;
                            }
                        }
                    }
                    updatePreferences(context, (PreferenceGroup) pref);
                }
            } else {
                if (isProFeature(pref) && !proActive) {
                    if (pref.getClass().getName().contains("ProSwitchPreference")) {
                        if (pref instanceof TwoStatePreference) {
                            ((TwoStatePreference) pref).setChecked(false);
                        }
                    } else {
                        pref.setEnabled(false);
                        if (pref instanceof TwoStatePreference) {
                            ((TwoStatePreference) pref).setChecked(false);
                        }
                    }
                } else if (isProFeature(pref) && proActive) {
                    String key = pref.getKey();
                    String hookKey = getHookKeyForPref(key);
                    if (hookKey != null) {
                        String hookClass = getHookStringSafely(hookKey);
                        if (hookClass == null || hookClass.trim().isEmpty()) {
                            pref.setEnabled(false);
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                            CharSequence summary = pref.getSummary();
                            if (summary == null || !summary.toString().contains("Disabled by Server")) {
                                pref.setSummary("Disabled by Server");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void disableChildrenOfProGroup(PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref.getClass().getName().contains("ProSwitchPreference")) {
                if (pref instanceof TwoStatePreference) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            } else {
                pref.setEnabled(false);
                if (pref instanceof TwoStatePreference) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            }
            if (pref instanceof PreferenceGroup) {
                disableChildrenOfProGroup((PreferenceGroup) pref);
            }
        }
    }

    private static void disableAndUncheckGroupFromServer(PreferenceGroup group, String suffix) {
        if (group == null) return;
        group.setEnabled(false);
        CharSequence title = group.getTitle();
        if (title != null && !title.toString().contains(suffix)) {
            group.setTitle(title + " " + suffix);
        }
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            pref.setEnabled(false);
            if (pref instanceof TwoStatePreference) {
                ((TwoStatePreference) pref).setChecked(false);
            }
            if (pref instanceof androidx.preference.ListPreference || pref instanceof TwoStatePreference) {
                CharSequence summary = pref.getSummary();
                if (summary == null || !summary.toString().contains("Disabled by Server")) {
                    pref.setSummary("Disabled by Server");
                }
            }
            if (pref instanceof PreferenceGroup) {
                disableAndUncheckGroupFromServer((PreferenceGroup) pref, suffix);
            }
        }
    }

    /**
     * Checks if a PreferenceGroup is classified as a Pro group.
     */
    private static boolean isProGroup(Preference pref) {
        if (pref == null) return false;
        String className = pref.getClass().getName();
        if (className.contains("ProPreferenceCategory")) {
            return true;
        }
        String key = pref.getKey();
        if (key != null) {
            return key.equals("customize_status_view_category");
        }
        return false;
    }

    /**
     * Recursively unchecks all TwoStatePreferences within a group.
     */
    private static void uncheckTwoStatePreferences(PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof TwoStatePreference) {
                ((TwoStatePreference) pref).setChecked(false);
            }
            if (pref instanceof PreferenceGroup) {
                uncheckTwoStatePreferences((PreferenceGroup) pref);
            }
        }
    }

    /**
     * Identifies if a preference is classified as a Pro feature.
     */
    private static boolean isProFeature(Preference pref) {
        if (pref == null) return false;

        String className = pref.getClass().getName();
        if (className.contains("ProSwitchPreference")) {
            return true;
        }

        String key = pref.getKey();
        if (key != null) {
            return key.equals("message_bomber") 
                    || key.equals("license_verify") 
                    || key.equals("delete_message_file") 
                    || key.equals("delete_message_file_sent") 
                    || key.equals("pro_status_splitter")
                    || key.equals("remove_status_bottom_tile")
                    || key.equals("remove_status_quick_reactions")
                    || key.equals("remove_status_heart_button")
                    || key.equals("status_bottom_play_pause_button")
                    || key.equals("add_status_reply_menu_item")
                    || key.equals("status_video_fast_gesture")
                    || key.equals("status_video_fast_speed");
        }
        return false;
    }

    private static String getHookKeyForPref(String key) {
        if (key == null) return null;
        if (key.equals("message_bomber")) {
            return "message_bomber";
        }
        if (key.equals("delete_message_file") || key.equals("delete_message_file_sent")) {
            return "delete_message_file";
        }
        if (key.equals("pro_status_splitter")) {
            return "pro_status_splitter";
        }
        if (key.equals("remove_status_bottom_tile")
                || key.equals("remove_status_quick_reactions")
                || key.equals("remove_status_heart_button")
                || key.equals("status_bottom_play_pause_button")
                || key.equals("add_status_reply_menu_item")
                || key.equals("status_video_fast_gesture")
                || key.equals("status_video_fast_speed")) {
            return "customize_status_control_class";
        }
        return null;
    }

    private static String getHookStringSafely(String hookKey) {
        try {
            Class<?> configClass = Class.forName("com.waenhancer.pro.utils.ProConfig");
            return (String) configClass.getMethod("getHookString", String.class).invoke(null, hookKey);
        } catch (Throwable t) {
            return null;
        }
    }
}
