package com.waenhancer.xposed.features.customization;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.annotation.SuppressLint;
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
import java.util.ListIterator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
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

    public SeparateGroup(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {

        var cFragClass = XposedHelpers.findClass("com.whatsapp.conversationslist.ConversationsFragment", classLoader);
        var homeActivityClass = WppCore.getHomeActivityClass(classLoader);

        if (!prefs.getBoolean("separategroups", false)) return;

        try {
            // Populate the static tabs list  
            hookTabList(homeActivityClass);

            // Don't try to inject actual tab - just keep static list for reference
            // hookOnResume(homeActivityClass);

            // Setting group icon - DISABLED for now due to crashes
            // hookTabIcon();

            // Setting up fragments - DISABLED for now
            // hookTabInstance(cFragClass);

            // Setting group tab name
            hookTabName();

            // Setting tab count
            hookTabCount();
        } catch (Exception e) {
            XposedBridge.log("SeparateGroup: Error during hook setup: " + e);
            e.printStackTrace();
        }
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
                if (indexTab == tabs.indexOf(CHATS)) {

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
                    if (tabs.contains(CHATS) && tabInstances.containsKey(CHATS)) {
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
                        hooked = XposedBridge.hookMethod(menuAddAndroidX, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (param.args.length > 2) {
                                    int itemId = (int) param.args[1];
                                    if (tabs != null && !tabs.isEmpty() && itemId < tabs.size() && tabs.get(itemId) == GROUPS) {
                                        MenuItem menuItem = (MenuItem) param.getResult();
                                        if (menuItem != null) {
                                            menuItem.setIcon(Utils.getID("home_tab_communities_selector", "drawable"));
                                        }
                                    }
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
                var tabIndex = (int) param.args[0];
                if (tabs == null || tabs.isEmpty() || tabIndex >= tabs.size()) {
                    return;
                }
                var tabId = tabs.get(tabIndex);
                if (tabId == GROUPS) {
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
                if (tabs == null || tabs.isEmpty()) return;
                var tabId = tabs.get((int) param.args[0]).intValue();
                if (tabId == GROUPS || tabId == CHATS) {
                    var convFragment = cFrag.newInstance();
                    param.setResult(convFragment);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (tabs == null || tabs.isEmpty()) return;
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

    private void hookOnResume(@NonNull Class<?> home) throws Exception {
        var onResumeMethod = XposedHelpers.findMethodExact(home, "onResume");
        XposedBridge.hookMethod(onResumeMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (!prefs.getBoolean("separategroups", false)) return;
                    
                    XposedBridge.log("SeparateGroup: onResume called, attempting to inject GROUPS tab");
                    
                    var fieldTabsList = ReflectionUtils.getFieldByExtendType(home, List.class);
                    if (fieldTabsList == null) return;
                    
                    var listObj = fieldTabsList.get(param.thisObject);
                    if (!(listObj instanceof ArrayList)) return;
                    
                    ArrayList<Integer> currentList = (ArrayList<Integer>) listObj;
                    if (!currentList.contains(GROUPS) && currentList.contains(CHATS)) {
                        int insertPos = currentList.indexOf(CHATS) + 1;
                        currentList.add(insertPos, GROUPS);
                        tabs = new ArrayList<>(currentList);
                        XposedBridge.log("SeparateGroup: onResume injected GROUPS tab. New tabs: " + tabs);
                    }
                } catch (Exception e) {
                    XposedBridge.log("SeparateGroup: Exception in onResume: " + e.getMessage());
                }
            }
        });
    }

    private void hookTabList(@NonNull Class<?> home) throws Exception {
        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));
        var fieldTabsList = ReflectionUtils.getFieldByExtendType(home, List.class);
        if (fieldTabsList == null) {
            throw new NullPointerException("fieldTabList is NULL!");
        }
        
        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    var listObj = fieldTabsList.get(param.thisObject);
                    if (!(listObj instanceof List<?> rawList)) {
                        return;
                    }

                    ArrayList<Integer> currentTabs = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof Integer tabId) {
                            currentTabs.add(tabId);
                        }
                    }

                    // Populate static tabs list with GROUPS for reference
                    if (!currentTabs.isEmpty() && currentTabs.contains(CHATS) && !currentTabs.contains(GROUPS)) {
                        tabs = new ArrayList<>(currentTabs);
                        tabs.add(tabs.indexOf(CHATS) + 1, GROUPS);
                        XposedBridge.log("SeparateGroup: Tabs initialized with GROUPS: " + tabs);
                    } else {
                        tabs = new ArrayList<>(currentTabs);
                    }
                } catch (Exception e) {
                    XposedBridge.log("SeparateGroup: Exception in hookTabList: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Wrapper ArrayList that transparently adds GROUPS tab to the list
     * without modifying the underlying list
     */
    public class TabsListWrapper extends ArrayList<Integer> {
        private final ArrayList<?> originalList;
        private final ArrayList<Integer> tabs;
        private final int groupsIndex;

        @SuppressWarnings("unchecked")
        public TabsListWrapper(ArrayList<?> original, ArrayList<Integer> currentTabs) {
            this.originalList = original;
            this.tabs = new ArrayList<>(currentTabs);
            
            // Insert GROUPS at position after CHATS
            if (!this.tabs.isEmpty() && this.tabs.contains(CHATS) && !this.tabs.contains(GROUPS)) {
                this.groupsIndex = this.tabs.indexOf(CHATS) + 1;
                this.tabs.add(this.groupsIndex, GROUPS);
                XposedBridge.log("SeparateGroup: TabsListWrapper created with GROUPS at index " + this.groupsIndex);
            } else {
                this.groupsIndex = -1;
                XposedBridge.log("SeparateGroup: TabsListWrapper created without GROUPS injection");
            }
        }

        @Override
        public int size() {
            return tabs.size();
        }

        @Override
        public Integer get(int index) {
            return tabs.get(index);
        }

        @Override
        public Iterator<Integer> iterator() {
            return tabs.iterator();
        }

        @Override
        public ListIterator<Integer> listIterator() {
            return tabs.listIterator();
        }

        @Override
        public ListIterator<Integer> listIterator(int index) {
            return tabs.listIterator(index);
        }

        @Override
        public List<Integer> subList(int fromIndex, int toIndex) {
            return tabs.subList(fromIndex, toIndex);
        }

        @Override
        public boolean contains(Object o) {
            return tabs.contains(o);
        }

        @Override
        public int indexOf(Object o) {
            return tabs.indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return tabs.lastIndexOf(o);
        }

        @Override
        public boolean isEmpty() {
            return tabs.isEmpty();
        }

        @Override
        public Object[] toArray() {
            return tabs.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return tabs.toArray(a);
        }

        @Override
        public String toString() {
            return tabs.toString();
        }
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
