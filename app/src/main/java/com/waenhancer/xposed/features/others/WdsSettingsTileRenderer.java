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

                View row = createWdsRow(activity, title, summary, v -> {
                    android.content.Intent intent = new android.content.Intent(activity, activity.getClass());
                    intent.putExtra("waex_screen_id", id);
                    activity.startActivity(intent);
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
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static View buildSubScreen(Context context, JSONObject category, SharedPreferences prefs, PrefChangeListener listener) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        String catTitle = category.optString("title", "Settings");
        container.setTag(catTitle);

        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        container.setPadding(0, pad, 0, pad);

        try {
            JSONArray subScreens = category.getJSONArray("sub_screens");
            for (int i = 0; i < subScreens.length(); i++) {
                JSONObject sub = subScreens.getJSONObject(i);
                String subTitle = sub.getString("title");
                JSONArray prefsArray = sub.getJSONArray("prefs");

                // Add header for sub-screen section if multiple sub_screens exist
                if (subScreens.length() > 1) {
                    TextView header = new TextView(context);
                    header.setText(subTitle.toUpperCase());
                    header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    header.setTypeface(Typeface.DEFAULT_BOLD);
                    header.setTextColor(0xFF21c063);
                    header.setPadding((int) (24 * density), (int) (16 * density), (int) (24 * density), (int) (8 * density));
                    container.addView(header);
                }

                Map<String, View> tileViews = new HashMap<>();

                for (int j = 0; j < prefsArray.length(); j++) {
                    JSONObject pref = prefsArray.getJSONObject(j);
                    String type = pref.getString("type");
                    String key = pref.getString("key");
                    String title = pref.getString("title");
                    String summary = pref.optString("summary", "");

                    View tile = null;
                    if ("switch".equals(type)) {
                        boolean def = pref.optBoolean("default_bool", false);
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
                        // Standard tile for unsupported or other types
                        tile = createWdsRow(context, title, summary, null);
                    }

                    if (tile != null) {
                        tileViews.put(key, tile);
                        container.addView(tile);
                    }
                }

                // Initial dependency check
                checkDependencies(prefsArray, prefs, tileViews);
            }
        } catch (Exception ignored) {}

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(container);
        return scrollView;
    }

    private static View createWdsRow(Context context, String title, String summary, View.OnClickListener clickListener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
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

        TextView titleView = createWdsTextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(primaryTextColor);
        row.addView(titleView);

        if (!TextUtils.isEmpty(summary)) {
            TextView summaryView = createWdsTextView(context);
            summaryView.setText(summary);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            summaryView.setTextColor(secondaryTextColor);
            summaryView.setPadding(0, (int) (4 * density), 0, 0);
            row.addView(summaryView);
        }

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
            String title = pref.getString("title");
            String summary = pref.optString("summary", "");
            String defVal = pref.optString("default_str", "");
            JSONArray entriesJson = pref.getJSONArray("entries");
            JSONArray valuesJson = pref.getJSONArray("entry_values");

            String[] entries = new String[entriesJson.length()];
            String[] values = new String[valuesJson.length()];
            for (int i = 0; i < entriesJson.length(); i++) {
                entries[i] = entriesJson.getString(i);
                values[i] = valuesJson.getString(i);
            }

            return createWdsRow(context, title, summary, v -> {
                String current = prefs.getString(key, defVal);
                int selectedIndex = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i].equals(current)) {
                        selectedIndex = i;
                        break;
                    }
                }

                AlertDialogWpp builder = new AlertDialogWpp(context);
                builder.setTitle(title);
                builder.setSingleChoiceItems(entries, selectedIndex, (dialog, which) -> {
                    String selectedVal = values[which];
                    prefs.edit().putString(key, selectedVal).apply();
                    if (listener != null) listener.onPrefChanged(key, selectedVal);
                    dialog.dismiss();
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            });
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
            JSONArray valuesJson = pref.getJSONArray("entry_values");

            String[] entries = new String[entriesJson.length()];
            String[] values = new String[valuesJson.length()];
            for (int i = 0; i < entriesJson.length(); i++) {
                entries[i] = entriesJson.getString(i);
                values[i] = valuesJson.getString(i);
            }

            return createWdsRow(context, title, summary, v -> {
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
            String defVal = pref.optString("default_str", "");

            return createWdsRow(context, title, summary, v -> {
                AlertDialogWpp builder = new AlertDialogWpp(context);
                builder.setTitle(title);

                float density = context.getResources().getDisplayMetrics().density;
                EditText input = new EditText(context);
                input.setText(prefs.getString(key, defVal));
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
                    prefs.edit().putString(key, newVal).apply();
                    if (listener != null) listener.onPrefChanged(key, newVal);
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

            return createWdsRow(context, title, summary, v -> {
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
