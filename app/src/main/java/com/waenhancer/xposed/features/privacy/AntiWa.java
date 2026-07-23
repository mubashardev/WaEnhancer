package com.waenhancer.xposed.features.privacy;

import android.content.ContentResolver;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class AntiWa extends Feature {
    public AntiWa(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("bootloader_spoofer", false)) return;
        var rootDetector = Unobfuscator.loadRootDetector(classLoader);
        for (var detector : rootDetector) {
            /* Log removed */
            XposedBridge.hookMethod(detector, XC_MethodReplacement.returnConstant(false));
        }
        var settingsGetInt = Settings.Global.class.getDeclaredMethod("getInt", ContentResolver.class, String.class, int.class);
        /* Log removed */
        XposedBridge.hookMethod(settingsGetInt, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var key = (String) param.args[1];
                if (key.equals("adb_enabled")) {
                    param.setResult(0);
                }
            }
        });
        var checkEmulator = Unobfuscator.loadCheckEmulator(classLoader);
        /* Log removed */
        XposedBridge.hookMethod(checkEmulator, XC_MethodReplacement.returnConstant(false));
        // File Check
        var FileConstructor = File.class.getConstructor(String.class);
        XposedBridge.hookMethod(FileConstructor, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var path = (String) param.args[0];
                var fakePath = "/data/fakepath";
                if (path.contains("qemu") || path.contains("superuser")) {
                    param.args[0] = fakePath;
                }

            }
        });

        var checkCustomRom = Unobfuscator.loadCheckCustomRom(classLoader);
        /* Log removed */
        XposedBridge.hookMethod(checkCustomRom, XC_MethodReplacement.returnConstant(false));
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "AntiDetector";
    }
}
