package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
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
            settingsActivityClass = Unobfuscator.loadSettingsActivityClass(classLoader);
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] SettingsInjector disabled: unable to resolve settings activity");
            return;
        }
        if (settingsActivityClass == null) {
            return;
        }

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
                int hash = System.identityHashCode(activity);
                if (!processedActivities.add(hash)) return;
                mainHandler.post(() -> {
                    try {
                        activity.invalidateOptionsMenu();
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
                    MenuHome.showWaeSettingsDialog(activity);
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
