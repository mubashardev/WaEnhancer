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
                updatePreferences(context, (PreferenceGroup) pref);
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
                }
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
            return key.equals("message_bomber") || key.equals("license_verify") || key.equals("delete_message_file") || key.equals("delete_message_file_sent");
        }
        return false;
    }
}
