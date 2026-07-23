package com.waenhancer.xposed.features.others;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import android.os.Bundle;
import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.bridge.client.ProviderSharedPreferences;
import com.waenhancer.xposed.core.FeatureLoader;
import de.robv.android.xposed.XposedBridge;

public class MenuHome extends Feature {

    private static final int MENU_ID_OPEN_WAEX = 9901;
    private static final int MENU_ID_RESTART = 9902;
    private static final int MENU_ID_GHOST = 9903;
    private static final int MENU_ID_DND = 9904;
    private static final int MENU_ID_FREEZE = 9905;
    private static final int MENU_ID_SUBMENU = 9906;
    private static final int MENU_ID_NEW_CHAT = 9907;
    private static final int MENU_ID_RECORDINGS = 9908;
    private static final int MENU_ID_DELIVERED = 9909;

    public static final LinkedHashSet<HomeMenuItem> menuItems = new LinkedHashSet<>();

    public MenuHome(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static Drawable cachedRestartIcon = null;
    private static Drawable cachedWaeIcon = null;
    
    private static Drawable cachedGhostOnIcon = null;
    private static Drawable cachedGhostOffIcon = null;
    private static Drawable cachedDndOnIcon = null;
    private static Drawable cachedDndOffIcon = null;
    private static Drawable cachedFreezeOnIcon = null;
    private static Drawable cachedFreezeOffIcon = null;
    private static Drawable cachedDeliveredOnIcon = null;
    private static Drawable cachedDeliveredOffIcon = null;
    
    private static Drawable cachedNewChatIcon = null;
    private static Drawable cachedRecordingsIcon = null;

    private static boolean internalItemsAdded = false;

    @Override
    public void doHook() throws Throwable {
        // Pre-cache strings and drawables to avoid lookups during menu preparation
        try {
            cachedRestartIcon = DesignUtils.getDrawable(R.drawable.refresh);
            
            cachedWaeIcon = DesignUtils.getDrawableByName("ic_settings");
            if (cachedWaeIcon != null) cachedWaeIcon.setTint(0xff8696a0);

            cachedGhostOnIcon = DesignUtils.getDrawable(R.drawable.ghost_enabled);
            cachedGhostOffIcon = DesignUtils.getDrawable(R.drawable.ghost_disabled);
            cachedDndOnIcon = DesignUtils.getDrawable(R.drawable.airplane_enabled);
            cachedDndOffIcon = DesignUtils.getDrawable(R.drawable.airplane_disabled);
            cachedFreezeOnIcon = DesignUtils.getDrawable(R.drawable.eye_enabled);
            cachedFreezeOffIcon = DesignUtils.getDrawable(R.drawable.eye_disabled);
            cachedDeliveredOnIcon = DesignUtils.getDrawable(R.drawable.eye_enabled);
            cachedDeliveredOffIcon = DesignUtils.getDrawable(R.drawable.eye_disabled);
            
            int iconTint = 0xff8696a0;
            if (cachedGhostOnIcon != null) cachedGhostOnIcon.setTint(iconTint);
            if (cachedGhostOffIcon != null) cachedGhostOffIcon.setTint(iconTint);
            if (cachedDndOnIcon != null) cachedDndOnIcon.setTint(iconTint);
            if (cachedDndOffIcon != null) cachedDndOffIcon.setTint(iconTint);
            if (cachedFreezeOnIcon != null) cachedFreezeOnIcon.setTint(iconTint);
            if (cachedFreezeOffIcon != null) cachedFreezeOffIcon.setTint(iconTint);
            if (cachedDeliveredOnIcon != null) cachedDeliveredOnIcon.setTint(iconTint);
            if (cachedDeliveredOffIcon != null) cachedDeliveredOffIcon.setTint(iconTint);
            
            cachedNewChatIcon = DesignUtils.getDrawableByName("vec_ic_chat_add");
            if (cachedNewChatIcon == null) cachedNewChatIcon = DesignUtils.getDrawable(R.drawable.ic_contacts);
            if (cachedNewChatIcon != null) cachedNewChatIcon.setTint(iconTint);
            
            cachedRecordingsIcon = DesignUtils.getDrawable(R.drawable.ic_privacy); // Fallback icon
            if (cachedRecordingsIcon != null) cachedRecordingsIcon.setTint(iconTint);

        } catch (Throwable e) {
            logDebug("Error pre-caching menu resources: " + e.getMessage());
        }

        var homeMenuMode = prefs.getString("show_home_menu", "1");

        synchronized (menuItems) {
            if (!internalItemsAdded) {
                menuItems.add(this::InsertOpenWaex);
                menuItems.add((menu, activity) -> InsertGhostModeOption(menu, activity, "1".equals(homeMenuMode)));
                menuItems.add((menu, activity) -> InsertDNDOption(menu, activity, "1".equals(homeMenuMode)));
                menuItems.add((menu, activity) -> InsertFreezeLastSeenOption(menu, activity, "1".equals(homeMenuMode)));
                menuItems.add((menu, activity) -> InsertHideDeliveredOption(menu, activity, "1".equals(homeMenuMode)));
                menuItems.add(this::InsertManageRecordings);
                menuItems.add((menu, activity) -> InsertRestartButton(menu, activity, false));
                internalItemsAdded = true;
            }
        }
        hookMenu(homeMenuMode);
    }

    private void InsertOpenWaex(Menu menu, Activity activity) {
        String entryPoint = prefs.getString("open_waex", "2");
        if (!"1".equals(entryPoint)) return; // Only show in home menu if explicitly set to '1'
        if (!prefs.getBoolean("wa_enhancer_button", true)) return;
        if (menu.findItem(MENU_ID_OPEN_WAEX) != null) return;

        String title = FeatureLoader.getModuleString(activity, R.string.waenhancer_settings, "WaEnhancerX Settings");
        var itemMenu = menu.add(0, MENU_ID_OPEN_WAEX, 0, title);
        
        itemMenu.setOnMenuItemClickListener(item -> {
            Utils.openModule(activity);
            return true;
        });
    }

    private void InsertGhostModeOption(Menu menu, Activity activity, boolean buttonAction) {
        boolean ghostmode = prefs.getBoolean("ghostmode_actual", false);
        if (!prefs.getBoolean("ghostmode", true) && !ghostmode) return;
        if (menu.findItem(MENU_ID_GHOST) != null) return;
        String title = "Ghost Mode (" + (ghostmode ? "ON" : "OFF") + ")";
        try {
            String moduleTitle = FeatureLoader.getModuleString(activity, R.string.ghost_mode_s, "Ghost Mode");
            if (moduleTitle != null && !moduleTitle.isEmpty()) {
                title = String.format(moduleTitle, ghostmode ? "ON" : "OFF");
            }
        } catch (Exception ignored) {}

        var itemMenu = menu.add(0, MENU_ID_GHOST, 1, title);
        if (buttonAction && !(menu instanceof SubMenu)) {
            Drawable icon = ghostmode ? cachedGhostOnIcon : cachedGhostOffIcon;
            if (icon != null) itemMenu.setIcon(icon);
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        final String finalTitle = title;
        itemMenu.setOnMenuItemClickListener(item -> {
            boolean current = prefs.getBoolean("ghostmode_actual", false);
            showToggleDialog(activity, finalTitle, "ghostmode_actual", current);
            return true;
        });
    }

    private void InsertDNDOption(Menu menu, Activity activity, boolean buttonAction) {
        boolean dndmode = prefs.getBoolean("dndmode_actual", false);
        if (!prefs.getBoolean("show_dndmode", true) && !dndmode) return;
        if (menu.findItem(MENU_ID_DND) != null) return;

        String title = "DND Mode (" + (dndmode ? "ON" : "OFF") + ")";
        try {
            String moduleTitle = FeatureLoader.getModuleString(activity, R.string.dnd_mode_s, "DND Mode");
            if (moduleTitle != null && !moduleTitle.isEmpty()) {
                title = String.format(moduleTitle, dndmode ? "ON" : "OFF");
            }
        } catch (Exception ignored) {}

        var itemMenu = menu.add(0, MENU_ID_DND, 2, title);
        if (buttonAction && !(menu instanceof SubMenu)) {
            Drawable icon = dndmode ? cachedDndOnIcon : cachedDndOffIcon;
            if (icon != null) itemMenu.setIcon(icon);
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        final String finalTitleDnd = title;
        itemMenu.setOnMenuItemClickListener(item -> {
            boolean current = prefs.getBoolean("dndmode_actual", false);
            showToggleDialog(activity, finalTitleDnd, "dndmode_actual", current);
            return true;
        });
    }

    private void InsertFreezeLastSeenOption(Menu menu, Activity activity, boolean buttonAction) {
        boolean freeze = prefs.getBoolean("freeze_last_seen_actual", false);
        if (!prefs.getBoolean("show_freezeLastSeen", true) && !freeze) return;
        if (menu.findItem(MENU_ID_FREEZE) != null) return;

        String title = "Freeze Last Seen (" + (freeze ? "ON" : "OFF") + ")";
        try {
            String moduleTitle = FeatureLoader.getModuleString(activity, R.string.freeze_last_seen_s, "Freeze Last Seen");
            if (moduleTitle != null && !moduleTitle.isEmpty()) {
                title = String.format(moduleTitle, freeze ? "ON" : "OFF");
            }
        } catch (Exception ignored) {}

        var itemMenu = menu.add(0, MENU_ID_FREEZE, 3, title);
        if (buttonAction && !(menu instanceof SubMenu)) {
            Drawable icon = freeze ? cachedFreezeOnIcon : cachedFreezeOffIcon;
            if (icon != null) itemMenu.setIcon(icon);
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        final String finalTitleFreeze = title;
        itemMenu.setOnMenuItemClickListener(item -> {
            boolean current = prefs.getBoolean("freeze_last_seen_actual", false);
            showToggleDialog(activity, finalTitleFreeze, "freeze_last_seen_actual", current);
            return true;
        });
    }

    private void InsertHideDeliveredOption(Menu menu, Activity activity, boolean buttonAction) {
        boolean hidereceipt = prefs.getBoolean("hidereceipt", false);
        if (!prefs.getBoolean("show_hidereceipt", true) && !hidereceipt) return;
        if (menu.findItem(MENU_ID_DELIVERED) != null) return;

        String title = "Hide Delivered (" + (hidereceipt ? "ON" : "OFF") + ")";
        try {
            String moduleTitle = FeatureLoader.getModuleString(activity, R.string.hide_delivered_s, "Hide Delivered");
            if (moduleTitle != null && !moduleTitle.isEmpty()) {
                title = String.format(moduleTitle, hidereceipt ? "ON" : "OFF");
            }
        } catch (Exception ignored) {}

        var itemMenu = menu.add(0, MENU_ID_DELIVERED, 4, title);
        if (buttonAction && !(menu instanceof SubMenu)) {
            Drawable icon = hidereceipt ? cachedDeliveredOnIcon : cachedDeliveredOffIcon;
            if (icon != null) itemMenu.setIcon(icon);
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        final String finalTitle = title;
        itemMenu.setOnMenuItemClickListener(item -> {
            boolean current = prefs.getBoolean("hidereceipt", false);
            showToggleDialog(activity, finalTitle, "hidereceipt", current);
            return true;
        });
    }

    private void InsertNewChat(Menu menu, Activity activity) {
        if (!prefs.getBoolean("newchat", true)) return;
        if (menu.findItem(MENU_ID_NEW_CHAT) != null) return;

        String title = FeatureLoader.getModuleString(activity, R.string.new_chat, "New Chat");
        var itemMenu = menu.add(0, MENU_ID_NEW_CHAT, 10, title);

        itemMenu.setOnMenuItemClickListener(item -> {
            var view = new LinearLayout(activity);
            view.setGravity(Gravity.CENTER);
            view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            var edt = new EditText(view.getContext());
            edt.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
            edt.setMaxLines(1);
            edt.setInputType(InputType.TYPE_CLASS_PHONE);
            edt.setTransformationMethod(null);
            edt.setHint(FeatureLoader.getModuleString(activity, R.string.number_with_country_code, "Number with country code"));
            view.addView(edt);

            new AlertDialogWpp(activity)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(FeatureLoader.getModuleString(activity, R.string.message, "Message"), (dialog, which) -> {
                    var number = edt.getText().toString();
                    var numberFomatted = number.replaceAll("[+\\-()/\\s]", "");
                    var intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://wa.me/" + numberFomatted));
                    intent.setPackage(Utils.getApplication().getPackageName());
                    activity.startActivity(intent);
                })
                .setNegativeButton(FeatureLoader.getModuleString(activity, R.string.cancel, "Cancel"), null)
                .show();
            return true;
        });
    }

    private void InsertManageRecordings(Menu menu, Activity activity) {
        if (!prefs.getBoolean("call_recording_enable", false)) return;
        if (menu.findItem(MENU_ID_RECORDINGS) != null) return;

        String title = "Manage Recordings";
        try {
            String moduleTitle = FeatureLoader.getModuleString(activity, R.string.manage_recordings, "Manage Recordings");
            if (moduleTitle != null && !moduleTitle.isEmpty()) {
                title = moduleTitle;
            }
        } catch (Exception ignored) {}

        var itemMenu = menu.add(0, MENU_ID_RECORDINGS, 11, title);

        itemMenu.setOnMenuItemClickListener(item -> {
            try {
                Intent intent = new Intent();
                intent.setClassName(BuildConfig.APPLICATION_ID, "com.waenhancer.activities.RecordingsActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Throwable t) {
                Utils.showToast("Failed to open Recordings Manager", Toast.LENGTH_SHORT);
            }
            return true;
        });
    }

    private void showToggleDialog(Activity activity, String title, String key, boolean current) {
        new AlertDialogWpp(activity)
            .setTitle(title)
            .setMessage(FeatureLoader.getModuleString(activity, R.string.restart_wpp, 
                "It is necessary to restart WhatsApp for the changes in WaEnhancer X to take effect.\n\nDo you want to restart?"))
            .setPositiveButton(FeatureLoader.getModuleString(activity, android.R.string.ok, "OK"), (dialog, which) -> {
                if ("ghostmode_actual".equals(key) || "dndmode_actual".equals(key) || "freeze_last_seen_actual".equals(key) || "hidereceipt".equals(key)) {
                    if (prefs instanceof ProviderSharedPreferences) {
                        prefs.edit().putBoolean(key, !current).commit();
                    } else {
                        try {
                            Bundle extras = new Bundle();
                            extras.putString("key", key);
                            extras.putString("type", "boolean");
                            extras.putBoolean("value", !current);
                            activity.getContentResolver().call(
                                Uri.parse("content://com.waenhancer.hookprovider"),
                                "put_preference",
                                null,
                                extras
                            );
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] Failed to write preference via provider: " + t.getMessage());
                        }
                    }
                } else {
                    WppCore.setPrivBooleanSync(key, !current);
                }
                Utils.doRestart(activity);
            })
            .setNegativeButton(FeatureLoader.getModuleString(activity, android.R.string.cancel, "Cancel"), null)
            .show();
    }

    private void InsertRestartButton(Menu menu, Activity activity, boolean newSettings) {
        if (!prefs.getBoolean("restartbutton", true)) return;
        if (menu.findItem(MENU_ID_RESTART) != null) return;

        String restartLabel = FeatureLoader.getModuleString(activity, R.string.restart_whatsapp, "Restart WhatsApp");
        var itemMenu = menu.add(0, MENU_ID_RESTART, 4, restartLabel);
        
        if (newSettings && !(menu instanceof SubMenu)) {
            if (cachedRestartIcon != null) {
                cachedRestartIcon.setTint(DesignUtils.getPrimaryTextColor());
                itemMenu.setIcon(cachedRestartIcon);
            }
            itemMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else if (!(menu instanceof SubMenu)) {
            if (cachedRestartIcon != null) {
                cachedRestartIcon.setTint(0xff8696a0);
                itemMenu.setIcon(cachedRestartIcon);
            }
        }
        
        itemMenu.setOnMenuItemClickListener(item -> {
            Utils.doRestart(activity);
            return true;
        });
    }

    private void hookMenu(String homeMenuMode) {
        if ("0".equals(homeMenuMode)) {
            // Disabled: do not hook/inject the home menu
            return;
        }
        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var activity = (Activity) param.thisObject;
                
                // Only inject if it's HomeActivity to avoid pollution of other menus
                if (!activity.getClass().getSimpleName().equals("HomeActivity")) return;

                // Create or find the WaEnhancerX submenu
                var subMenuItem = menu.findItem(MENU_ID_SUBMENU);
                SubMenu subMenu;
                if (subMenuItem == null) {
                    String waeTitle = "WaEnhancerX";
                    try {
                        String moduleTitle = FeatureLoader.getModuleString(activity, R.string.app_name, "WaEnhancerX");
                        if (moduleTitle != null && !moduleTitle.isEmpty()) {
                            waeTitle = moduleTitle;
                        }
                    } catch (Exception ignored) {}

                    subMenu = menu.addSubMenu(0, MENU_ID_SUBMENU, 0, waeTitle);
                    var item = subMenu.getItem();
                    if (cachedWaeIcon != null) {
                        item.setIcon(cachedWaeIcon);
                    }
                    
                    // If homeMenuMode is "1" (Show as icon), show the WaEnhancerX submenu icon on the toolbar
                    if ("1".equals(homeMenuMode)) {
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    }
                } else {
                    subMenu = subMenuItem.getSubMenu();
                }

                // Inject all items into the submenu. 
                // We pass 'false' for buttonAction inside the submenu to keep it clean (no nested icons on toolbar).
                for (var menuItem : MenuHome.menuItems) {
                    menuItem.addMenu(subMenu, activity);
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