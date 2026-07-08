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

public class WdsSettingsTileRenderer {

    public interface PrefChangeListener {
        void onPrefChanged(String key, Object newValue);
    }

    public static JSONObject loadSettingsMap(Context context) {
        try {
            android.content.res.Resources res = com.waenhancer.xposed.utils.XResManager.moduleResources;
            if (res == null) res = context.getResources();
            int resId = res.getIdentifier("waex_settings_map", "raw", "com.waenhancer");
            if (resId == 0) {
                resId = res.getIdentifier("waex_settings_map", "raw", context.getPackageName());
            }
            if (resId == 0) return null;
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
        if (str == null) return "";
        if (str.startsWith("@string/")) {
            try {
                String name = str.substring(8);
                android.content.res.Resources res = com.waenhancer.xposed.utils.XResManager.moduleResources;
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
                    return res.getString(id);
                }
            } catch (Throwable ignored) {}
        }
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

                String iconName = cat.optString("icon", "ic_settings");
                android.graphics.drawable.Drawable icon = com.waenhancer.xposed.utils.DesignUtils.getDrawableByName(iconName);
                if (icon == null) {
                    icon = com.waenhancer.xposed.utils.DesignUtils.getDrawableByName("ic_settings");
                }
                de.robv.android.xposed.XposedBridge.log("[WAEX] Category id: " + id + ", iconName: " + iconName + ", icon: " + icon);

                View row = createWdsRow(activity, title, summary, icon, iconName, v -> {
                    if ("optimization".equals(id)) {
                        try {
                            Class<?> aboutClass = com.waenhancer.xposed.core.WppCore.getAboutActivityClass(activity.getClassLoader());
                            if (aboutClass != null) {
                                android.content.Intent intent = new android.content.Intent(activity, aboutClass);
                                intent.putExtra("wae_optimize_db", true);
                                activity.startActivity(intent);
                            }
                        } catch (Throwable t) {
                            de.robv.android.xposed.XposedBridge.log("[WAEX] Failed to start optimization from settings: " + t.getMessage());
                        }
                    } else {
                        android.content.Intent intent = new android.content.Intent(activity, activity.getClass());
                        intent.putExtra("waex_screen_id", id);
                        activity.startActivity(intent);
                    }
                });
                container.addView(row);
            }
        } catch (Exception ignored) {}

        ScrollView scrollView = new ScrollView(activity);
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
        } catch (Exception ignored) {}
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
            renderPrefsArray(activity, container, prefsArray, prefs, listener);
        } catch (Exception ignored) {}

        ScrollView scrollView = new ScrollView(activity);
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
                
                android.graphics.drawable.Drawable icon = null;
                String iconName = "";
                if ("home_screen_main".equals(subId)) {
                    iconName = "ic_home_black_24dp";
                } else if ("conversation_main".equals(subId)) {
                    iconName = "ic_home_tab_chats_unfilled";
                }
                
                if (!iconName.isEmpty()) {
                    icon = com.waenhancer.xposed.utils.DesignUtils.getDrawableByName(iconName);
                }
                if (icon == null) {
                    icon = com.waenhancer.xposed.utils.DesignUtils.getDrawableByName("ic_chevron_right");
                }
                
                View catTile = createWdsRow(activity, subTitle, subSummary, icon, iconName, v -> {
                    android.content.Intent intent = new android.content.Intent(activity, activity.getClass());
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
                renderPrefsArray(activity, container, prefsArray, prefs, listener);
            }
        } catch (Exception ignored) {}

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(container);
        return scrollView;
    }

    private static void renderPrefsArray(Context context, LinearLayout container, JSONArray prefsArray, SharedPreferences prefs, PrefChangeListener listener) {
        try {
            Map<String, View> tileViews = new HashMap<>();
            
            for (int j = 0; j < prefsArray.length(); j++) {
                JSONObject pref = prefsArray.getJSONObject(j);
                String type = pref.getString("type");
                String key = pref.getString("key");
                String title = pref.getString("title");
                boolean isEnabled = pref.optBoolean("enabled", true);
                if (!isEnabled) {
                    title = title + " [Pro]";
                }
                String summary = pref.optString("summary", "");

                View tile = null;
                if (!isEnabled) {
                    final String displayTitle = title;
                    tile = createWdsRow(context, title, summary, null, v -> {
                        try {
                            AlertDialogWpp builder = new AlertDialogWpp(context);
                            builder.asBottomSheet();
                            builder.setTitle(displayTitle);
                            builder.setMessage("This feature is under development and will be available in the future updates. Stay tuned.");
                            builder.setPositiveButton("Dismiss", null);
                            builder.show();
                        } catch (Throwable t) {
                            de.robv.android.xposed.XposedBridge.log("[WAEX] Failed to show pro bottom sheet: " + t.getMessage());
                        }
                    });
                } else {
                    if ("switch".equals(type)) {
                        boolean def = pref.optBoolean("default", false);
                        tile = createSwitchTile(context, key, title, summary, def, prefs, listener, tileViews, prefsArray);
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
                }

                if (tile != null) {
                    tileViews.put(key, tile);
                    container.addView(tile);
                }
            }

            checkDependencies(prefsArray, prefs, tileViews);
        } catch (Exception ignored) {}
    }

    private static View createWdsRow(Context context, String title, String summary, android.graphics.drawable.Drawable icon, View.OnClickListener clickListener) {
        return createWdsRow(context, title, summary, icon, null, clickListener);
    }

    private static View createWdsRow(Context context, String title, String summary, android.graphics.drawable.Drawable icon, String iconName, View.OnClickListener clickListener) {
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
            int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}

        title = resolveString(context, title);
        summary = resolveString(context, summary);

        if (icon != null) {
            ImageView iconView = new ImageView(context);
            int iconSizeDp = 24;
            int marginEndDp = 20;
            if ("ic_home_tab_status_unfilled".equals(iconName)) {
                iconSizeDp = 28; // Make status icon slightly larger to balance visual weight
                marginEndDp = 16; // Keep the total spacing (iconSizeDp + marginEndDp = 44dp) constant for alignment
            }
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int) (iconSizeDp * density), (int) (iconSizeDp * density));
            iconParams.setMarginEnd((int) (marginEndDp * density));
            iconView.setLayoutParams(iconParams);
            iconView.setImageDrawable(icon);
            iconView.setImageTintList(android.content.res.ColorStateList.valueOf(secondaryTextColor));
            row.addView(iconView);
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

    private static View createSwitchTile(Context context, String key, String title, String summary, boolean defVal, SharedPreferences prefs, PrefChangeListener listener, Map<String, View> tileViews, JSONArray prefsArray) {
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
            int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}

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
        row.setOnClickListener(v -> {
            boolean newVal = !getSwitchChecked(finalSwitch);
            setSwitchChecked(finalSwitch, newVal);
            prefs.edit().putBoolean(key, newVal).apply();
            if (listener != null) listener.onPrefChanged(key, newVal);
            checkDependencies(prefsArray, prefs, tileViews);
        });

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
                    } catch (Exception ignored) {}
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
                        } catch (Exception ignored) {}
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
                        if (listener != null) listener.onPrefChanged(key, intVal);
                    } else if ("boolean".equals(valueType)) {
                        boolean boolVal = Boolean.parseBoolean(selectedVal);
                        prefs.edit().putBoolean(key, boolVal).apply();
                        if (listener != null) listener.onPrefChanged(key, boolVal);
                    } else {
                        prefs.edit().putString(key, selectedVal).apply();
                        if (listener != null) listener.onPrefChanged(key, selectedVal);
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
                    } catch (Exception ignored) {}

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
                            if (sb.length() > 0) sb.append(",");
                            sb.append(values[i]);
                        }
                    }
                    String result = sb.toString();
                    prefs.edit().putString(key, result).apply();
                    if (listener != null) listener.onPrefChanged(key, result);
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
                        } catch (Exception ignored) {}
                        prefs.edit().putInt(key, intVal).apply();
                        if (listener != null) listener.onPrefChanged(key, intVal);
                    } else {
                        prefs.edit().putString(key, newVal).apply();
                        if (listener != null) listener.onPrefChanged(key, newVal);
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
                        android.content.Intent intent = new android.content.Intent();
                        intent.setClassName(context.getPackageName(), "com.waenhancer.activities.DeletedMessagesActivity");
                        context.startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    private static void setSwitchChecked(View view, boolean checked) {
        try {
            de.robv.android.xposed.XposedHelpers.callMethod(view, "setChecked", checked);
        } catch (Throwable ignored) {
            if (view instanceof android.widget.CompoundButton) {
                ((android.widget.CompoundButton) view).setChecked(checked);
            }
        }
    }

    private static boolean getSwitchChecked(View view) {
        try {
            return (boolean) de.robv.android.xposed.XposedHelpers.callMethod(view, "isChecked");
        } catch (Throwable ignored) {
            if (view instanceof android.widget.CompoundButton) {
                return ((android.widget.CompoundButton) view).isChecked();
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
                if (tile == null) continue;

                if (pref.has("dep")) {
                    String depKey = pref.getString("dep");
                    boolean depVal = prefs.getBoolean(depKey, false);
                    tile.setVisibility(depVal ? View.VISIBLE : View.GONE);
                }
            }
        } catch (Exception ignored) {}
    }

    private static TextView createWdsTextView(Context context) {
        try {
            Class<?> wdsTvClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.textview.WDSTextView");
            return (TextView) wdsTvClass.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(context, null);
        } catch (Throwable t) {
            return new TextView(context);
        }
    }

    private static View createWdsSwitch(Context context) {
        try {
            Class<?> wdsSwitchClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.toggle.WDSSwitch");
            return (View) wdsSwitchClass.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(context, null);
        } catch (Throwable t) {
            try {
                Class<?> switchClass = Class.forName("X.0xb", true, context.getClassLoader());
                return (View) switchClass.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(context, null);
            } catch (Throwable t2) {
                return new androidx.appcompat.widget.SwitchCompat(context);
            }
        }
    }
}
