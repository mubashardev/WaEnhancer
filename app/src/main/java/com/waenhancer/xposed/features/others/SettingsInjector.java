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
                if ("0".equals(entryPoint)) return;
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
                if ("0".equals(entryPoint)) return;
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
