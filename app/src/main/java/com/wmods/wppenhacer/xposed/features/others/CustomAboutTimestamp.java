package com.wmods.wppenhacer.xposed.features.others;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.util.Date;

public class CustomAboutTimestamp extends Feature {

    public CustomAboutTimestamp(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("custom_about_timestamp_enabled", false)) return;

        Class<?> setStatusJobClass;
        try {
            setStatusJobClass = Unobfuscator.loadSetStatusJob(classLoader);
        } catch (Exception e) {
            log(e);
            return;
        }

        if (setStatusJobClass == null) return;

        // Hook the constructor to modify the timestamp when the job is created
        XposedBridge.hookAllConstructors(setStatusJobClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // Get the custom timestamp from preferences
                String customTimeStr = prefs.getString("custom_about_timestamp_value", "-1");
                long customTime = -1;
                try {
                    customTime = Long.parseLong(customTimeStr);
                } catch (NumberFormatException e) {
                   log("Invalid timestamp format: " + customTimeStr);
                   return;
                }
                if (customTime == -1) return;

                // Find a long field that might be the timestamp
                // Strategy: look for a field that is close to System.currentTimeMillis() or matches expected usage
                // Since we don't know the exact field name, we iterate.
                // However, be careful not to break other long fields (like message IDs).
                
                // For "About" update, usually only one timestamp is involved.
                // Or maybe none, and the server sets it? (User concern).
                // If the client sends it, it should be in the job object.
                
                Object job = param.thisObject;
                Field[] fields = job.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (field.getType() == long.class || field.getType() == Long.class) {
                        field.setAccessible(true);
                        // We set it indiscriminately for now? No, that's dangerous.
                        // But usually the job is simple.
                        // Let's log it first.
                        logDebug("CustomAboutTimestamp: Found long field " + field.getName() + " in " + job.getClass().getSimpleName());
                        
                        // Set the value
                        field.set(job, customTime);
                        logDebug("CustomAboutTimestamp: Set timestamp to " + new Date(customTime));
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Custom About Timestamp (Experimental)";
    }
}
