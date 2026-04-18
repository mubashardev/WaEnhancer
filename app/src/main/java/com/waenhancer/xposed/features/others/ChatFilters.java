package com.waenhancer.xposed.features.others;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChatFilters extends Feature {
    public ChatFilters(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("separategroups", false)) return;

        var filterAdaperClass = Unobfuscator.loadFilterAdaperClass(classLoader);
        XposedBridge.hookAllConstructors(filterAdaperClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var list = ReflectionUtils.findInstancesOfType(param.args, List.class);
                if (!list.isEmpty()) {
                    var argResult = list.get(0);
                    var newList = new ArrayList<Object>(argResult.second);
                    newList.removeIf(item -> {
                        var name = XposedHelpers.getObjectField(item, "A01");
                        return name == null || "CONTACTS_FILTER".equals(name) || "GROUP_FILTER".equals(name);
                    });
                    param.args[argResult.first] = newList;
                }
            }
        });
        var methodSetFilter = ReflectionUtils.findMethodUsingFilter(filterAdaperClass, method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(int.class));
        if (methodSetFilter == null) {
            XposedBridge.log("ChatFilters: methodSetFilter not found, skipping index guard hook");
            return;
        }

        var listField = ReflectionUtils.getFieldByType(methodSetFilter.getDeclaringClass(), List.class);
        if (listField == null) {
            XposedBridge.log("ChatFilters: list field not found, skipping index guard hook");
            return;
        }
        listField.setAccessible(true);

        XposedBridge.hookMethod(methodSetFilter, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var index = (int) param.args[0];
                var list = (List) listField.get(param.thisObject);
                if (list == null || index >= list.size()) {
                    param.setResult(null);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chat Filters";
    }
}
