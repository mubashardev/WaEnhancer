package com.waenhancer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;

import com.waenhancer.xposed.core.components.FMessageWpp.UserJid;

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
        Method method = Unobfuscator.loadGhostModeMethod(classLoader);
        if (method == null) return;

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // Safeguard: Check if this was triggered by the AlwaysTyping pro engine
                try {
                    if ("true".equals(System.getProperty("com.waex.helper.AlwaysTyping.isEngineTriggering"))) {
                        return; // Let the engine send its simulated composing packets
                    }
                } catch (Throwable ignored) {}

                // Look for JID and composing state in the parameters dynamically
                Object jidObj = null;
                int state = -1; // 0 = typing, 1 = recording, 2 = clear

                for (Object arg : param.args) {
                    if (arg == null) continue;
                    if (arg instanceof Integer) {
                        state = (Integer) arg;
                    } else if (!(arg instanceof Boolean)) {
                        boolean isJid = false;
                        if (UserJid.TYPE_JID != null) {
                            if (UserJid.TYPE_JID.isInstance(arg)) {
                                isJid = true;
                            }
                        }
                        if (!isJid) {
                            String className = arg.getClass().getName();
                            if (className.contains("Jid") || className.contains("jid")) {
                                isJid = true;
                            }
                        }
                        if (!isJid) {
                            Class<?> declClass = method.getDeclaringClass();
                            if (!declClass.isInstance(arg)) {
                                isJid = true;
                            }
                        }
                        if (isJid) {
                            jidObj = arg;
                        }
                    }
                }

                if (jidObj == null) {
                    return; // Cannot resolve recipient, skip blocking to avoid breakages
                }

                // Retrieve contact phone number
                var userJid = new UserJid(jidObj);
                String phone = userJid.getPhoneNumber();
                if (phone == null || phone.isEmpty()) return;

                // Load preferences
                boolean ghostmode = prefs.getBoolean("ghostmode_actual", false);
                boolean ghostmode_t = prefs.getBoolean("ghostmode_t", false);
                boolean ghostmode_r = prefs.getBoolean("ghostmode_r", false);

                // Check custom privacy settings
                JSONObject privacy = CustomPrivacy.getJSON(phone);
                boolean hideTyping = privacy.optBoolean("HideTyping", ghostmode_t) || ghostmode;
                boolean hideRecording = privacy.optBoolean("HideRecording", ghostmode_r) || ghostmode;

                // Block matching state (0 = typing, 1 = recording)
                if ((state == 0 && hideTyping) || (state == 1 && hideRecording)) {
                    param.setResult(null); // Cancel the method execution (block outgoing packet)
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Typing Privacy";
    }
}
