package com.waenhancer.xposed.features.others;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.xposed.utils.XResManager;

import java.util.HashSet;
import java.util.LinkedHashSet;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MenuHome extends Feature {

    public static final int MENU_ID_RESTART = 0x7EAE0001;
    public static final int MENU_ID_DND = 0x7EAE0002;
    public static final int MENU_ID_GHOST = 0x7EAE0003;
    public static final int MENU_ID_FREEZE = 0x7EAE0004;
    public static final int MENU_ID_OPEN_WAE = 0x7EAE0005;

    public static HashSet<HomeMenuItem> menuItems = new LinkedHashSet<>();


    public MenuHome(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        menuItems.clear();
        hookMenu();
        var action = prefs.getBoolean("buttonaction", true);

        // restart button
        menuItems.add((menu, activity) -> InsertRestartButton(menu, activity, action));

        // dnd mode
        menuItems.add((menu, activity) -> InsertDNDOption(menu, activity, action));

        // ghost mode
        menuItems.add((menu, activity) -> InsertGhostModeOption(menu, activity, action));

        // freeze last seen
        menuItems.add((menu, activity) -> InsertFreezeLastSeenOption(menu, activity, action));

        // open WAE
        menuItems.add(this::InsertOpenWae);

    }

    private void InsertOpenWae(Menu menu, Activity activity) {
        try {
            var entryPoint = getSafeString("open_wae", "1");
            XposedBridge.log("[WaEnhancer] MenuHome: entryPoint is " + entryPoint + " in " + activity.getClass().getSimpleName());
            if (!"1".equals(entryPoint)) return;

            String title = "WaEnhancerX Settings";
            try {
                if (XResManager.moduleResources != null) {
                    String moduleTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.waenhancer_settings, "WaEnhancerX Settings");
                    if (moduleTitle != null && !moduleTitle.isEmpty()) {
                        title = moduleTitle;
                    }
                }
            } catch (Exception ignored) {}
            if (menu.findItem(MENU_ID_OPEN_WAE) != null) return;
            var itemMenu = menu.add(0, MENU_ID_OPEN_WAE, 9999, " " + title);
            var iconDraw = DesignUtils.getDrawableByName("ic_settings");
            if (iconDraw != null) {
                iconDraw.setTint(0xff8696a0);
                itemMenu.setIcon(iconDraw);
            }
            itemMenu.setOnMenuItemClickListener(item -> {
                showWaeSettingsDialog(activity);
                return true;
            });
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] Failed to insert menu item: " + t.getMessage());
        }
    }

    /**
     * Show WaEnhancerX settings embedded inside the host WhatsApp activity.
     * Delegates to {@link EmbeddedSettingsDialogFragment#show(Activity)} which
     * handles all cross-process safety, fallback, and back-stack management.
     */
    public static void showWaeSettingsDialog(Activity activity) {
        try {
            EmbeddedSettingsDialogFragment.show(activity);
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancer] showWaeSettingsDialog failed: " + t.getMessage());
            Utils.showToast("Could not open WaEnhancerX Settings", android.widget.Toast.LENGTH_SHORT);
        }
    }

    private void InsertGhostModeOption(Menu menu, Activity activity, boolean newSettings) {
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        if (!prefs.getBoolean("ghostmode", true)) {
            if (ghostmode) {
                WppCore.setPrivBooleanSync("ghostmode", false);
                Utils.doRestart(activity);
            }
            return;
        }
        String ghostLabel = "Ghost Mode";
        try {
            if (XResManager.moduleResources != null) {
                String moduleString = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.ghost_mode, "Ghost Mode");
                if (moduleString != null && !moduleString.isEmpty()) {
                    ghostLabel = moduleString;
                }
            }
        } catch (Exception ignored) {}
        if (menu.findItem(MENU_ID_GHOST) != null) return;
        var itemMenu = menu.add(0, MENU_ID_GHOST, 0, ghostLabel);

        try {
            var iconDraw = XResManager.moduleResources.getDrawable(ghostmode ? R.drawable.ghost_enabled : R.drawable.ghost_disabled, null);
            if (iconDraw != null) {
                iconDraw.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
                itemMenu.setIcon(iconDraw);
            }
        } catch (Exception ignored) {}
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        itemMenu.setOnMenuItemClickListener(item -> {
            String gmTitle = "Ghost Mode (" + (ghostmode ? "ON" : "OFF") + ")";
            String gmMsg = "Toggle ghost mode";
            String disableStr = "Disable";
            String enableStr = "Enable";
            try {
                if (XResManager.moduleResources != null) {
                    try {
                        gmTitle = XResManager.moduleResources.getString(R.string.ghost_mode_s, (ghostmode ? "ON" : "OFF"));
                    } catch (Exception ignored1) {}
                    gmMsg = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.ghost_mode_message, gmMsg);
                    disableStr = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.disable, disableStr);
                    enableStr = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.enable, enableStr);
                }
            } catch (Exception ignored) {}
            new AlertDialogWpp(activity).setTitle(gmTitle).
                    setMessage(gmMsg)
                    .setPositiveButton(disableStr, (dialog, which) -> {
                        WppCore.setPrivBooleanSync("ghostmode", false);
                        Utils.doRestart(activity);
                    })
                    .setNegativeButton(enableStr, (dialog, which) -> {
                        WppCore.setPrivBooleanSync("ghostmode", true);
                        Utils.doRestart(activity);
                    }).show();
            return true;

        });
    }

    private void InsertRestartButton(Menu menu, Activity activity, boolean newSettings) {
        if (!prefs.getBoolean("restartbutton", true)) return;
        android.graphics.drawable.Drawable iconDraw = null;
        try { iconDraw = XResManager.moduleResources.getDrawable(R.drawable.refresh, null); } catch (Exception ignored) {}
        if (iconDraw != null) iconDraw.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
        String restartLabel = "Restart WhatsApp";
        try {
            if (XResManager.moduleResources != null) {
                String moduleString = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.restart_whatsapp, "Restart WhatsApp");
                if (moduleString != null && !moduleString.isEmpty()) {
                    restartLabel = moduleString;
                }
            }
        } catch (Exception ignored) {}
        if (menu.findItem(MENU_ID_RESTART) != null) return;
        var itemMenu = menu.add(0, MENU_ID_RESTART, 0, restartLabel);
        if (iconDraw != null) itemMenu.setIcon(iconDraw);
        if (newSettings) {
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        itemMenu.setOnMenuItemClickListener(item -> {
            Utils.doRestart(activity);
            return true;
        });
    }

    @SuppressLint({"DiscouragedApi", "UseCompatLoadingForDrawables", "ApplySharedPref"})
    private void InsertDNDOption(Menu menu, Activity activity, boolean newSettings) {
        var dndmode = WppCore.getPrivBoolean("dndmode", false);
        if (!prefs.getBoolean("show_dndmode", false)) {
            if (WppCore.getPrivBoolean("dndmode", false)) {
                WppCore.setPrivBooleanSync("dndmode", false);
                Utils.doRestart(activity);
            }
            return;
        }
        String dndTitle = "DND Mode";
        try {
            if (XResManager.moduleResources != null) {
                String moduleString = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.dnd_mode_title, "DND Mode");
                if (moduleString != null && !moduleString.isEmpty()) {
                    dndTitle = moduleString;
                }
            }
        } catch (Exception ignored) {}
        if (menu.findItem(MENU_ID_DND) != null) return;
        var item = menu.add(0, MENU_ID_DND, 0, dndTitle);
        try {
            var drawable = XResManager.moduleResources.getDrawable(dndmode ? R.drawable.airplane_enabled : R.drawable.airplane_disabled, null);
            if (drawable != null) {
                drawable.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
                item.setIcon(drawable);
            }
        } catch (Exception ignored) {}
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        item.setOnMenuItemClickListener(menuItem -> {
            String dTitle = "DND Mode (" + (dndmode ? "ON" : "OFF") + ")";
            String dMsg = "When Do Not Disturb mode is on, you will be unable to send or receive messages";
            String disableStr = "Disable";
            String enableStr = "Enable";
            try {
                if (XResManager.moduleResources != null) {
                    dTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.dnd_mode_title, "DND Mode") + " (" + (dndmode ? "ON" : "OFF") + ")";
                    dMsg = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.dnd_message, dMsg);
                    disableStr = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.disable, disableStr);
                    enableStr = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.enable, enableStr);
                }
            } catch (Exception ignored) {}
            new AlertDialogWpp(activity)
                    .setTitle(dTitle)
                    .setMessage(dMsg)
                    .setPositiveButton(disableStr, (dialog, which) -> {
                        WppCore.setPrivBooleanSync("dndmode", false);
                        Utils.doRestart(activity);
                    })
                    .setNegativeButton(enableStr, (dialog, which) -> {
                        WppCore.setPrivBooleanSync("dndmode", true);
                        Utils.doRestart(activity);
                    })
                    .create().show();
            return true;
        });
    }

    private void InsertFreezeLastSeenOption(Menu menu, Activity activity, boolean newSettings) {
        var freezelastseen = WppCore.getPrivBoolean("freezelastseen", false);
        if (!prefs.getBoolean("show_freezeLastSeen", true)) {
            if (freezelastseen) {
                WppCore.setPrivBooleanSync("freezelastseen", false);
                Utils.doRestart(activity);
            }
            return;
        }

        String flsTitle = "Freeze Last Seen";
        try { if (XResManager.moduleResources != null) flsTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.freezelastseen_title, "Freeze Last Seen"); } catch (Exception ignored) {}
        if (menu.findItem(MENU_ID_FREEZE) != null) return;
        MenuItem item = menu.add(0, MENU_ID_FREEZE, 0, flsTitle);
        try {
            var drawable = XResManager.moduleResources.getDrawable(freezelastseen ? R.drawable.eye_enabled : R.drawable.eye_disabled, null);
            if (drawable != null) {
                drawable.setTint(newSettings ? DesignUtils.getPrimaryTextColor() : 0xff8696a0);
                item.setIcon(drawable);
            }
        } catch (Exception ignored) {}
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        item.setOnMenuItemClickListener(menuItem -> {
            String fTitle = "Freeze Last Seen (" + (freezelastseen ? "ON" : "OFF") + ")";
            String fMsg = "When Freeze Last Seen is on, you will be unable to see others last seen time or online";
            String disableStr = "Disable";
            String enableStr = "Enable";
            try {
                if (XResManager.moduleResources != null) {
                    fTitle = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.freezelastseen_title, "Freeze Last Seen") + " (" + (freezelastseen ? "ON" : "OFF") + ")";
                    fMsg = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.freezelastseen_message, fMsg);
                    disableStr = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.disable, disableStr);
                    enableStr = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.enable, enableStr);
                }
            } catch (Exception ignored) {}
            new AlertDialogWpp(activity)
                    .setTitle(fTitle)
                    .setMessage(fMsg)
                    .setPositiveButton(disableStr, (dialog, which) -> {
                        WppCore.setPrivBooleanSync("freezelastseen", false);
                        Utils.doRestart(activity);
                    })
                    .setNegativeButton(enableStr, (dialog, which) -> {
                        WppCore.setPrivBooleanSync("freezelastseen", true);
                        Utils.doRestart(activity);
                    })
                    .create().show();
            return true;
        });
    }

    private void hookMenu() {
        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var activity = (Activity) param.thisObject;
                for (var menuItem : MenuHome.menuItems) {
                    menuItem.addMenu(menu, activity);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Menu Home";
    }

    public interface HomeMenuItem {

        void addMenu(Menu menu, Activity activity);

    }
}
