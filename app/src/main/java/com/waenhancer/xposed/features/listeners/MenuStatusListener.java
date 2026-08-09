package com.waenhancer.xposed.features.listeners;

import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.StatusItemWaex;
import com.waenhancer.xposed.features.media.StatusDownload;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;

import org.luckypray.dexkit.query.enums.StringMatchType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.utils.Utils;
import java.lang.reflect.Method;

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
            Method mapMethod = Unobfuscator.loadFStatusToFMessage(obj.getClass().getClassLoader());
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

        Class<?> statusPlaybackBaseFragmentClass;
        Class<?> statusPlaybackContactFragmentClass;

        try {
            statusPlaybackBaseFragmentClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "StatusPlaybackBaseFragment");
        } catch (Throwable t) {
            statusPlaybackBaseFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment");
        }

        try {
            statusPlaybackContactFragmentClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "StatusPlaybackContactFragment");
        } catch (Throwable t) {
            statusPlaybackContactFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        }

        final Class<?> baseFragClass = statusPlaybackBaseFragmentClass;
        final Class<?> contactFragClass = statusPlaybackContactFragmentClass;

        Field listStatusField = ReflectionUtils.getFieldByExtendType(
                contactFragClass,
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
                    if (param.thisObject != null && contactFragClass.isInstance(param.thisObject)) {
                        fragmentInstance = param.thisObject;
                    } else {
                        fragmentInstance = fieldObjects.stream()
                                .filter(obj -> baseFragClass != null && baseFragClass.isInstance(obj))
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
                    List<?> listStatus = listStatusField != null ? (List<?>) listStatusField.get(fragmentInstance) : null;
                    XposedBridge.log("[WAEX] MenuStatusListener hook triggered! index: " + index + ", listStatus size: " + (listStatus != null ? listStatus.size() : "null"));
                    if (listStatus == null || listStatus.isEmpty()) return;

                    List<StatusItemWaex> statusItemList = new ArrayList<>();
                    List<FMessageWpp> fMessageList = new ArrayList<>();
                    for (Object obj : listStatus) {
                        StatusItemWaex statusItem = StatusItemWaex.from(obj);
                        if (statusItem != null) {
                            statusItemList.add(statusItem);
                            FMessageWpp fMsg = statusItem.getFMessage();
                            if (fMsg != null) {
                                fMessageList.add(fMsg);
                            }
                        }
                    }

                    XposedBridge.log("[WAEX] MenuStatusListener: parsed " + statusItemList.size() + " statusItems, " + fMessageList.size() + " fMessages");

                    currentStatusList.clear();
                    currentStatusList.addAll(fMessageList);
                    currentIndex = index;
                    StatusDownload.activeStatusObj = listStatus.get(index);

                    if (index < 0 || index >= statusItemList.size()) {
                        XposedBridge.log("[WAEX] MenuStatusListener: index " + index + " out of bounds for statusItemList size " + statusItemList.size());
                        return;
                    }

                    StatusItemWaex currentStatusItem = statusItemList.get(index);
                    XposedBridge.log("[WAEX] Current status item: isFromMe=" + currentStatusItem.isFromMe() + ", isMedia=" + currentStatusItem.isMediaFile() + ", mediaFile=" + currentStatusItem.getMediaFile());

                    SubMenu waeSubMenu = null;
                    for (OnMenuItemStatusListener menuStatus : menuStatuses) {
                        if (waeSubMenu == null) {
                            String waeTitle = "WaEnhancerX";
                            try {
                                String moduleTitle = FeatureLoader.getModuleString(Utils.getApplication(), R.string.app_name, "WaEnhancerX");
                                if (moduleTitle != null && !moduleTitle.isEmpty()) {
                                    waeTitle = moduleTitle;
                                }
                            } catch (Exception ignored) {}

                            waeSubMenu = menu.addSubMenu(0, 0x7EAD0012, 0, waeTitle);
                            Drawable waeIcon = DesignUtils.getDrawableByName("ic_settings");
                            if (waeIcon != null) {
                                waeIcon.setTint(0xff8696a0);
                                waeSubMenu.getItem().setIcon(waeIcon);
                            }
                        }

                        var menuItem = menuStatus.addMenu(waeSubMenu, currentStatusItem, fMessageList, index);
                        if (menuItem == null) {
                            XposedBridge.log("[WAEX] listener " + menuStatus.getClass().getSimpleName() + " returned null menuItem");
                            continue;
                        }

                        menuItem.setOnMenuItemClickListener(item -> {
                            menuStatus.onClick(item, fragmentInstance, currentStatusItem, fMessageList, index);
                            return true;
                        });
                    }
                    
                    if (waeSubMenu != null && !waeSubMenu.hasVisibleItems()) {
                        menu.removeItem(0x7EAD0012);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] MenuStatusListener error in hook: " + t);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Menu Status";
    }

    public interface OnMenuItemStatusListener {

        MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageList, int currentIndex);

        void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageList, int currentIndex);

        default MenuItem addMenu(Menu menu, StatusItemWaex currentItem, List<FMessageWpp> fMessageList, int currentIndex) {
            return addMenu(menu, fMessageList, currentIndex);
        }

        default void onClick(MenuItem item, Object fragmentInstance, StatusItemWaex currentItem, List<FMessageWpp> fMessageList, int currentIndex) {
            onClick(item, fragmentInstance, fMessageList, currentIndex);
        }
    }
}