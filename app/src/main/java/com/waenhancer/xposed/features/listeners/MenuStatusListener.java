package com.waenhancer.xposed.features.listeners;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MenuStatusListener extends Feature {

    public static final LinkedHashSet<OnMenuItemStatusListener> menuStatuses = new LinkedHashSet<>();

    public static final ArrayList<FMessageWpp> currentStatusList = new ArrayList<>();

    public static int currentIndex = -1;

    public static LinkedHashSet<OnMenuItemStatusListener> getMenuStatuses() {
        return menuStatuses;
    }

    public static synchronized void registerStatusListener(OnMenuItemStatusListener listener) {
        menuStatuses.removeIf(l -> l.getClass().getName().equals(listener.getClass().getName()));
        menuStatuses.add(listener);
    }

    public static FMessageWpp getFMessageFromStatusData(Object obj) {
        if (obj == null) return null;

        // Try direct FMessage field first
        Field fMessageField = ReflectionUtils.findFieldUsingFilterIfExists(obj.getClass(),
                f -> FMessageWpp.TYPE != null && FMessageWpp.TYPE.isAssignableFrom(f.getType()));
        if (fMessageField != null) {
            Object fMessageObj = ReflectionUtils.getObjectField(fMessageField, obj);
            if (fMessageObj != null) {
                return new FMessageWpp(fMessageObj);
            }
        }

        // Try via FStatus -> FMessage mapper
        try {
            java.lang.reflect.Method mapMethod = Unobfuscator.loadFStatusToFMessage(obj.getClass().getClassLoader());
            Class<?> fStatusClass = mapMethod.getParameterTypes()[0];
            Field fStatusField = ReflectionUtils.findFieldUsingFilterIfExists(obj.getClass(),
                    f -> fStatusClass.isAssignableFrom(f.getType()));
            if (fStatusField != null) {
                Object fStatusObj = fStatusField.get(obj);
                Object fMessageObj = WppCore.getFMessageFromFStatus(fStatusObj);
                if (fMessageObj != null) {
                    return new FMessageWpp(fMessageObj);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public MenuStatusListener(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader);
        var menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader);

        Class<?> statusPlaybackBaseFragmentClass =
                classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment");
        Class<?> statusPlaybackContactFragmentClass =
                classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        Field listStatusField = ReflectionUtils.getFieldByExtendType(
                statusPlaybackContactFragmentClass,
                List.class);

        XposedBridge.hookMethod(menuStatusMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    var fieldObjects = new ArrayList<>();
                    for (Field field : menuStatusMethod.getDeclaringClass().getDeclaredFields()) {
                        Object value = ReflectionUtils.getObjectField(field, param.thisObject);
                        if (value != null) {
                            fieldObjects.add(value);
                        }
                    }

                    Object fragmentInstance;
                    if (param.thisObject != null && statusPlaybackContactFragmentClass.isInstance(param.thisObject)) {
                        fragmentInstance = param.thisObject;
                    } else {
                        fragmentInstance = fieldObjects.stream()
                                .filter(obj -> statusPlaybackBaseFragmentClass.isInstance(obj))
                                .findFirst()
                                .orElse(null);
                    }
                    if (fragmentInstance == null) return;

                    Menu menu;
                    if (param.args.length > 0 && param.args[0] instanceof Menu) {
                        menu = (Menu) param.args[0];
                    } else {
                        var menuManager = fieldObjects.stream().filter(menuManagerClass::isInstance).findFirst().orElse(null);
                        var menuField = ReflectionUtils.getFieldByExtendType(menuManagerClass, Menu.class);
                        menu = menuField == null ? null : (Menu) ReflectionUtils.getObjectField(menuField, menuManager);
                    }
                    if (menu == null) return;

                    int index = (int) XposedHelpers.getObjectField(fragmentInstance, "A00");
                    @SuppressWarnings("unchecked")
                    List<?> listStatus = (List<?>) listStatusField.get(fragmentInstance);
                    if (listStatus == null || listStatus.isEmpty()) return;

                    List<FMessageWpp> fMessageList = new ArrayList<>();
                    for (Object obj : listStatus) {
                        FMessageWpp fMsg = getFMessageFromStatusData(obj);
                        if (fMsg != null) {
                            fMessageList.add(fMsg);
                        }
                    }

                    currentStatusList.clear();
                    currentStatusList.addAll(fMessageList);
                    currentIndex = index;

                    if (index < 0 || index >= fMessageList.size()) return;

                    for (OnMenuItemStatusListener menuStatus : menuStatuses) {
                        var menuItem = menuStatus.addMenu(menu, fMessageList, index);
                        if (menuItem == null) continue;

                        menuItem.setOnMenuItemClickListener(item -> {
                            menuStatus.onClick(item, fragmentInstance, fMessageList, index);
                            return true;
                        });
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[WAE] MenuStatusListener error in hook: " + t);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Menu Status";
    }

    public abstract static class OnMenuItemStatusListener {

        public abstract MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageList, int currentIndex);

        public abstract void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageList, int currentIndex);
    }
}
