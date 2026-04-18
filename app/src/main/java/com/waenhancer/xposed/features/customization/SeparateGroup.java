package com.waenhancer.xposed.features.customization;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.db.MessageStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.utils.DebugUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeparateGroup extends Feature {

    public static final int CHATS = 200;
    public static final int STATUS = 300;
    //    public static final int CALLS = 400;
//    public static final int COMMUNITY = 600;
    public static final int GROUPS = 500;
    public static ArrayList<Integer> tabs = new ArrayList<>();
    public static HashMap<Integer, Object> tabInstances = new HashMap<>();
    private static volatile int chatTabId = CHATS;
    private static volatile long lastInjectAttemptAt = 0L;
    private static volatile boolean globalResumeHookInstalled = false;
    private static volatile boolean loggedTabListSearchShape = false;
    private static volatile int emptyTabDetections = 0;
    private static volatile boolean statusFallbackMode = false;
    private static volatile int fallbackGroupTabId = STATUS;
    private static volatile boolean objectBackedTabsMode = false;
    private static volatile int objectGroupsIndex = -1;
    private static volatile int objectUpdatesId = -1;
    private static volatile int objectUpdatesNameHitCount = 0;
    private volatile boolean featureEnabled;

    public SeparateGroup(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {

        var cFragClass = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);
        var homeActivityClass = WppCore.getHomeActivityClass(classLoader);

        if (!prefs.getBoolean("separategroups", false)) return;
        featureEnabled = true;
        statusFallbackMode = false;
        fallbackGroupTabId = STATUS;
        objectBackedTabsMode = false;
        objectGroupsIndex = -1;
        objectUpdatesId = -1;
        objectUpdatesNameHitCount = 0;
        XposedBridge.log("SeparateGroup: doHook enabled=true, pref separategroups=" + prefs.getBoolean("separategroups", false));

        // Modifying tab list order
        hookTabList(homeActivityClass);
        hookTabListFallback(homeActivityClass);
        WppCore.addListenerActivity((activity, state) -> {
            if (state != WppCore.ActivityChangeState.ChangeType.RESUMED) return;
            try {
                long now = System.currentTimeMillis();
                if (now - lastInjectAttemptAt < 1200L) return;
                lastInjectAttemptAt = now;
                XposedBridge.log("SeparateGroup: listener resume reached for " + activity.getClass().getName());
                injectGroupsTab(activity, null);
            } catch (Throwable throwable) {
                XposedBridge.log("SeparateGroup: listener inject failed: " + throwable);
            }
        });

        try {
            var currentActivity = WppCore.getCurrentActivity();
            if (currentActivity != null) {
                XposedBridge.log("SeparateGroup: immediate inject on current activity " + currentActivity.getClass().getName());
                injectGroupsTab(currentActivity, null);
            }
        } catch (Throwable throwable) {
            XposedBridge.log("SeparateGroup: immediate inject failed: " + throwable);
        }

        // Setting group icon
        hookTabIcon();

        // Setting up fragments
        hookTabInstance(cFragClass);

        // Setting group tab name
        hookTabName();

        // Setting tab count
        hookTabCount();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Separate Group";
    }

    private void hookTabCount() throws Exception {

        var runMethod = Unobfuscator.loadTabCountMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(runMethod));

        var enableCountMethod = Unobfuscator.loadEnableCountTabMethod(classLoader);
        var constructor1 = Unobfuscator.loadEnableCountTabConstructor1(classLoader);
        var constructor2 = Unobfuscator.loadEnableCountTabConstructor2(classLoader);
        var constructor3 = Unobfuscator.loadEnableCountTabConstructor3(classLoader);
        constructor3.setAccessible(true);

        logDebug(Unobfuscator.getMethodDescriptor(enableCountMethod));
        XposedBridge.hookMethod(enableCountMethod, new XC_MethodHook() {
            @Override
            @SuppressLint({"Range", "Recycle"})
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var indexTab = (int) param.args[2];
                var resolvedChatTabId = resolveChatTabId();
                if (indexTab == tabs.indexOf(resolvedChatTabId)) {

                    var chatCount = 0;
                    var groupCount = 0;
                    synchronized (SeparateGroup.class) {
                        var db = MessageStore.getInstance().getDatabase();
                        var sql = "SELECT * FROM chat WHERE unseen_message_count != 0";
                        var cursor = db.rawQuery(sql, null);
                        while (cursor.moveToNext()) {
                            int jid = cursor.getInt(cursor.getColumnIndex("jid_row_id"));
                            int groupType = cursor.getInt(cursor.getColumnIndex("group_type"));
                            int archived = cursor.getInt(cursor.getColumnIndex("archived"));
                            int chatLocked = cursor.getInt(cursor.getColumnIndex("chat_lock"));
                            if (archived != 0 || (groupType != 0 && groupType != 6) || chatLocked != 0)
                                continue;
                            var sql2 = "SELECT * FROM jid WHERE _id == ?";
                            var cursor1 = db.rawQuery(sql2, new String[]{String.valueOf(jid)});
                            if (!cursor1.moveToFirst()) continue;
                            var server = cursor1.getString(cursor1.getColumnIndex("server"));
                            if (server.equals("g.us")) {
                                groupCount++;
                            } else {
                                chatCount++;
                            }
                            cursor1.close();
                        }
                        cursor.close();
                    }
                    if (tabs.contains(resolvedChatTabId) && tabInstances.containsKey(resolvedChatTabId)) {
                        var instance12 = chatCount <= 0 ? constructor3.newInstance() : constructor2.newInstance(chatCount);
                        var instance22 = constructor1.newInstance(instance12);
                        param.args[1] = instance22;
                    }
                    if (tabs.contains(GROUPS) && tabInstances.containsKey(GROUPS)) {
                        var instance2 = groupCount <= 0 ? constructor3.newInstance() : constructor2.newInstance(groupCount);
                        var instance1 = constructor1.newInstance(instance2);
                        enableCountMethod.invoke(param.thisObject, param.args[0], instance1, tabs.indexOf(GROUPS));
                    }
                }
            }
        });
    }

    private void hookTabIcon() throws Exception {
        var iconTabMethod = Unobfuscator.loadIconTabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(iconTabMethod));
        var menuAddAndroidX = Unobfuscator.loadAddMenuAndroidX(classLoader);
        logDebug(menuAddAndroidX);

        XposedBridge.hookMethod(iconTabMethod, new XC_MethodHook() {

                    private Unhook hooked;

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            var activity = WppCore.getCurrentActivity();
                            injectGroupsTab(activity != null ? activity : param.thisObject, null);
                        } catch (Throwable throwable) {
                            XposedBridge.log("SeparateGroup: icon hook inject failed: " + throwable);
                        }
                        hooked = XposedBridge.hookMethod(menuAddAndroidX, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (param.args.length > 2 && ((int) param.args[1]) == GROUPS) {
                                    MenuItem menuItem = (MenuItem) param.getResult();
                                    menuItem.setIcon(Utils.getID("home_tab_communities_selector", "drawable"));
                                }
                            }
                        });
                    }

                    @SuppressLint("ResourceType")
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (hooked != null) {
                            hooked.unhook();
                        }
                    }
                }
        );
    }

    @SuppressLint("ResourceType")
    private void hookTabName() throws Exception {
        var tabNameMethod = Unobfuscator.loadTabNameMethod(classLoader);
        logDebug("TAB NAME", Unobfuscator.getMethodDescriptor(tabNameMethod));
        XposedBridge.hookMethod(tabNameMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    var activity = WppCore.getCurrentActivity();
                    injectGroupsTab(activity != null ? activity : param.thisObject, null);
                } catch (Throwable throwable) {
                    XposedBridge.log("SeparateGroup: tab name hook inject failed: " + throwable);
                }
                var tab = (int) param.args[0];
                if (objectBackedTabsMode) {
                    if (tab == objectGroupsIndex) {
                        param.setResult(UnobfuscatorCache.getInstance().getString("groups"));
                        return;
                    }
                    if (objectUpdatesId != -1 && tab == objectUpdatesId) {
                        objectUpdatesNameHitCount++;
                        if (objectUpdatesNameHitCount == 1) {
                            param.setResult(UnobfuscatorCache.getInstance().getString("groups"));
                            return;
                        }
                    }
                }
                if (isGroupsTabId(tab)) {
                    param.setResult(UnobfuscatorCache.getInstance().getString("groups"));
                }
            }
        });
    }

    private void hookTabInstance(Class<?> cFrag) throws Exception {
        var getTabMethod = Unobfuscator.loadGetTabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(getTabMethod));

        var methodTabInstance = Unobfuscator.loadTabFragmentMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(methodTabInstance));

        var recreateFragmentMethod = Unobfuscator.loadRecreateFragmentConstructor(classLoader);

        var pattern = Pattern.compile("android:switcher:\\d+:(\\d+)");

        Class<?> FragmentClass = Unobfuscator.loadFragmentClass(classLoader);

        XposedBridge.hookMethod(recreateFragmentMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var string = "";
                if (param.args[0] instanceof Bundle bundle) {
                    var state = bundle.getParcelable("state");
                    if (state == null) return;
                    string = state.toString();
                } else {
                    string = param.args[2].toString();
                }
                var matcher = pattern.matcher(string);
                if (matcher.find()) {
                    var tabId = Integer.parseInt(matcher.group(1));
                    if (isGroupsTabId(tabId) || tabId == resolveChatTabId()) {
                        var fragmentField = ReflectionUtils.getFieldByType(param.thisObject.getClass(), FragmentClass);
                        var convFragment = ReflectionUtils.getObjectField(fragmentField, param.thisObject);
                        tabInstances.remove(tabId);
                        tabInstances.put(tabId, convFragment);
                    }
                }
            }
        });

        XposedBridge.hookMethod(getTabMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (tabs == null || tabs.isEmpty()) return;
                var index = (int) param.args[0];
                if (index < 0 || index >= tabs.size()) return;
                var tabId = tabs.get(index).intValue();
                if (isGroupsTabId(tabId) || tabId == resolveChatTabId()) {
                    var convFragment = cFrag.newInstance();
                    param.setResult(convFragment);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (tabs == null || tabs.isEmpty()) return;
                var index = (int) param.args[0];
                if (index < 0 || index >= tabs.size()) return;
                var tabId = tabs.get(index).intValue();
                tabInstances.remove(tabId);
                tabInstances.put(tabId, param.getResult());
            }
        });

        XposedBridge.hookMethod(methodTabInstance, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var chatsList = (List) param.getResult();
                var resultList = filterChat(param.thisObject, chatsList);
                param.setResult(resultList);

                // Physical devices can skip early Home hooks. Inject again when the fragment list is already alive.
                try {
                    Object activityTarget = WppCore.getCurrentActivity();
                    if (activityTarget == null) {
                        activityTarget = XposedHelpers.callMethod(param.thisObject, "getActivity");
                    }
                    if (activityTarget != null) {
                        injectGroupsTab(activityTarget, null);
                    }
                } catch (Throwable throwable) {
                    XposedBridge.log("SeparateGroup: methodTabInstance inject failed: " + throwable);
                }
            }
        });

        var fabintMethod = Unobfuscator.loadFabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(fabintMethod));

        XposedBridge.hookMethod(fabintMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Objects.equals(tabInstances.get(GROUPS), param.thisObject)
                        || (statusFallbackMode && Objects.equals(tabInstances.get(fallbackGroupTabId), param.thisObject))
                        || Objects.equals(tabInstances.get(STATUS), param.thisObject)) {
                    param.setResult(GROUPS);
                }
            }
        });

        var publishResultsMethod = Unobfuscator.loadGetFiltersMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(publishResultsMethod));

        XposedBridge.hookMethod(publishResultsMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var filters = param.args[1];
                var chatsList = (List) XposedHelpers.getObjectField(filters, "values");
                var baseField = ReflectionUtils.getFieldByExtendType(publishResultsMethod.getDeclaringClass(), BaseAdapter.class);
                if (baseField == null) return;
                var convField = ReflectionUtils.getFieldByType(baseField.getType(), cFrag);
                Object thiz = convField.get(baseField.get(param.thisObject));
                if (thiz == null) return;
                var resultList = filterChat(thiz, chatsList);
                XposedHelpers.setObjectField(filters, "values", resultList);
                XposedHelpers.setIntField(filters, "count", resultList.size());

                // Last-resort reinjection on active chat-list updates.
                try {
                    Object activityTarget = WppCore.getCurrentActivity();
                    if (activityTarget == null) {
                        activityTarget = XposedHelpers.callMethod(thiz, "getActivity");
                    }
                    if (activityTarget != null) {
                        injectGroupsTab(activityTarget, null);
                    }
                } catch (Throwable throwable) {
                    XposedBridge.log("SeparateGroup: publishResults inject failed: " + throwable);
                }
            }
        });
    }

    private List filterChat(Object thiz, List chatsList) {
        var tabChat = tabInstances.get(resolveChatTabId());
        var tabGroup = tabInstances.get(GROUPS);
        if (tabGroup == null && statusFallbackMode) {
            tabGroup = tabInstances.get(fallbackGroupTabId);
            if (tabGroup == null && fallbackGroupTabId != STATUS) {
                tabGroup = tabInstances.get(STATUS);
            }
        }
        if (!Objects.equals(tabChat, thiz) && !Objects.equals(tabGroup, thiz)) {
            return chatsList;
        }
        var editableChatList = new ArrayListFilter(Objects.equals(tabGroup, thiz));
        editableChatList.addAll(chatsList);
        return editableChatList;
    }

    private void hookTabList(@NonNull Class<?> home) throws Exception {
        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));
        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var result = param.getResult();
                if (result instanceof List<?> rawResult && !rawResult.isEmpty()) {
                    boolean allNumeric = true;
                    boolean hasNumericEntry = false;
                    for (Object item : rawResult) {
                        if (item instanceof Integer || item instanceof Number) {
                            hasNumericEntry = true;
                        } else {
                            allNumeric = false;
                        }
                    }
                    if (!allNumeric) {
                        if (hasNumericEntry) {
                            XposedBridge.log("SeparateGroup: Mixed numeric/object tab entries detected; trying object-backed insertion");
                        }

                        ArrayList<Object> mutableObjects = new ArrayList<>(rawResult);
                        boolean alreadyInjected = mutableObjects.size() >= 3 && mutableObjects.get(1) == mutableObjects.get(2);
                        int insertPos = 1;
                        Object updatesTab = mutableObjects.size() > insertPos ? mutableObjects.get(insertPos) : null;
                        if (updatesTab instanceof Number) {
                            updatesTab = null;
                        }
                        if (updatesTab == null) {
                            for (Object candidate : mutableObjects) {
                                if (candidate instanceof Number) continue;
                                updatesTab = candidate;
                                break;
                            }
                        }

                        if (mutableObjects.size() >= 2 && !alreadyInjected && updatesTab != null) {
                            mutableObjects.add(insertPos, updatesTab);
                            param.setResult(mutableObjects);

                            statusFallbackMode = false;
                            fallbackGroupTabId = STATUS;
                            objectBackedTabsMode = true;
                            objectGroupsIndex = insertPos;
                            Integer parsedUpdatesId = extractTabId(updatesTab);
                            objectUpdatesId = parsedUpdatesId != null ? parsedUpdatesId : -1;
                            objectUpdatesNameHitCount = 0;

                            ArrayList<Integer> mappedTabs = new ArrayList<>();
                            for (int i = 0; i < mutableObjects.size(); i++) {
                                Integer parsed = extractTabId(mutableObjects.get(i));
                                mappedTabs.add(parsed != null ? parsed : (1000 + i));
                            }
                            if (objectGroupsIndex >= 0 && objectGroupsIndex < mappedTabs.size()) {
                                mappedTabs.set(objectGroupsIndex, GROUPS);
                            }
                            tabs = mappedTabs;
                            XposedBridge.log("SeparateGroup: Injected object-backed GROUPS slot. tabs=" + tabs);
                        } else {
                            if (alreadyInjected) {
                                XposedBridge.log("SeparateGroup: Object-backed slot already present; reusing existing tab order");
                                statusFallbackMode = false;
                                fallbackGroupTabId = STATUS;
                                objectBackedTabsMode = true;
                                objectGroupsIndex = 1;
                                objectUpdatesId = extractTabId(mutableObjects.get(1)) != null ? extractTabId(mutableObjects.get(1)) : -1;
                                objectUpdatesNameHitCount = 0;

                                ArrayList<Integer> mappedTabs = new ArrayList<>();
                                for (int i = 0; i < mutableObjects.size(); i++) {
                                    Integer parsed = extractTabId(mutableObjects.get(i));
                                    mappedTabs.add(parsed != null ? parsed : (1000 + i));
                                }
                                if (objectGroupsIndex < mappedTabs.size()) {
                                    mappedTabs.set(objectGroupsIndex, GROUPS);
                                }
                                tabs = mappedTabs;
                            } else {
                                XposedBridge.log("SeparateGroup: Skipping method-result object insertion (no object template or too small list)");
                                enableStatusFallback(rawResult, "object insertion unavailable");
                            }
                        }
                        return;
                    }

                    ArrayList<Integer> mutableTabs = new ArrayList<>();
                    for (Object item : rawResult) {
                        if (item instanceof Integer tabId) {
                            mutableTabs.add(tabId);
                        } else if (item instanceof Number number) {
                            mutableTabs.add(number.intValue());
                        }
                    }

                    if (!mutableTabs.isEmpty()) {
                        tabs = mutableTabs;
                        var resolvedChatTabId = resolveChatTabId();
                        if (!tabs.contains(GROUPS)) {
                            if (tabs.contains(resolvedChatTabId)) {
                                tabs.add(tabs.indexOf(resolvedChatTabId) + 1, GROUPS);
                            } else {
                                tabs.add(Math.min(1, tabs.size()), GROUPS);
                            }
                        }
                        XposedBridge.log("SeparateGroup: Computed tabs from method result (read-only). Tabs=" + tabs);
                        return;
                    }
                }

                injectGroupsTab(param.thisObject, null);
            }
        });
    }

    private void enableStatusFallback(List<?> rawTabs, String reason) {
        ArrayList<Integer> parsedTabs = new ArrayList<>();
        for (Object item : rawTabs) {
            Integer parsed = extractTabId(item);
            if (parsed != null) {
                parsedTabs.add(parsed);
            }
        }
        if (parsedTabs.isEmpty()) {
            XposedBridge.log("SeparateGroup: Fallback not enabled (no parseable tab ids). reason=" + reason);
            return;
        }

        tabs = parsedTabs;
        objectBackedTabsMode = false;
        objectGroupsIndex = -1;
        objectUpdatesId = -1;
        objectUpdatesNameHitCount = 0;

        int candidate = STATUS;
        if (!parsedTabs.contains(candidate)) {
            int resolvedChatTabId = resolveChatTabId();
            for (Integer id : parsedTabs) {
                if (id == null) continue;
                if (id == resolvedChatTabId || id == GROUPS) continue;
                candidate = id;
                break;
            }
        }

        statusFallbackMode = true;
        fallbackGroupTabId = candidate;
        XposedBridge.log("SeparateGroup: Enabled fallback mode using tabId=" + fallbackGroupTabId + ", reason=" + reason + ", tabs=" + tabs);
    }

    private void hookTabListFallback(@NonNull Class<?> home) {
        var onCreate = ReflectionUtils.findMethodUsingFilter(home,
                m -> m.getName().equals("onCreate") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Bundle.class);
        if (onCreate != null) {
            XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("SeparateGroup: fallback onCreate reached");
                    injectGroupsTab(param.thisObject, null);
                }
            });
        }

        var onResume = ReflectionUtils.findMethodUsingFilter(home,
                m -> m.getName().equals("onResume") && m.getParameterCount() == 0);
        if (onResume != null) {
            XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("SeparateGroup: fallback onResume reached");
                    injectGroupsTab(param.thisObject, null);
                }
            });
        }

        var onPostResume = ReflectionUtils.findMethodUsingFilter(home,
                m -> m.getName().equals("onPostResume") && m.getParameterCount() == 0);
        if (onPostResume != null) {
            XposedBridge.hookMethod(onPostResume, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("SeparateGroup: fallback onPostResume reached");
                    injectGroupsTab(param.thisObject, null);
                }
            });
        }

        if (!globalResumeHookInstalled) {
            globalResumeHookInstalled = true;
            try {
                XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!featureEnabled) return;
                        Object obj = param.thisObject;
                        if (!(obj instanceof Activity activity)) return;
                        long now = System.currentTimeMillis();
                        if (now - lastInjectAttemptAt < 1200L) return;
                        lastInjectAttemptAt = now;
                        try {
                            XposedBridge.log("SeparateGroup: global onResume fallback for " + activity.getClass().getName());
                            injectGroupsTab(activity, null);
                        } catch (Throwable throwable) {
                            XposedBridge.log("SeparateGroup: global onResume inject failed: " + throwable);
                        }
                    }
                });
            } catch (Throwable throwable) {
                XposedBridge.log("SeparateGroup: failed to install global onResume hook: " + throwable);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void injectGroupsTab(@NonNull Object homeInstance, java.lang.reflect.Field preferredField) {
        if (!featureEnabled) return;

        Object listOwner = homeInstance;
        java.lang.reflect.Field listField = preferredField;
        if (listField == null) {
            var listRef = findTabListRef(homeInstance);
            if (listRef != null) {
                listOwner = listRef.owner;
                listField = listRef.field;
            }
        }

        if (listField == null || listOwner == null) {
            if (!loggedTabListSearchShape) {
                loggedTabListSearchShape = true;
                XposedBridge.log("SeparateGroup: Tab-list search failed for class " + homeInstance.getClass().getName());
            }
            XposedBridge.log("SeparateGroup: No candidate tab list field found in fallback scan");
            return;
        }

        try {
            var listObj = listField.get(listOwner);
            if (!(listObj instanceof List<?> rawList)) {
                XposedBridge.log("SeparateGroup: Tab list field is null or not a List");
                return;
            }

            boolean allNumeric = true;
            for (Object item : rawList) {
                if (!(item instanceof Integer) && !(item instanceof Number)) {
                    allNumeric = false;
                    break;
                }
            }
            if (!rawList.isEmpty() && !allNumeric) {
                XposedBridge.log("SeparateGroup: Skipping field injection (object-backed tabs, preserving runtime type)");
                enableStatusFallback(rawList, "field object-backed list");
                return;
            }

            ArrayList<Integer> mutableTabs = new ArrayList<>();
            for (Object item : rawList) {
                Integer parsed = extractTabId(item);
                if (parsed != null) {
                    mutableTabs.add(parsed);
                }
            }

            // Safety: avoid writing integer lists back to WhatsApp runtime tab objects on builds
            // that may expect object-backed entries and crash with ClassCastException.

            tabs = mutableTabs;
            objectBackedTabsMode = false;
            objectGroupsIndex = -1;
            objectUpdatesId = -1;
            objectUpdatesNameHitCount = 0;
            var resolvedChatTabId = resolveChatTabId();
            XposedBridge.log("SeparateGroup: Current tabs before: " + tabs + " (field=" + listField.getName() + ", chatTabId=" + resolvedChatTabId + ")");

            if (!tabs.isEmpty() && tabs.contains(resolvedChatTabId) && !tabs.contains(GROUPS)) {
                tabs.add(tabs.indexOf(resolvedChatTabId) + 1, GROUPS);
                XposedBridge.log("SeparateGroup: Injected GROUPS tab. Current tabs: " + tabs);
                emptyTabDetections = 0;
            } else if (!tabs.isEmpty() && !tabs.contains(GROUPS)) {
                tabs.add(1 <= tabs.size() ? 1 : 0, GROUPS);
                XposedBridge.log("SeparateGroup: Injected GROUPS tab using fallback position. Current tabs: " + tabs);
                emptyTabDetections = 0;
            } else if (tabs.isEmpty()) {
                XposedBridge.log("SeparateGroup: Skipping injection (list is empty)");
                emptyTabDetections++;
            } else if (!tabs.contains(resolvedChatTabId)) {
                XposedBridge.log("SeparateGroup: Skipping injection (resolved chat tab not found). Current tabs: " + tabs);
            } else {
                XposedBridge.log("SeparateGroup: Skipping injection (GROUPS tab already present). Current tabs: " + tabs);
            }
        } catch (Throwable throwable) {
            XposedBridge.log("SeparateGroup: Injection failed: " + throwable);
        }
    }

    private int resolveChatTabId() {
        if (tabs == null || tabs.isEmpty()) {
            return chatTabId;
        }
        if (tabs.contains(chatTabId)) {
            return chatTabId;
        }
        if (tabs.contains(CHATS)) {
            chatTabId = CHATS;
            return chatTabId;
        }
        for (Integer id : tabs) {
            if (id != null && id != GROUPS && id != STATUS) {
                chatTabId = id;
                return chatTabId;
            }
        }
        chatTabId = tabs.get(0);
        return chatTabId;
    }

    private TabListRef findTabListRef(@NonNull Object root) {
        TabListRef best = findTabListRefOnObject(root, false);
        long bestRank = rankTabListRef(best);

        TabListRef emptyFallback = null;
        Class<?> cursor = root.getClass();
        while (cursor != null && cursor != Object.class) {
            for (var field : cursor.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    var nested = field.get(root);
                    if (nested == null) continue;
                    if (nested == root) continue;
                    var nestedRef = findTabListRefOnObject(nested, false);
                    long nestedRank = rankTabListRef(nestedRef);
                    if (nestedRank > bestRank) {
                        best = nestedRef;
                        bestRank = nestedRank;
                    }
                    if (emptyFallback == null) {
                        emptyFallback = findTabListRefOnObject(nested, true);
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }

        if (best != null) {
            return best;
        }

        if (emptyFallback != null) return emptyFallback;
        return findTabListRefOnObject(root, true);
    }

    private long rankTabListRef(TabListRef ref) {
        if (ref == null) return Long.MIN_VALUE;
        try {
            Object value = ref.field.get(ref.owner);
            if (!(value instanceof List<?> raw)) return Long.MIN_VALUE;
            int numericCount = 0;
            for (Object item : raw) {
                if (extractTabId(item) != null) {
                    numericCount++;
                }
            }
            return ((long) numericCount * 10000L) + raw.size();
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    private TabListRef findTabListRefOnObject(@NonNull Object owner, boolean allowEmptyFallback) {
        Class<?> cursor = owner.getClass();
        TabListRef bestNumericRef = null;
        int bestNumericCount = -1;
        TabListRef bestNonEmptyRef = null;
        int bestNonEmptySize = -1;
        TabListRef emptyFallback = null;
        while (cursor != null && cursor != Object.class) {
            for (var field : cursor.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                try {
                    var value = field.get(owner);
                    if (!(value instanceof List<?> raw)) continue;
                    if (raw.isEmpty()) {
                        if (emptyFallback == null) {
                            emptyFallback = new TabListRef(owner, field);
                        }
                        continue;
                    }

                    int numericCount = 0;
                    for (Object item : raw) {
                        if (extractTabId(item) != null) {
                            numericCount++;
                        }
                    }

                    if (numericCount > bestNumericCount) {
                        bestNumericCount = numericCount;
                        bestNumericRef = new TabListRef(owner, field);
                    }
                    if (raw.size() > bestNonEmptySize) {
                        bestNonEmptySize = raw.size();
                        bestNonEmptyRef = new TabListRef(owner, field);
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }

        if (bestNumericRef != null && bestNumericCount >= 2) {
            return bestNumericRef;
        }
        if (bestNonEmptyRef != null) {
            return bestNonEmptyRef;
        }
        return allowEmptyFallback ? emptyFallback : null;
    }

    private static final class TabListRef {
        final Object owner;
        final java.lang.reflect.Field field;

        TabListRef(Object owner, java.lang.reflect.Field field) {
            this.owner = owner;
            this.field = field;
        }
    }

    private Integer extractTabId(Object item) {
        if (item == null) return null;
        if (item instanceof Integer tabId) return tabId;
        if (item instanceof Number number) return number.intValue();

        try {
            var intMethod = XposedHelpers.findMethodExactIfExists(item.getClass(), "intValue");
            if (intMethod != null) {
                Object value = intMethod.invoke(item);
                if (value instanceof Number number) return number.intValue();
            }
        } catch (Throwable ignored) {
        }

        Class<?> cursor = item.getClass();
        while (cursor != null && cursor != Object.class) {
            for (var field : cursor.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> type = field.getType();
                    if (type == int.class) {
                        int v = field.getInt(item);
                        if (v > 0 && v < 5000) return v;
                    } else if (Number.class.isAssignableFrom(type)) {
                        Object value = field.get(item);
                        if (value instanceof Number number) {
                            int v = number.intValue();
                            if (v > 0 && v < 5000) return v;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }

        return null;
    }

    private boolean isGroupsTabId(int tabId) {
        return tabId == GROUPS || (statusFallbackMode && (tabId == STATUS || tabId == fallbackGroupTabId));
    }


    public static class ArrayListFilter extends ArrayList<Object> {

        private final boolean isGroup;

        public ArrayListFilter(boolean isGroup) {
            this.isGroup = isGroup;
        }


        @Override
        public void add(int index, Object element) {
            if (checkGroup(element)) {
                super.add(index, element);
            }
        }

        @Override
        public boolean add(Object object) {
            if (checkGroup(object)) {
                return super.add(object);
            }
            return true;
        }

        @Override
        public boolean addAll(@NonNull Collection c) {
            for (var chat : c) {
                if (checkGroup(chat)) {
                    super.add(chat);
                }
            }
            return true;
        }

        private boolean checkGroup(Object chat) {
            var jid = getObjectField(chat, "A00");
            if (jid == null) jid = getObjectField(chat, "A01");
            if (jid == null) return true;
            if (XposedHelpers.findMethodExactIfExists(jid.getClass(), "getServer") != null) {
                var server = (String) callMethod(jid, "getServer");
                if (isGroup)
                    return server.equals("broadcast") || server.equals("g.us");
                return server.equals("s.whatsapp.net") || server.equals("lid");
            }
            return true;
        }
    }

}
