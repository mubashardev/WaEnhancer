package com.waenhancer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class FreezeLastSeen extends Feature {
    public FreezeLastSeen(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var freezeLastSeen = prefs.getBoolean("freezelastseen", false);
        var freezeLastSeenOption = prefs.getBoolean("freeze_last_seen_actual", false);
        var ghostmode = prefs.getBoolean("ghostmode_actual", false);

        if (freezeLastSeen || freezeLastSeenOption || ghostmode) {
            var method = Unobfuscator.loadFreezeSeenMethod(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Freeze Last Seen";
    }

}
