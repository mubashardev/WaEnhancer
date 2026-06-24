package com.waenhancer.xposed.features.general;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ShareLimit extends Feature {
    public ShareLimit(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("removeforwardlimit", false)) return;

        final int limitValue;
        String limitStr = prefs.getString("customforwardlimit", "9999");
        int parsedLimit = 9999;
        try {
            parsedLimit = Integer.parseInt(limitStr);
        } catch (NumberFormatException ignored) {}
        limitValue = parsedLimit;

        // 1. Hook the final forwarding execution check to ensure the actual sending succeeds
        try {
            var shareLimitMethod = Unobfuscator.loadShareLimitMethod(classLoader);
            var shareItemField = Unobfuscator.loadShareMapItemField(classLoader);
            if (shareLimitMethod != null && shareItemField != null) {
                XposedBridge.hookMethod(
                        shareLimitMethod,
                        new XC_MethodHook() {
                            private HashMap<Object, Object> fakeMap;
                            private HashMap<Object, Object> mMap;

                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                fakeMap = new HashMap<>();
                                mMap = (HashMap<Object, Object>) shareItemField.get(param.thisObject);
                                shareItemField.set(param.thisObject, fakeMap);
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                mMap.putAll(fakeMap);
                                shareItemField.set(param.thisObject, mMap);
                                fakeMap.clear();
                            }
                        });
            }
        } catch (Throwable t) {
            XposedBridge.log("[SHARE_LIMIT] Failed to hook final share limit method: " + t.toString());
        }

        // 2. Hook the multi-selection limit metadata info to dynamically raise the selection limit
        try {
            Class<?> limitInfoClass = Unobfuscator.loadMultiSelectionLimitInfoClass(classLoader);
            if (limitInfoClass != null) {
                XposedBridge.hookAllConstructors(limitInfoClass, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args != null && param.args.length > 0) {
                            if (param.args[0] instanceof Integer) {
                                param.args[0] = limitValue;
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[SHARE_LIMIT] Failed to hook MultiSelectionLimitInfo:");
            XposedBridge.log(t);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Share Limit";
    }
}
