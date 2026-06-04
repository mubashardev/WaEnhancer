package com.waenhancer;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.XModuleResources;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.waenhancer.activities.MainActivity;
import com.waenhancer.xposed.AntiUpdater;
import com.waenhancer.xposed.bridge.ScopeHook;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.downgrade.Patch;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.XResManager;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WppXposed implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {

    private static XSharedPreferences pref;
    private static volatile boolean logHookInstalled = false;
    private static final ThreadLocal<Boolean> forwardingLog = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private String MODULE_PATH;
    public static XC_InitPackageResources.InitPackageResourcesParam ResParam;



    @NonNull
    public static XSharedPreferences getPref() {
        if (pref == null) {
            pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + "_preferences");
            pref.makeWorldReadable();
            pref.reload();
        }
        return pref;
    }

    @SuppressLint("WorldReadableFiles")
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (Utils.DEBUG) {
            ;
        }
        var packageName = lpparam.packageName;
        var classLoader = lpparam.classLoader;

        if (packageName.equals(BuildConfig.APPLICATION_ID)) {
            if (Utils.DEBUG) {
                ;
            }
            XposedHelpers.findAndHookMethod("com.waenhancer.utils.ModuleStatus", lpparam.classLoader, "isModuleActive", XC_MethodReplacement.returnConstant(true));
            
            // Bypass the Android 7.0+ SecurityException when using MODE_WORLD_READABLE in the module settings app process
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ContextImpl", lpparam.classLoader,
                        "checkMode", int.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(null);
                            }
                        });
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to hook ContextImpl.checkMode: " + t.getMessage());
            }

            // Make default SharedPreferences world-readable so XSharedPreferences
            // can read them from WhatsApp's process without ContentProvider IPC.
            // This is the critical hook that the competitor uses and we were missing.
            @SuppressWarnings("deprecation")
            int worldReadable = ContextWrapper.MODE_WORLD_READABLE;
            XposedHelpers.findAndHookMethod(
                    PreferenceManager.class.getName(), lpparam.classLoader,
                    "getDefaultSharedPreferencesMode",
                    XC_MethodReplacement.returnConstant(worldReadable));
            return;
        }

        if (Utils.DEBUG) {
            ;
        }

        AntiUpdater.hookSession(lpparam);

        Patch.handleLoadPackage(lpparam, getPref());

        ScopeHook.hook(lpparam);

        //  AndroidPermissions.hook(lpparam); in tests
        boolean isWpp = packageName.equals(FeatureLoader.PACKAGE_WPP);
        boolean isBusiness = packageName.equals(FeatureLoader.PACKAGE_BUSINESS);
        boolean isOriginal = App.isOriginalPackage();

        if (Utils.DEBUG) {
            ;
        }

        if ((isWpp && isOriginal) || isBusiness) {
            if (Utils.DEBUG) {
                ;
            }

            // Initialize module resources early
            XResManager.moduleResources = XModuleResources.createInstance(MODULE_PATH, null);
            
            // Populate valid IDs immediately for hooks to work
            populateValidIds();

            // Only install logging hooks when user has explicitly enabled logging.
            // The println_native hook intercepts EVERY Log call in WhatsApp and does
            // cross-process IPC per call, which causes severe lag when always active.
            if (getPref().getBoolean("logging_enabled", false)) {
                try {
                    setupLogging(lpparam);
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] Logging hook setup failed: " + t.getMessage());
                }
            }

            try {
                FeatureLoader.start(classLoader, getPref(), lpparam.appInfo.sourceDir);
                if (Utils.DEBUG) {
                    ;
                }
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] CRITICAL ERROR in FeatureLoader.start: " + t.getMessage());
                XposedBridge.log(t);
            }

            disableSecureFlag();
        }
    }

    private void setupLogging(XC_LoadPackage.LoadPackageParam lpparam) {
        if (logHookInstalled) {
            return;
        }
        synchronized (WppXposed.class) {
            if (logHookInstalled) {
                return;
            }

            XposedHelpers.findAndHookMethod(XposedBridge.class, "log", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (Boolean.TRUE.equals(forwardingLog.get())) {
                        return;
                    }
                    var appContext = com.waenhancer.xposed.utils.Utils.getApplication();
                    if (appContext == null) {
                        return;
                    }

                    String logMessage = (String) param.args[0];
                    if (logMessage == null || logMessage.isEmpty()) {
                        return;
                    }

                    forwardLog(appContext, lpparam.packageName, "[logcat][I][LSPosed] " + logMessage);
                }
            });

            XposedHelpers.findAndHookMethod(XposedBridge.class, "log", Throwable.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (Boolean.TRUE.equals(forwardingLog.get())) {
                        return;
                    }
                    var appContext = com.waenhancer.xposed.utils.Utils.getApplication();
                    if (appContext == null) {
                        return;
                    }

                    Throwable t = (Throwable) param.args[0];
                    if (t == null) {
                        return;
                    }

                    forwardLog(appContext, lpparam.packageName,
                            "[logcat][E][LSPosed] " + android.util.Log.getStackTraceString(t));
                }
            });

            try {
                XposedHelpers.findAndHookMethod(android.util.Log.class, "println_native",
                        int.class, int.class, String.class, String.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (Boolean.TRUE.equals(forwardingLog.get())) {
                                    return;
                               }
                                var appContext = com.waenhancer.xposed.utils.Utils.getApplication();
                                if (appContext == null) {
                                    return;
                                }

                                int priority = (int) param.args[1];
                                String tag = (String) param.args[2];
                                String message = (String) param.args[3];
                                if (message == null || message.isEmpty()) {
                                    return;
                                }

                                String normalizedTag = (tag == null || tag.isEmpty()) ? "NO_TAG" : tag;
                                String line = "[logcat][" + priorityToString(priority) + "][" + normalizedTag + "] "
                                        + message;
                                forwardLog(appContext, lpparam.packageName, line);
                            }
                        });
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Unable to hook Log.println_native: " + t.getMessage());
            }
            logHookInstalled = true;
        }
    }

    private static void forwardLog(Context appContext, String packageName, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String normalized = message.length() > 6000
                ? message.substring(0, 6000) + " ... [truncated]"
                : message;
        forwardingLog.set(Boolean.TRUE);
        try {
            com.waenhancer.utils.LogManager.addLogViaProvider(appContext, packageName, normalized);
        } finally {
            forwardingLog.set(Boolean.FALSE);
        }
    }

    private static String priorityToString(int priority) {
        switch (priority) {
            case android.util.Log.VERBOSE:
                return "V";
            case android.util.Log.DEBUG:
                return "D";
            case android.util.Log.INFO:
                return "I";
            case android.util.Log.WARN:
                return "W";
            case android.util.Log.ERROR:
                return "E";
            case android.util.Log.ASSERT:
                return "A";
            default:
                return String.valueOf(priority);
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        var packageName = resparam.packageName;

        if (!packageName.equals(FeatureLoader.PACKAGE_WPP) && !packageName.equals(FeatureLoader.PACKAGE_BUSINESS))
            return;

        XResManager.moduleResources = XModuleResources.createInstance(MODULE_PATH, resparam.res);
        XResManager.hostResources = resparam.res;
        ResParam = resparam;

        // Populate valid IDs list immediately (fast reflection)
        populateValidIds();

        // Map everything synchronously on the main thread to ensure consistency
        mapAllResources(resparam);
    }

    private void populateValidIds() {
        try {
            Class<?> rClass = com.waenhancer.R.class;
            for (Class<?> subClass : rClass.getDeclaredClasses()) {
                for (java.lang.reflect.Field field : subClass.getFields()) {
                    try {
                        XResManager.validModuleIds.add(field.getInt(null));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error populating valid IDs: " + t.getMessage());
        }
    }

    private void mapAllResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        try {
            Class<?> rClass = com.waenhancer.R.class;
            int count = 0;
            for (Class<?> subClass : rClass.getDeclaredClasses()) {
                String type = subClass.getSimpleName();
                if (type.equals("id") || type.equals("styleable") || type.equals("attr")) {
                    continue;
                }
                for (java.lang.reflect.Field field : subClass.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        if (field.getType() == int.class) {
                            int originalId = field.getInt(null);
                            if (originalId >= 0x70000000 && originalId <= 0x7FFFFFFF) {
                                int hostId = XResManager.getHostId(originalId);
                                
                                // Update the R class field directly to the mapped ID
                                field.set(null, hostId);
                                count++;

                                // Also update ResId fields for backward compatibility
                                try {
                                    Class<?> resIdSubClass = Class.forName("com.waenhancer.xposed.utils.ResId$" + type);
                                    java.lang.reflect.Field resIdField = resIdSubClass.getField(field.getName());
                                    resIdField.setAccessible(true);
                                    resIdField.set(null, hostId);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Resource mapping error: " + t.getMessage());
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }


    public void disableSecureFlag() {
        XposedHelpers.findAndHookMethod(android.view.Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = (int) param.args[0] & ~android.view.WindowManager.LayoutParams.FLAG_SECURE;
                param.args[1] = (int) param.args[1] & ~android.view.WindowManager.LayoutParams.FLAG_SECURE;
            }
        });

        XposedHelpers.findAndHookMethod(android.view.Window.class, "addFlags", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = (int) param.args[0] & ~android.view.WindowManager.LayoutParams.FLAG_SECURE;
                if ((int) param.args[0] == 0) {
                    param.setResult(null);
                }
            }
        });
    }

}
