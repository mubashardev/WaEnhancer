package com.waenhancer.xposed.features.customization;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.SharedPreferences;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.waenhancer.xposed.core.devkit.Unobfuscator;

public class SeparateGroup extends Feature {
    public static final int GROUPS = 500;

    // The actual ViewPager position of our cloned Groups fragment (always last = count - 1)
    private int mGroupsViewPagerPos = -1;

    // Filter Reflection Caches
    private Class<?> mFilterAdapterClass;
    private java.lang.reflect.Method mMethodSetFilter;
    private java.lang.reflect.Field mFilterListField;

    // Cached communities icon drawable
    private android.graphics.drawable.Drawable mCommunitiesIcon = null;

    // Reference to the BottomNavigationView (or NavigationBarView) for forcing selection
    private Object mNavBarView = null;

    public SeparateGroup(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() {
        if (!com.waenhancer.BuildConfig.DEBUG) {
            return;
        }
        if (!prefs.getBoolean("separategroups", false)) {
            return;
        }
        try {
            // 1. Hook the ViewPager adapter to add an extra tab count (Groups cloned from Chats)
            Class<?> TabsPagerClass = WppCore.getTabsPagerClass(classLoader);
            Class<?> current = TabsPagerClass;
            while (current != null && !current.getName().equals("java.lang.Object")) {
                java.util.Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(current, "setAdapter", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                            hookPagerAdapter(param.args[0]);
                        }
                    }
                });
                if (!unhooks.isEmpty()) {
                    XposedBridge.log("SeparateGroup: Successfully hooked setAdapter on " + current.getName());
                    break;
                }
                current = current.getSuperclass();
            }

            // 2. Initialize Filter Reflection
            try {
                mFilterAdapterClass = Unobfuscator.loadFilterAdaperClass(classLoader);
                if (mFilterAdapterClass != null) {
                    mMethodSetFilter = ReflectionUtils.findMethodUsingFilter(mFilterAdapterClass, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(int.class));
                    mFilterListField = ReflectionUtils.getFieldByExtendType(mFilterAdapterClass, java.util.List.class);
                }
            } catch (Throwable t) {
                XposedBridge.log("SeparateGroup: Failed to load FilterAdapter: " + t);
            }

