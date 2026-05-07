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
        XposedBridge.log("[WAE] handleLoadPackage: " + lpparam.packageName + " (process: " + lpparam.processName + ")");
        var packageName = lpparam.packageName;
        var classLoader = lpparam.classLoader;

        if (packageName.equals(BuildConfig.APPLICATION_ID)) {
            XposedBridge.log("[WAE] Hooking module's own process: " + packageName);
            XposedHelpers.findAndHookMethod("com.waenhancer.utils.ModuleStatus", lpparam.classLoader, "isModuleActive", XC_MethodReplacement.returnConstant(true));
            XposedHelpers.findAndHookMethod(PreferenceManager.class.getName(), lpparam.classLoader, "getDefaultSharedPreferencesMode", XC_MethodReplacement.returnConstant(ContextWrapper.MODE_WORLD_READABLE));
            return;
        }

        XposedBridge.log("[WAE] Checking if target is WhatsApp or Business");

        AntiUpdater.hookSession(lpparam);

        Patch.handleLoadPackage(lpparam, getPref());

        ScopeHook.hook(lpparam);

        //  AndroidPermissions.hook(lpparam); in tests
        boolean isWpp = packageName.equals(FeatureLoader.PACKAGE_WPP);
        boolean isBusiness = packageName.equals(FeatureLoader.PACKAGE_BUSINESS);
        boolean isOriginal = App.isOriginalPackage();

        XposedBridge.log("[WAE] isWpp: " + isWpp + ", isBusiness: " + isBusiness + ", isOriginal: " + isOriginal);

        if ((isWpp && isOriginal) || isBusiness) {
            XposedBridge.log("[WAE] Target verified. Starting FeatureLoader...");

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
                    XposedBridge.log("[WAE] Logging hook setup failed: " + t.getMessage());
                }
            } else {
                XposedBridge.log("[WAE] Logging hooks skipped (logging_enabled=false)");
            }

            try {
                FeatureLoader.start(classLoader, getPref(), lpparam.appInfo.sourceDir);
                XposedBridge.log("[WAE] FeatureLoader.start completed successfully");
            } catch (Throwable t) {
                XposedBridge.log("[WAE] CRITICAL ERROR in FeatureLoader.start: " + t.getMessage());
                XposedBridge.log(t);
            }

            installGlobalEnvironmentHooks(lpparam);
            disableSecureFlag();
        }
    }

    private void installGlobalEnvironmentHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Resources.getText and getLayout to translate module resource IDs
        // to host-mapped IDs when embedded fragments inflate their layouts.
        // Only these two methods need hooking — getString/getDrawable/getColor
        // are NOT hooked globally because both WhatsApp and the module use the
        // same 0x7f prefix, causing fatal ID collisions.
        final ThreadLocal<Boolean> inResourceHook = new ThreadLocal<>();
        XC_MethodHook resourceHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int id = (int) param.args[0];
                // Fast path: skip host resources and zero IDs
                if (id <= 0 || (id & 0xFF000000) != 0x7F000000) return;

                // Fast path: skip if we know it's not one of our resources
                if (!XResManager.validModuleIds.isEmpty() && !XResManager.validModuleIds.contains(id)) return;

                if (Boolean.TRUE.equals(inResourceHook.get())) return;

                inResourceHook.set(true);
                try {
                    int translatedId = XResManager.getHostId(id);
                    if (translatedId != id) {
                        param.args[0] = translatedId;
                    }
                } finally {
                    inResourceHook.remove();
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(android.content.res.Resources.class, "getText", int.class, resourceHook);
        } catch (Throwable t) {
            XposedBridge.log("[WAE] Failed to hook Resources.getText: " + t.getMessage());
        }
        try {
            XposedHelpers.findAndHookMethod(android.content.res.Resources.class, "getLayout", int.class, resourceHook);
        } catch (Throwable t) {
            XposedBridge.log("[WAE] Failed to hook Resources.getLayout: " + t.getMessage());
        }

        XposedBridge.log("[WAE] Global resource translation hooks installed.");
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
                XposedBridge.log("[WAE] Unable to hook Log.println_native: " + t.getMessage());
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

        // Map everything in background to avoid splash screen delay
        new Thread(() -> mapAllResources(resparam)).start();
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
            XposedBridge.log("[WAE] Valid module IDs populated: " + XResManager.validModuleIds.size());
        } catch (Throwable t) {
            XposedBridge.log("[WAE] Error populating valid IDs: " + t.getMessage());
        }
    }

    private void mapAllResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        try {
            Class<?> rClass = com.waenhancer.R.class;
            for (Class<?> subClass : rClass.getDeclaredClasses()) {
                String type = subClass.getSimpleName();
                for (java.lang.reflect.Field field : subClass.getFields()) {
                    try {
                        int originalId = field.getInt(null);
                        // getHostId will perform the mapping and caching
                        int hostId = XResManager.getHostId(originalId);
                        
                        // Also update ResId fields for backward compatibility
                        try {
                            Class<?> resIdSubClass = Class.forName("com.waenhancer.xposed.utils.ResId$" + type);
                            java.lang.reflect.Field resIdField = resIdSubClass.getField(field.getName());
                            resIdField.set(null, hostId);
                        } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                }
            }
            XposedBridge.log("[WAE] Background resource mapping complete. Total: " + XResManager.moduleToHostIdMap.size());
        } catch (Throwable t) {
            XposedBridge.log("[WAE] Resource mapping background error: " + t.getMessage());
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
