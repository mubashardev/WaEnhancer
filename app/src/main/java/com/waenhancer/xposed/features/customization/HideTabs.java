package com.waenhancer.xposed.features.customization;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.PerfLogger;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideTabs extends Feature {
    private static final int STATUS_TAB_ID = 300;
    private Object mTabPagerInstance;
    private final ArrayList<Integer> currentTabs = new ArrayList<>();

    public HideTabs(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var hidetabs = prefs.getStringSet("hidetabs", null);
        var igstatus = prefs.getBoolean("igstatus", false);
        if (hidetabs == null || hidetabs.isEmpty())
            return;

        var home = WppCore.getHomeActivityClass(classLoader);

        var hideTabsList = hidetabs.stream().map(Integer::valueOf).collect(Collectors.toList());

        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));
        var ListField = ReflectionUtils.getFieldByType(home, List.class);

        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var list = (List<Integer>) XposedHelpers.getStaticObjectField(home, ListField.getName());
                for (var item : hideTabsList) {
                    if (item != STATUS_TAB_ID || !igstatus) {
                        list.remove(item);
                    }
                }
                synchronized (currentTabs) {
                    currentTabs.clear();
                    currentTabs.addAll(list);
                }
            }
        });

        var OnTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(classLoader);
        XposedBridge.hookMethod(OnTabItemAddMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menuItem = (MenuItem) param.getResult();
                var menuItemId = menuItem.getItemId();
                if (hideTabsList.contains(menuItemId)) {
                    menuItem.setVisible(false);
                }
            }
        });

        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                long perfStart = PerfLogger.start();
                Class<?> TabsPagerClass = WppCore.getTabsPagerClass(classLoader);
                var tabsField = ReflectionUtils.getFieldByType(param.thisObject.getClass(), TabsPagerClass);
                mTabPagerInstance = tabsField.get(param.thisObject);

                // Also apply hiding immediately in onCreate for faster initial render
                if (mTabPagerInstance != null) {
                    try {
                        var contentView = ((Activity) param.thisObject).findViewById(android.R.id.content);
                        if (contentView != null) {
                            ArrayList<Integer> tabsSnapshot;
                            synchronized (currentTabs) {
                                tabsSnapshot = new ArrayList<>(currentTabs);
                            }
                            if (!tabsSnapshot.isEmpty()) {
                                var arr = new ArrayList<>(tabsSnapshot);
                                arr.removeAll(hideTabsList);
                                View tabFrame = contentView.findViewById(android.R.id.tabs);
                                if (tabFrame != null && arr.size() == 1) {
                                    tabFrame.setVisibility(View.GONE);
                                }
                            }
                            for (var item : hideTabsList) {
                                View tabView = contentView.findViewById(item);
                                if (tabView != null) {
                                    tabView.setVisibility(View.GONE);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                PerfLogger.end("HideTabs.homeOnCreate", perfStart, 1);
            }
        });


        var onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader);

        XposedBridge.hookMethod(onMenuItemSelected, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                long perfStart = PerfLogger.start();
                if (param.thisObject == mTabPagerInstance) {
                    var index = (int) param.args[0];
                    var idxAtual = (int) XposedHelpers.callMethod(param.thisObject, "getCurrentItem");
                    param.args[0] = getNewTabIndex(hideTabsList, idxAtual, index);
                }
                PerfLogger.end("HideTabs.onMenuItemSelected", perfStart, 1);
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Tabs";
    }

    public int getNewTabIndex(List<?> hidetabs, int indexAtual, int index) {
        ArrayList<Integer> tabsSnapshot;
        synchronized (currentTabs) {
            if (currentTabs.isEmpty()) {
                return index;
            }
            tabsSnapshot = new ArrayList<>(currentTabs);
        }
        
        int target = index;
        int direction = index > indexAtual ? 1 : -1;
        
        while (target >= 0 && target < tabsSnapshot.size()) {
            if (!hidetabs.contains(tabsSnapshot.get(target))) {
                return target;
            }
            target += direction;
        }
        
        // Fallback: search in opposite direction if we went out of bounds
        if (target < 0 || target >= tabsSnapshot.size()) {
            target = index - direction;
            while (target >= 0 && target < tabsSnapshot.size()) {
                if (!hidetabs.contains(tabsSnapshot.get(target))) {
                    return target;
                }
                target -= direction;
            }
        }
        
        return indexAtual; // Final fallback
    }
}
