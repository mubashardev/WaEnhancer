package com.waenhancer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class TypingPrivacy extends Feature {

    public TypingPrivacy(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        // Obfuscated method hooks for Ghost Mode and Always Typing are now handled
        // dynamically via the composing manager Handler in AlwaysTyping.java.
        XposedBridge.log("[WAEX] TypingPrivacy: Hooking deferred to AlwaysTyping Handler hook.");
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Typing Privacy";
    }
}
