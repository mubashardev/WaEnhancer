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
import de.robv.android.xposed.XposedHelpers;

public class Tasker extends Feature {
    private static FMessageWpp fMessage;
    private static boolean taskerEnabled;
    // Timestamp of last successful notification-reply send, used for deduplication
    static volatile long lastNotificationReplyTime = 0;


    public Tasker(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        taskerEnabled = prefs.getBoolean("tasker", false);
        if (!taskerEnabled) return;
        hookReceiveMessage();
        registerSenderMessage();
        hookConversationActivity();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Tasker";
    }

    private void registerSenderMessage() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.waenhancer.MESSAGE_SENT");
        filter.addAction("com.waenhancer.MESSAGE_SENT_INTERNAL");
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
        private static String lastProcessedNumber = null;
        private static String lastProcessedMessage = null;
        private static long lastProcessedTime = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            ;
            
            if (intent.getExtras() != null) {
                for (String key : intent.getExtras().keySet()) {
                    Object val = intent.getExtras().get(key);
                    ;
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

            ;

            if (number == null || message == null) {
                ;
                return;
            }

            number = number.replaceAll("\\D", "");
            ;

            // Time-based deduplication
            long now = System.currentTimeMillis();
            if (Objects.equals(number, lastProcessedNumber) && Objects.equals(message, lastProcessedMessage) && (now - lastProcessedTime < 2000)) {
                ;
                return;
            }
            lastProcessedNumber = number;
            lastProcessedMessage = message;
            lastProcessedTime = now;

            logEventViaProvider(context, "OUTGOING", number, message);

            boolean sent = WppCore.sendMessage(number, message);
            if (sent) {
                // Mark successful notification-reply so the Conversation hook can skip
                lastNotificationReplyTime = System.currentTimeMillis();
                ;
            } else {
                ;
                try {
                    Intent activityIntent = new Intent();
                    activityIntent.setClassName(context.getPackageName(), "com.whatsapp.Conversation");
                    String jid = number.contains("@") ? number : number + "@s.whatsapp.net";
                    activityIntent.putExtra("jid", jid);
                    activityIntent.putExtra("wae_auto_send_message", message);
                    activityIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        | Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    );
                    context.startActivity(activityIntent);
                } catch (Exception e) {
                    XposedBridge.log("[WaEnhancerX] Failed to launch Conversation fallback: " + e);
                }
            }
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

    private void hookConversationActivity() {
        try {
            XposedHelpers.findAndHookMethod("com.whatsapp.Conversation", classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final android.app.Activity activity = (android.app.Activity) param.thisObject;
                    android.content.Intent intent = activity.getIntent();
                    if (intent != null && intent.hasExtra("wae_auto_send_message")) {
                        final String msgToSend = intent.getStringExtra("wae_auto_send_message");
                        ;
                        
                        // Make the activity invisible
                        activity.overridePendingTransition(0, 0);
                        activity.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                        activity.getWindow().setDimAmount(0f);
                        
                        // Proceed with auto-type-and-send
                        autoSendTextAndFinish(activity, msgToSend, 15);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[WaEnhancerX] Failed to hook Conversation onCreate: " + t.getMessage());
        }
    }

    private static void finishActivitySilently(android.app.Activity activity) {
        if (activity.isFinishing()) return;
        try {
            activity.moveTaskToBack(true);
        } catch (Exception ignored) {}
        activity.finishAndRemoveTask();
        activity.overridePendingTransition(0, 0);
    }

    private static void autoSendTextAndFinish(final android.app.Activity activity, final String message, final int attemptsLeft) {
        if (attemptsLeft <= 0) {
            ;
            try {
                dumpViewHierarchy(activity.getWindow().getDecorView(), 0);
            } catch (Exception ignored) {}
            finishActivitySilently(activity);
            return;
        }

        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (activity.isFinishing()) return;
                    android.view.View decorView = activity.getWindow().getDecorView();
                    
                    // 1. Check for blocking dialogs (common for new numbers)
                    // If there's an "OK" or "Block" or "Add" button, try to bypass it if possible
                    // For now, we'll just log it and try to find the input anyway
                    
                    // 2. Find message entry EditText
                    final android.widget.EditText inputField = findMessageInput(decorView);
                    if (inputField == null) {
                        ;
                        autoSendTextAndFinish(activity, message, attemptsLeft - 1);
                        return;
                    }

                    // Set text and focus
                    inputField.setText(message);
                    inputField.setSelection(message.length());

                    // 3. Find and click send button
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (activity.isFinishing()) return;
                                android.view.View sendBtn = findSendButton(activity, decorView);
                                if (sendBtn != null && sendBtn.isEnabled() && sendBtn.getVisibility() == android.view.View.VISIBLE) {
                                    sendBtn.performClick();
                                    ;
                                    
                                    new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            finishActivitySilently(activity);
                                        }
                                    }, 300);
                                } else {
                                    ;
                                    autoSendTextAndFinish(activity, message, attemptsLeft - 1);
                                }
                            } catch (Exception e) {
                                XposedBridge.log("[WaEnhancerX] Headless send error: " + e);
                                autoSendTextAndFinish(activity, message, attemptsLeft - 1);
                            }
                        }
                    }, 300); // Increased delay for send button

                } catch (Exception e) {
                    XposedBridge.log("[WaEnhancerX] Headless send loop error: " + e);
                    autoSendTextAndFinish(activity, message, attemptsLeft - 1);
                }
            }
        }, 400); // Increased initial delay
    }

    private static android.widget.EditText findMessageInput(android.view.View root) {
        if (root == null) return null;
        if (root instanceof android.widget.EditText) {
            android.widget.EditText et = (android.widget.EditText) root;
            // Check for hint or content description to ensure it's the message field
            CharSequence hint = et.getHint();
            if (hint != null && (hint.toString().toLowerCase().contains("message") || hint.toString().toLowerCase().contains("type"))) {
                return et;
            }
            // Fallback: if it's the only EditText, it's likely the one
            return et;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.EditText found = findMessageInput(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static android.view.View findSendButton(android.app.Activity activity, android.view.View root) {
        if (root == null) return null;
        try {
            // Try standard ID first
            int sendId = activity.getResources().getIdentifier("send", "id", activity.getPackageName());
            if (sendId != 0) {
                android.view.View v = activity.findViewById(sendId);
                if (v != null && v.getVisibility() == android.view.View.VISIBLE) {
                    return v;
                }
            }
        } catch (Exception ignored) {}

        // Fallback to searching by description or ID name
        return findSendButtonRecursive(root);
    }

    private static android.view.View findSendButtonRecursive(android.view.View root) {
        if (root == null) return null;
        try {
            CharSequence desc = root.getContentDescription();
            if (desc != null) {
                String d = desc.toString().toLowerCase();
                if (d.contains("send") || d.contains("submit")) return root;
            }
            if (root.getId() != android.view.View.NO_ID) {
                String idName = root.getResources().getResourceEntryName(root.getId());
                if (idName != null && idName.toLowerCase().contains("send")) return root;
            }
        } catch (Exception ignored) {}
        
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findSendButtonRecursive(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    // Helper to log the entire view hierarchy if we can't find elements
    private static void dumpViewHierarchy(android.view.View root, int depth) {
        if (root == null || depth > 20) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(root.getClass().getSimpleName());
        if (root.getId() != android.view.View.NO_ID) {
            try {
                sb.append(" id=").append(root.getResources().getResourceEntryName(root.getId()));
            } catch (Exception ignored) {}
        }
        if (root.getContentDescription() != null) {
            sb.append(" desc=").append(root.getContentDescription());
        }
        if (root instanceof android.widget.TextView) {
            sb.append(" text=").append(((android.widget.TextView) root).getText());
        }
        ;

        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViewHierarchy(group.getChildAt(i), depth + 1);
            }
        }
    }

}


