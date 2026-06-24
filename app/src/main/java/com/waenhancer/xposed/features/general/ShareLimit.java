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

        var shareLimitMethod = Unobfuscator.loadShareLimitMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(shareLimitMethod));
        var shareItemField = Unobfuscator.loadShareMapItemField(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(shareItemField));

        // 1. Existing final-check hook
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

        // 2. New hooks for ContactPickerFragment to bypass the 5-chat forward limit checks dynamically
        try {
            Class<?> fragmentClass = null;
            try {
                fragmentClass = classLoader.loadClass("X.0Ig");
                XposedBridge.log("[SHARE_LIMIT] Successfully loaded target class X.0Ig directly");
            } catch (Throwable t) {
                XposedBridge.log("[SHARE_LIMIT] Failed to load X.0Ig, falling back to ContactPickerFragment: " + t.toString());
                fragmentClass = classLoader.loadClass("com.whatsapp.contact.ui.picker.ContactPickerFragment");
            }
            
            // Find all overloaded versions of A0F (limit check method) and A0G (dialog show method)
            for (Method m : fragmentClass.getDeclaredMethods()) {
                if (m.getName().equals("A0F")) {
                    XposedBridge.log("[SHARE_LIMIT] Hooking A0F method: " + m.toString());
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        private final Map<Field, Map<Object, Object>> originalMaps = new HashMap<>();
                        private final Map<Field, HashMap<Object, Object>> fakeMaps = new HashMap<>();

                        @Override
                        @SuppressWarnings("unchecked")
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                XposedBridge.log("[SHARE_LIMIT] A0F beforeHookedMethod entered");
                                Object fragment = param.thisObject != null ? param.thisObject : (param.args.length > 0 ? param.args[0] : null);
                                if (fragment != null) {
                                    originalMaps.clear();
                                    fakeMaps.clear();
                                    
                                    // Dynamically traverse the class hierarchy to find all Map fields at runtime
                                    Class<?> c = fragment.getClass();
                                    while (c != null && c != Object.class) {
                                        for (Field f : c.getDeclaredFields()) {
                                            if (Map.class.isAssignableFrom(f.getType())) {
                                                try {
                                                    f.setAccessible(true);
                                                    Object val = f.get(fragment);
                                                    if (val instanceof Map) {
                                                        originalMaps.put(f, (Map<Object, Object>) val);
                                                        HashMap<Object, Object> fakeMap = new HashMap<>();
                                                        fakeMaps.put(f, fakeMap);
                                                        f.set(fragment, fakeMap);
                                                        XposedBridge.log("[SHARE_LIMIT] A0F: Mocked Map field: " + f.getName() + " in " + c.getName());
                                                    }
                                                } catch (Throwable t) {
                                                    XposedBridge.log("[SHARE_LIMIT] Error mocking field " + f.getName() + ": " + t.toString());
                                                }
                                            }
                                        }
                                        c = c.getSuperclass();
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[SHARE_LIMIT] Error in A0F beforeHookedMethod: " + t.toString());
                            }
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                XposedBridge.log("[SHARE_LIMIT] A0F afterHookedMethod entered");
                                Object fragment = param.thisObject != null ? param.thisObject : (param.args.length > 0 ? param.args[0] : null);
                                if (fragment != null) {
                                    for (Map.Entry<Field, Map<Object, Object>> entry : originalMaps.entrySet()) {
                                        Field f = entry.getKey();
                                        Map<Object, Object> originalMap = entry.getValue();
                                        HashMap<Object, Object> fakeMap = fakeMaps.get(f);
                                        if (originalMap != null && fakeMap != null) {
                                            originalMap.putAll(fakeMap);
                                            f.set(fragment, originalMap);
                                            XposedBridge.log("[SHARE_LIMIT] A0F: Restored Map field: " + f.getName() + " (new size: " + originalMap.size() + ")");
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[SHARE_LIMIT] Error in A0F afterHookedMethod: " + t.toString());
                            }
                        }
                    });
                } else if (m.getName().equals("A0G")) {
                    XposedBridge.log("[SHARE_LIMIT] Hooking A0G method to suppress dialog: " + m.toString());
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[SHARE_LIMIT] A0G beforeHookedMethod entered (suppressing dialog)");
                            // Set result to null to skip execution of the warning dialog display
                            param.setResult(null);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[SHARE_LIMIT] Failed to setup ContactPickerFragment hooks: " + t.toString());
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Share Limit";
    }
}
