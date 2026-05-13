package com.waenhancer.xposed.features.customization;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.db.MessageStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SeparateGroup extends Feature {

    public static final int CHATS = 200;
    public static final int STATUS = 300;
    public static final int GROUPS = 500;
    public static ArrayList<Integer> tabs = new ArrayList<>();
    public static HashMap<Integer, Object> tabInstances = new HashMap<>();
    private static final ExecutorService COUNT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile long lastUnreadRefreshAt = 0L;
    private static volatile int cachedChatCount = 0;
    private static volatile int cachedGroupCount = 0;
    private static volatile boolean unreadRefreshInFlight = false;

    public SeparateGroup(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        var cFragClass = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);

        if (!prefs.getBoolean("separategroups", false)) return;
        if (!isSupportedVersion()) {
            ;
            return;
        }

        hookTabList();
        hookTabIcon();
        hookTabInstance(cFragClass);
        hookTabName();
        hookTabCount();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Separate Group";
    }

    private boolean isSupportedVersion() {
        try {
            var versionName = Utils.getApplication().getPackageManager()
                    .getPackageInfo(Utils.getApplication().getPackageName(), 0).versionName;
            return isVersionAtMost(versionName, 2, 26, 12);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isVersionAtMost(String versionName, int major, int minor, int patch) {
        if (versionName == null) return true;
        var parts = versionName.split("\\.");
        if (parts.length < 3) return true;
        try {
            int vMajor = Integer.parseInt(parts[0]);
            int vMinor = Integer.parseInt(parts[1]);
            int vPatch = Integer.parseInt(parts[2]);
            if (vMajor != major) return vMajor < major;
            if (vMinor != minor) return vMinor < minor;
            return vPatch <= patch;
        } catch (NumberFormatException ignored) {
            return true;
        }
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
                int chatsIndex = tabs.indexOf(CHATS);
                int groupsIndex = tabs.indexOf(GROUPS);
                if (indexTab != chatsIndex && indexTab != groupsIndex) {
                    return;
                }

                refreshUnreadCountsAsync(false);

                if (indexTab == chatsIndex && tabs.contains(CHATS) && tabInstances.containsKey(CHATS)) {
                    var instance12 = cachedChatCount <= 0
                            ? constructor3.newInstance()
                            : constructor2.newInstance(cachedChatCount);
                    param.args[1] = constructor1.newInstance(instance12);
                    return;
                }

                if (indexTab == groupsIndex && tabs.contains(GROUPS) && tabInstances.containsKey(GROUPS)) {
                    var instance2 = cachedGroupCount <= 0
                            ? constructor3.newInstance()
                            : constructor2.newInstance(cachedGroupCount);
                    param.args[1] = constructor1.newInstance(instance2);
                }
            }
        });
    }

    private void refreshUnreadCountsAsync(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastUnreadRefreshAt < 1500) {
            return;
        }
        if (unreadRefreshInFlight) {
            return;
        }
        unreadRefreshInFlight = true;
        COUNT_EXECUTOR.execute(() -> {
            int chatCount = 0;
            int groupCount = 0;
            try {
                synchronized (SeparateGroup.class) {
                    var db = MessageStore.getInstance().getDatabase();
                    var cursor = db.rawQuery("SELECT * FROM chat WHERE unseen_message_count != 0", null);
                    while (cursor.moveToNext()) {
                        int jid = cursor.getInt(cursor.getColumnIndex("jid_row_id"));
                        int groupType = cursor.getInt(cursor.getColumnIndex("group_type"));
                        int archived = cursor.getInt(cursor.getColumnIndex("archived"));
                        int chatLocked = cursor.getInt(cursor.getColumnIndex("chat_lock"));
                        if (archived != 0 || (groupType != 0 && groupType != 6) || chatLocked != 0) continue;
                        var cursor1 = db.rawQuery("SELECT * FROM jid WHERE _id == ?", new String[]{String.valueOf(jid)});
                        if (!cursor1.moveToFirst()) {
                            cursor1.close();
                            continue;
                        }
                        var server = cursor1.getString(cursor1.getColumnIndex("server"));
                        if ("g.us".equals(server)) {
                            groupCount++;
                        } else {
                            chatCount++;
                        }
                        cursor1.close();
                    }
                    cursor.close();
                }
                cachedChatCount = chatCount;
                cachedGroupCount = groupCount;
                lastUnreadRefreshAt = System.currentTimeMillis();
            } catch (Throwable t) {
                logDebug("SeparateGroup unread refresh failed", t);
            } finally {
                unreadRefreshInFlight = false;
            }
        });
    }

    private void hookTabIcon() throws Exception {
        var iconTabMethod = Unobfuscator.loadIconTabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(iconTabMethod));
        var menuAddAndroidX = Unobfuscator.loadAddMenuAndroidX(classLoader);
        logDebug(menuAddAndroidX);

        XposedBridge.hookMethod(iconTabMethod, new XC_MethodHook() {
            private XC_MethodHook.Unhook hooked;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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
                if (hooked != null) hooked.unhook();
            }
        });
    }

    @SuppressLint("ResourceType")
    private void hookTabName() throws Exception {
        var tabNameMethod = Unobfuscator.loadTabNameMethod(classLoader);
        XposedBridge.hookMethod(tabNameMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var tab = (int) param.args[0];
                if (tab == GROUPS) {
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
                if (param.args[0] instanceof Bundle) {
                    Bundle bundle = (Bundle) param.args[0];
                    var state = bundle.getParcelable("state");
                    if (state == null) return;
                    string = state.toString();
                } else {
                    string = param.args[2].toString();
                }
                var matcher = pattern.matcher(string);
                if (matcher.find()) {
                    var tabId = Integer.parseInt(matcher.group(1));
                    if (tabId == GROUPS || tabId == CHATS) {
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
                var tabId = tabs.get((int) param.args[0]).intValue();
                if (tabId == GROUPS || tabId == CHATS) {
                    var convFragment = cFrag.newInstance();
                    param.setResult(convFragment);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var tabId = tabs.get((int) param.args[0]).intValue();
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
            }
        });

        var fabintMethod = Unobfuscator.loadFabMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(fabintMethod));

        XposedBridge.hookMethod(fabintMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Objects.equals(tabInstances.get(GROUPS), param.thisObject)) {
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
            }
        });
    }

    private List filterChat(Object thiz, List chatsList) {
        var tabChat = tabInstances.get(CHATS);
        var tabGroup = tabInstances.get(GROUPS);
        if (!Objects.equals(tabChat, thiz) && !Objects.equals(tabGroup, thiz)) {
            return chatsList;
        }
        var editableChatList = new ArrayListFilter(Objects.equals(tabGroup, thiz));
        editableChatList.addAll(chatsList);
        return editableChatList;
    }

    private void hookTabList() throws Exception {
        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));

        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                tabs = getArrayListTab(loadTabListField(classLoader));
                if (tabs == null) return;
                if (!tabs.contains(GROUPS)) {
                    tabs.add(tabs.isEmpty() ? 0 : 1, GROUPS);
                }
                refreshUnreadCountsAsync(true);
            }
        });
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
                if (isGroup) {
                    return server.equals("broadcast") || server.equals("g.us");
                }
                return server.equals("s.whatsapp.net") || server.equals("lid");
            }
            return true;
        }
    }

    private Field loadTabListField(ClassLoader classLoader) throws Exception {
        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        var owner = onCreateTabList.getDeclaringClass();
        Field fallback = null;
        for (var cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                if (fallback == null) fallback = field;
                field.setAccessible(true);
                try {
                    var value = field.get(null);
                    if (!(value instanceof List)) continue;
                    List<?> list = (List<?>) value;
                    if (list.isEmpty() || list.stream().allMatch(item -> item instanceof Number)) {
                        return field;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (fallback != null) {
            fallback.setAccessible(true);
            return fallback;
        }
        throw new Exception("Tab list field not found");
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<Integer> getArrayListTab(Field listField) throws Exception {
        var list = (List<Integer>) listField.get(null);
        if (list instanceof ArrayList) {
            return (ArrayList<Integer>) list;
        }
        var tabs = new ArrayList<>(list);
        listField.set(null, tabs);
        return tabs;
    }
}
