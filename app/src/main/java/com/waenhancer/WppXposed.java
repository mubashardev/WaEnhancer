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
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import android.content.SharedPreferences;
import com.waenhancer.xposed.spoofer.HookBL;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class WppXposed implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {

    private static XSharedPreferences pref;
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
            
            // Hook Application.onCreate in manager app to save active Xposed version locally
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.Application", lpparam.classLoader,
                        "onCreate", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Context context = (Context) param.thisObject;
                                try {
                                    int apiVersion = XposedBridge.getXposedVersion();
                                    Class<?> prefMgr = Class.forName("androidx.preference.PreferenceManager", true, context.getClassLoader());
                                    Method getPrefs = prefMgr.getMethod("getDefaultSharedPreferences", Context.class);
                                    SharedPreferences localPrefs = (SharedPreferences) getPrefs.invoke(null, context);
                                    localPrefs.edit().putInt("active_xposed_api_version", apiVersion).commit();
                                    /* Log removed */
                                } catch (Throwable t) {
                                    XposedBridge.log("[WAEX] Failed to save active Xposed API version in manager: " + t.toString());
                                }
                            }
                        });
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to hook Application.onCreate in manager: " + t.toString());
            }
            
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

            // Inject Bootloader Spoofer in the manager app itself for live verification
            if (getPref().getBoolean("bootloader_spoofer", false)) {
                try {
                    HookBL.hook(lpparam.classLoader, getPref());
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] Failed to hook Bootloader Spoofer in settings app: " + t.getMessage());
                }
            }
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
                for (Field field : subClass.getFields()) {
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
                for (Field field : subClass.getDeclaredFields()) {
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
                                    Field resIdField = resIdSubClass.getField(field.getName());
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

        // Write a system property that persists until reboot.
        // Reading debug.* properties requires NO permission from any app,
        // so WaEnhancer can detect LSPosed even when it's not in module scope.
        // Writing debug.* properties IS allowed from the zygote/root context
        // that initZygote runs under.
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getMethod("set", String.class, String.class);
            set.invoke(null, "debug.waenhancer.lsposed", "1");
            try {
                int apiVersion = XposedBridge.getXposedVersion();
                set.invoke(null, "debug.waenhancer.lsposed.api", String.valueOf(apiVersion));
                /* Log removed */
            } catch (Throwable t2) {
                XposedBridge.log("[WAEX] LSPosed marker written: debug.waenhancer.lsposed=1 (Failed to write API version: " + t2.getMessage() + ")");
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to write LSPosed marker: " + t.getMessage());
        }
    }


    public void disableSecureFlag() {
        XposedHelpers.findAndHookMethod(Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = (int) param.args[0] & ~WindowManager.LayoutParams.FLAG_SECURE;
                param.args[1] = (int) param.args[1] & ~WindowManager.LayoutParams.FLAG_SECURE;
            }
        });

        XposedHelpers.findAndHookMethod(Window.class, "addFlags", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = (int) param.args[0] & ~WindowManager.LayoutParams.FLAG_SECURE;
                if ((int) param.args[0] == 0) {
                    param.setResult(null);
                }
            }
        });
    }

}