package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;
import java.util.HashSet;
import java.util.Set;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ViewParent;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.waenhancer.App;
import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.bridge.client.ProviderSharedPreferences;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.ProHelper;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import org.json.JSONArray;

/**
 * Ultimate Settings Injector: Uses multiple strategies to find and inject into WA Settings.
 * Injects BOTH a Tile (row) and a Toolbar menu item as a backup.
 */
public class SettingsInjector extends Feature {
    private static final String SETTINGS_TAB_ACTIVITY = "com.whatsapp.settings.ui.SettingsTabActivity";
    private static final int VIEW_ID_WAEX_SETTINGS = 10001;
    private static final int VIEW_ID_WAEX_SEARCH = 10004;
    private static final int VIEW_ID_WAEX_TEST_SWITCH = 10003;
    private final Set<Integer> processedActivities = new HashSet<>();
    private static final int MENU_ID_WAEX_SETTINGS = 9999;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SettingsInjector(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        final Class<?> settingsActivityClass;
        try {
            Class<?> directClass = XposedHelpers.findClassIfExists(SETTINGS_TAB_ACTIVITY, classLoader);
            settingsActivityClass = directClass != null ? directClass : Unobfuscator.loadSettingsActivityClass(classLoader);
        } catch (Throwable t) {
            // XposedBridge.log("[WaEnhancerX] SettingsInjector disabled: unable to resolve settings activity");
            return;
        }
        if (settingsActivityClass == null) return;

        XC_MethodHook menuHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String screenId = activity.getIntent().getStringExtra("waex_screen_id");
                if (screenId != null) {
                    Menu menu = (Menu) param.args[0];
                    if (menu != null) {
                        menu.clear(); // Hides all native menu items (including search!)
                        if ("root".equals(screenId)) {
                            if (menu.findItem(VIEW_ID_WAEX_SEARCH) == null) {
                                MenuItem searchItem = menu.add(0, VIEW_ID_WAEX_SEARCH, 0, "Search");
                                Drawable icon = DesignUtils.getDrawable(R.drawable.ic_search);
                                if (icon != null) {
                                    boolean isNight = DesignUtils.isNightMode();
                                    icon = DesignUtils.coloredDrawable(icon, isNight ? Color.WHITE : Color.BLACK);
                                    searchItem.setIcon(icon);
                                }
                                searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                                searchItem.setOnMenuItemClickListener(item -> {
                                    Intent intent = new Intent(activity, activity.getClass());
                                    intent.putExtra("waex_screen_id", "search");
                                    activity.startActivity(intent);
                                    return true;
                                });
                            }
                        }
                    }
                    return;
                }

                String entryPoint = getSafeString("open_waex", "2");
                if ("0".equals(entryPoint) || "2".equals(entryPoint)) return;
                Menu menu = (Menu) param.args[0];
                injectToolbarMenu(menu, activity);
            }
        };
        XposedBridge.hookAllMethods(settingsActivityClass, "onPrepareOptionsMenu", menuHook);
        XposedBridge.hookAllMethods(settingsActivityClass, "onCreateOptionsMenu", menuHook);

        XposedBridge.hookAllMethods(settingsActivityClass, "onNewIntent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Intent intent = (Intent) param.args[0];
                activity.setIntent(intent);
            }
        });

        XposedBridge.hookAllMethods(settingsActivityClass, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Intent intent = activity.getIntent();
                XposedBridge.log("[WAEX] SettingsInjector onResume called, intent: " + intent + ", extras: " + (intent != null ? intent.getExtras() : "null"));
                String screenId = intent != null ? intent.getStringExtra("waex_screen_id") : null;
                if (screenId != null) {
                    hijackWholeScreen(activity, screenId);
                    return;
                }

                // Check for pending restart on main settings screen
                try {
                    boolean needRestart = WppCore.getPrivBoolean("need_restart", false);
                    if (needRestart) {
                        FeatureLoader.showRestartDialog(activity);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] Error checking pending restart on settings resume: " + t.getMessage());
                }

                String entryPoint = getSafeString("open_waex", "2");

                // Clean up elements that shouldn't be present in the current mode
                if (!"2".equals(entryPoint)) {
                    removeNativeViewTile(activity);
                } else {
                    removeToolbarButton(activity);
                }

                // Always remove optimization tile from main settings screen
                removeOptimizationTile(activity);

                if ("0".equals(entryPoint)) {
                    removeToolbarButton(activity);
                    return;
                }

                if ("2".equals(entryPoint)) {
                    injectNativeViewTile(activity);
                    return;
                }

                injectToolbarButton(activity);
                int hash = System.identityHashCode(activity);
                if (!processedActivities.add(hash)) return;
                mainHandler.post(() -> {
                    try {
                        activity.invalidateOptionsMenu();
                        injectToolbarButton(activity);
                    } catch (Throwable ignored) {}
                });
            }
        });

        XposedBridge.hookAllMethods(settingsActivityClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                processedActivities.remove(System.identityHashCode(param.thisObject));
            }
        });

        Class<?> settingsNotificationsClass = null;
        try {
            settingsNotificationsClass = WppCore.getSettingsNotificationsActivityClass(classLoader);
        } catch (Throwable t) {
        }

        if (settingsNotificationsClass != null) {
            XposedBridge.hookAllMethods(settingsNotificationsClass, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    injectTestNotificationTile(activity);
                }
            });
        }
    }

    private View findAccountRow(ViewGroup listContainer, Activity activity) {
        View accountTextView = findTextViewWithText(listContainer, getLocalizedText(activity, "settings_account", "Account"));
        if (accountTextView != null) {
            return findDirectChildOfContainer(listContainer, accountTextView);
        }
        return null;
    }

    private void injectNativeViewTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            if (root.findViewById(VIEW_ID_WAEX_SETTINGS) != null) return;

            // Strategy 1: Find main settings container by ID 'settings_nested_scroll_view' -> 'container' and insert above Account.
            int scrollId = activity.getResources().getIdentifier("settings_nested_scroll_view", "id", activity.getPackageName());
            if (scrollId == 0) {
                scrollId = activity.getResources().getIdentifier("settings_scroll_view", "id", activity.getPackageName());
            }

            ViewGroup listContainer = null;
            if (scrollId != 0) {
                View scrollView = activity.findViewById(scrollId);
                if (scrollView != null) {
                    int containerId = activity.getResources().getIdentifier("container", "id", activity.getPackageName());
                    if (containerId != 0) {
                        View v = scrollView.findViewById(containerId);
                        if (v instanceof ViewGroup) {
                            listContainer = (ViewGroup) v;
                        }
                    }
                }
            }

            if (listContainer != null) {
                int accountInfoId = activity.getResources().getIdentifier("settings_account_info", "id", activity.getPackageName());
                View accountRow = null;
                if (accountInfoId != 0) {
                    accountRow = listContainer.findViewById(accountInfoId);
                }
                if (accountRow == null) {
                    accountRow = findAccountRow(listContainer, activity);
                }

                int insertionIndex = 0;
                if (accountRow != null) {
                    insertionIndex = listContainer.indexOfChild(accountRow);
                } else {
                    int profileId = activity.getResources().getIdentifier("profile_info", "id", activity.getPackageName());
                    if (profileId != 0) {
                        View profileInfo = listContainer.findViewById(profileId);
                        if (profileInfo != null) {
                            insertionIndex = listContainer.indexOfChild(profileInfo) + 1;
                        }
                    }
                }

                View anchor = accountRow != null ? accountRow : (listContainer.getChildCount() > 0 ? listContainer.getChildAt(0) : null);
                if (anchor != null) {
                    View customRow = createDynamicSettingRow(activity, anchor);
                    if (customRow != null) {
                        customRow.setId(VIEW_ID_WAEX_SETTINGS);
                        listContainer.addView(customRow, insertionIndex);
                        return; // Success
                    }
                }
            }

            // Strategy 2: Find main settings container by structure (Vertical LinearLayout with multiple clickable rows)
            listContainer = findSettingsListByStructure(root);
            if (listContainer != null) {
                View accountRow = findAccountRow(listContainer, activity);
                if (accountRow != null) {
                    int accountIndex = listContainer.indexOfChild(accountRow);
                    View customRow = createDynamicSettingRow(activity, accountRow);
                    if (customRow != null) {
                        customRow.setId(VIEW_ID_WAEX_SETTINGS);
                        listContainer.addView(customRow, accountIndex);
                        return; // Success
                    }
                }
            }

            // Strategy 3: Fallback to finding TextViews for Account and Privacy using localized resources
            View accountTextView = findTextViewWithText(root, getLocalizedText(activity, "settings_account", "Account"));
            View privacyTextView = findTextViewWithText(root, getLocalizedText(activity, "settings_privacy", "Privacy"));

            if (accountTextView == null && privacyTextView == null) return;

            // If only one is found, fall back to simple vertical parent resolution
            if (accountTextView == null || privacyTextView == null) {
                View found = accountTextView != null ? accountTextView : privacyTextView;
                ViewGroup container = findVerticalContainer(found);
                if (container != null) {
                    View row = findDirectChildOfContainer(container, found);
                    if (row != null) {
                        int index = container.indexOfChild(row);
                        View customRow = createDynamicSettingRow(activity, row);
                        if (customRow != null) {
                            customRow.setId(VIEW_ID_WAEX_SETTINGS);
                            container.addView(customRow, index);
                        }
                    }
                }
                return;
            }

            // Both found! Resolve their first common ancestor (main vertical settings list container)
            View accountRow = null;
            ViewGroup commonAncestor = null;

            // Trace ancestors of Account TextView
            List<View> accountAncestors = new ArrayList<>();
            View current = accountTextView;
            while (current != null) {
                accountAncestors.add(current);
                ViewParent parent = current.getParent();
                current = (parent instanceof View) ? (View) parent : null;
            }

            // Trace ancestors of Privacy TextView and find the first common ancestor
            current = privacyTextView;
            while (current != null) {
                int index = accountAncestors.indexOf(current);
                if (index != -1) {
                    commonAncestor = (ViewGroup) current;
                    if (index > 0) {
                        accountRow = accountAncestors.get(index - 1);
                    }
                    break;
                }
                ViewParent parent = current.getParent();
                current = (parent instanceof View) ? (View) parent : null;
            }

            if (commonAncestor != null && accountRow != null) {
                int index = commonAncestor.indexOfChild(accountRow);
                View customRow = createDynamicSettingRow(activity, accountRow);
                if (customRow != null) {
                    customRow.setId(VIEW_ID_WAEX_SETTINGS);
                    commonAncestor.addView(customRow, index);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] SettingsInjector: native view tile error: " + t.getMessage());
        }
    }

    private String getLocalizedText(Activity activity, String resName, String fallback) {
        try {
            Resources res = activity.getResources();
            int resId = res.getIdentifier(resName, "string", activity.getPackageName());
            if (resId != 0) {
                return res.getString(resId);
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private View findTextViewWithText(View view, String targetText) {
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString().trim();
            if (text.equalsIgnoreCase(targetText)) {
                return view;
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTextViewWithText(group.getChildAt(i), targetText);
                if (found != null) return found;
            }
        }
        return null;
    }

    private ViewGroup findVerticalContainer(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            if (group instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) group;
                if (layout.getOrientation() == LinearLayout.VERTICAL) {
                    return layout;
                }
            }
            parent = group.getParent();
        }
        return null;
    }

    private View findDirectChildOfContainer(ViewGroup container, View view) {
        View current = view;
        while (current != null) {
            ViewParent parent = current.getParent();
            if (parent == container) {
                return current;
            }
            current = (parent instanceof View) ? (View) parent : null;
        }
        return null;
    }

    private ViewGroup findSettingsListByStructure(View view) {
        if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            if (layout.getOrientation() == LinearLayout.VERTICAL) {
                int clickableRows = 0;
                for (int i = 0; i < layout.getChildCount(); i++) {
                    View child = layout.getChildAt(i);
                    if (child instanceof ViewGroup && (child.isClickable() || child.hasOnClickListeners())) {
                        clickableRows++;
                    }
                }
                // The main settings screen usually has many clickable rows (Profile, Account, Privacy, etc.)
                if (clickableRows >= 4) {
                    return layout;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ViewGroup found = findSettingsListByStructure(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private ImageView findImageView(View view) {
        if (view instanceof ImageView) {
            return (ImageView) view;
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView found = findImageView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findTextView(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private View createDynamicSettingRow(Activity activity, View anchorView) {
        try {
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);

            // Copy anchor layout params so it behaves exactly like a native row in the list
            if (anchorView.getLayoutParams() != null) {
                rowLayout.setLayoutParams(anchorView.getLayoutParams());
            } else {
                rowLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            // Standard WDS paddings for settings row (starts at 16dp left, 24dp right)
            int padLeft = dp(activity, 16);
            int padRight = dp(activity, 24);
            int padTop = anchorView.getPaddingTop() > 0 ? anchorView.getPaddingTop() : dp(activity, 15);
            int padBottom = anchorView.getPaddingBottom() > 0 ? anchorView.getPaddingBottom() : dp(activity, 15);
            rowLayout.setPadding(padLeft, padTop, padRight, padBottom);

            // Match native ripple selector background
            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            rowLayout.setBackgroundResource(outValue.resourceId);

            rowLayout.setClickable(true);
            rowLayout.setFocusable(true);

            // Find adjacent elements to copy styling exactly
            ImageView anchorIcon = findImageView(anchorView);
            List<TextView> anchorTextViews = new ArrayList<>();
            findTextViews(anchorView, anchorTextViews);

            TextView anchorTitle = anchorTextViews.size() > 0 ? anchorTextViews.get(0) : null;
            TextView anchorSummary = anchorTextViews.size() > 1 ? anchorTextViews.get(1) : null;

            // FrameLayout container for the icon to match 40dp width/height native bounds
            FrameLayout container = new FrameLayout(activity);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                dp(activity, 40),
                dp(activity, 40)
            );
            containerParams.gravity = Gravity.CENTER_VERTICAL;
            containerParams.setMarginStart(0);
            containerParams.setMarginEnd(dp(activity, 16));
            container.setLayoutParams(containerParams);

            // Icon ImageView (Styled centered inside container)
            ImageView iconView = new ImageView(activity);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(activity, 24),
                dp(activity, 24)
            );
            iconParams.gravity = Gravity.CENTER;
            iconView.setLayoutParams(iconParams);

            Drawable icon = DesignUtils.getDrawableByName("ic_settings");
            if (icon != null) {
                iconView.setImageDrawable(icon);
            }

            // Extract the native icon's exact tint to match colors perfectly
            if (anchorIcon != null && anchorIcon.getImageTintList() != null) {
                iconView.setImageTintList(anchorIcon.getImageTintList());
                iconView.setColorFilter(anchorIcon.getColorFilter());
                iconView.setAlpha(anchorIcon.getAlpha());
            } else if (anchorSummary != null) {
                iconView.setImageTintList(anchorSummary.getTextColors());
                iconView.setAlpha(anchorSummary.getAlpha());
            } else {
                iconView.setImageTintList(ColorStateList.valueOf(0xff8696a0));
            }
            container.addView(iconView);
            rowLayout.addView(container);

            // Text vertical container (starts at 72dp keyline, meaning leftMargin = 0 since container handles marginEnd)
            LinearLayout textContainer = new LinearLayout(activity);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textContainerParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            textContainerParams.setMarginStart(0);
            textContainer.setLayoutParams(textContainerParams);

            // Title TextView
            TextView titleText = new TextView(activity);
            titleText.setText(FeatureLoader.getModuleString(activity, R.string.waenhancer_settings, "WaEnhancerX Settings"));

            // Extract title typography from the anchor title TextView if available
            if (anchorTitle != null) {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorTitle.getTextSize());
                titleText.setTextColor(anchorTitle.getTextColors());
                titleText.setTypeface(anchorTitle.getTypeface());
            } else {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                titleText.setTextColor(0xffe9edef);
            }
            titleText.setEllipsize(TextUtils.TruncateAt.END);
            titleText.setSingleLine(true);

            // Summary TextView (Professional 1-2 line settings guide)
            TextView summaryText = new TextView(activity);
            summaryText.setText(FeatureLoader.getModuleString(
                activity,
                R.string.waenhancer_settings_desc, 
                "Configure WaEnhancerX features, UI customization, and privacy settings."
            ));

            // Copy typography and exact native description color from adjacent row!
            if (anchorSummary != null) {
                summaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorSummary.getTextSize());
                summaryText.setTextColor(anchorSummary.getTextColors());
                summaryText.setTypeface(anchorSummary.getTypeface());
                summaryText.setAlpha(anchorSummary.getAlpha());
            } else {
                summaryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                summaryText.setTextColor(0xff8696a0);
            }
            summaryText.setPadding(0, dp(activity, 2), 0, 0);

            // Limit summary to 2 lines maximum and show ellipsis (...) on overflow
            summaryText.setMaxLines(2);
            summaryText.setEllipsize(TextUtils.TruncateAt.END);

            textContainer.addView(titleText);
            textContainer.addView(summaryText);
            rowLayout.addView(textContainer);

            // Dynamic Click Action to open Settings
            rowLayout.setOnClickListener(v -> {
                String entryPoint = getSafeString("open_waex", "2");
                String openMode = getSafeString("open_settings_mode", "1");
                if ("2".equals(entryPoint) && "1".equals(openMode)) {
                    // Embedded: open within WhatsApp's hijacked SettingsTabActivity
                    Intent intent = new Intent(activity, activity.getClass());
                    intent.putExtra("waex_screen_id", "root");
                    activity.startActivity(intent);
                } else {
                    // External: open the WaEnhancerX module app
                    Utils.openModule(activity);
                }
            });

            return rowLayout;
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] SettingsInjector: Error creating dynamic setting row: " + t.getMessage());
            return null;
        }
    }

    private void hijackWholeScreen(Activity activity, String screenId) {
        try {
            XposedBridge.log("[WAEX] hijackWholeScreen called for screenId: " + screenId);
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            View existingContainer = root.findViewWithTag("waex_container_" + screenId);
            if (existingContainer != null) {
                XposedBridge.log("[WAEX] hijackWholeScreen: already hijacked for " + screenId);
                final String scrollToPref = activity.getIntent().getStringExtra("scroll_to_pref");
                if (scrollToPref != null) {
                    activity.getIntent().removeExtra("scroll_to_pref");
                    safeScrollTo(existingContainer, scrollToPref, 30);
                }
                return;
            }

            // Hide profile info and top divider on hijacked settings sub-screens
            String[] profileIds = {
                "profile_info", "profile_container", "settings_profile_info", "profile_header", "settings_top_divider",
                "me_tab_cover_photo", "me_tab_container", "me_tab_profile_picture_container", "me_tab_profile_info_name_area"
            };
            for (String id : profileIds) {
                int resId = activity.getResources().getIdentifier(id, "id", activity.getPackageName());
                if (resId != 0) {
                    View v = activity.findViewById(resId);
                    if (v != null) {
                        v.setVisibility(View.GONE);
                    }
                }
            }

            View originalList = findSettingsListByStructure(root);
            if (originalList == null) {
                originalList = root.findViewById(android.R.id.list);
            }
            if (originalList == null) return;

            ViewGroup parent = (ViewGroup) originalList.getParent();
            if (parent == null) return;

            ViewGroup.LayoutParams origParams = originalList.getLayoutParams();
            FrameLayout container = new FrameLayout(activity);
            if (origParams != null) {
                container.setLayoutParams(origParams);
            } else {
                container.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            container.setTag("waex_container_" + screenId);

            int listIndex = parent.indexOfChild(originalList);
            parent.removeView(originalList);
            parent.addView(container, listIndex);

            JSONObject settingsMap = WdsSettingsTileRenderer.loadSettingsMap(activity);
            if (settingsMap != null) {
                View contentView = null;
                String title = FeatureLoader.getModuleString(
                        activity, R.string.waenhancer_settings, "WaeX");
                
                SharedPreferences localPrefs = activity.getSharedPreferences(BuildConfig.APPLICATION_ID + "_preferences", Context.MODE_PRIVATE);
                SharedPreferences readWritePrefs = new ProviderSharedPreferences(activity, localPrefs, prefs);

                boolean isNight = DesignUtils.isNightMode();
                WdsSettingsTileRenderer.PrefChangeListener listener = (key, newValue) -> {
                    try {
                        WppCore.setPrivBooleanSync("need_restart", true);
                        String prefTitle = key;
                        try {
                            JSONArray categories = settingsMap.getJSONArray("categories");
                            for (int i = 0; i < categories.length(); i++) {
                                JSONObject category = categories.getJSONObject(i);
                                JSONArray subScreens = category.optJSONArray("sub_screens");
                                if (subScreens != null) {
                                    for (int j = 0; j < subScreens.length(); j++) {
                                        JSONObject subScreen = subScreens.getJSONObject(j);
                                        JSONArray prefsArray = subScreen.optJSONArray("prefs");
                                        if (prefsArray != null) {
                                            for (int k = 0; k < prefsArray.length(); k++) {
                                                JSONObject prefObj = prefsArray.getJSONObject(k);
                                                if (key.equals(prefObj.optString("key"))) {
                                                    prefTitle = prefObj.optString("title", key);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        String existing = WppCore.getPrivString("pending_changes", "");
                        Set<String> all = new LinkedHashSet<>();
                        if (!existing.isEmpty()) {
                            for (String t : existing.split("\\|")) {
                                if (!t.trim().isEmpty()) all.add(t.trim());
                            }
                        }
                        all.add(prefTitle);
                        WppCore.setPrivString("pending_changes", String.join("|", all));
                    } catch (Throwable t) {
                        XposedBridge.log("[WAEX] Failed to record pending restart: " + t.getMessage());
                    }

                    Intent changeIntent = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
                    changeIntent.putExtra("key", key);
                    changeIntent.setPackage(activity.getPackageName());
                    activity.sendBroadcast(changeIntent);
                };

                if ("search".equals(screenId)) {
                    title = "Search";
                    
                    ScrollView scrollView = new ScrollView(activity);
                    LinearLayout containerLayout = new LinearLayout(activity);
                    containerLayout.setOrientation(LinearLayout.VERTICAL);
                    float density = activity.getResources().getDisplayMetrics().density;
                    containerLayout.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
                    scrollView.addView(containerLayout);
                    
                    // Render all items initially
                    performSearch(activity, "", containerLayout, settingsMap, readWritePrefs, listener, isNight, density);
                    
                    // Setup native WDSSearchView at the top
                    try {
                        int searchBarId = activity.getResources().getIdentifier("wds_search_bar", "id", activity.getPackageName());
                        ViewGroup searchBar = activity.findViewById(searchBarId);
                        if (searchBar != null) {
                            // Find and hide standard toolbar child of wds_search_bar
                            ViewGroup rootToolbar = findToolbar(root);
                            if (rootToolbar != null) {
                                rootToolbar.setVisibility(View.GONE);
                            }
                            
                            // Instantiate com.whatsapp.ui.wds.components.search.WDSSearchView via reflection
                            Class<?> searchViewClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.search.WDSSearchView");
                            View wdsSearchView = (View) searchViewClass.getConstructor(Context.class, AttributeSet.class).newInstance(activity, null);
                            
                            XposedHelpers.callMethod(wdsSearchView, "setHint", "Search features...");
                            
                            // Back button close activity
                            ImageButton backBtn = (ImageButton) XposedHelpers.callMethod(wdsSearchView, "getBackButton");
                            if (backBtn != null) {
                                backBtn.setOnClickListener(v -> activity.finish());
                            }
                            
                            // Add search view to the bar on top
                            searchBar.addView(wdsSearchView);
                            
                            // Get input field - try by public field A09 (WDSEditText), then resource id, then tree walk
                            EditText searchInput = null;
                            try {
                                searchInput = (EditText) XposedHelpers.getObjectField(wdsSearchView, "A09");
                            } catch (Throwable ignored) {}
                            if (searchInput == null) {
                                int editId = activity.getResources().getIdentifier("search_src_text", "id", activity.getPackageName());
                                if (editId != 0) {
                                    searchInput = wdsSearchView.findViewById(editId);
                                }
                            }
                            if (searchInput == null) {
                                searchInput = findFirstEditText((ViewGroup) wdsSearchView);
                            }

                            if (searchInput != null) {
                                final EditText finalSearchInput = searchInput;
                                finalSearchInput.addTextChangedListener(new TextWatcher() {
                                    @Override
                                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                                    
                                    @Override
                                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                                        String query = s.toString().trim().toLowerCase();
                                        performSearch(activity, query, containerLayout, settingsMap, readWritePrefs, listener, isNight, density);
                                    }
                                    
                                    @Override
                                    public void afterTextChanged(Editable s) {}
                                });
                                
                                // Auto-focus and open keyboard on start
                                finalSearchInput.requestFocus();
                                finalSearchInput.post(() -> {
                                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) {
                                        imm.showSoftInput(finalSearchInput, InputMethodManager.SHOW_IMPLICIT);
                                    }
                                });
                            } else {
                                XposedBridge.log("[WAEX] WDSSearchView: could not find EditText field A09");
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[WAEX] Failed to initialize native WDSSearchView, using fallback: " + t.getMessage());
                    }
                    
                    contentView = scrollView;
                } else if ("root".equals(screenId)) {
                    contentView = WdsSettingsTileRenderer.buildCategoryList(activity, settingsMap, readWritePrefs, (key, newValue) -> {
                        try {
                            WppCore.setPrivBooleanSync("need_restart", true);
                            String prefTitle = key;
                            try {
                                JSONArray categories = settingsMap.getJSONArray("categories");
                                for (int i = 0; i < categories.length(); i++) {
                                    JSONObject category = categories.getJSONObject(i);
                                    JSONArray subScreens = category.optJSONArray("sub_screens");
                                    if (subScreens != null) {
                                        for (int j = 0; j < subScreens.length(); j++) {
                                            JSONObject subScreen = subScreens.getJSONObject(j);
                                            JSONArray prefsArray = subScreen.optJSONArray("prefs");
                                            if (prefsArray != null) {
                                                for (int k = 0; k < prefsArray.length(); k++) {
                                                    JSONObject prefObj = prefsArray.getJSONObject(k);
                                                    if (key.equals(prefObj.optString("key"))) {
                                                        prefTitle = prefObj.optString("title", key);
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                            String existing = WppCore.getPrivString("pending_changes", "");
                            Set<String> all = new LinkedHashSet<>();
                            if (!existing.isEmpty()) {
                                for (String t : existing.split("\\|")) {
                                    if (!t.trim().isEmpty()) all.add(t.trim());
                                }
                            }
                            all.add(prefTitle);
                            WppCore.setPrivString("pending_changes", String.join("|", all));
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] Failed to record pending restart: " + t.getMessage());
                        }

                        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
                        intent.putExtra("key", key);
                        intent.setPackage(activity.getPackageName());
                        activity.sendBroadcast(intent);
                    });
                } else if ("pro_plans".equals(screenId)) {
                    title = "Become Pro";
                    
                    // Exact colors matching native settings & cards
                    int dialogBg = isNight ? 0xFF12181C : 0xFFFFFFFF; // Dialog/sheet surface background
                    int cardBg = isNight ? 0xFF1F2C34 : 0xFFF0F2F5; // Cards background
                    int primaryText = isNight ? 0xFFE9EDEF : 0xFF111B21;
                    int secondaryText = isNight ? 0xFF8696A0 : 0xFF667781;
                    int accentG = isNight ? 0xFF21C063 : 0xFF008069;
                    int strokeColor = isNight ? 0xFF2D3B43 : 0xFFE1E3E6;

                    ScrollView scrollView = new ScrollView(activity);
                    scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    scrollView.setClipChildren(false);
                    // Keep native window background by not setting custom bg color on ScrollView
                    
                    LinearLayout containerLayout = new LinearLayout(activity);
                    containerLayout.setOrientation(LinearLayout.VERTICAL);
                    containerLayout.setClipChildren(false);
                    containerLayout.setClipToPadding(false);
                    float density = activity.getResources().getDisplayMetrics().density;
                    int pad24 = (int) (24 * density);
                    int pad16 = (int) (16 * density);
                    containerLayout.setPadding(pad24, pad24, pad24, pad24);
                    scrollView.addView(containerLayout);

                    // ── Read license state from Utils.xprefs (module prefs, already loaded at hook time) ──
                    String proStatus;
                    String planName;
                    long expiresAt = 0;
                    String tgUsername = "";
                    String licenseKey = "";
                    try {
                        // Utils.xprefs is the XSharedPreferences pointing at the module app's
                        // DefaultSharedPreferences — this is the authoritative source for license data.
                        SharedPreferences xp = Utils.xprefs;
                        if (xp == null) {
                            // Fallback: reload via reflection
                            xp = (SharedPreferences)
                                    Class.forName("com.waenhancer.xposed.utils.Utils")
                                         .getField("xprefs").get(null);
                        }

                        boolean isVerified = xp != null && xp.getBoolean("is_pro_verified", false);
                        String rawKey = xp != null ? xp.getString("license_key", "").trim() : "";

                        if (xp != null) {
                            try { expiresAt = xp.getLong("expires_at", 0); }
                            catch (ClassCastException ignored) {
                                try { expiresAt = Long.parseLong(xp.getString("expires_at", "0")); }
                                catch (Exception ignored2) {}
                            }
                            tgUsername = xp.getString("tg_username", "");
                            licenseKey = rawKey;
                        }

                        if (!isVerified || rawKey.isEmpty()) {
                            proStatus = "FREE";
                            planName  = "Free";
                        } else if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
                            proStatus = "EXPIRED";
                            String storedPlan = xp != null ? xp.getString("plan_name", "") : "";
                            planName = storedPlan.isEmpty() ? "Pro Expired" : storedPlan;
                        } else {
                            proStatus = "ACTIVE";
                            String storedPlan = xp != null ? xp.getString("plan_name", "") : "";
                            planName = storedPlan.isEmpty() ? "Pro Active" : storedPlan;
                        }
                    } catch (Throwable t) {
                        proStatus = "FREE";
                        planName  = "Free";
                    }

                    boolean isActive  = "ACTIVE".equalsIgnoreCase(proStatus);
                    boolean isExpired = "EXPIRED".equalsIgnoreCase(proStatus);

                    if (isActive || isExpired) {
                        // ─────────────────────────────────────────────
                        //  ACTIVE / EXPIRED LICENSE CARD
                        // ─────────────────────────────────────────────
                        LinearLayout licCard = new LinearLayout(activity);
                        licCard.setOrientation(LinearLayout.VERTICAL);
                        licCard.setPadding(pad16, pad16, pad16, pad16);

                        GradientDrawable licCardGd = new GradientDrawable();
                        licCardGd.setCornerRadius(12 * density);
                        licCardGd.setColor(dialogBg);
                        licCardGd.setStroke((int)(1 * density), strokeColor);
                        licCard.setBackground(licCardGd);
                        licCard.setElevation(5 * density);

                        LinearLayout.LayoutParams licCardLp =
                                new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        int marginH = (int)(6 * density);
                        licCardLp.setMargins(marginH, (int)(2 * density), marginH, (int)(20 * density));
                        licCard.setLayoutParams(licCardLp);

                        // ── Status badge row ──
                        LinearLayout badgeRow = new LinearLayout(activity);
                        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
                        badgeRow.setGravity(Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams badgeRowLp =
                                new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        badgeRowLp.bottomMargin = (int)(10 * density);
                        badgeRow.setLayoutParams(badgeRowLp);

                        // Plan name
                        TextView tvPlan = new TextView(activity);
                        tvPlan.setText(planName);
                        tvPlan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        tvPlan.setTextColor(primaryText);
                        tvPlan.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                        LinearLayout.LayoutParams tvPlanLp =
                                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        tvPlan.setLayoutParams(tvPlanLp);
                        badgeRow.addView(tvPlan);

                        // Status pill badge
                        TextView tvBadge = new TextView(activity);
                        tvBadge.setText(isActive ? "ACTIVE" : "EXPIRED");
                        tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                        tvBadge.setTextColor(isNight ? 0xFF111B21 : 0xFFFFFFFF);
                        tvBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                        tvBadge.setPadding((int)(8 * density), (int)(3 * density), (int)(8 * density), (int)(3 * density));
                        GradientDrawable badgeGd = new GradientDrawable();
                        badgeGd.setCornerRadius(8 * density);
                        badgeGd.setColor(isActive ? accentG : 0xFFC62828);
                        tvBadge.setBackground(badgeGd);
                        badgeRow.addView(tvBadge);
                        licCard.addView(badgeRow);

                        // ── Divider ──
                        View licDiv = new View(activity);
                        LinearLayout.LayoutParams licDivLp =
                                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)(1 * density));
                        licDivLp.bottomMargin = (int)(10 * density);
                        licDiv.setLayoutParams(licDivLp);
                        licDiv.setBackgroundColor(strokeColor);
                        licCard.addView(licDiv);

                        // ── Expiry ──
                        TextView tvExpiry = new TextView(activity);
                        if (expiresAt > 0) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                            String label = isActive ? "Valid until: " : "Expired on: ";
                            tvExpiry.setText(label + sdf.format(new Date(expiresAt)));
                            if (isExpired) tvExpiry.setTextColor(0xFFC62828);
                            else tvExpiry.setTextColor(secondaryText);
                        } else {
                            tvExpiry.setText("Valid until: Lifetime Access");
                            tvExpiry.setTextColor(accentG);
                        }
                        tvExpiry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        LinearLayout.LayoutParams tvExpiryLp =
                                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        tvExpiryLp.bottomMargin = (int)(4 * density);
                        tvExpiry.setLayoutParams(tvExpiryLp);
                        licCard.addView(tvExpiry);

                        // ── Telegram username ──
                        if (tgUsername != null && !tgUsername.isEmpty()) {
                            TextView tvTg = new TextView(activity);
                            tvTg.setText("Linked to: @" + tgUsername);
                            tvTg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                            tvTg.setTextColor(secondaryText);
                            LinearLayout.LayoutParams tvTgLp =
                                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            tvTgLp.bottomMargin = (int)(4 * density);
                            tvTg.setLayoutParams(tvTgLp);
                            licCard.addView(tvTg);
                        }

                        // ── Masked license key ──
                        if (licenseKey != null && !licenseKey.isEmpty()) {
                            // Mask: show first 9 chars + ****-****
                            String masked = licenseKey.length() > 9
                                    ? licenseKey.substring(0, 9) + "****-****"
                                    : licenseKey;
                            TextView tvKey = new TextView(activity);
                            tvKey.setText("Key: " + masked);
                            tvKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                            tvKey.setTextColor(secondaryText);
                            LinearLayout.LayoutParams tvKeyLp =
                                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            tvKeyLp.bottomMargin = (int)(12 * density);
                            tvKey.setLayoutParams(tvKeyLp);
                            licCard.addView(tvKey);
                        }

                        // ── Unlink button — WDSButton OUTLINE ("OUTLINE" = correct enum name used by AlertDialogWpp) ──
                        View btnUnlink = null;
                        try {
                            Class<?> wdsButtonClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.button.WDSButton");
                            btnUnlink = (View) wdsButtonClass.getConstructor(Context.class, AttributeSet.class).newInstance(activity, null);
                            ((TextView) btnUnlink).setText("Unlink Device");
                            Class<?> variantClass = null;
                            for (Method m : wdsButtonClass.getDeclaredMethods()) {
                                if (m.getName().equals("setVariant") && m.getParameterTypes().length == 1) {
                                    variantClass = m.getParameterTypes()[0];
                                    break;
                                }
                            }
                            if (variantClass == null) {
                                variantClass = activity.getClassLoader().loadClass("X.0xb");
                            }
                            Object outlineVariant = Enum.valueOf((Class<Enum>) variantClass, "OUTLINE");
                            XposedHelpers.callMethod(btnUnlink, "setVariant", outlineVariant);
                        } catch (Throwable t) {
                            // Fallback matching AlertDialogWpp negative button fallback style
                            TextView fbUnlink = new TextView(activity);
                            fbUnlink.setText("Unlink Device");
                            fbUnlink.setGravity(Gravity.CENTER);
                            fbUnlink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                            fbUnlink.setTextColor(accentG);
                            fbUnlink.setTypeface(Typeface.DEFAULT_BOLD);
                            fbUnlink.setPadding(0, (int)(14 * density), 0, (int)(14 * density));
                            btnUnlink = fbUnlink;
                        }
                        btnUnlink.setClickable(true);
                        btnUnlink.setFocusable(true);

                        // Confirmation: AlertDialogWpp — same as used across entire codebase
                        final View finalBtnUnlink = btnUnlink;
                        btnUnlink.setOnClickListener(v -> {
                            // AlertDialogWpp — same pattern as showRestartDialog and all other dialogs
                            new AlertDialogWpp(activity)
                                .setTitle("Unlink this device?")
                                .setMessage("This will remove your license from this device. You will lose access to all Pro features and will need to re-enter your key to reactivate.")
                                .setPositiveButton("Unlink", (dialog, which) -> {
                                    // Show loading spinner
                                    final ProgressDialog progressDialog = new ProgressDialog(activity);
                                    progressDialog.setMessage("Unlinking device...");
                                    progressDialog.setCancelable(false);
                                    progressDialog.show();
                                    
                                    if (finalBtnUnlink != null) finalBtnUnlink.setEnabled(false);

                                    App.getExecutorService().execute(() -> {
                                        try {
                                            Uri providerUri = Uri.parse("content://com.waenhancer.hookprovider");
                                            final Bundle result = activity.getContentResolver().call(providerUri, "unlink_device", null, null);
                                            
                                            activity.runOnUiThread(() -> {
                                                if (progressDialog.isShowing()) progressDialog.dismiss();
                                                if (result != null && result.getBoolean("success", false)) {
                                                    Toast.makeText(activity, "Device unlinked successfully.", Toast.LENGTH_SHORT).show();
                                                    
                                                    // Broadcast restart intent to the module app process
                                                    try {
                                                        Intent restartIntent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
                                                        restartIntent.putExtra("PKG", BuildConfig.APPLICATION_ID);
                                                        activity.sendBroadcast(restartIntent);
                                                    } catch (Throwable ignored) {}

                                                    // Restart WhatsApp itself (host app)
                                                    try {
                                                        Utils.doRestart(activity);
                                                    } catch (Throwable t) {
                                                        activity.finish();
                                                    }
                                                } else {
                                                    if (finalBtnUnlink != null) finalBtnUnlink.setEnabled(true);
                                                    String err = result != null ? result.getString("message", "Unlink failed") : "Provider communication failed";
                                                    Toast.makeText(activity, "Unlink failed: " + err, Toast.LENGTH_LONG).show();
                                                }
                                            });
                                        } catch (Throwable t) {
                                            activity.runOnUiThread(() -> {
                                                if (progressDialog.isShowing()) progressDialog.dismiss();
                                                if (finalBtnUnlink != null) finalBtnUnlink.setEnabled(true);
                                                Toast.makeText(activity, "Unlink failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    });
                                })
                                .setNegativeButton("Cancel", (dialog, which) -> {})
                                .show();
                        });
                        licCard.addView(btnUnlink);

                        containerLayout.addView(licCard);

                        // ── Show features button ──
                        TextView tvFeaturesLinked = new TextView(activity);
                        tvFeaturesLinked.setText("View Premium Features");
                        tvFeaturesLinked.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        tvFeaturesLinked.setTextColor(accentG);
                        tvFeaturesLinked.setTypeface(Typeface.DEFAULT_BOLD);
                        tvFeaturesLinked.setGravity(Gravity.CENTER);
                        tvFeaturesLinked.setPadding(0, pad16, 0, pad16);
                        tvFeaturesLinked.setClickable(true);
                        tvFeaturesLinked.setFocusable(true);
                        tvFeaturesLinked.setOnClickListener(v -> {
                            try {
                                Intent subScreenIntent = new Intent(activity, activity.getClass());
                                subScreenIntent.putExtra("waex_screen_id", "premium_features");
                                activity.startActivity(subScreenIntent);
                            } catch (Throwable ignored) {}
                        });
                        containerLayout.addView(tvFeaturesLinked);

                    } else {
                        // ─────────────────────────────────────────────
                        //  FREE STATE: Activation form + plans section
                        // ─────────────────────────────────────────────

                    // 1. Description
                    TextView actDesc = new TextView(activity);
                    actDesc.setText("Enter your license key received from the Telegram Bot to unlock all premium capabilities.");
                    actDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    actDesc.setTextColor(secondaryText);
                    LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    descLp.setMargins(0, 0, 0, (int)(16 * density));
                    actDesc.setLayoutParams(descLp);
                    containerLayout.addView(actDesc);
                    
                    // 2. Input field for License Key
                    EditText etLicense = new EditText(activity);
                    etLicense.setHint("WAEX-XXXX-XXXX-XXXX");
                    etLicense.setSingleLine(true);
                    etLicense.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    etLicense.setHintTextColor(secondaryText);
                    etLicense.setTextColor(primaryText);
                    
                    // Style edittext border dynamically
                    GradientDrawable editGd = new GradientDrawable();
                    editGd.setCornerRadius(8 * density);
                    editGd.setColor(cardBg);
                    editGd.setStroke((int) (1 * density), strokeColor);
                    etLicense.setBackground(editGd);
                    etLicense.setPadding(pad16, pad16, pad16, pad16);
                    
                    LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    inputLp.bottomMargin = pad16;
                    etLicense.setLayoutParams(inputLp);
                    containerLayout.addView(etLicense);
                    
                    // 3. WDS Verification Button
                    View btnVerify = null;
                    try {
                        Class<?> wdsButtonClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.button.WDSButton");
                        btnVerify = (View) wdsButtonClass.getConstructor(Context.class, AttributeSet.class).newInstance(activity, null);
                        ((TextView) btnVerify).setText("Verify & Activate");
                        
                        Class<?> variantClass = null;
                        for (Method m : wdsButtonClass.getDeclaredMethods()) {
                            if (m.getName().equals("setVariant") && m.getParameterTypes().length == 1) {
                                variantClass = m.getParameterTypes()[0];
                                break;
                            }
                        }
                        if (variantClass == null) {
                            variantClass = activity.getClassLoader().loadClass("X.0xb");
                        }
                        Object variantVal = Enum.valueOf((Class<Enum>) variantClass, "FILLED");
                        XposedHelpers.callMethod(btnVerify, "setVariant", variantVal);
                    } catch (Throwable t) {
                        TextView fbBtn = new TextView(activity);
                        fbBtn.setText("Verify & Activate");
                        fbBtn.setGravity(Gravity.CENTER);
                        fbBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        fbBtn.setTextColor(0xFFFFFFFF);
                        fbBtn.setTypeface(Typeface.DEFAULT_BOLD);
                        fbBtn.setPadding(0, (int) (12 * density), 0, (int) (12 * density));
                        
                        GradientDrawable btnGd = new GradientDrawable();
                        btnGd.setCornerRadius(24 * density);
                        btnGd.setColor(accentG);
                        fbBtn.setBackground(btnGd);
                        btnVerify = fbBtn;
                    }
                    btnVerify.setClickable(true);
                    btnVerify.setFocusable(true);
                    
                    // Click listener to verify license key
                    final View finalBtnVerify = btnVerify;
                    btnVerify.setOnClickListener(v -> {
                        String key = etLicense.getText().toString().trim().toUpperCase();
                        if (key.isEmpty()) {
                            Toast.makeText(activity, "Key cannot be empty", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        // Show loading progress spinner
                        final ProgressDialog progressDialog = new ProgressDialog(activity);
                        progressDialog.setMessage("Verifying license key...");
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                        
                        // Disable UI elements
                        if (finalBtnVerify != null) finalBtnVerify.setEnabled(false);
                        etLicense.setEnabled(false);

                        // Call the module's HookProvider to execute license verification in the module's process context
                        App.getExecutorService().execute(() -> {
                            try {
                                Uri providerUri = Uri.parse("content://com.waenhancer.hookprovider");
                                Bundle extras = new Bundle();
                                extras.putString("license_key", key);
                                
                                final Bundle result = activity.getContentResolver().call(providerUri, "verify_license", null, extras);
                                
                                activity.runOnUiThread(() -> {
                                    if (progressDialog.isShowing()) progressDialog.dismiss();
                                    if (result != null && result.getBoolean("success", false)) {
                                        Toast.makeText(activity, "Activation Successful! 🎉", Toast.LENGTH_LONG).show();
                                        
                                        // Broadcast restart intent to the module app process
                                        try {
                                            Intent restartIntent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
                                            restartIntent.putExtra("PKG", BuildConfig.APPLICATION_ID);
                                            activity.sendBroadcast(restartIntent);
                                        } catch (Throwable ignored) {}

                                        // Restart WhatsApp itself (host app)
                                        try {
                                            Utils.doRestart(activity);
                                        } catch (Throwable t) {
                                            activity.finish();
                                        }
                                    } else {
                                        if (finalBtnVerify != null) finalBtnVerify.setEnabled(true);
                                        etLicense.setEnabled(true);
                                        String err = result != null ? result.getString("message", "Verification Failed") : "Provider communication failed";
                                        Toast.makeText(activity, "Verification Failed: " + err, Toast.LENGTH_LONG).show();
                                    }
                                });
                            } catch (Throwable t) {
                                activity.runOnUiThread(() -> {
                                    if (progressDialog.isShowing()) progressDialog.dismiss();
                                    if (finalBtnVerify != null) finalBtnVerify.setEnabled(true);
                                    etLicense.setEnabled(true);
                                    Toast.makeText(activity, "Failed to call LicenseManager: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    });
                    containerLayout.addView(btnVerify);
                    
                    // 4. "Show Features" button to display Sub Screen
                    TextView tvFeatures = new TextView(activity);
                    tvFeatures.setText("Premium & Limited Free Features");
                    tvFeatures.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvFeatures.setTextColor(accentG);
                    tvFeatures.setTypeface(Typeface.DEFAULT_BOLD);
                    tvFeatures.setGravity(Gravity.CENTER);
                    tvFeatures.setPadding(0, pad16, 0, pad16);
                    tvFeatures.setClickable(true);
                    tvFeatures.setFocusable(true);
                    
                    tvFeatures.setOnClickListener(v -> {
                        try {
                            Intent subScreenIntent = new Intent(activity, activity.getClass());
                            subScreenIntent.putExtra("waex_screen_id", "premium_features");
                            activity.startActivity(subScreenIntent);
                        } catch (Throwable t) {
                            Toast.makeText(activity, "Failed to open Features: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    containerLayout.addView(tvFeatures);

                    // 5. Divider
                    View separator = new View(activity);
                    LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
                    sepLp.setMargins(0, pad16, 0, pad16);
                    separator.setLayoutParams(sepLp);
                    separator.setBackgroundColor(strokeColor);
                    containerLayout.addView(separator);
                    
                    // 6. Plans Section Header
                    TextView plansHeader = new TextView(activity);
                    plansHeader.setText("Purchase License Key");
                    plansHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    plansHeader.setTextColor(primaryText);
                    plansHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    plansHeader.setPadding(0, 0, 0, (int) (8 * density));
                    containerLayout.addView(plansHeader);

                    // 7. Dynamic plans container layout
                    LinearLayout plansContainer = new LinearLayout(activity);
                    plansContainer.setOrientation(LinearLayout.VERTICAL);
                    plansContainer.setClipChildren(false);
                    plansContainer.setClipToPadding(false);
                    containerLayout.setClipChildren(false);
                    containerLayout.setClipToPadding(false);
                    containerLayout.addView(plansContainer);
                    
                    // Check cache for plans (1 hour TTL)
                    SharedPreferences cachePrefs = activity.getSharedPreferences("waex_plans_cache", Context.MODE_PRIVATE);
                    long cacheTime = cachePrefs.getLong("plans_cache_time", 0);
                    String cachedData = cachePrefs.getString("plans_cache_data", null);
                    long currentTime = System.currentTimeMillis();

                    if (cachedData != null && (currentTime - cacheTime) < 900000) {
                        try {
                            JSONArray plansArray = new JSONArray(cachedData);
                            clearPlansContainer(plansContainer);
                            for (int i = 0; i < plansArray.length(); i++) {
                                JSONObject planObj = plansArray.getJSONObject(i);
                                buildPlanCard(plansContainer, activity, density, pad16, dialogBg, strokeColor, primaryText, secondaryText, accentG, planObj);
                            }
                        } catch (Throwable t) {
                            showShimmer(plansContainer, activity, density, dialogBg, strokeColor);
                            fetchPlansFromNetwork(plansContainer, activity, density, pad16, dialogBg, strokeColor, primaryText, secondaryText, accentG, cachePrefs);
                        }
                    } else {
                        showShimmer(plansContainer, activity, density, dialogBg, strokeColor);
                        fetchPlansFromNetwork(plansContainer, activity, density, pad16, dialogBg, strokeColor, primaryText, secondaryText, accentG, cachePrefs);
                    }
                    } // end else FREE state
                    
                    contentView = scrollView;
                } else if ("premium_features".equals(screenId)) {
                    title = "Premium Features";
                    
                    int dialogBg = isNight ? 0xFF12181C : 0xFFFFFFFF; // Dialog/sheet surface background
                    int cardBg = isNight ? 0xFF1F2C34 : 0xFFF0F2F5; // Cards background matching WDS sheet fill color
                    int primaryText = isNight ? 0xFFE9EDEF : 0xFF111B21;
                    int secondaryText = isNight ? 0xFF8696A0 : 0xFF667781;
                    int accentG = isNight ? 0xFF21C063 : 0xFF008069;
                    int strokeColor = isNight ? 0xFF2D3B43 : 0xFFE1E3E6;

                    ScrollView scrollView = new ScrollView(activity);
                    scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Keep native window background

                    LinearLayout containerLayout = new LinearLayout(activity);
                    containerLayout.setOrientation(LinearLayout.VERTICAL);
                    containerLayout.setClipChildren(false);
                    containerLayout.setClipToPadding(false);
                    float density = activity.getResources().getDisplayMetrics().density;
                    int pad24 = (int) (24 * density);
                    int pad16 = (int) (16 * density);
                    containerLayout.setPadding(pad24, pad24, pad24, pad24);
                    scrollView.addView(containerLayout);

                    try {
                        ClassLoader moduleLoader = ProHelper.getPluginClassLoader(activity);
                        if (moduleLoader == null) {
                            moduleLoader = SettingsInjector.class.getClassLoader();
                        }
                        
                        Context moduleContext = activity;
                        try {
                            moduleContext = activity.createPackageContext("com.waenhancer", Context.CONTEXT_IGNORE_SECURITY);
                        } catch (Throwable ignored) {}
                        
                        Class<?> fcClass = moduleLoader.loadClass("com.waenhancer.utils.FeatureCatalog");
                        List<?> allFeatures = (List<?>) fcClass.getMethod("getAllFeatures", Context.class).invoke(null, moduleContext);
                        
                        Class<?> sfClass = moduleLoader.loadClass("com.waenhancer.model.SearchableFeature");
                        Class<?> phClass = moduleLoader.loadClass("com.waenhancer.xposed.utils.ProHelper");

                        for (Object feature : allFeatures) {
                            String key = (String) sfClass.getMethod("getKey").invoke(feature);
                            String fTitle = (String) sfClass.getMethod("getTitle").invoke(feature);
                            String fSummary = (String) sfClass.getMethod("getSummary").invoke(feature);
                            
                            boolean isProFeature = false;
                            if ("file_size_spoofer".equals(key)
                                    || "filter_group_members_messages".equals(key)
                                    || "message_bomber".equals(key) 
                                    || "delete_message_file".equals(key) 
                                    || "pro_status_splitter".equals(key)
                                    || "customize_status_view_category".equals(key)
                                    || "always_typing_global".equals(key)
                                    || "floating_bottom_bar_pill_design".equals(key)
                                    || "filter_items".equals(key)
                                    || "unlock_premium_customization".equals(key)
                                    || "send_audio_as_voice_status".equals(key)) {
                                
                                boolean isLimited = (Boolean) phClass.getMethod("isLimitedFreePreferenceEnabled", String.class).invoke(null, key);
                                if (!isLimited) {
                                    isProFeature = true;
                                }
                            }

                            // Strictly display Pro features only
                            if (!isProFeature) {
                                continue;
                            }

                            // Interactive premium card layout
                            LinearLayout card = new LinearLayout(activity);
                            card.setOrientation(LinearLayout.VERTICAL);
                            card.setPadding(pad16, pad16, pad16, pad16);
                            card.setClickable(true);
                            card.setFocusable(true);
                            
                            GradientDrawable normalGd = new GradientDrawable();
                            normalGd.setCornerRadius(12 * density);
                            normalGd.setColor(dialogBg);
                            normalGd.setStroke((int) (1 * density), strokeColor);
                            card.setBackground(normalGd);

                            // Apply elevation for physical card shadow look
                            card.setElevation(5 * density);

                            LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            int marginHoriz = (int) (6 * density);
                            pcLp.setMargins(marginHoriz, (int)(2 * density), marginHoriz, (int)(14 * density));
                            card.setLayoutParams(pcLp);

                            // Add material ripple foreground
                            try {
                                TypedValue outValue = new TypedValue();
                                activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                                card.setForeground(activity.getDrawable(outValue.resourceId));
                            } catch (Throwable ignored) {}

                            TextView titleTv = new TextView(activity);
                            titleTv.setText(fTitle);
                            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                            titleTv.setTextColor(accentG);
                            titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                            card.addView(titleTv);

                            TextView summaryTv = new TextView(activity);
                            summaryTv.setText(fSummary);
                            summaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            summaryTv.setTextColor(secondaryText);
                            summaryTv.setPadding(0, (int)(4*density), 0, 0);
                            card.addView(summaryTv);

                            containerLayout.addView(card);
                        }
                    } catch (Throwable t) {
                        Throwable actual = t;
                        if (t instanceof InvocationTargetException && t.getCause() != null) {
                            actual = t.getCause();
                        }
                        StringWriter sw = new StringWriter();
                        actual.printStackTrace(new PrintWriter(sw));
                        TextView errTv = new TextView(activity);
                        errTv.setText("Failed to load features: " + actual.toString() + "\n\nStack:\n" + sw.toString());
                        errTv.setTextColor(primaryText);
                        containerLayout.addView(errTv);
                    }

                    contentView = scrollView;
                } else {
                    contentView = WdsSettingsTileRenderer.buildSubScreenById(activity, settingsMap, screenId, readWritePrefs, (key, newValue) -> {
                        try {
                            WppCore.setPrivBooleanSync("need_restart", true);
                            String prefTitle = key;
                            try {
                                JSONArray categories = settingsMap.getJSONArray("categories");
                                for (int i = 0; i < categories.length(); i++) {
                                    JSONObject category = categories.getJSONObject(i);
                                    JSONArray subScreens = category.optJSONArray("sub_screens");
                                    if (subScreens != null) {
                                        for (int j = 0; j < subScreens.length(); j++) {
                                            JSONObject subScreen = subScreens.getJSONObject(j);
                                            JSONArray prefsArray = subScreen.optJSONArray("prefs");
                                            if (prefsArray != null) {
                                                for (int k = 0; k < prefsArray.length(); k++) {
                                                    JSONObject prefObj = prefsArray.getJSONObject(k);
                                                    if (key.equals(prefObj.optString("key"))) {
                                                        prefTitle = prefObj.optString("title", key);
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                            String existing = WppCore.getPrivString("pending_changes", "");
                            Set<String> all = new LinkedHashSet<>();
                            if (!existing.isEmpty()) {
                                for (String t : existing.split("\\|")) {
                                    if (!t.trim().isEmpty()) all.add(t.trim());
                                }
                            }
                            all.add(prefTitle);
                            WppCore.setPrivString("pending_changes", String.join("|", all));
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] Failed to record pending restart: " + t.getMessage());
                        }

                        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
                        intent.putExtra("key", key);
                        intent.setPackage(activity.getPackageName());
                        activity.sendBroadcast(intent);
                    });
                    // Resolve title from JSON categories or sub-screens
                    try {
                        JSONArray categories = settingsMap.getJSONArray("categories");
                        boolean titleFound = false;
                        for (int i = 0; i < categories.length(); i++) {
                            JSONObject cat = categories.getJSONObject(i);
                            if (cat.getString("id").equals(screenId)) {
                                title = cat.getString("title");
                                titleFound = true;
                                break;
                            }
                            JSONArray subScreens = cat.optJSONArray("sub_screens");
                            if (subScreens != null) {
                                for (int j = 0; j < subScreens.length(); j++) {
                                    JSONObject sub = subScreens.getJSONObject(j);
                                    if (sub.getString("id").equals(screenId)) {
                                        title = sub.getString("title");
                                        titleFound = true;
                                        break;
                                    }
                                }
                            }
                            if (titleFound) break;
                        }
                    } catch (Exception ignored) {}
                }

                if (contentView != null) {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    container.addView(contentView, lp);
                    setToolbarTitle(activity, title);
                    
                    // Handle scroll to target preference if navigating from search
                    final String scrollToPref = activity.getIntent().getStringExtra("scroll_to_pref");
                    if (scrollToPref != null) {
                        activity.getIntent().removeExtra("scroll_to_pref"); // prevent repeated scrolling
                        safeScrollTo(contentView, scrollToPref, 30); // retry up to 30 times (1.5 seconds)
                    }
                    if ("root".equals(screenId)) {
                        ViewGroup rootToolbar = findToolbar(root);
                        XposedBridge.log("[WAEX] hijackWholeScreen findToolbar: " + (rootToolbar != null ? rootToolbar.getClass().getName() : "null"));
                        if (rootToolbar != null) {
                            try {
                                Object menuObj = XposedHelpers.callMethod(rootToolbar, "getMenu");
                                XposedBridge.log("[WAEX] hijackWholeScreen getMenu: " + (menuObj != null ? menuObj.getClass().getName() : "null"));
                                if (menuObj instanceof Menu) {
                                    Menu menu = (Menu) menuObj;
                                    XposedBridge.log("[WAEX] hijackWholeScreen menu size: " + menu.size());
                                    if (menu.findItem(VIEW_ID_WAEX_SEARCH) == null) {
                                        MenuItem searchItem = menu.add(0, VIEW_ID_WAEX_SEARCH, 0, "Search");
                                        Drawable icon = DesignUtils.getDrawable(R.drawable.ic_search);
                                        if (icon != null) {
                                            icon = DesignUtils.coloredDrawable(icon, isNight ? Color.WHITE : Color.BLACK);
                                            searchItem.setIcon(icon);
                                        }
                                        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                                        searchItem.setOnMenuItemClickListener(item -> {
                                            Intent intent = new Intent(activity, activity.getClass());
                                            intent.putExtra("waex_screen_id", "search");
                                            activity.startActivity(intent);
                                            return true;
                                        });
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[WAEX] hijackWholeScreen getMenu error: " + t.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] SettingsInjector: Hijack failed: " + t.getMessage());
        }
    }

    private void performSearch(Activity activity, String query, ViewGroup containerLayout, JSONObject settingsMap, SharedPreferences readWritePrefs, WdsSettingsTileRenderer.PrefChangeListener listener, boolean isNight, float density) {
        containerLayout.removeAllViews();
        try {
            JSONArray matchingPrefs = new JSONArray();
            final Map<String, String> prefToSubScreenMap = new HashMap<>();
            JSONArray categories = settingsMap.getJSONArray("categories");
            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.getJSONObject(i);
                JSONArray subScreens = category.optJSONArray("sub_screens");
                if (subScreens != null) {
                    for (int j = 0; j < subScreens.length(); j++) {
                        JSONObject subScreen = subScreens.getJSONObject(j);
                        String subScreenId = subScreen.optString("id", "");
                        JSONArray prefsArray = subScreen.optJSONArray("prefs");
                        if (prefsArray != null) {
                            for (int k = 0; k < prefsArray.length(); k++) {
                                JSONObject pref = prefsArray.getJSONObject(k);
                                String key = pref.optString("key", "");
                                String t = pref.optString("title", "").toLowerCase();
                                String sum = pref.optString("summary", "").toLowerCase();
                                if (query.isEmpty() || t.contains(query) || sum.contains(query)) {
                                    JSONObject copyPref = new JSONObject(pref.toString());
                                    String catTitle = category.optString("title", "");
                                    String subTitle = subScreen.optString("title", "");
                                    String breadcrumb = catTitle + " > " + subTitle;
                                    copyPref.put("summary", breadcrumb);
                                    matchingPrefs.put(copyPref);
                                    prefToSubScreenMap.put(key, subScreenId);
                                }
                            }
                        }
                    }
                }
            }
            if (matchingPrefs.length() > 0) {
                BiConsumer<String, String> navigateCallback = (prefKey, ignored) -> {
                    String targetScreenId = prefToSubScreenMap.get(prefKey);
                    if (targetScreenId != null) {
                        Intent intent = new Intent(activity, activity.getClass());
                        intent.putExtra("waex_screen_id", targetScreenId);
                        intent.putExtra("scroll_to_pref", prefKey);
                        activity.startActivity(intent);
                    }
                };
                WdsSettingsTileRenderer.renderPrefsArray(activity, (LinearLayout) containerLayout, matchingPrefs, readWritePrefs, listener, true, navigateCallback);
            } else {
                TextView noResults = new TextView(activity);
                noResults.setText("No features found matching \"" + query + "\"");
                noResults.setGravity(Gravity.CENTER);
                noResults.setTextColor(isNight ? 0x88FFFFFF : 0x88111B21);
                noResults.setTextSize(16);
                LinearLayout.LayoutParams noResultsParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                noResultsParams.topMargin = (int) (40 * density);
                noResults.setLayoutParams(noResultsParams);
                containerLayout.addView(noResults);
            }
        } catch (Exception ex) {
            XposedBridge.log("[WAEX] Search filtering error: " + ex.getMessage());
        }
    }

    private EditText findFirstEditText(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof EditText) {
                return (EditText) child;
            } else if (child instanceof ViewGroup) {
                EditText found = findFirstEditText((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void blinkView(View view, int times) {
        if (times <= 0 || view == null) return;
        view.animate()
            .alpha(0.3f)
            .setDuration(250)
            .withEndAction(() -> {
                view.animate()
                    .alpha(1.0f)
                    .setDuration(250)
                    .withEndAction(() -> blinkView(view, times - 1))
                    .start();
            })
            .start();
    }

    private void safeScrollTo(View startView, String tag, int attempts) {
        if (attempts <= 0 || startView == null) return;
        View target = startView.findViewWithTag(tag);
        if (target != null && startView.getHeight() > 0 && target.getHeight() > 0) {
            final View finalTarget = target;
            startView.postDelayed(() -> {
                // Walk up parent hierarchy to find first scrollable ancestor
                View scrollingParent = null;
                ViewParent pWalk = finalTarget.getParent();
                while (pWalk instanceof View) {
                    View v = (View) pWalk;
                    if (v.getClass().getName().contains("ScrollView")) {
                        scrollingParent = v;
                    }
                    pWalk = v.getParent();
                }

                if (scrollingParent != null) {
                    int offset = 0;
                    View curr = finalTarget;
                    while (curr != null && curr != scrollingParent) {
                        offset += curr.getTop();
                        ViewParent p = curr.getParent();
                        curr = (p instanceof View) ? (View) p : null;
                    }
                    int scrollY = Math.max(0, offset - (int)(16 * scrollingParent.getResources().getDisplayMetrics().density));
                    try {
                        XposedHelpers.callMethod(scrollingParent, "smoothScrollTo", 0, scrollY);
                    } catch (Throwable t) {
                        scrollingParent.scrollTo(0, scrollY);
                    }
                }
            }, 300);
            startView.postDelayed(() -> blinkView(finalTarget, 2), 600);
        } else {
            startView.postDelayed(() -> safeScrollTo(startView, tag, attempts - 1), 50);
        }
    }


    private void setToolbarTitle(Activity activity, String title) {
        try {
            activity.setTitle(title);
        } catch (Throwable ignored) {}
        try {
            Object actionBar = XposedHelpers.callMethod(activity, "getSupportActionBar");
            if (actionBar != null) {
                XposedHelpers.callMethod(actionBar, "setTitle", title);
            }
        } catch (Throwable ignored) {}
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root != null) {
                ViewGroup toolbar = findToolbar(root);
                if (toolbar != null) {
                    try {
                        XposedHelpers.callMethod(toolbar, "setTitle", title);
                    } catch (Throwable ignored) {}
                    
                    boolean titleSet = false;
                    for (int i = 0; i < toolbar.getChildCount(); i++) {
                        View child = toolbar.getChildAt(i);
                        if (child instanceof TextView) {
                            ((TextView) child).setText(title);
                            titleSet = true;
                        } else if (child instanceof ViewGroup) {
                            TextView tv = findTextView(child);
                            if (tv != null) {
                                tv.setText(title);
                                titleSet = true;
                            }
                        }
                    }
                    if (!titleSet) {
                        TextView titleView = new TextView(activity);
                        titleView.setText(title);
                        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                        boolean isNight = DesignUtils.isNightMode();
                        titleView.setTextColor(isNight ? 0xFFFFFFFF : 0xFF111B21);
                        titleView.setTypeface(Typeface.DEFAULT_BOLD);
                        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMarginStart(dp(activity, 16));
                        titleView.setLayoutParams(params);
                        toolbar.addView(titleView);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void findTextViews(View view, List<TextView> list) {
        if (view instanceof TextView) {
            list.add((TextView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findTextViews(group.getChildAt(i), list);
            }
        }
    }

    private void injectToolbarButton(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            ViewGroup toolbar = findToolbar(root);
            if (toolbar != null) {
                if (toolbar.findViewById(VIEW_ID_WAEX_SETTINGS) != null) return;
                ImageView button = createSettingsButton(activity);
                button.setId(VIEW_ID_WAEX_SETTINGS);
                int size = dp(activity, 24);
                int margin = dp(activity, 16);
                ViewGroup.MarginLayoutParams params =
                        new ViewGroup.MarginLayoutParams(size, size);
                params.topMargin = margin / 2;
                params.bottomMargin = margin / 2;
                params.setMarginEnd(margin);
                button.setLayoutParams(params);
                toolbar.addView(button);
                return;
            }

            if (root.findViewById(VIEW_ID_WAEX_SETTINGS) != null || !(root instanceof FrameLayout)) return;
            ImageView floatingButton = createSettingsButton(activity);
            floatingButton.setId(VIEW_ID_WAEX_SETTINGS);
            int size = dp(activity, 40);
            int margin = dp(activity, 12);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
            params.topMargin = margin;
            params.setMarginEnd(margin);
            ((FrameLayout) root).addView(floatingButton, params);
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] SettingsInjector: direct button error: " + t.getMessage());
        }
    }

    private void removeNativeViewTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View customRow = root.findViewById(VIEW_ID_WAEX_SETTINGS);
            if (customRow != null && customRow.getParent() instanceof ViewGroup) {
                ((ViewGroup) customRow.getParent()).removeView(customRow);
            }
        } catch (Throwable ignored) {}
    }

    private void removeToolbarButton(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View button = root.findViewById(VIEW_ID_WAEX_SETTINGS);
            if (button != null && button.getParent() instanceof ViewGroup) {
                ((ViewGroup) button.getParent()).removeView(button);
            }
        } catch (Throwable ignored) {}
    }

    private ImageView createSettingsButton(Activity activity) {
        ImageView button = new ImageView(activity);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("WaEnhancerX Settings");
        button.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        var icon = DesignUtils.getDrawableByName("ic_settings");
        if (icon != null) {
            icon.setTint(0xff8696a0);
            button.setImageDrawable(icon);
        }
        button.setOnClickListener(v -> Utils.openModule(activity));
        return button;
    }

    private ViewGroup findToolbar(View view) {
        if (view == null) return null;
        try {
            Context context = view.getContext();
            int toolbarId = context.getResources().getIdentifier("toolbar", "id", context.getPackageName());
            if (toolbarId != 0) {
                View toolbar = view.findViewById(toolbarId);
                if (toolbar instanceof ViewGroup) {
                    return (ViewGroup) toolbar;
                }
            }
        } catch (Throwable ignored) {}

        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        String className = group.getClass().getName();
        String simpleName = group.getClass().getSimpleName();
        if (simpleName.contains("Toolbar") || className.contains("toolbar") || className.contains("Toolbar")) {
            return group;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = findToolbar(group.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                activity.getResources().getDisplayMetrics()
        );
    }

    private void injectToolbarMenu(Menu menu, Activity activity) {
        try {
            if (menu != null && menu.findItem(MENU_ID_WAEX_SETTINGS) == null) {
                String title = "WaEnhancerX Settings";
                try {
                    String moduleTitle = FeatureLoader.getModuleString(activity, R.string.waenhancer_settings, "WaEnhancerX Settings");
                    if (moduleTitle != null && !moduleTitle.isEmpty()) {
                        title = moduleTitle;
                    }
                } catch (Throwable ignored) {}

                var item = menu.add(0, MENU_ID_WAEX_SETTINGS, 0, title);
                var icon = DesignUtils.getDrawableByName("ic_settings");
                if (icon != null) {
                    icon.setTint(0xff8696a0);
                    item.setIcon(icon);
                }
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                item.setOnMenuItemClickListener(it -> {
                    Utils.openModule(activity);
                    return true;
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] SettingsInjector: Toolbar error: " + t.getMessage());
        }
    }

    private static final int VIEW_ID_WAEX_OPTIMIZATION = 10002;

    private boolean needDatabaseOptimization(Activity activity) {
        try {
            boolean needFilterIndex = prefs.getBoolean("filter_group_members_messages", false);
            boolean needSeparateIndex = prefs.getBoolean("separategroups", false);
            XposedBridge.log("[WAEX] needDatabaseOptimization check: needFilterIndex=" + needFilterIndex + ", needSeparateIndex=" + needSeparateIndex);
            if (!needFilterIndex && !needSeparateIndex) return false;

            File dbFile = activity.getDatabasePath("msgstore.db");
            XposedBridge.log("[WAEX] msgstore.db file path: " + dbFile.getAbsolutePath() + ", exists: " + dbFile.exists());
            if (!dbFile.exists()) return false;

            boolean filterIndexed = !needFilterIndex;
            boolean separateIndexed = !needSeparateIndex;
            
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try {
                if (needFilterIndex) {
                    try (Cursor c = db.rawQuery(
                            "SELECT name FROM sqlite_master WHERE type='index' AND name='wae_msg_filter_idx'", null)) {
                        filterIndexed = c != null && c.moveToFirst();
                    }
                }
                if (needSeparateIndex) {
                    try (Cursor c = db.rawQuery(
                            "SELECT name FROM sqlite_master WHERE type='index' AND name='wae_chat_unseen_idx'", null)) {
                        separateIndexed = c != null && c.moveToFirst();
                    }
                }
            } finally {
                db.close();
            }
            XposedBridge.log("[WAEX] filterIndexed=" + filterIndexed + ", separateIndexed=" + separateIndexed);
            return !filterIndexed || !separateIndexed;
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error checking database optimization state in SettingsInjector: " + t.toString());
            return false;
        }
    }

    private void injectOptimizationTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            if (root.findViewById(VIEW_ID_WAEX_OPTIMIZATION) != null) return;

            ViewGroup listContainer = findSettingsListByStructure(root);
            if (listContainer != null) {
                View accountRow = findAccountRow(listContainer, activity);
                if (accountRow != null) {
                    int insertionIndex = listContainer.indexOfChild(accountRow);
                    View waexSettingsRow = listContainer.findViewById(VIEW_ID_WAEX_SETTINGS);
                    if (waexSettingsRow != null) {
                        insertionIndex = listContainer.indexOfChild(waexSettingsRow) + 1;
                    }
                    View customRow = createOptimizationSettingRow(activity, accountRow);
                    if (customRow != null) {
                        customRow.setId(VIEW_ID_WAEX_OPTIMIZATION);
                        listContainer.addView(customRow, insertionIndex);
                        return;
                    }
                }
            }

            View accountTextView = findTextViewWithText(root, getLocalizedText(activity, "settings_account", "Account"));
            View privacyTextView = findTextViewWithText(root, getLocalizedText(activity, "settings_privacy", "Privacy"));
            if (accountTextView == null && privacyTextView == null) return;

            View found = accountTextView != null ? accountTextView : privacyTextView;
            ViewGroup container = findVerticalContainer(found);
            if (container != null) {
                View row = findDirectChildOfContainer(container, found);
                if (row != null) {
                    int index = container.indexOfChild(row);
                    View waexSettingsRow = container.findViewById(VIEW_ID_WAEX_SETTINGS);
                    if (waexSettingsRow != null) {
                        index = container.indexOfChild(waexSettingsRow) + 1;
                    }
                    View customRow = createOptimizationSettingRow(activity, row);
                    if (customRow != null) {
                        customRow.setId(VIEW_ID_WAEX_OPTIMIZATION);
                        container.addView(customRow, index);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] SettingsInjector: optimization view tile error: " + t.getMessage());
        }
    }

    private void removeOptimizationTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View customRow = root.findViewById(VIEW_ID_WAEX_OPTIMIZATION);
            if (customRow != null && customRow.getParent() instanceof ViewGroup) {
                ((ViewGroup) customRow.getParent()).removeView(customRow);
            }
        } catch (Throwable ignored) {}
    }

    private View createOptimizationSettingRow(Activity activity, View anchorView) {
        try {
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);

            if (anchorView.getLayoutParams() != null) {
                rowLayout.setLayoutParams(anchorView.getLayoutParams());
            } else {
                rowLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            int padLeft = dp(activity, 24);
            int padRight = dp(activity, 24);
            int padTop = anchorView.getPaddingTop() > 0 ? anchorView.getPaddingTop() : dp(activity, 15);
            int padBottom = anchorView.getPaddingBottom() > 0 ? anchorView.getPaddingBottom() : dp(activity, 15);
            rowLayout.setPadding(padLeft, padTop, padRight, padBottom);

            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            rowLayout.setBackgroundResource(outValue.resourceId);

            rowLayout.setClickable(true);
            rowLayout.setFocusable(true);

            ImageView anchorIcon = findImageView(anchorView);
            List<TextView> anchorTextViews = new ArrayList<>();
            findTextViews(anchorView, anchorTextViews);

            TextView anchorTitle = anchorTextViews.size() > 0 ? anchorTextViews.get(0) : null;
            TextView anchorSummary = anchorTextViews.size() > 1 ? anchorTextViews.get(1) : null;

            ImageView iconView = new ImageView(activity);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
            iconView.setLayoutParams(iconParams);

            Drawable icon = DesignUtils.getDrawableByName("ic_settings");
            if (icon != null) {
                iconView.setImageDrawable(icon);
            }

            if (anchorIcon != null && anchorIcon.getImageTintList() != null) {
                iconView.setImageTintList(anchorIcon.getImageTintList());
                iconView.setColorFilter(anchorIcon.getColorFilter());
                iconView.setAlpha(anchorIcon.getAlpha());
            } else if (anchorSummary != null) {
                iconView.setImageTintList(anchorSummary.getTextColors());
                iconView.setAlpha(anchorSummary.getAlpha());
            } else {
                iconView.setImageTintList(ColorStateList.valueOf(0xff8696a0));
            }
            rowLayout.addView(iconView);

            LinearLayout textContainer = new LinearLayout(activity);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textContainerParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            textContainerParams.setMarginStart(dp(activity, 24));
            textContainer.setLayoutParams(textContainerParams);

            TextView titleText = new TextView(activity);
            titleText.setText(FeatureLoader.getModuleString(
                activity,
                R.string.waenhancer_db_optimization,
                "WaEnhancerX db Optimization"
            ));

            if (anchorTitle != null) {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorTitle.getTextSize());
                titleText.setTextColor(anchorTitle.getTextColors());
                titleText.setTypeface(anchorTitle.getTypeface());
            } else {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                titleText.setTextColor(0xffe9edef);
            }
            titleText.setEllipsize(TextUtils.TruncateAt.END);
            titleText.setSingleLine(true);

            TextView summaryText = new TextView(activity);
            summaryText.setText(FeatureLoader.getModuleString(
                activity,
                R.string.waenhancer_db_optimization_desc,
                "Optimize database performance by creating speed-boosting query indexes."
            ));

            if (anchorSummary != null) {
                summaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorSummary.getTextSize());
                summaryText.setTextColor(anchorSummary.getTextColors());
                summaryText.setTypeface(anchorSummary.getTypeface());
                summaryText.setAlpha(anchorSummary.getAlpha());
            } else {
                summaryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                summaryText.setTextColor(0xff8696a0);
            }
            summaryText.setPadding(0, dp(activity, 2), 0, 0);
            summaryText.setMaxLines(2);
            summaryText.setEllipsize(TextUtils.TruncateAt.END);

            textContainer.addView(titleText);
            textContainer.addView(summaryText);
            rowLayout.addView(textContainer);

            rowLayout.setOnClickListener(v -> {
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
            });

            return rowLayout;
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] SettingsInjector: Error creating database optimization row: " + t.getMessage());
            return null;
        }
    }

    private void injectTestNotificationTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            if (root.findViewById(VIEW_ID_WAEX_TEST_SWITCH) != null) return;

            ViewGroup listContainer = findSettingsListByStructure(root);
            if (listContainer != null && listContainer.getChildCount() > 0) {
                View anchorRow = listContainer.getChildAt(0); // This is conversation_sound_setting
                View testRow = createTestSwitchRow(activity, anchorRow);
                if (testRow != null) {
                    testRow.setId(VIEW_ID_WAEX_TEST_SWITCH);
                    listContainer.addView(testRow, 1); // Insert it at index 1 (just below conversation_sound_setting)
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error injecting test notification switch: " + t.toString());
        }
    }

    private View createTestSwitchRow(Activity activity, View anchorView) {
        try {
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);

            if (anchorView.getLayoutParams() != null) {
                rowLayout.setLayoutParams(anchorView.getLayoutParams());
            } else {
                rowLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            int padLeft = dp(activity, 24);
            int padRight = dp(activity, 24);
            int padTop = anchorView.getPaddingTop() > 0 ? anchorView.getPaddingTop() : dp(activity, 15);
            int padBottom = anchorView.getPaddingBottom() > 0 ? anchorView.getPaddingBottom() : dp(activity, 15);
            rowLayout.setPadding(padLeft, padTop, padRight, padBottom);

            TypedValue outValue = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            rowLayout.setBackgroundResource(outValue.resourceId);

            rowLayout.setClickable(true);
            rowLayout.setFocusable(true);

            // Find typography from anchorView
            List<TextView> anchorTextViews = new ArrayList<>();
            findTextViews(anchorView, anchorTextViews);
            TextView anchorTitle = anchorTextViews.size() > 0 ? anchorTextViews.get(0) : null;
            TextView anchorSummary = anchorTextViews.size() > 1 ? anchorTextViews.get(1) : null;

            // Left side text container (Vertical)
            LinearLayout textContainer = new LinearLayout(activity);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textContainerParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            textContainer.setLayoutParams(textContainerParams);

            // Title TextView
            TextView titleText = new TextView(activity);
            titleText.setText("WAEX Test Option");
            if (anchorTitle != null) {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorTitle.getTextSize());
                titleText.setTextColor(anchorTitle.getTextColors());
                titleText.setTypeface(anchorTitle.getTypeface());
            } else {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                titleText.setTextColor(0xffe9edef);
            }
            titleText.setEllipsize(TextUtils.TruncateAt.END);
            titleText.setSingleLine(true);

            // Summary TextView
            TextView summaryText = new TextView(activity);
            summaryText.setText("Toggle to print true/false state to system log.");
            if (anchorSummary != null) {
                summaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorSummary.getTextSize());
                summaryText.setTextColor(anchorSummary.getTextColors());
                summaryText.setTypeface(anchorSummary.getTypeface());
                summaryText.setAlpha(anchorSummary.getAlpha());
            } else {
                summaryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                summaryText.setTextColor(0xff8696a0);
            }
            summaryText.setPadding(0, dp(activity, 2), 0, 0);
            summaryText.setMaxLines(2);
            summaryText.setEllipsize(TextUtils.TruncateAt.END);

            textContainer.addView(titleText);
            textContainer.addView(summaryText);
            rowLayout.addView(textContainer);

            // Right side: WDSSwitch
            Class<?> wdsSwitchClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.toggle.WDSSwitch");
            View switchView = (View) wdsSwitchClass.getConstructor(Context.class).newInstance(activity);

            // Standard layout parameters for the switch
            LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            switchView.setLayoutParams(switchParams);

            if (switchView instanceof CompoundButton) {
                CompoundButton compoundButton = (CompoundButton) switchView;
                compoundButton.setChecked(true); // Default to checked
                compoundButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Toast.makeText(activity, "WAEX Test: " + isChecked, Toast.LENGTH_SHORT).show();
                    XposedBridge.log("[WAEX] WAEX Test switch toggled: " + isChecked);
                });
            }

            rowLayout.addView(switchView);

            // Clicking the row toggles the switch
            rowLayout.setOnClickListener(v -> {
                if (switchView instanceof CompoundButton) {
                    CompoundButton cb = (CompoundButton) switchView;
                    cb.toggle();
                }
            });

            return rowLayout;
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error creating test switch row: " + t.toString());
        }
        return null;
    }

    private void fetchPlansFromNetwork(
            LinearLayout plansContainer,
            Activity activity,
            float density,
            int pad16,
            int dialogBg,
            int strokeColor,
            int primaryText,
            int secondaryText,
            int accentG,
            SharedPreferences cachePrefs
    ) {
        new Thread(() -> {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("https://waex.mubashar.dev/api/v1/plans");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                
                String responseStr = sb.toString();
                // Test parsing
                JSONArray plansArray = new JSONArray(responseStr);
                
                // Cache successfully fetched plans
                cachePrefs.edit()
                        .putString("plans_cache_data", responseStr)
                        .putLong("plans_cache_time", System.currentTimeMillis())
                        .apply();
                
                activity.runOnUiThread(() -> {
                    clearPlansContainer(plansContainer);
                    try {
                        for (int i = 0; i < plansArray.length(); i++) {
                            JSONObject planObj = plansArray.getJSONObject(i);
                            buildPlanCard(plansContainer, activity, density, pad16, dialogBg, strokeColor, primaryText, secondaryText, accentG, planObj);
                        }
                    } catch (Throwable t) {
                        Toast.makeText(activity, "Error rendering plans: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable t) {
                activity.runOnUiThread(() -> {
                    clearPlansContainer(plansContainer);
                    // Fallback static plans in case API endpoint is unreachable
                    try {
                        JSONObject monthlyFallback = new JSONObject();
                        monthlyFallback.put("id", 2);
                        monthlyFallback.put("name", "Pro Monthly");
                        monthlyFallback.put("type", "offer");
                        monthlyFallback.put("original_price", "3.50");
                        monthlyFallback.put("offer_price", "2.30");
                        monthlyFallback.put("badge", JSONObject.NULL);

                        JSONObject yearlyFallback = new JSONObject();
                        yearlyFallback.put("id", 3);
                        yearlyFallback.put("name", "Pro Yearly");
                        yearlyFallback.put("type", "offer");
                        yearlyFallback.put("original_price", "28.50");
                        yearlyFallback.put("offer_price", "18.99");
                        yearlyFallback.put("badge", "Best Value");

                        buildPlanCard(plansContainer, activity, density, pad16, dialogBg, strokeColor, primaryText, secondaryText, accentG, monthlyFallback);
                        buildPlanCard(plansContainer, activity, density, pad16, dialogBg, strokeColor, primaryText, secondaryText, accentG, yearlyFallback);
                    } catch (Throwable ignored) {}
                });
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }).start();
    }

    private void buildPlanCard(
            LinearLayout plansContainer,
            Activity activity,
            float density,
            int pad16,
            int cardBg,
            int strokeColor,
            int primaryText,
            int secondaryText,
            int accentG,
            JSONObject planObj
    ) {
        try {
            final String name = planObj.getString("name");
            final String originalPrice = planObj.getString("original_price");
            final String offerPrice = planObj.getString("offer_price");
            final String badge = planObj.isNull("badge") ? null : planObj.getString("badge");
            
            // Build elegant card style dialog item
            LinearLayout planCard = new LinearLayout(activity);
            planCard.setOrientation(LinearLayout.VERTICAL);
            planCard.setPadding(pad16, pad16, pad16, pad16);
            
            GradientDrawable pcGd = new GradientDrawable();
            pcGd.setCornerRadius(12 * density);
            pcGd.setColor(cardBg);
            pcGd.setStroke((int) (1 * density), strokeColor);
            planCard.setBackground(pcGd);
            
            // Apply elevation for physical card shadow look
            planCard.setElevation(5 * density);
            
            LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int marginHoriz = (int) (6 * density);
            pcLp.setMargins(marginHoriz, (int)(2 * density), marginHoriz, (int)(14 * density));
            planCard.setLayoutParams(pcLp);

            // Add material ripple foreground
            try {
                TypedValue outValue = new TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                planCard.setForeground(activity.getDrawable(outValue.resourceId));
            } catch (Throwable ignored) {}

            // Top Row (Name + Badge)
            LinearLayout topRow = new LinearLayout(activity);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topLp.bottomMargin = (int) (8 * density);
            topRow.setLayoutParams(topLp);

            TextView pct = new TextView(activity);
            pct.setText(name);
            pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            pct.setTextColor(primaryText);
            pct.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            pct.setLayoutParams(nameLp);
            topRow.addView(pct);

            if (badge != null && !badge.trim().isEmpty()) {
                TextView pcb = new TextView(activity);
                pcb.setText(badge.toUpperCase());
                pcb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                boolean isNight = DesignUtils.isNightMode();
                pcb.setTextColor(isNight ? 0xFF111B21 : 0xFFFFFFFF);
                pcb.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                pcb.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
                
                GradientDrawable badgeGd = new GradientDrawable();
                badgeGd.setCornerRadius(8 * density);
                badgeGd.setColor(accentG);
                pcb.setBackground(badgeGd);

                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                pcb.setLayoutParams(badgeLp);
                topRow.addView(pcb);
            }
            planCard.addView(topRow);

            // Bottom Row (Prices + Duration)
            LinearLayout priceRow = new LinearLayout(activity);
            priceRow.setOrientation(LinearLayout.HORIZONTAL);
            priceRow.setGravity(Gravity.BOTTOM);
            LinearLayout.LayoutParams priceRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            priceRow.setLayoutParams(priceRowLp);

            boolean hasOffer = !originalPrice.equals(offerPrice);
            if (hasOffer) {
                TextView originalPriceTv = new TextView(activity);
                originalPriceTv.setText("$" + originalPrice);
                originalPriceTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                originalPriceTv.setTextColor(secondaryText);
                originalPriceTv.setPaintFlags(originalPriceTv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                LinearLayout.LayoutParams origLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                origLp.rightMargin = (int) (8 * density);
                originalPriceTv.setLayoutParams(origLp);
                priceRow.addView(originalPriceTv);
            }

            TextView offerPriceTv = new TextView(activity);
            offerPriceTv.setText("$" + offerPrice);
            offerPriceTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            offerPriceTv.setTextColor(accentG);
            offerPriceTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            priceRow.addView(offerPriceTv);

            String billingPeriod = "";
            if (name.toLowerCase().contains("monthly")) {
                billingPeriod = " / Month";
            } else if (name.toLowerCase().contains("yearly")) {
                billingPeriod = " / Year";
            }
            if (!billingPeriod.isEmpty()) {
                TextView periodTv = new TextView(activity);
                periodTv.setText(billingPeriod);
                periodTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                periodTv.setTextColor(secondaryText);
                priceRow.addView(periodTv);
            }
            planCard.addView(priceRow);

            // Subtext/Features Description
            String featureText = "";
            if (name.toLowerCase().contains("monthly")) {
                featureText = "Full access to all Pro features for 30 days";
            } else if (name.toLowerCase().contains("yearly")) {
                featureText = "Save more with full Pro access for 365 days";
            } else {
                featureText = "Unlock all premium Pro capabilities";
            }
            TextView descTv = new TextView(activity);
            descTv.setText(featureText);
            descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            descTv.setTextColor(secondaryText);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = (int) (6 * density);
            descTv.setLayoutParams(descLp);
            planCard.addView(descTv);

            planCard.setClickable(true);
            planCard.setFocusable(true);
            planCard.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/waenhancerx_bot?start=subscribe"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(browserIntent);
                } catch (Throwable t) {
                    Toast.makeText(activity, "Could not open Telegram", Toast.LENGTH_SHORT).show();
                }
            });

            plansContainer.addView(planCard);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error rendering plan card: " + t.toString());
        }
     }

    private void clearPlansContainer(LinearLayout container) {
        if (container == null) return;
        int childCount = container.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            if (child != null) {
                child.clearAnimation();
            }
        }
        container.removeAllViews();
        container.invalidate();
        container.requestLayout();
    }

    private void showShimmer(LinearLayout container, Activity activity, float density, int cardBg, int strokeColor) {
        clearPlansContainer(container);
        for (int i = 0; i < 2; i++) {
            LinearLayout shimmerCard = new LinearLayout(activity);
            shimmerCard.setOrientation(LinearLayout.VERTICAL);
            shimmerCard.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(16 * density));
            
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(12 * density);
            gd.setColor(cardBg);
            gd.setStroke((int) (1 * density), strokeColor);
            shimmerCard.setBackground(gd);
            shimmerCard.setElevation(5 * density);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (96 * density));
            int marginHoriz = (int) (6 * density);
            lp.setMargins(marginHoriz, (int)(2 * density), marginHoriz, (int)(14 * density));
            shimmerCard.setLayoutParams(lp);

            View titlePlaceholder = new View(activity);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    (int) (120 * density), (int) (16 * density));
            titleLp.bottomMargin = (int) (12 * density);
            titlePlaceholder.setLayoutParams(titleLp);
            
            GradientDrawable titleGd = new GradientDrawable();
            titleGd.setCornerRadius(4 * density);
            titleGd.setColor(strokeColor);
            titlePlaceholder.setBackground(titleGd);
            shimmerCard.addView(titlePlaceholder);

            View pricePlaceholder = new View(activity);
            LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                    (int) (80 * density), (int) (24 * density));
            priceLp.bottomMargin = (int) (8 * density);
            pricePlaceholder.setLayoutParams(priceLp);
            
            GradientDrawable priceGd = new GradientDrawable();
            priceGd.setCornerRadius(4 * density);
            priceGd.setColor(strokeColor);
            pricePlaceholder.setBackground(priceGd);
            shimmerCard.addView(pricePlaceholder);
            
            container.addView(shimmerCard);

            AlphaAnimation pulse = new AlphaAnimation(0.4f, 0.9f);
            pulse.setDuration(800);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            shimmerCard.startAnimation(pulse);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Settings Injector";
    }
}