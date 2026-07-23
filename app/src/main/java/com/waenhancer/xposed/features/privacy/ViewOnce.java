package com.waenhancer.xposed.features.privacy;


import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ViewOnce extends Feature {

    public ViewOnce(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("viewonce", false)) return;

        var methods = Unobfuscator.loadViewOnceMethod(classLoader);

        for (var method : methods) {
            /* Log removed */
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int returnValue = (int) param.args[0];
                    var fMessage = new FMessageWpp(param.thisObject);
                    if (returnValue == 1 && !fMessage.getKey().isFromMe) {
                        param.args[0] = 0;
                    }
                }
            });
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "View Once";
    }


}