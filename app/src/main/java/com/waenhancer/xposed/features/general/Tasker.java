package com.waenhancer.xposed.features.general;

import android.content.BroadcastReceiver;
import android.net.Uri;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.utils.TaskerHistoryManager;

import com.waenhancer.xposed.utils.ReflectionUtils;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class Tasker extends Feature {
    private static FMessageWpp fMessage;
    private static boolean taskerEnabled;


    public Tasker(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        taskerEnabled = prefs.getBoolean("tasker", false);
        if (!taskerEnabled) return;
        hookReceiveMessage();
        registerSenderMessage();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Tasker";
    }

    private void registerSenderMessage() {
        IntentFilter filter = new IntentFilter("com.waenhancer.MESSAGE_SENT");
        ContextCompat.registerReceiver(Utils.getApplication(), new SenderMessageBroadcastReceiver(), filter, ContextCompat.RECEIVER_EXPORTED);
    }

    public synchronized static void sendTaskerEvent(String name, String number, String event) {
        if (!taskerEnabled) return;

        Intent intent = new Intent("com.waenhancer.EVENT");
        intent.putExtra("name", name);
        intent.putExtra("number", number);
        intent.putExtra("event", event);
        Utils.getApplication().sendBroadcast(intent);

    }

    public void hookReceiveMessage() throws Throwable {
        var method = Unobfuscator.loadReceiptMethod(classLoader);

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    // 1. Get JID Class and User JID object safely
                    Class<?> jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
                    Object userJidObject = ReflectionUtils.getArg(param.args, jidClass, 0);
                    if (userJidObject == null) return;

                    // 2. Safely find the String types in arguments (positions of message id, receipt type)
                    java.util.List<android.util.Pair<Integer, Class<? extends String>>> strings = 
                            ReflectionUtils.findClassesOfType(((java.lang.reflect.Method) param.method).getParameterTypes(), String.class);
                    if (strings.isEmpty()) return;

                    // 3. Skip "sender" (receipt confirmation sent by us)
                    int msgTypeIdx = strings.get(strings.size() - 1).first;
                    if (msgTypeIdx < param.args.length && "sender".equals(param.args[msgTypeIdx])) {
                        return;
                    }

                    // 4. Extract or build FMessageWpp.Key
                    FMessageWpp.Key keyMessage = null;
                    Object keyObject = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
                    if (keyObject != null) {
                        keyMessage = new FMessageWpp.Key(keyObject);
                    } else if (strings.size() >= 2) {
                        int msgIdIdx = strings.get(0).first;
                        if (msgIdIdx < param.args.length) {
                            String idMessage = (String) param.args[msgIdIdx];
                            FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(userJidObject);
                            keyMessage = new FMessageWpp.Key(idMessage, userJid, false);
                        }
                    }

                    if (keyMessage == null) return;

                    // 5. Get FMessageWpp and extract contents
                    FMessageWpp fMessage = keyMessage.getFMessage();
                    if (fMessage == null) return;

                    FMessageWpp.UserJid userJid = fMessage.getKey().remoteJid;
                    if (userJid.isNull() || userJid.isStatus()) return;

                    String name = WppCore.getContactName(userJid);
                    String number = userJid.getPhoneNumber();
                    String msg = fMessage.getMessageStr();

                    if (TextUtils.isEmpty(msg) || TextUtils.isEmpty(number)) return;

                    // Post to main handler
                    new Handler(Utils.getApplication().getMainLooper()).post(() -> {
                        logEventViaProvider(Utils.getApplication(), "INCOMING", number, msg);

                        Intent intent = new Intent("com.waenhancer.MESSAGE_RECEIVED");
                        intent.putExtra("number", number);
                        intent.putExtra("name", name);
                        intent.putExtra("message", msg);
                        Utils.getApplication().sendBroadcast(intent);
                    });
                } catch (Throwable t) {
                    XposedBridge.log("[WaEnhancerX] Tasker receive message hook error: " + t.getMessage());
                }
            }
        });

    }

    public static class SenderMessageBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            XposedBridge.log("[WaEnhancerX] SenderMessageBroadcastReceiver onReceive triggered");
            
            if (intent.getExtras() != null) {
                for (String key : intent.getExtras().keySet()) {
                    Object val = intent.getExtras().get(key);
                    XposedBridge.log("[WaEnhancerX] Intent Extra: " + key + " = " + val + " (class: " + (val != null ? val.getClass().getName() : "null") + ")");
                }
            }

            String number = null;
            String message = null;

            if (intent.getExtras() != null) {
                Object numObj = intent.getExtras().get("number");
                if (numObj != null) {
                    number = String.valueOf(numObj);
                }
                Object msgObj = intent.getExtras().get("message");
                if (msgObj != null) {
                    message = String.valueOf(msgObj);
                }
            }

            XposedBridge.log("[WaEnhancerX] Resolved inputs -> number: " + number + ", message: " + message);

            if (number == null || message == null) {
                XposedBridge.log("[WaEnhancerX] Aborting: number or message is null!");
                return;
            }

            number = number.replaceAll("\\D", "");
            XposedBridge.log("[WaEnhancerX] Stripped target number: " + number);

            logEventViaProvider(context, "OUTGOING", number, message);

            WppCore.sendMessage(number, message);
        }
    }

    private static void logEventViaProvider(Context context, String type, String targetNumber, String messagePreview) {
        try {
            Uri uri = Uri.parse("content://com.waenhancer.provider");
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("type", type);
            extras.putString("targetNumber", targetNumber);
            extras.putString("messagePreview", messagePreview);
            context.getContentResolver().call(uri, "log_tasker_event", null, extras);
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] Failed to log tasker event via provider: " + t.getMessage());
        }
    }

}
