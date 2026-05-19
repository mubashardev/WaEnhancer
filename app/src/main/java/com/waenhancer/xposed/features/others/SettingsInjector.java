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
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Ultimate Settings Injector: Uses multiple strategies to find and inject into WA Settings.
 * Injects BOTH a Tile (row) and a Toolbar menu item as a backup.
 */
public class SettingsInjector extends Feature {
    private static final String SETTINGS_TAB_ACTIVITY = "com.whatsapp.settings.ui.SettingsTabActivity";
    private static final int VIEW_ID_WAE_SETTINGS = 10001;
    private final Set<Integer> processedActivities = new HashSet<>();
    private static final int MENU_ID_WAE_SETTINGS = 9999;
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
            XposedBridge.log("[WaEnhancer] SettingsInjector disabled: unable to resolve settings activity");
            return;
        }
        if (settingsActivityClass == null) return;

        XC_MethodHook menuHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String entryPoint = getSafeString("open_wae", "1");
                if ("0".equals(entryPoint) || "2".equals(entryPoint)) return;
                Menu menu = (Menu) param.args[0];
                injectToolbarMenu(menu, activity);
            }
        };
        XposedBridge.hookAllMethods(settingsActivityClass, "onPrepareOptionsMenu", menuHook);
        XposedBridge.hookAllMethods(settingsActivityClass, "onCreateOptionsMenu", menuHook);

        XposedBridge.hookAllMethods(settingsActivityClass, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String entryPoint = getSafeString("open_wae", "1");

                // Clean up elements that shouldn't be present in the current mode
                if (!"2".equals(entryPoint)) {
                    removeNativeViewTile(activity);
                } else {
                    removeToolbarButton(activity);
                }

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
    }

    private void injectNativeViewTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            // Check if our custom tile is already injected
            if (root.findViewById(VIEW_ID_WAE_SETTINGS) != null) return;

            // Find TextViews for Account and Privacy using localized resources
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
                            customRow.setId(VIEW_ID_WAE_SETTINGS);
                            container.addView(customRow, index + 1);
                        }
                    }
                }
                return;
            }

            // Both found! Resolve their first common ancestor (main vertical settings list container)
            View accountRow = null;
            ViewGroup commonAncestor = null;

            // Trace ancestors of Account TextView
            java.util.List<View> accountAncestors = new java.util.ArrayList<>();
            View current = accountTextView;
            while (current != null) {
                accountAncestors.add(current);
                android.view.ViewParent parent = current.getParent();
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
                android.view.ViewParent parent = current.getParent();
                current = (parent instanceof View) ? (View) parent : null;
            }

            if (commonAncestor != null && accountRow != null) {
                int index = commonAncestor.indexOfChild(accountRow);
                View customRow = createDynamicSettingRow(activity, accountRow);
                if (customRow != null) {
                    customRow.setId(VIEW_ID_WAE_SETTINGS);
                    commonAncestor.addView(customRow, index + 1);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: native view tile error: " + t.getMessage());
        }
    }

    private String getLocalizedText(Activity activity, String resName, String fallback) {
        try {
            android.content.res.Resources res = activity.getResources();
            int resId = res.getIdentifier(resName, "string", activity.getPackageName());
            if (resId != 0) {
                return res.getString(resId);
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private View findTextViewWithText(View view, String targetText) {
        if (view instanceof android.widget.TextView) {
            String text = ((android.widget.TextView) view).getText().toString().trim();
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
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            if (group instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout layout = (android.widget.LinearLayout) group;
                if (layout.getOrientation() == android.widget.LinearLayout.VERTICAL) {
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
            android.view.ViewParent parent = current.getParent();
            if (parent == container) {
                return current;
            }
            current = (parent instanceof View) ? (View) parent : null;
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

    private android.widget.TextView findTextView(View view) {
        if (view instanceof android.widget.TextView) {
            return (android.widget.TextView) view;
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.TextView found = findTextView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private View createDynamicSettingRow(Activity activity, View anchorView) {
        try {
            android.widget.LinearLayout rowLayout = new android.widget.LinearLayout(activity);
            rowLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
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

            // Standard Material Design paddings for settings row (starts at 24dp)
            int padLeft = dp(activity, 24);
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
            java.util.List<android.widget.TextView> anchorTextViews = new java.util.ArrayList<>();
            findTextViews(anchorView, anchorTextViews);

            android.widget.TextView anchorTitle = anchorTextViews.size() > 0 ? anchorTextViews.get(0) : null;
            android.widget.TextView anchorSummary = anchorTextViews.size() > 1 ? anchorTextViews.get(1) : null;

            // Icon ImageView (Styled perfectly to match keylines)
            ImageView iconView = new ImageView(activity);
            android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
            iconView.setLayoutParams(iconParams);

            android.graphics.drawable.Drawable icon = DesignUtils.getDrawableByName("ic_settings");
            if (icon != null) {
                iconView.setImageDrawable(icon);
            }

            // Extract the native icon's exact tint to match colors perfectly (with fallback to description text colors for theme-awareness)
            if (anchorIcon != null && anchorIcon.getImageTintList() != null) {
                iconView.setImageTintList(anchorIcon.getImageTintList());
                iconView.setColorFilter(anchorIcon.getColorFilter());
                iconView.setAlpha(anchorIcon.getAlpha());
            } else if (anchorSummary != null) {
                iconView.setImageTintList(anchorSummary.getTextColors());
                iconView.setAlpha(anchorSummary.getAlpha());
            } else {
                iconView.setImageTintList(android.content.res.ColorStateList.valueOf(0xff8696a0));
            }
            rowLayout.addView(iconView);

            // Text vertical container (starts at 72dp keyline, meaning leftMargin = 24dp)
            android.widget.LinearLayout textContainer = new android.widget.LinearLayout(activity);
            textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams textContainerParams = new android.widget.LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            textContainerParams.leftMargin = dp(activity, 24);
            textContainer.setLayoutParams(textContainerParams);

            // Title TextView
            android.widget.TextView titleText = new android.widget.TextView(activity);
            titleText.setText(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings, "WaEnhancerX Settings"));

            // Extract title typography from the anchor title TextView if available
            if (anchorTitle != null) {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorTitle.getTextSize());
                titleText.setTextColor(anchorTitle.getTextColors());
                titleText.setTypeface(anchorTitle.getTypeface());
            } else {
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                titleText.setTextColor(0xffe9edef);
            }
            titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleText.setSingleLine(true);

            // Summary TextView (Professional 1-2 line settings guide)
            android.widget.TextView summaryText = new android.widget.TextView(activity);
            summaryText.setText(com.waenhancer.xposed.core.FeatureLoader.getModuleString(
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
            summaryText.setEllipsize(android.text.TextUtils.TruncateAt.END);

            textContainer.addView(titleText);
            textContainer.addView(summaryText);
            rowLayout.addView(textContainer);

            // Dynamic Click Action to open Settings
            rowLayout.setOnClickListener(v -> Utils.openModule(activity));

            return rowLayout;
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: Error creating dynamic setting row: " + t.getMessage());
            return null;
        }
    }

    private void findTextViews(View view, java.util.List<android.widget.TextView> list) {
        if (view instanceof android.widget.TextView) {
            list.add((android.widget.TextView) view);
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
                if (toolbar.findViewById(VIEW_ID_WAE_SETTINGS) != null) return;
                ImageView button = createSettingsButton(activity);
                button.setId(VIEW_ID_WAE_SETTINGS);
                int size = dp(activity, 24);
                int margin = dp(activity, 16);
                ViewGroup.MarginLayoutParams params =
                        new ViewGroup.MarginLayoutParams(size, size);
                params.topMargin = margin / 2;
                params.bottomMargin = margin / 2;
                params.rightMargin = margin;
                button.setLayoutParams(params);
                toolbar.addView(button);
                return;
            }

            if (root.findViewById(VIEW_ID_WAE_SETTINGS) != null || !(root instanceof FrameLayout)) return;
            ImageView floatingButton = createSettingsButton(activity);
            floatingButton.setId(VIEW_ID_WAE_SETTINGS);
            int size = dp(activity, 40);
            int margin = dp(activity, 12);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
            params.topMargin = margin;
            params.rightMargin = margin;
            ((FrameLayout) root).addView(floatingButton, params);
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: direct button error: " + t.getMessage());
        }
    }

    private void removeNativeViewTile(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View customRow = root.findViewById(VIEW_ID_WAE_SETTINGS);
            if (customRow != null && customRow.getParent() instanceof ViewGroup) {
                ((ViewGroup) customRow.getParent()).removeView(customRow);
            }
        } catch (Throwable ignored) {}
    }

    private void removeToolbarButton(Activity activity) {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View button = root.findViewById(VIEW_ID_WAE_SETTINGS);
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
            if (menu != null && menu.findItem(MENU_ID_WAE_SETTINGS) == null) {
                String title = "WaEnhancerX Settings";
                try {
                    String moduleTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings);
                    if (moduleTitle != null && !moduleTitle.isEmpty()) {
                        title = moduleTitle;
                    }
                } catch (Throwable ignored) {}

                var item = menu.add(0, MENU_ID_WAE_SETTINGS, 0, title);
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
            XposedBridge.log("[WaEnhancer] SettingsInjector: Toolbar error: " + t.getMessage());
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Settings Injector";
    }
}
