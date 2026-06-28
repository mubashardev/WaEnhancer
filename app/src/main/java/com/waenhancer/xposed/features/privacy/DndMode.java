package com.waenhancer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class DndMode extends Feature {
    public DndMode(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("dndmode_actual", false)) return;
        var dndMethod = Unobfuscator.loadDndModeMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(dndMethod));
        XposedBridge.hookMethod(dndMethod, XC_MethodReplacement.DO_NOTHING);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Dnd Mode";
    }
}
