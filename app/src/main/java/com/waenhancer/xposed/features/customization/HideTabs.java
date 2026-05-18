package com.waenhancer.xposed.features.customization;

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

    /**
     * Original ordered tab IDs, built by recording each tab as it's added to the
     * bottom-nav menu. The index here == ViewPager child position.
     */
    private final ArrayList<Integer> originalTabs = new ArrayList<>();

    public HideTabs(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var hidetabs = prefs.getStringSet("hidetabs", null);
        var igstatus = prefs.getBoolean("igstatus", false);
        if (hidetabs == null || hidetabs.isEmpty())
            return;

        var hideTabsList = hidetabs.stream().map(Integer::valueOf).collect(Collectors.toList());

        // Keep this hook so we can also remove hidden tabs from the adapter's tab list
        // if the method actually returns an ArrayList (some WA versions).
        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));

        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object result = param.getResult();
                if (result instanceof ArrayList) {
                    var tabs = (ArrayList<Integer>) result;
                    // Sync originalTabs in case OnTabItemAddMethod hasn't fired yet
                    synchronized (originalTabs) {
                        if (originalTabs.isEmpty()) {
                            originalTabs.addAll(tabs);
                        }
                    }
                    for (var item : hideTabsList) {
                        if (item != STATUS_TAB_ID || !igstatus) {
                            tabs.remove(item);
                        }
                    }
                }
            }
        });

        // This hook fires once per tab at startup, in ViewPager position order.
        // We use it both to hide bottom-nav items AND to build the originalTabs mapping.
        var OnTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(classLoader);
        XposedBridge.hookMethod(OnTabItemAddMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menuItem = (MenuItem) param.getResult();
                var menuItemId = menuItem.getItemId();

                // Record each tab ID in order — index == ViewPager child position.
                synchronized (originalTabs) {
                    if (!originalTabs.contains(menuItemId)) {
                        originalTabs.add(menuItemId);
                    }
                }

                if (hideTabsList.contains(menuItemId)) {
                    menuItem.setVisible(false);
                }
            }
        });

        // Hide tab-bar item views; hide entire tab bar if only one tab remains.
        var loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        logDebug(loadTabFrameClass);

        XposedHelpers.findAndHookMethod(loadTabFrameClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ArrayList<Integer> origSnapshot;
                synchronized (originalTabs) {
                    origSnapshot = new ArrayList<>(originalTabs);
                }
                if (!origSnapshot.isEmpty()) {
                    var arr = new ArrayList<>(origSnapshot);
                    arr.removeAll(hideTabsList);
                    if (arr.size() == 1) {
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                }
                for (var item : hideTabsList) {
                    View view;
                    if ((view = ((View) param.thisObject).findViewById(item)) != null) {
                        view.setVisibility(View.GONE);
                    }
                }
            }
        });

        // Capture the TabsPager instance after HomeActivity.onCreate completes.
        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                long perfStart = PerfLogger.start();
                try {
                    Class<?> TabsPagerClass = WppCore.getTabsPagerClass(classLoader);
                    var tabsField = ReflectionUtils.getFieldByType(param.thisObject.getClass(), TabsPagerClass);
                    if (tabsField != null) {
                        tabsField.setAccessible(true);
                        mTabPagerInstance = tabsField.get(param.thisObject);
                    }
                } catch (Exception e) {
                    XposedBridge.log("HideTabs: failed to get TabsPager instance: " + e);
                }
                XposedBridge.log("HideTabs: after HomeActivity.onCreate: originalTabs=" + originalTabs
                        + " mTabPager=" + (mTabPagerInstance != null));
                PerfLogger.end("HideTabs.homeOnCreate", perfStart, 1);
            }
        });

        // Intercepts ViewPager scroll settlement (both swipe AND bottom-nav tap).
        // Redirects away from hidden-tab positions using the originalTabs mapping.
        var onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader);
        XposedBridge.hookMethod(onMenuItemSelected, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                long perfStart = PerfLogger.start();
                if (param.thisObject == mTabPagerInstance) {
                    var index = (int) param.args[0];
                    var idxAtual = (int) XposedHelpers.callMethod(param.thisObject, "getCurrentItem");
                    int newIndex = getNewTabIndex(hideTabsList, idxAtual, index);
                    if (newIndex != index) {
                        XposedBridge.log("HideTabs: scroll redirect " + index + " -> " + newIndex);
                    }
                    param.args[0] = newIndex;
                }
                PerfLogger.end("HideTabs.onMenuItemSelected", perfStart, 1);
            }
        });

        // Hide the fragment view for hidden-tab positions when added to the ViewPager.
        XposedHelpers.findAndHookMethod("androidx.viewpager.widget.ViewPager", classLoader,
                "addView",
                classLoader.loadClass("android.view.View"),
                int.class,
                classLoader.loadClass("android.view.ViewGroup$LayoutParams"),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != mTabPagerInstance) return;
                        ArrayList<Integer> origSnapshot;
                        synchronized (originalTabs) {
                            origSnapshot = new ArrayList<>(originalTabs);
                        }
                        if (origSnapshot.isEmpty()) return;
                        for (var item : hideTabsList) {
                            var index = origSnapshot.indexOf(item);
                            if (index == -1) continue;
                            if ((int) param.args[1] == index) {
                                ((View) param.args[0]).setVisibility(View.GONE);
                            }
                        }
                    }
                });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Tabs";
    }

    /**
     * Returns the correct ViewPager target index, skipping any hidden-tab positions.
     * Recursively steps away from the hidden position until a visible tab is found.
     */
    public int getNewTabIndex(List<?> hidetabs, int indexAtual, int index) {
        ArrayList<Integer> tabsSnapshot;
        synchronized (originalTabs) {
            if (originalTabs.isEmpty()) return index;
            tabsSnapshot = new ArrayList<>(originalTabs);
        }

        if (index < 0 || index >= tabsSnapshot.size()) return index;
        if (!hidetabs.contains(tabsSnapshot.get(index))) return index;

        // Step away from the hidden position in the direction of travel.
        int newIndex = index > indexAtual ? index + 1 : index - 1;
        if (newIndex < 0) return 0;
        if (newIndex >= tabsSnapshot.size()) return indexAtual;
        return getNewTabIndex(hidetabs, indexAtual, newIndex);
    }
}
