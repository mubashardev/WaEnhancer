package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ProHelper;
import com.waenhancer.xposed.utils.XResManager;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.function.BiConsumer;

public class WdsSettingsTileRenderer {

    public interface PrefChangeListener {

        void onPrefChanged(String key, Object newValue);
    }

    public static JSONObject loadSettingsMap(Context context) {
        try {
            Resources res = XResManager.moduleResources;
            if (res == null) {
                res = context.getResources();
            }
            int resId = res.getIdentifier("waex_settings_map", "raw", "com.waenhancer");
            if (resId == 0) {
                resId = res.getIdentifier("waex_settings_map", "raw", context.getPackageName());
            }
            if (resId == 0) {
                return null;
            }
            InputStream is = res.openRawResource(resId);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public static String resolveString(Context context, String str) {
        if (str == null) {
            return "";
        }
        if (str.startsWith("@string/")) {
            try {
                String name = str.substring(8);
                Resources res = XResManager.moduleResources;
                int id = 0;
                if (res != null) {
                    id = res.getIdentifier(name, "string", "com.waenhancer");
                }
                if (id == 0) {
                    res = context.getResources();
                    id = res.getIdentifier(name, "string", context.getPackageName());
                }
                if (id == 0) {
                    id = res.getIdentifier(name, "string", "com.waenhancer");
                }
                if (id != 0) {
                    str = res.getString(id);
                }
            } catch (Throwable ignored) {
            }
        }
        // Strip unformatted Android format specifiers (%s, %d, %1$s, etc.) that
        // have no runtime values to fill — leave the rest of the string intact.
        str = str.replaceAll("%(?:\\d+\\$)?[sdf]", "").trim();
        return str;
    }

    public static View buildCategoryList(Activity activity, JSONObject settingsMap, SharedPreferences prefs, PrefChangeListener listener) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setTag("WaEnhancerX Settings");

        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        container.setPadding(0, pad, 0, pad);

        try {
            JSONArray categories = settingsMap.getJSONArray("categories");
            for (int i = 0; i < categories.length(); i++) {
                JSONObject cat = categories.getJSONObject(i);
                String id = cat.getString("id");
                String title = cat.getString("title");
                String summary = cat.optString("summary", "");

                if ("pro_plans".equals(id) && ProHelper.isProEnabled()) {
                    title = com.waenhancer.xposed.core.FeatureLoader.getModuleString(activity, com.waenhancer.R.string.pro_status_title, "Pro Subscription");
                    summary = com.waenhancer.xposed.core.FeatureLoader.getModuleString(activity, com.waenhancer.R.string.pro_status_summary, "Pro features unlocked — manage plan and view active features");
                }

                String iconName = cat.optString("icon", "ic_settings");
                Drawable icon = DesignUtils.getDrawableByName(iconName);
                if (icon == null) {
                    icon = DesignUtils.getDrawableByName("ic_settings");
                }
                XposedBridge.log("[WAEX] Category id: " + id + ", iconName: " + iconName + ", icon: " + icon);

                View row = createWdsRow(activity, title, summary, icon, iconName, v -> {
                    if ("optimization".equals(id)) {
                        try {
                            Class<?> aboutClass = WppCore.getAboutActivityClass(activity.getClassLoader());
                            if (aboutClass != null) {
                                Intent intent = new Intent(activity, aboutClass);
                                intent.putExtra("wae_optimize_db", true);
                                activity.startActivity(intent);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] Failed to start optimization from settings: " + t.getMessage());
                        }
                    } else {
                        Intent intent = new Intent(activity, activity.getClass());
                        intent.putExtra("waex_screen_id", id);
                        activity.startActivity(intent);
                    }
                });
                container.addView(row);
            }
        } catch (Exception ignored) {
        }

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.addView(container);
        return scrollView;
    }

    public static View buildSubScreenById(Activity activity, JSONObject settingsMap, String catId, SharedPreferences prefs, PrefChangeListener listener) {
        try {
            JSONArray categories = settingsMap.getJSONArray("categories");
            for (int i = 0; i < categories.length(); i++) {
                JSONObject cat = categories.getJSONObject(i);
                if (cat.getString("id").equals(catId)) {
                    return buildSubScreen(activity, cat, prefs, listener);
                }

                JSONArray subScreens = cat.optJSONArray("sub_screens");
                if (subScreens != null) {
                    for (int j = 0; j < subScreens.length(); j++) {
                        JSONObject sub = subScreens.getJSONObject(j);
                        if (sub.getString("id").equals(catId)) {
                            return buildSingleSubScreen(activity, sub, prefs, listener);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static View buildSingleSubScreen(Activity activity, JSONObject sub, SharedPreferences prefs, PrefChangeListener listener) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        String subTitle = sub.optString("title", "Settings");
        container.setTag(subTitle);

        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        container.setPadding(0, pad, 0, pad);

        try {
            JSONArray prefsArray = sub.getJSONArray("prefs");
            renderPrefsArray(activity, container, prefsArray, prefs, listener, false);
        } catch (Exception ignored) {
        }

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.addView(container);
        return scrollView;
    }

    private static View buildSubScreen(Activity activity, JSONObject category, SharedPreferences prefs, PrefChangeListener listener) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        String catTitle = category.optString("title", "Settings");
        container.setTag(catTitle);

        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        container.setPadding(0, pad, 0, pad);

        try {
            JSONArray subScreens = category.getJSONArray("sub_screens");

            // Add Category tiles for the remaining sub-screens at the TOP
            for (int i = 1; i < subScreens.length(); i++) {
                JSONObject sub = subScreens.getJSONObject(i);
                String subId = sub.getString("id");
                String subTitle = sub.getString("title");
                String subSummary = sub.optString("summary", "Customize " + subTitle + " settings");

                Drawable icon = null;
                String iconName = "";
                if ("home_screen_main".equals(subId)) {
                    iconName = "ic_home_black_24dp";
                } else if ("conversation_main".equals(subId)) {
                    iconName = "ic_home_tab_chats_unfilled";
                }

                if (!iconName.isEmpty()) {
                    icon = DesignUtils.getDrawableByName(iconName);
                }
                if (icon == null) {
                    icon = DesignUtils.getDrawableByName("ic_chevron_right");
                }

                View catTile = createWdsRow(activity, subTitle, subSummary, icon, iconName, v -> {
                    Intent intent = new Intent(activity, activity.getClass());
                    intent.putExtra("waex_screen_id", subId);
                    activity.startActivity(intent);
                });
                container.addView(catTile);
            }

            if (subScreens.length() > 1) {
                View divider = new View(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
                lp.setMargins(0, (int) (16 * density), 0, (int) (16 * density));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(0xFF222d34);
                container.addView(divider);
            }

            // Render the first main sub-screen (general_main)
            if (subScreens.length() > 0) {
                JSONObject mainSub = subScreens.getJSONObject(0);
                JSONArray prefsArray = mainSub.getJSONArray("prefs");
                renderPrefsArray(activity, container, prefsArray, prefs, listener, false);
            }
        } catch (Exception ignored) {
        }

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.addView(container);
        return scrollView;
    }

    static void renderPrefsArray(Context context, LinearLayout container, JSONArray prefsArray, SharedPreferences prefs, PrefChangeListener listener, boolean isSearch) {
        renderPrefsArray(context, container, prefsArray, prefs, listener, isSearch, null);
    }

    static void renderPrefsArray(Context context, LinearLayout container, JSONArray prefsArray, SharedPreferences prefs, PrefChangeListener listener, boolean isSearch, BiConsumer<String, String> navigateCallback) {
        try {
            Map<String, View> tileViews = new HashMap<>();

            for (int j = 0; j < prefsArray.length(); j++) {
                JSONObject pref = prefsArray.getJSONObject(j);
                String type = pref.getString("type");
                String key = pref.getString("key");
                String title = pref.getString("title");

                boolean isProKey = ProHelper.isProFeature(key);
                boolean isProEnabled = ProHelper.isProEnabled();
                boolean limitedFree = ProHelper.isLimitedFreePreferenceEnabled(key);
                boolean isProLocked = isProKey && !isProEnabled && !limitedFree;

                boolean isEnabled = pref.optBoolean("enabled", true);
                if (isProLocked || !isEnabled) {
                    title = title + " [Pro]";
                }
                String summary = pref.optString("summary", "");

                // === MODULE-ONLY PREFERENCE KEYS ===
                // These are managed exclusively in the WaEnhancerX module app.
                java.util.Set<String> moduleOnlyKeys = new java.util.HashSet<>(java.util.Arrays.asList(
                        // Theme & Appearance
                        "thememode", "wae_color_mode", "wae_color_preset",
                        "changecolor", "changecolor_mode", "primary_color", "background_color", "text_color",
                        "bubble_color", "bubble_left", "bubble_right",
                        "wallpaper", "wallpaper_file", "wallpaper_alpha",
                        "wallpaper_alpha_toolbar", "wallpaper_alpha_navigation",
                        "unlock_premium_customization", "customize_supported_versions",
                        // Custom CSS / Filter / Theme Manager
                        "custom_filters", "filter_items", "css_theme", "change_dpi", "folder_theme",
                        // Spoofer / Keybox
                        "bootloader_spoofer", "bootloader_spoofer_custom", "bootloader_spoofer_xml",
                        "bootloader_spoofer_verify", "file_size_spoofer",
                        // Call blocking / recording
                        "call_privacy", "call_type", "call_block_contacts", "call_white_contacts",
                        "call_recording_enable", "call_recording_calls_tab_menu", "call_recording_path",
                        "call_recording_mode", "call_recording_whitelist", "call_recording_blacklist",
                        "call_recording_toast", "call_recording_settings", "call_recording_manage",
                        // Audio / Voice notes
                        "send_audio_as_voice_status",
                        "audio_type", "voicenote_speed", "audio_transcription", "transcription_provider",
                        "proximity_audios",
                        // Pro features & Batch Forwarding
                        "pro_status_splitter", "pref_forward_batch_enabled", "pref_forward_batch_count", "pref_forward_batch_delay",
                        "floating_bottom_bar_pill_design", "floating_bottom_bar_glass", "floating_bottom_bar_glass_opacity", "floating_bottom_bar_fill_color",
                        "message_bomber", "license_verify", "delete_message_file", "delete_message_file_sent",
                        "remove_status_bottom_tile", "remove_status_quick_reactions", "remove_status_heart_button",
                        "status_bottom_play_pause_button", "add_status_reply_menu_item", "status_video_fast_gesture",
                        "status_video_fast_speed", "disable_status_swipe_up", "waex_sim_enabled", "waex_sim_trigger",
                        "waex_sim_kind", "filter_group_members_messages", "recover_deleted_media",
                        // Newly added module-only features
                        "download_local", "hidetabs", "secondstotime"
                ));

                boolean isModuleOnly = isProKey || isProLocked || !isEnabled || moduleOnlyKeys.contains(key);

                View tile = null;

                if (isModuleOnly) {
                    final String fKey = key;
                    final View.OnClickListener moduleClickListener = v -> {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("com.waenhancer", "com.waenhancer.activities.MainActivity");
                            intent.putExtra("scroll_to_preference", fKey);

                            Context moduleContext = context;
                            try {
                                moduleContext = context.createPackageContext("com.waenhancer", Context.CONTEXT_IGNORE_SECURITY);
                            } catch (Throwable ignored) {}

                            String dialogTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(moduleContext, 0, "Configure in WaEnhancerX");
                            String dialogMsg = com.waenhancer.xposed.core.FeatureLoader.getModuleString(moduleContext, 0, "This setting must be configured inside the WaEnhancerX module app.");

                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                    .setTitle(dialogTitle)
                                    .setMessage(dialogMsg)
                                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                        try {
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(intent);
                                        } catch (Throwable t) {
                                            Toast.makeText(context, "Open WaEnhancerX app to change this setting", Toast.LENGTH_LONG).show();
                                        }
                                    })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        } catch (Throwable t) {
                            try {
                                Intent intent = new Intent();
                                intent.setClassName("com.waenhancer", "com.waenhancer.activities.MainActivity");
                                intent.putExtra("scroll_to_preference", fKey);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            } catch (Throwable ignored) {}
                        }
                    };

                    String activeValue = getActiveValueText(context, pref, prefs);
                    tile = createWdsRow(context, title, summary, null, moduleClickListener);
                    if (tile != null) {
                        addModuleLogoTrailing(context, tile);
                    }
                } else if (isSearch) {
                    // In search mode: switch tiles get switch-only toggle + row navigates
                    // All other tiles: row click navigates, no dialogs/inputs shown
                    if ("switch".equals(type) && isEnabled && !isProLocked) {
                        tile = createSearchSwitchTile(context, key, title, summary, pref.optBoolean("default", false), prefs, listener, navigateCallback);
                    } else {
                        // Simple navigation row for list/multi/text/action
                        String activeValue = (isEnabled && !isProLocked) ? getActiveValueText(context, pref, prefs) : "";
                        String displaySummary = summary;
                        final String fKey = key;
                        final String fActiveValue = activeValue;
                        tile = createWdsRow(context, title, displaySummary, null, v -> {
                            if (navigateCallback != null) {
                                navigateCallback.accept(fKey, "");
                            }
                        });
                        // Append active value as trailing text if possible
                        if (!TextUtils.isEmpty(activeValue)) {
                            try {
                                boolean isDarkMode = DesignUtils.isNightMode();
                                TextView trailingView = new TextView(context);
                                trailingView.setText(fActiveValue);
                                trailingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                                trailingView.setTextColor(isDarkMode ? 0xFF8696a0 : 0xFF667781);
                                XposedHelpers.callMethod(tile, "setEndAddon", trailingView);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } else if ("switch".equals(type)) {
                    boolean def = pref.optBoolean("default", false);
                    tile = createSwitchTile(context, key, title, summary, def, prefs, listener, tileViews, prefsArray, false);
                } else if ("list".equals(type)) {
                    tile = createListTile(context, pref, prefs, listener);
                } else if ("multi".equals(type)) {
                    tile = createMultiTile(context, pref, prefs, listener);
                } else if ("text".equals(type)) {
                    tile = createTextTile(context, pref, prefs, listener);
                } else if ("action".equals(type)) {
                    tile = createActionTile(context, pref);
                } else {
                    tile = createWdsRow(context, title, summary, null, null);
                }

                if (tile != null) {
                    tile.setTag(key);
                    tileViews.put(key, tile);
                    container.addView(tile);
                }
            }

            if (!isSearch) {
                checkDependencies(prefsArray, prefs, tileViews);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Search-mode switch tile: only the switch widget toggles the pref.
     * Clicking anywhere else on the row fires the navigateCallback.
     */
    private static View createSearchSwitchTile(Context context, String key, String title, String summary, boolean defVal, SharedPreferences prefs, PrefChangeListener listener, BiConsumer<String, String> navigateCallback) {
        // Build the row as a plain WDSListItem/fallback LinearLayout
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        row.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        boolean isDarkMode = DesignUtils.isNightMode();
        int primaryColor = isDarkMode ? 0xFFe9edef : 0xFF111B21;
        int secondaryColor = isDarkMode ? 0xFF8696a0 : 0xFF667781;

        // Text block
        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textLp);

        TextView titleView = createWdsTextView(context);
        titleView.setText(resolveString(context, title));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(primaryColor);
        textLayout.addView(titleView);

        if (!TextUtils.isEmpty(summary)) {
            TextView summaryView = createWdsTextView(context);
            summaryView.setText(resolveString(context, summary));
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            summaryView.setTextColor(secondaryColor);
            summaryView.setPadding(0, (int) (2 * density), 0, 0);
            textLayout.addView(summaryView);
        }
        row.addView(textLayout);

        // Switch widget — only this toggles the value
        View wdsSwitch = createWdsSwitch(context);
        boolean currentVal = prefs.getBoolean(key, defVal);
        setSwitchChecked(wdsSwitch, currentVal);
        wdsSwitch.setClickable(true);
        wdsSwitch.setFocusable(true);
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchLp.setMarginStart((int) (8 * density));
        wdsSwitch.setLayoutParams(switchLp);
        wdsSwitch.setOnClickListener(v -> {
            boolean newVal = !getSwitchChecked(wdsSwitch);
            setSwitchChecked(wdsSwitch, newVal);
            prefs.edit().putBoolean(key, newVal).apply();
            if (listener != null) {
                listener.onPrefChanged(key, newVal);
            }
        });
        row.addView(wdsSwitch);

        // Row click navigates (but not when touching switch area)
        row.setOnClickListener(v -> {
            if (navigateCallback != null) {
                navigateCallback.accept(key, "");
            }
        });

        return row;
    }

    public static String getActiveValueText(Context context, JSONObject pref, SharedPreferences prefs) {
        try {
            String type = pref.getString("type");
            String key = pref.getString("key");
            if ("switch".equals(type)) {
                return "";
            } else if ("list".equals(type)) {
                String valueType = pref.optString("value_type", "string");
                JSONArray entriesJson = pref.getJSONArray("entries");
                String[] entries = new String[entriesJson.length()];
                String[] values = new String[entriesJson.length()];
                for (int i = 0; i < entriesJson.length(); i++) {
                    JSONObject entryObj = entriesJson.getJSONObject(i);
                    entries[i] = resolveString(context, entryObj.getString("label"));
                    values[i] = String.valueOf(entryObj.get("value"));
                }
                int initialSelectedIndex = 0;
                if ("int".equals(valueType)) {
                    int defaultVal = pref.optInt("default", 0);
                    int current = prefs.getInt(key, defaultVal);
                    for (int i = 0; i < values.length; i++) {
                        try {
                            if (Integer.parseInt(values[i]) == current) {
                                initialSelectedIndex = i;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } else if ("boolean".equals(valueType)) {
                    boolean defaultVal = pref.optBoolean("default", false);
                    boolean current = prefs.getBoolean(key, defaultVal);
                    for (int i = 0; i < values.length; i++) {
                        if (Boolean.parseBoolean(values[i]) == current) {
                            initialSelectedIndex = i;
                            break;
                        }
                    }
                } else {
                    String defaultVal = pref.optString("default", "");
                    String current = prefs.getString(key, defaultVal);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(current)) {
                            initialSelectedIndex = i;
                            break;
                        }
                    }
                }
                return initialSelectedIndex < entries.length ? entries[initialSelectedIndex] : "";
            } else if ("multi".equals(type)) {
                JSONArray entriesJson = pref.getJSONArray("entries");
                String[] entries = new String[entriesJson.length()];
                String[] values = new String[entriesJson.length()];
                for (int i = 0; i < entriesJson.length(); i++) {
                    JSONObject entryObj = entriesJson.getJSONObject(i);
                    entries[i] = resolveString(context, entryObj.getString("label"));
                    values[i] = String.valueOf(entryObj.get("value"));
                }
                String current = prefs.getString(key, "");
                if (current.isEmpty()) {
                    return "None";
                }
                List<String> selectedLabels = new ArrayList<>();
                String[] selectedValues = current.split(",");
                for (String val : selectedValues) {
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(val.trim())) {
                            selectedLabels.add(entries[i]);
                            break;
                        }
                    }
                }
                return String.join(", ", selectedLabels);
            } else if ("text".equals(type)) {
                String valueType = pref.optString("value_type", "string");
                if ("int".equals(valueType)) {
                    return String.valueOf(prefs.getInt(key, pref.optInt("default", 0)));
                } else {
                    return prefs.getString(key, pref.optString("default", ""));
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Recursively disables clickability on all child views of a ViewGroup so
     * that touch events are not consumed by children and instead bubble to the
     * parent's OnClickListener.
     */
    private static void disableChildrenClickability(ViewGroup parent) {
        if (parent == null) {
            return;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setClickable(false);
            child.setFocusable(false);
            child.setLongClickable(false);
            if (child instanceof ViewGroup) {
                disableChildrenClickability((ViewGroup) child);
            }
        }
    }

    /**
     * Attaches the WaEnhancerX app logo as a small trailing icon on a
     * preference row. Uses PackageManager to load the app icon reliably, then
     * appends it as the last child of the tile ViewGroup (works for both
     * WDSListItem and fallback LinearLayout).
     */
    public static void addModuleLogoTrailing(Context context, View tile) {
        if (tile == null) {
            return;
        }
        try {
            float density = context.getResources().getDisplayMetrics().density;
            int sizePx = (int) (24 * density);

            // Load the WaEnhancerX launcher icon via PackageManager (most reliable path)
            Drawable logo = null;
            try {
                logo = context.getPackageManager().getApplicationIcon("com.waenhancer");
            } catch (Throwable ignored) {
            }

            // Fallback: createPackageContext + mipmap resource
            if (logo == null) {
                try {
                    Context moduleCtx = context.createPackageContext(
                            "com.waenhancer",
                            Context.CONTEXT_IGNORE_SECURITY);
                    int resId = moduleCtx.getResources().getIdentifier(
                            "ic_launcher_round", "mipmap", "com.waenhancer");
                    if (resId != 0) {
                        logo = moduleCtx.getResources().getDrawable(resId, null);
                    }
                } catch (Throwable ignored) {
                }
            }

            // Last fallback: XResManager
            if (logo == null) {
                try {
                    Resources res = XResManager.moduleResources;
                    if (res != null) {
                        int resId = res.getIdentifier("ic_launcher_round", "mipmap", "com.waenhancer");
                        if (resId != 0) {
                            logo = res.getDrawable(resId, null);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            if (logo == null) {
                return;
            }

            // Build the trailing ImageView
            ImageView logoView = new ImageView(context);
            logoView.setImageDrawable(logo);
            logoView.setAlpha(0.80f);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // Use FrameLayout.LayoutParams in case the parent is a FrameLayout (WDSListItem internals),
            // but set a generic size and gravity compatible with LinearLayout too.
            int margin = (int) (8 * density);

            // Append as last child — works for both WDSListItem (FrameLayout/ViewGroup)
            // and the LinearLayout fallback row.
            if (tile instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) tile;
                // Use FrameLayout.LayoutParams with CENTER_VERTICAL | END gravity for WDSListItem
                // and LinearLayout.LayoutParams for the fallback.
                android.view.ViewGroup.LayoutParams lp;
                if (parent instanceof LinearLayout) {
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(sizePx, sizePx);
                    llp.gravity = Gravity.CENTER_VERTICAL;
                    llp.setMarginStart(margin);
                    llp.setMarginEnd(margin);
                    lp = llp;
                } else {
                    FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(sizePx, sizePx);
                    flp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                    flp.setMarginEnd(margin);
                    lp = flp;
                }
                logoView.setLayoutParams(lp);
                parent.addView(logoView);
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] addModuleLogoTrailing failed: " + t.getMessage());
        }
    }

    public static View createWdsRow(Context context, String title, String summary, Drawable icon, View.OnClickListener clickListener) {
        return createWdsRow(context, title, summary, icon, null, clickListener);
    }

    public static View createWdsRow(Context context, String title, String summary, Drawable icon, String iconName, View.OnClickListener clickListener) {
        try {
            Class<?> wdsListItemClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.list.listitem.WDSListItem");
            View wdsListItem = (View) wdsListItemClass.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);

            // Set text/title
            XposedHelpers.callMethod(wdsListItem, "setText", resolveString(context, title));

            // Set subtext/summary
            String resolvedSummary = resolveString(context, summary);
            if (!TextUtils.isEmpty(resolvedSummary)) {
                XposedHelpers.callMethod(wdsListItem, "setSubText", resolvedSummary);
            }

            // Set icon
            if (icon != null) {
                try {
                    float density = context.getResources().getDisplayMetrics().density;

                    // Container FrameLayout of 40dp
                    FrameLayout container = new FrameLayout(context);
                    LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                            (int) (40 * density), (int) (40 * density)
                    );
                    containerLp.gravity = Gravity.CENTER_VERTICAL;
                    containerLp.setMarginStart(0);
                    containerLp.setMarginEnd((int) (16 * density));
                    container.setLayoutParams(containerLp);

                    // ImageView centered inside container
                    ImageView iconView = new ImageView(context);
                    iconView.setImageDrawable(icon);

                    boolean isNight = DesignUtils.isNightMode();
                    iconView.setImageTintList(ColorStateList.valueOf(isNight ? 0xFF8696a0 : 0xFF667781));

                    int iconSizeDp = 24;
                    if ("ic_home_tab_status_unfilled".equals(iconName)) {
                        iconSizeDp = 28;
                    }
                    FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                            (int) (iconSizeDp * density), (int) (iconSizeDp * density)
                    );
                    iconLp.gravity = Gravity.CENTER;
                    iconView.setLayoutParams(iconLp);

                    container.addView(iconView);
                    ((ViewGroup) wdsListItem).addView(container, 0);
                } catch (Throwable t2) {
                    XposedBridge.log("[WAEX] Failed to add leading icon view: " + t2.getMessage());
                }
            }

            if (clickListener != null) {
                wdsListItem.setOnClickListener(clickListener);
                wdsListItem.setClickable(true);
                wdsListItem.setFocusable(true);
                // Prevent child views from consuming touch events so the parent listener fires.
                disableChildrenClickability((ViewGroup) wdsListItem);
            }

            return wdsListItem;
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to instantiate WDSListItem, falling back: " + t.getMessage());
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        row.setPadding((int) (24 * density), (int) (12 * density), (int) (24 * density), (int) (12 * density));

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        // Resolve theme colors dynamically
        boolean isDarkMode = false;
        try {
            int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception ignored) {
        }
        int primaryTextColor = isDarkMode ? 0xFFe9edef : 0xFF111B21;
        int secondaryTextColor = isDarkMode ? 0xFF8696a0 : 0xFF667781;
        try {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
                primaryTextColor = typedValue.data;
            }
            if (context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
                secondaryTextColor = typedValue.data;
            }
        } catch (Exception ignored) {
        }

        title = resolveString(context, title);
        summary = resolveString(context, summary);

        if (icon != null) {
            // Container FrameLayout of 40dp
            FrameLayout container = new FrameLayout(context);
            LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                    (int) (40 * density), (int) (40 * density)
            );
            containerLp.gravity = Gravity.CENTER_VERTICAL;
            containerLp.setMarginStart(0);
            containerLp.setMarginEnd((int) (16 * density));
            container.setLayoutParams(containerLp);

            // ImageView centered inside container
            ImageView iconView = new ImageView(context);
            iconView.setImageDrawable(icon);
            iconView.setImageTintList(ColorStateList.valueOf(secondaryTextColor));

            int iconSizeDp = 24;
            if ("ic_home_tab_status_unfilled".equals(iconName)) {
                iconSizeDp = 28;
            }
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                    (int) (iconSizeDp * density), (int) (iconSizeDp * density)
            );
            iconLp.gravity = Gravity.CENTER;
            iconView.setLayoutParams(iconLp);

            container.addView(iconView);
            row.addView(container);
        }

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textParams);

        TextView titleView = createWdsTextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(primaryTextColor);
        textLayout.addView(titleView);

        if (!TextUtils.isEmpty(summary)) {
            TextView summaryView = createWdsTextView(context);
            summaryView.setText(summary);
            summaryView.setTag("wds_summary");
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            summaryView.setTextColor(secondaryTextColor);
            summaryView.setPadding(0, (int) (4 * density), 0, 0);
            textLayout.addView(summaryView);
        }
        row.addView(textLayout);

        if (clickListener != null) {
            row.setOnClickListener(clickListener);
            row.setClickable(true);
            row.setFocusable(true);
        }

        return row;
    }

    private static View createSwitchTile(Context context, String key, String title, String summary, boolean defVal, SharedPreferences prefs, PrefChangeListener listener, Map<String, View> tileViews, JSONArray prefsArray, boolean isSearch) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        row.setPadding((int) (16 * density), (int) (12 * density), (int) (24 * density), (int) (12 * density));

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        // Resolve theme colors dynamically
        boolean isDarkMode = false;
        try {
            int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception ignored) {
        }
        int primaryTextColor = isDarkMode ? 0xFFe9edef : 0xFF111B21;
        int secondaryTextColor = isDarkMode ? 0xFF8696a0 : 0xFF667781;
        try {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
                primaryTextColor = typedValue.data;
            }
            if (context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
                secondaryTextColor = typedValue.data;
            }
        } catch (Exception ignored) {
        }

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(lp);

        TextView titleView = createWdsTextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(primaryTextColor);
        textLayout.addView(titleView);

        if (!TextUtils.isEmpty(summary)) {
            TextView summaryView = createWdsTextView(context);
            summaryView.setText(summary);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            summaryView.setTextColor(secondaryTextColor);
            summaryView.setPadding(0, (int) (4 * density), 0, 0);
            textLayout.addView(summaryView);
        }
        row.addView(textLayout);

        View wdsSwitch = createWdsSwitch(context);

        boolean currentVal = prefs.getBoolean(key, defVal);
        setSwitchChecked(wdsSwitch, currentVal);

        final View finalSwitch = wdsSwitch;
        if (isSearch) {
            wdsSwitch.setClickable(true);
            wdsSwitch.setFocusable(true);
            wdsSwitch.setOnClickListener(v -> {
                boolean newVal = !getSwitchChecked(finalSwitch);
                setSwitchChecked(finalSwitch, newVal);
                prefs.edit().putBoolean(key, newVal).apply();
                if (listener != null) {
                    listener.onPrefChanged(key, newVal);
                }
            });
        } else {
            wdsSwitch.setClickable(false);
            wdsSwitch.setFocusable(false);
            row.setOnClickListener(v -> {
                boolean newVal = !getSwitchChecked(finalSwitch);
                setSwitchChecked(finalSwitch, newVal);
                prefs.edit().putBoolean(key, newVal).apply();
                if (listener != null) {
                    listener.onPrefChanged(key, newVal);
                }
                checkDependencies(prefsArray, prefs, tileViews);
            });
        }

        row.addView(wdsSwitch);
        return row;
    }

    private static View createListTile(Context context, JSONObject pref, SharedPreferences prefs, PrefChangeListener listener) {
        try {
            String key = pref.getString("key");
            String title = resolveString(context, pref.getString("title"));
            String summary = resolveString(context, pref.optString("summary", ""));
            String valueType = pref.optString("value_type", "string");
            JSONArray entriesJson = pref.getJSONArray("entries");

            String[] entries = new String[entriesJson.length()];
            String[] values = new String[entriesJson.length()];
            for (int i = 0; i < entriesJson.length(); i++) {
                JSONObject entryObj = entriesJson.getJSONObject(i);
                entries[i] = resolveString(context, entryObj.getString("label"));
                values[i] = String.valueOf(entryObj.get("value"));
            }

            int initialSelectedIndex = 0;
            if ("int".equals(valueType)) {
                int defaultVal = pref.optInt("default", 0);
                int current = prefs.getInt(key, defaultVal);
                for (int i = 0; i < values.length; i++) {
                    try {
                        if (Integer.parseInt(values[i]) == current) {
                            initialSelectedIndex = i;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } else if ("boolean".equals(valueType)) {
                boolean defaultVal = pref.optBoolean("default", false);
                boolean current = prefs.getBoolean(key, defaultVal);
                for (int i = 0; i < values.length; i++) {
                    if (Boolean.parseBoolean(values[i]) == current) {
                        initialSelectedIndex = i;
                        break;
                    }
                }
            } else {
                String defaultVal = pref.optString("default", "");
                String current = prefs.getString(key, defaultVal);
                for (int i = 0; i < values.length; i++) {
                    if (values[i].equals(current)) {
                        initialSelectedIndex = i;
                        break;
                    }
                }
            }

            String currentLabel = initialSelectedIndex < entries.length ? entries[initialSelectedIndex] : "";
            String displaySummary = summary;
            if (displaySummary.contains("%s")) {
                displaySummary = displaySummary.replace("%s", currentLabel);
            } else if (displaySummary.isEmpty()) {
                displaySummary = currentLabel;
            }

            final String rawSummary = summary;
            final int finalInitialSelectedIndex = initialSelectedIndex;
            final View[] rowHolder = new View[1];

            rowHolder[0] = createWdsRow(context, title, displaySummary, null, v -> {
                int selectedIndex = 0;
                if ("int".equals(valueType)) {
                    int defaultVal = pref.optInt("default", 0);
                    int current = prefs.getInt(key, defaultVal);
                    for (int i = 0; i < values.length; i++) {
                        try {
                            if (Integer.parseInt(values[i]) == current) {
                                selectedIndex = i;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } else if ("boolean".equals(valueType)) {
                    boolean defaultVal = pref.optBoolean("default", false);
                    boolean current = prefs.getBoolean(key, defaultVal);
                    for (int i = 0; i < values.length; i++) {
                        if (Boolean.parseBoolean(values[i]) == current) {
                            selectedIndex = i;
                            break;
                        }
                    }
                } else {
                    String defaultVal = pref.optString("default", "");
                    String current = prefs.getString(key, defaultVal);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(current)) {
                            selectedIndex = i;
                            break;
                        }
                    }
                }

                AlertDialogWpp builder = new AlertDialogWpp(context);
                builder.setTitle(title);
                builder.setSingleChoiceItems(entries, selectedIndex, (dialog, which) -> {
                    String selectedVal = values[which];
                    String selectedLabel = entries[which];
                    if ("int".equals(valueType)) {
                        int intVal = Integer.parseInt(selectedVal);
                        prefs.edit().putInt(key, intVal).apply();
                        if (listener != null) {
                            listener.onPrefChanged(key, intVal);
                        }
                    } else if ("boolean".equals(valueType)) {
                        boolean boolVal = Boolean.parseBoolean(selectedVal);
                        prefs.edit().putBoolean(key, boolVal).apply();
                        if (listener != null) {
                            listener.onPrefChanged(key, boolVal);
                        }
                    } else {
                        prefs.edit().putString(key, selectedVal).apply();
                        if (listener != null) {
                            listener.onPrefChanged(key, selectedVal);
                        }
                    }

                    // Dynamically update the summary text view on selection
                    try {
                        TextView summaryView = rowHolder[0].findViewWithTag("wds_summary");
                        if (summaryView != null) {
                            String newSummary = rawSummary;
                            if (newSummary.contains("%s")) {
                                newSummary = newSummary.replace("%s", selectedLabel);
                            } else if (newSummary.isEmpty()) {
                                newSummary = selectedLabel;
                            }
                            summaryView.setText(newSummary);
                        }
                    } catch (Exception ignored) {
                    }

                    dialog.dismiss();
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            });

            return rowHolder[0];
        } catch (Exception e) {
            return null;
        }
    }

    private static View createMultiTile(Context context, JSONObject pref, SharedPreferences prefs, PrefChangeListener listener) {
        try {
            String key = pref.getString("key");
            String title = pref.getString("title");
            String summary = pref.optString("summary", "");
            JSONArray entriesJson = pref.getJSONArray("entries");

            String[] entries = new String[entriesJson.length()];
            String[] values = new String[entriesJson.length()];
            for (int i = 0; i < entriesJson.length(); i++) {
                JSONObject entryObj = entriesJson.getJSONObject(i);
                entries[i] = entryObj.getString("label");
                values[i] = String.valueOf(entryObj.get("value"));
            }

            return createWdsRow(context, title, summary, null, v -> {
                String savedVal = prefs.getString(key, "");
                boolean[] checkedStates = new boolean[values.length];
                for (int i = 0; i < values.length; i++) {
                    checkedStates[i] = savedVal.contains(values[i]);
                }

                AlertDialogWpp builder = new AlertDialogWpp(context);
                builder.setTitle(title);
                builder.setMultiChoiceItems(entries, checkedStates, (dialog, which, isChecked) -> {
                    checkedStates[which] = isChecked;
                });
                builder.setPositiveButton("OK", (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < values.length; i++) {
                        if (checkedStates[i]) {
                            if (sb.length() > 0) {
                                sb.append(",");
                            }
                            sb.append(values[i]);
                        }
                    }
                    String result = sb.toString();
                    prefs.edit().putString(key, result).apply();
                    if (listener != null) {
                        listener.onPrefChanged(key, result);
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            });
        } catch (Exception e) {
            return null;
        }
    }

    private static View createTextTile(Context context, JSONObject pref, SharedPreferences prefs, PrefChangeListener listener) {
        try {
            String key = pref.getString("key");
            String title = pref.getString("title");
            String summary = pref.optString("summary", "");
            String valueType = pref.optString("value_type", "string");

            return createWdsRow(context, title, summary, null, v -> {
                AlertDialogWpp builder = new AlertDialogWpp(context);
                builder.setTitle(title);

                float density = context.getResources().getDisplayMetrics().density;
                EditText input = new EditText(context);
                String currentText;
                if ("int".equals(valueType)) {
                    currentText = String.valueOf(prefs.getInt(key, pref.optInt("default", 0)));
                } else {
                    currentText = prefs.getString(key, pref.optString("default", ""));
                }
                input.setText(currentText);
                input.setTextColor(0xFFE9EDEF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int margin = (int) (24 * density);
                lp.setMargins(margin, margin / 2, margin, margin / 2);
                input.setLayoutParams(lp);

                LinearLayout container = new LinearLayout(context);
                container.setOrientation(LinearLayout.VERTICAL);
                container.addView(input);
                builder.setView(container);

                builder.setPositiveButton("Save", (dialog, which) -> {
                    String newVal = input.getText().toString();
                    if ("int".equals(valueType)) {
                        int intVal = 0;
                        try {
                            intVal = Integer.parseInt(newVal);
                        } catch (Exception ignored) {
                        }
                        prefs.edit().putInt(key, intVal).apply();
                        if (listener != null) {
                            listener.onPrefChanged(key, intVal);
                        }
                    } else {
                        prefs.edit().putString(key, newVal).apply();
                        if (listener != null) {
                            listener.onPrefChanged(key, newVal);
                        }
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            });
        } catch (Exception e) {
            return null;
        }
    }

    private static View createActionTile(Context context, JSONObject pref) {
        try {
            String key = pref.getString("key");
            String title = pref.getString("title");
            String summary = pref.optString("summary", "");

            return createWdsRow(context, title, summary, null, v -> {
                if ("open_deleted_messages".equals(key)) {
                    try {
                        Intent intent = new Intent();
                        intent.setClassName(context.getPackageName(), "com.waenhancer.activities.DeletedMessagesActivity");
                        context.startActivity(intent);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    private static void setSwitchChecked(View view, boolean checked) {
        try {
            XposedHelpers.callMethod(view, "setChecked", checked);
        } catch (Throwable ignored) {
            if (view instanceof CompoundButton) {
                ((CompoundButton) view).setChecked(checked);
            }
        }
    }

    private static boolean getSwitchChecked(View view) {
        try {
            return (boolean) XposedHelpers.callMethod(view, "isChecked");
        } catch (Throwable ignored) {
            if (view instanceof CompoundButton) {
                return ((CompoundButton) view).isChecked();
            }
            return false;
        }
    }

    private static void checkDependencies(JSONArray prefsArray, SharedPreferences prefs, Map<String, View> tileViews) {
        try {
            for (int i = 0; i < prefsArray.length(); i++) {
                JSONObject pref = prefsArray.getJSONObject(i);
                String key = pref.getString("key");
                View tile = tileViews.get(key);
                if (tile == null) {
                    continue;
                }

                if (pref.has("dep")) {
                    String depKey = pref.getString("dep");
                    boolean depVal = prefs.getBoolean(depKey, false);
                    tile.setVisibility(depVal ? View.VISIBLE : View.GONE);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static TextView createWdsTextView(Context context) {
        try {
            Class<?> wdsTvClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.textview.WDSTextView");
            return (TextView) wdsTvClass.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
        } catch (Throwable t) {
            return new TextView(context);
        }
    }

    private static View createWdsSwitch(Context context) {
        try {
            Class<?> wdsSwitchClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.toggle.WDSSwitch");
            return (View) wdsSwitchClass.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
        } catch (Throwable t) {
            try {
                Class<?> switchClass = Class.forName("X.0xb", true, context.getClassLoader());
                return (View) switchClass.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
            } catch (Throwable t2) {
                return new SwitchCompat(context);
            }
        }
    }
}