            // 3. Hook ViewPager.addOnPageChangeListener to intercept page selections
            try {
                Class<?> viewPagerClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", classLoader);
                XposedBridge.hookAllMethods(viewPagerClass, "addOnPageChangeListener", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object listener = param.args[0];
                        if (listener == null) return;
                        XposedBridge.hookAllMethods(listener.getClass(), "onPageSelected", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param2) throws Throwable {
                                int position = (Integer) param2.args[0];
                                // When the user lands on the cloned Groups fragment (last position),
                                // override the nav bar highlight to point to Groups (ID=500, index=1)
                                if (mGroupsViewPagerPos >= 0 && position == mGroupsViewPagerPos) {
                                    forceNavSelection(GROUPS);
                                    // Apply group filter
                                    Object viewPager = param.thisObject;
                                    try {
                                        Object adapter = XposedHelpers.callMethod(viewPager, "getAdapter");
                                        if (adapter != null) {
                                            Object fragment = XposedHelpers.callMethod(adapter, "instantiateItem", viewPager, position);
                                            if (fragment != null && fragment.getClass().getName().equals("com.whatsapp.conversationslist.ConversationsFragment")) {
                                                applyNativeFilter(fragment, 1);
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                } else if (position == 0) {
                                    // Apply chats-only (contacts) filter to the main Chats tab
                                    Object viewPager = param.thisObject;
                                    try {
                                        Object adapter = XposedHelpers.callMethod(viewPager, "getAdapter");
                                        if (adapter != null) {
                                            Object fragment = XposedHelpers.callMethod(adapter, "instantiateItem", viewPager, position);
                                            if (fragment != null && fragment.getClass().getName().equals("com.whatsapp.conversationslist.ConversationsFragment")) {
                                                applyNativeFilter(fragment, 0);
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        });
                    }
                });
                XposedBridge.log("SeparateGroup: Hooked ViewPager PageChangeListener for filtering");
            } catch (Throwable t) {
                XposedBridge.log("SeparateGroup: Failed to hook ViewPager for filtering: " + t);
            }

            // 4. Hook BottomNavigationView/NavigationBarView to capture the reference
            //    and also hook setSelectedItemId so we can reverse-map item ID 500 → the real tab
            try {
                hookNavBarSelection(classLoader);
            } catch (Throwable t) {
                XposedBridge.log("SeparateGroup: Failed to hook NavBar: " + t);
            }

            // 5. Inject the Groups menu item into the bottom nav
            hookTabsMenu(classLoader);

        } catch (Throwable t) {
            XposedBridge.log("SeparateGroup Hook Error: " + t);
        }
    }

    // -----------------------------------------------------------------------
    // Navigation bar selection forcing
    // -----------------------------------------------------------------------

    private void hookNavBarSelection(ClassLoader classLoader) throws Throwable {
        // Hook NavigationBarView (parent of BottomNavigationView)
        String[] navClasses = {"com.google.android.material.navigation.NavigationBarView", "com.google.android.material.bottomnavigation.BottomNavigationView"};
        for (String clsName : navClasses) {
            try {
                Class<?> navClass = XposedHelpers.findClass(clsName, classLoader);
                // Capture the view when it's attached to a window
                XposedBridge.hookAllMethods(navClass, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mNavBarView = param.thisObject;
                        XposedBridge.log("SeparateGroup: Captured NavBarView: " + param.thisObject.getClass().getName());
                    }
                });
                // When the user clicks Groups tab (ID=500), map it to the last VP page
                XposedBridge.hookAllMethods(navClass, "setSelectedItemId", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int itemId = (int) param.args[0];
                        if (itemId == GROUPS) {
                            // Programmatically navigate the ViewPager to the cloned Groups position
                            navigateViewPagerToGroups(param.thisObject);
                        }
                    }
                });
                XposedBridge.log("SeparateGroup: Hooked " + clsName);
                break;
            } catch (Throwable ignored) {}
        }
    }

    private void navigateViewPagerToGroups(Object navView) {
        if (mGroupsViewPagerPos < 0) return;
        try {
            // Walk up to activity and find the TabsPager
            android.content.Context ctx = (android.content.Context) XposedHelpers.callMethod(navView, "getContext");
            if (ctx instanceof Activity) {
                Activity act = (Activity) ctx;
                Class<?> tabsPagerClass = WppCore.getTabsPagerClass(classLoader);
                View tabsPager = act.getWindow().getDecorView().findViewWithTag("TabsPager");
                if (tabsPager == null) {
                    // Fallback: find by class type in the view hierarchy
                    tabsPager = findViewByClass(act.getWindow().getDecorView(), tabsPagerClass);
                }
                if (tabsPager != null) {
                    XposedHelpers.callMethod(tabsPager, "setCurrentItem", mGroupsViewPagerPos, true);
                    XposedBridge.log("SeparateGroup: Navigated ViewPager to Groups at pos=" + mGroupsViewPagerPos);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("SeparateGroup: Error navigating to Groups: " + t);
        }
    }

    private View findViewByClass(View root, Class<?> targetClass) {
        if (targetClass.isInstance(root)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View result = findViewByClass(vg.getChildAt(i), targetClass);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void forceNavSelection(int itemId) {
        if (mNavBarView == null) return;
        try {
            // Post on main thread to avoid cross-thread issues
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            Object navRef = mNavBarView;
            h.post(() -> {
                try {
                    // Use the Menu to mark item as checked directly, bypassing setSelectedItemId
                    // to avoid a recursive loop
                    Object menu = XposedHelpers.callMethod(navRef, "getMenu");
                    android.view.MenuItem groupsItem = ((android.view.Menu) menu).findItem(itemId);
                    if (groupsItem != null) {
                        groupsItem.setChecked(true);
                        XposedBridge.log("SeparateGroup: Forced Groups highlight in NavBar");
                    }
                } catch (Throwable t) {
                    XposedBridge.log("SeparateGroup: forceNavSelection error: " + t);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("SeparateGroup: forceNavSelection outer error: " + t);
        }
    }

    // -----------------------------------------------------------------------
    // Tab menu injection (adds Groups item at index 1)
    // -----------------------------------------------------------------------

    private boolean mGroupsTabAdded = false;
    private android.view.MenuItem mGroupMenuItem = null;

    private void hookTabsMenu(ClassLoader classLoader) {
        try {
            var OnTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(classLoader);
            if (OnTabItemAddMethod == null) return;
            XposedBridge.hookMethod(OnTabItemAddMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0 || param.args[0] == null) return;

                    try {
                        int id = -1;
                        CharSequence title = null;

                        if (param.args.length >= 4 && param.args[0] instanceof Integer && param.args[1] instanceof Integer) {
                            id = (Integer) param.args[1];
                            title = (CharSequence) param.args[3];
                        } else {
                            java.lang.reflect.Method getItemIdMethod = param.args[0].getClass().getMethod("getItemId");
                            java.lang.reflect.Method getTitleMethod = param.args[0].getClass().getMethod("getTitle");
                            id = (int) getItemIdMethod.invoke(param.args[0]);
                            title = (CharSequence) getTitleMethod.invoke(param.args[0]);
                        }

                        XposedBridge.log("SeparateGroup: TabItem added: ID=" + id + ", Title=" + title);

                        // Steal the Communities icon when it's added
                        if ((id == 400 || id == 600) && param.thisObject instanceof android.view.Menu) {
                            android.view.Menu menu = (android.view.Menu) param.thisObject;
                            android.view.MenuItem comItem = menu.findItem(id);
                            if (comItem != null && comItem.getIcon() != null) {
                                mCommunitiesIcon = comItem.getIcon().mutate().getConstantState().newDrawable();
                                XposedBridge.log("SeparateGroup: Captured Communities icon from ID=" + id);
                                // Retroactively apply if Groups item was already created
                                if (mGroupMenuItem != null) {
                                    mGroupMenuItem.setIcon(mCommunitiesIcon);
                                    XposedBridge.log("SeparateGroup: Retroactively applied Communities icon to Groups tab");
                                }
                            }
                        }

                        if (id == 200 && !mGroupsTabAdded && param.thisObject instanceof android.view.Menu) {
                            mGroupsTabAdded = true;
                            android.view.Menu menu = (android.view.Menu) param.thisObject;

                            android.view.MenuItem groupItem = menu.add(0, GROUPS, 0, "Groups");
                            mGroupMenuItem = groupItem;

                            // Load icon from WhatsApp's own resources (communities selector icon)
                            try {
                                // Walk the MenuBuilder class hierarchy to find a mContext field
                                android.content.Context ctx = null;
                                Class<?> menuClass = menu.getClass();
                                while (menuClass != null && ctx == null) {
                                    for (java.lang.reflect.Field f : menuClass.getDeclaredFields()) {
                                        if (android.content.Context.class.isAssignableFrom(f.getType())) {
                                            f.setAccessible(true);
                                            ctx = (android.content.Context) f.get(menu);
                                            break;
                                        }
                                    }
                                    menuClass = menuClass.getSuperclass();
                                }
                                if (ctx != null) {
                                    String[] candidates = {
                                        "home_tab_communities_selector",
                                        "home_tab_chats_selector",
                                    };
                                    int iconId = 0;
                                    for (String name : candidates) {
                                        iconId = ctx.getResources().getIdentifier(name, "drawable", "com.whatsapp");
                                        if (iconId != 0) {
                                            XposedBridge.log("SeparateGroup: Loaded icon from WA: " + name);
                                            break;
                                        }
                                    }
                                    if (iconId != 0) {
                                        groupItem.setIcon(ctx.getResources().getDrawable(iconId, ctx.getTheme()));
                                    } else {
                                        groupItem.setIcon(android.R.drawable.ic_menu_myplaces);
                                    }
                                } else {
                                    groupItem.setIcon(android.R.drawable.ic_menu_myplaces);
                                }
                            } catch (Exception e) {
                                XposedBridge.log("SeparateGroup: Icon load error: " + e.getMessage());
                                groupItem.setIcon(android.R.drawable.ic_menu_myplaces);
                            }

                            // Move from end → index 1
                            try {
                                java.lang.reflect.Field itemsField = null;
                                Class<?> cls = menu.getClass();
                                while (cls != null) {
                                    try {
                                        itemsField = cls.getDeclaredField("mItems");
                                        break;
                                    } catch (NoSuchFieldException e) {
                                        cls = cls.getSuperclass();
                                    }
                                }
                                if (itemsField != null) {
                                    itemsField.setAccessible(true);
                                    java.util.ArrayList<android.view.MenuItem> items =
                                            (java.util.ArrayList<android.view.MenuItem>) itemsField.get(menu);
                                    items.remove(groupItem);
                                    items.add(1, groupItem);
                                    XposedBridge.log("SeparateGroup: Groups tab inserted at menu index 1");
                                }
                            } catch (Exception e) {
                                XposedBridge.log("SeparateGroup: mItems insert fallback: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log("SeparateGroup: TabHook Exception: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("SeparateGroup: TabHook Error: " + t);
        }
    }

    // -----------------------------------------------------------------------
    // ViewPager adapter hook — adds 1 to count, clones Chats as last fragment
    // -----------------------------------------------------------------------

    private void hookPagerAdapter(Object adapter) {
        Class<?> currentClazz = adapter.getClass();
        XposedBridge.log("SeparateGroup: Hooking full hierarchy for Adapter: " + currentClazz.getName());

        while (currentClazz != null && !currentClazz.getName().equals("java.lang.Object")) {
            for (java.lang.reflect.Method method : currentClazz.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                Class<?> returnType = method.getReturnType();

                // getCount() — returns int, no params
                if (params.length == 0 && returnType == int.class) {
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                int originalCount = (int) param.getResult();
                                mGroupsViewPagerPos = originalCount; // our slot is at the end
                                param.setResult(originalCount + 1);
                                XposedBridge.log("SeparateGroup: [Hierarchy] " + method.getName() + "() changed from " + originalCount + " to " + (originalCount + 1));
                            }
                        });
                        XposedBridge.log("SeparateGroup: Hooked getCount: " + method.getName() + " in " + currentClazz.getName());
                    } catch (Throwable t) { /* ignore */ }
                }

                // Single-int param methods: getItem(int), getItemId(int), getPageTitle(int)
                if (params.length == 1 && params[0] == int.class) {
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (mGroupsViewPagerPos < 0) return;
                                int val = (int) param.args[0];
                                param.setObjectExtra("requestedPos", val);

                                if (val == mGroupsViewPagerPos) {
                                    // Groups position: clone from Chats (pos 0)
                                    if (returnType.getName().contains("Fragment")) {
                                        param.args[0] = 0;
                                    } else if (returnType == int.class || returnType == Integer.class) {
                                        param.setResult(GROUPS);
                                    } else if (returnType == CharSequence.class || returnType == String.class) {
                                        param.setResult("Groups");
                                    } else {
                                        param.args[0] = 0;
                                    }
                                }
                                // All other positions: pass through unchanged
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Integer reqPos = (Integer) param.getObjectExtra("requestedPos");
                                if (reqPos == null || mGroupsViewPagerPos < 0) return;
                                int val = reqPos;

                                // Tag the cloned ConversationsFragment so we know it's the Groups one
                                if (val == mGroupsViewPagerPos && returnType.getName().contains("Fragment") && param.getResult() != null) {
                                    Object fragment = param.getResult();
                                    if (fragment.getClass().getName().equals("com.whatsapp.conversationslist.ConversationsFragment")) {
                                        try {
                                            java.lang.reflect.Method getArgsMethod = findMethodInHierarchy(fragment.getClass(), "getArguments");
                                            android.os.Bundle args = getArgsMethod != null
                                                    ? (android.os.Bundle) getArgsMethod.invoke(fragment) : null;
                                            if (args == null) {
                                                args = new android.os.Bundle();
                                                java.lang.reflect.Method setArgsMethod = findMethodInHierarchy(fragment.getClass(), "setArguments", android.os.Bundle.class);
                                                if (setArgsMethod != null) setArgsMethod.invoke(fragment, args);
                                            }
                                            args.putInt("waenhancer_tab_pos", 1);
                                            XposedBridge.log("SeparateGroup: Tagged Groups ConversationsFragment");
                                        } catch (Throwable e) {
                                            XposedBridge.log("SeparateGroup: Error tagging fragment: " + e);
                                        }
                                    }
                                }
                            }
                        });
                        XposedBridge.log("SeparateGroup: Hooked index method " + method.getName() + " in " + currentClazz.getName());
                    } catch (Throwable t) { /* ignore */ }
                }
            }
            currentClazz = currentClazz.getSuperclass();
        }
    }

    private java.lang.reflect.Method findMethodInHierarchy(Class<?> cls, String name, Class<?>... paramTypes) {
        while (cls != null) {
            try {
                return paramTypes.length == 0 ? cls.getMethod(name) : cls.getMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Native filter application
    // -----------------------------------------------------------------------

    private void applyNativeFilter(Object fragment, int position) {
        if (mFilterAdapterClass == null || mMethodSetFilter == null || mFilterListField == null) return;
        try {
            java.lang.reflect.Field adapterField = ReflectionUtils.getFieldByType(fragment.getClass(), mFilterAdapterClass);
            if (adapterField == null) {
                XposedBridge.log("SeparateGroup: FilterAdapter field not found in ConversationsFragment!");
                return;
            }

            Object filterInstance = adapterField.get(fragment);
            if (filterInstance == null) return;

            java.util.List<?> list = (java.util.List<?>) mFilterListField.get(filterInstance);
            if (list == null) return;

            String filterName = position == 0 ? "CONTACTS_FILTER" : (position == 1 ? "GROUP_FILTER" : null);
            if (filterName == null) return;

            int index = -1;
            for (Object item : list) {
                if (item != null && item.toString().contains(filterName)) {
                    index = list.indexOf(item);
                    break;
                }
            }

            if (index != -1) {
                mMethodSetFilter.invoke(filterInstance, index);
                XposedBridge.log("SeparateGroup: Native filter " + filterName + " applied for tab " + position);
            } else {
                XposedBridge.log("SeparateGroup: Native filter " + filterName + " not found in adapter list.");
            }
        } catch (Throwable t) {
            XposedBridge.log("SeparateGroup: Native filter apply error: " + t);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Separate Group";
    }
}
