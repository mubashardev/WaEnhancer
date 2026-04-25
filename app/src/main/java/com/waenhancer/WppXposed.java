package com.waenhancer;

import android.annotation.SuppressLint;
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
import com.waenhancer.xposed.utils.ResId;
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
        var packageName = lpparam.packageName;
        var classLoader = lpparam.classLoader;

        if (packageName.equals(BuildConfig.APPLICATION_ID)) {
            XposedHelpers.findAndHookMethod(MainActivity.class.getName(), lpparam.classLoader, "isXposedEnabled", XC_MethodReplacement.returnConstant(true));
            XposedHelpers.findAndHookMethod(PreferenceManager.class.getName(), lpparam.classLoader, "getDefaultSharedPreferencesMode", XC_MethodReplacement.returnConstant(ContextWrapper.MODE_WORLD_READABLE));
            return;
        }

        AntiUpdater.hookSession(lpparam);

        Patch.handleLoadPackage(lpparam, getPref());

        ScopeHook.hook(lpparam);

        //  AndroidPermissions.hook(lpparam); in tests
        if ((packageName.equals(FeatureLoader.PACKAGE_WPP) && App.isOriginalPackage()) || packageName.equals(FeatureLoader.PACKAGE_BUSINESS)) {
            XposedBridge.log("[•] This package: " + lpparam.packageName);

            // Load features
            FeatureLoader.start(classLoader, getPref(), lpparam.appInfo.sourceDir);

            disableSecureFlag();
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        var packageName = resparam.packageName;

        if (!packageName.equals(FeatureLoader.PACKAGE_WPP) && !packageName.equals(FeatureLoader.PACKAGE_BUSINESS))
            return;

        XResManager.moduleResources = XModuleResources.createInstance(MODULE_PATH, resparam.res);
        ResParam = resparam;

        for (var field : ResId.string.class.getFields()) {
            var field1 = R.string.class.getField(field.getName());
            field.set(null, resparam.res.addResource(XResManager.moduleResources, field1.getInt(null)));
        }

        for (var field : ResId.array.class.getFields()) {
            var field1 = R.array.class.getField(field.getName());
            field.set(null, resparam.res.addResource(XResManager.moduleResources, field1.getInt(null)));
        }

        for (var field : ResId.drawable.class.getFields()) {
            var field1 = R.drawable.class.getField(field.getName());
            field.set(null, resparam.res.addResource(XResManager.moduleResources, field1.getInt(null)));
        }

        for (var field : ResId.xml.class.getFields()) {
            var field1 = R.xml.class.getField(field.getName());
            field.set(null, resparam.res.addResource(XResManager.moduleResources, field1.getInt(null)));
        }

        for (var field : R.style.class.getFields()) {
            try {
                int id = resparam.res.addResource(XResManager.moduleResources, field.getInt(null));
                try {
                    var resIdField = ResId.style.class.getField(field.getName());
                    resIdField.set(null, id);
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Exception e) {
                // XposedBridge.log("[WaEnhancer] Failed to inject style " + field.getName() + ": " + e.getMessage());
            }
        }

        for (var field : R.attr.class.getFields()) {
            try {
                int id = resparam.res.addResource(XResManager.moduleResources, field.getInt(null));
                try {
                    var resIdField = ResId.attr.class.getField(field.getName());
                    resIdField.set(null, id);
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Exception e) {
            }
        }

        for (var field : R.layout.class.getFields()) {
            try {
                int id = resparam.res.addResource(XResManager.moduleResources, field.getInt(null));
                try {
                    var resIdField = ResId.layout.class.getField(field.getName());
                    resIdField.set(null, id);
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Exception e) {
            }
        }

        for (var field : R.color.class.getFields()) {
            try {
                int id = resparam.res.addResource(XResManager.moduleResources, field.getInt(null));
                try {
                    var resIdField = ResId.color.class.getField(field.getName());
                    resIdField.set(null, id);
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Exception e) {
            }
        }

        for (var field : R.dimen.class.getFields()) {
            try {
                int id = resparam.res.addResource(XResManager.moduleResources, field.getInt(null));
                try {
                    var resIdField = ResId.dimen.class.getField(field.getName());
                    resIdField.set(null, id);
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
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
