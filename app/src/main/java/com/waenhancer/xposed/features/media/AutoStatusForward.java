package com.waenhancer.xposed.features.media;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.xposed.utils.ReflectionUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import com.waenhancer.model.StatusForwardRule;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

/**
 * AutoStatusForward
 *
 * Hooks Unobfuscator.loadReceiptMethod() to intercept fully constructed
 * incoming messages from the cache with their fields (text, quoted context)
 * populated.
 */
public class AutoStatusForward extends Feature {
    private static Field quotedContextFieldCache = null;
    private static boolean scannedForQuoted = false;
    private static final int MAX_RESOLVE_ATTEMPTS = 4;

    public AutoStatusForward(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        Method notificationMethod = Unobfuscator.loadNotificationMethod(classLoader);
        /* Log removed */
        XposedBridge.hookMethod(notificationMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                reloadPrefs();
                if (!prefs.getBoolean("auto_status_forward", false))
                    return;

                List<Object> candidates = new ArrayList<>();
                Object result = param.getResult();
                candidates.addAll(extractNotificationCandidates(result));
                candidates.addAll(extractNotificationCandidates(param.thisObject));
                if (param.args != null) {
                    for (Object arg : param.args) {
                        candidates.addAll(extractNotificationCandidates(arg));
                    }
                }
                candidates = dedupeCandidates(candidates);

                if (candidates.isEmpty()) {
                    /* Log removed */
                    return;
                }

                /* Log removed */
                for (Object candidate : candidates) {
                    handleNotificationCandidate(candidate);
                }
            }
        });

        // Auto click send for media statuses
        try {
            XposedHelpers.findAndHookMethod("com.whatsapp.mediacomposer.ui.app.MediaComposerActivity", classLoader,
                    "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity activity = (Activity) param.thisObject;
                            Intent intent = activity.getIntent();
                            if (intent != null && intent.getBooleanExtra("auto_forward_status", false)) {
                                /* Log removed */
                                // Hide the UI to make it silent
                                activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                                activity.getWindow().setDimAmount(0f);
                                
                                autoClickAndFinish(activity, "send", 15); // Try 15 times (approx 3 seconds)
                            }
                        }
                    });


        } catch (Exception e) {
            log("AutoStatusForward MediaComposerActivity hook failed " + e.getMessage());
        }
    }

    private void autoClickAndFinish(Activity activity, String buttonIdStr, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            /* Log removed */
            activity.finishAndRemoveTask();
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                int sendId = activity.getResources().getIdentifier(buttonIdStr, "id", activity.getPackageName());
                View sendBtn = sendId != 0 ? activity.findViewById(sendId) : null;
                
                if (sendBtn == null) {
                    sendBtn = findSendButtonByIconOrClass(activity.getWindow().getDecorView());
                }

                if (sendBtn != null && sendBtn.isEnabled() && sendBtn.getVisibility() == View.VISIBLE) {
                    sendBtn.performClick();
                    /* Log removed */
                    
                    // Finish silently after a brief delay to ensure the click is processed
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!activity.isFinishing()) {
                            activity.finishAndRemoveTask();
                            activity.overridePendingTransition(0, 0);
                        }
                    }, 300);
                } else {
                    // Retry
                    autoClickAndFinish(activity, buttonIdStr, attemptsLeft - 1);
                }
            } catch (Exception e) {
                log("AutoStatusForward - autoClickAndFinish err: " + e);
            }
        }, 200);
    }

    private View findSendButtonByIconOrClass(View root) {
        if (root == null) return null;
        if (root instanceof ImageView || root.getClass().getName().contains("FloatingActionButton")) {
            // Check content description or resource ID name if possible
            CharSequence desc = root.getContentDescription();
            if (desc != null && desc.toString().toLowerCase().contains("send")) {
                return root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findSendButtonByIconOrClass(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void handleNotificationCandidate(Object fMessageRaw) {
        if (fMessageRaw == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                FMessageWpp fMessage = new FMessageWpp(fMessageRaw);
                FMessageWpp.Key key = fMessage.getKey();
                if (key == null || key.isFromMe || key.messageID == null) {
                    return;
                }

                String processedKey = "last_processed_" + key.messageID;
                String inFlightKey = "forwarding_" + key.messageID;
                if (WppCore.getPrivBoolean(processedKey, false) || WppCore.getPrivBoolean(inFlightKey, false)) {
                    /* Log removed */
                    return;
                }

                /* Log removed */
                handleFMessage(fMessageRaw, 0);
            } catch (Throwable t) {
                log("AutoStatusForward – notification path err: " + t);
            }
        }, 1000L);
    }

    private void handleFMessage(Object fMessageObj, int attempt) {
        FMessageWpp incoming;
        try {
            incoming = new FMessageWpp(fMessageObj);
        } catch (Throwable t) {
            return;
        }

        FMessageWpp.Key key = incoming.getKey();
        if (key == null || key.isFromMe)
            return;
        FMessageWpp.UserJid senderJid = key.remoteJid;
        if (senderJid == null || senderJid.isNull() || senderJid.isGroup())
            return;

        String phone = senderJid.getPhoneNumber();
        String text = incoming.getMessageStr();
        /* Log removed */

        // 1. Rule matching
        StatusForwardRule matchedRule = matchesRules(text);
        if (matchedRule == null) {
            /* Log removed */
            return;
        }

        // 2. Check for quoted status reply
        FMessageWpp quotedStatus = extractQuotedStatus(fMessageObj);
        if (quotedStatus == null) {
            if (attempt < MAX_RESOLVE_ATTEMPTS) {
                /* Log removed */
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> handleFMessage(fMessageObj, attempt + 1),
                        300L * (attempt + 1));
            } else {
                /* Log removed */
            }
            return;
        }

        logReplyDiagnostics(incoming, quotedStatus);

        // 3. Verify rule toggles against status type
        boolean isVoiceNote = quotedStatus.getMediaType() == 2;
        boolean isMedia = quotedStatus.isMediaFile();

        if (isVoiceNote && !matchedRule.applyVoice) {
            /* Log removed */
            return;
        } else if (isMedia && !isVoiceNote && !matchedRule.applyMedia) {
            /* Log removed */
            return;
        } else if (!isMedia && !isVoiceNote && !matchedRule.applyText) {
            /* Log removed */
            return;
        }

        final FMessageWpp statusToSend = quotedStatus;
        final String replyText = text;
        final FMessageWpp.UserJid recipient = senderJid;
        final Object incomingMsg = fMessageObj;
        final String processedKey = "last_processed_" + key.messageID;
        final String inFlightKey = "forwarding_" + key.messageID;

        WppCore.setPrivBoolean(inFlightKey, true);

        CompletableFuture.runAsync(() -> {
            try {
                forwardStatus(statusToSend, replyText, recipient, incomingMsg);
                WppCore.setPrivBoolean(processedKey, true);
            } catch (Throwable t) {
                log("AutoStatusForward – forward err: " + t);
            } finally {
                WppCore.removePrivKey(inFlightKey);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Safely and quickly extract quoted status without crashing
    // -------------------------------------------------------------------------

    private FMessageWpp extractQuotedStatus(Object fMessageObj) {
        /* Log removed */
        try {
            if (!scannedForQuoted)
                scanForQuotedContextInfo(fMessageObj.getClass());

            Object rawQuotedKey = null;

            // Strategy 1 (Methods returning Key) was too unreliable due to obfuscation
            // matching the main getKey() method.

            // Strategy 2: ContextInfo field containing Key inside it
            if (rawQuotedKey == null && quotedContextFieldCache != null) {
                try {
                    Object contextInfo = quotedContextFieldCache.get(fMessageObj);
                    /* Log removed */
                    if (contextInfo != null) {
                        for (Field f : getAllFields(contextInfo.getClass())) {
                            if (FMessageWpp.Key.TYPE.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                rawQuotedKey = f.get(contextInfo);
                                /* Log removed */
                                if (rawQuotedKey != null) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    log("AutoStatusForward – extractQuotedStatus Strategy 2 err: " + t);
                }
            } else if (rawQuotedKey == null) {
                /* Log removed */
            }

            // Since it's fully populated, we can also use getOriginalKey() API
            if (rawQuotedKey == null) {
                FMessageWpp wrapper = new FMessageWpp(fMessageObj);
                FMessageWpp.Key originalKey = wrapper.getOriginalKey(); // Context key
                /* Log removed */
                if (originalKey != null) {
                    rawQuotedKey = originalKey.thisObject;
                }
            }

            if (rawQuotedKey == null) {
                /* Log removed */
                return null;
            }

            FMessageWpp.Key msgKey = new FMessageWpp.Key(rawQuotedKey);
            /* Log removed */
            if (msgKey.remoteJid != null) {
                String phone = msgKey.remoteJid.getPhoneNumber();
                if (phone != null && (phone.equals("status") || phone.contains("broadcast"))) {
                    Object q = WppCore.getFMessageFromKey(rawQuotedKey);
                    if (q != null)
                        return new FMessageWpp(q);
                    /* Log removed */
                    return new FMessageWpp(fMessageObj); // fallback to trigger forwarding anyway
                }
            }
        } catch (Throwable t) {
            log("AutoStatusForward – extractQuotedStatus err: " + t);
        }
        return null;
    }

    private synchronized void scanForQuotedContextInfo(Class<?> fMessageClass) {
        if (scannedForQuoted)
            return;
        List<Field> fields = getAllFields(fMessageClass);
        List<Method> methods = getAllMethods(fMessageClass);

        // 1. Scanning for method returning Key (Strategy 1) removed because it
        // erroneously found the obfuscated `getKey()` method.

        // 2. Find field holding ContextInfo (looks like an object with a Key inside it)
        for (Field f : fields) {
            Class<?> type = f.getType();
            if (type.isPrimitive() || type.getName().startsWith("java.") || type.getName().startsWith("android."))
                continue;
            boolean hasKey = false;
            for (Field nestedF : getAllFields(type)) {
                if (FMessageWpp.Key.TYPE.isAssignableFrom(nestedF.getType())) {
                    hasKey = true;
                    break;
                }
            }
            if (hasKey) {
                /* Log removed */
                quotedContextFieldCache = f;
                f.setAccessible(true);
                break;
            }
        }
        scannedForQuoted = true;
    }

    private List<Field> getAllFields(Class<?> c) {
        List<Field> list = new ArrayList<>();
        while (c != null && c != Object.class) {
            list.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }
        return list;
    }

    private List<Method> getAllMethods(Class<?> c) {
        List<Method> list = new ArrayList<>();
        while (c != null && c != Object.class) {
            list.addAll(Arrays.asList(c.getDeclaredMethods()));
            c = c.getSuperclass();
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Rule matching
    // -------------------------------------------------------------------------

    private StatusForwardRule matchesRules(String messageText) {
        String json = prefs.getString("auto_status_forward_rules_json", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                /* Log removed */
                return new StatusForwardRule("contains", "", true, true, false);
            }
            if (TextUtils.isEmpty(messageText))
                return null;

            String lower = messageText.trim().toLowerCase();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ruleObj = arr.getJSONObject(i);
                String type = ruleObj.optString("type", "contains").toLowerCase();
                String ruleText = ruleObj.optString("text", "").trim().toLowerCase();

                boolean applyText = ruleObj.optBoolean("applyText", true);
                boolean applyMedia = ruleObj.optBoolean("applyMedia", true);
                boolean applyVoice = ruleObj.optBoolean("applyVoice", false);

                if (ruleText.isEmpty())
                    continue;

                if ("equals".equals(type) && lower.equals(ruleText)) {
                    /* Log removed */
                    return new StatusForwardRule(type, ruleText, applyText, applyMedia,
                            applyVoice);
                }
                if (!"equals".equals(type) && lower.contains(ruleText)) {
                    /* Log removed */
                    return new StatusForwardRule(type, ruleText, applyText, applyMedia,
                            applyVoice);
                }
            }
        } catch (Exception e) {
            log("AutoStatusForward – matchesRules exception: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Forward
    // -------------------------------------------------------------------------

    private void forwardStatus(FMessageWpp statusMsg, String replyText, FMessageWpp.UserJid recipientJid,
            Object incomingMsg)
            throws Exception {
        String jidRaw = recipientJid.getPhoneRawString();
        if (jidRaw == null)
            return;
        String name = WppCore.getContactName(recipientJid);
        if (TextUtils.isEmpty(name))
            name = recipientJid.getPhoneNumber();

        boolean isVoiceNote = statusMsg.getMediaType() == 2;
        /* Log removed */

        if (statusMsg.isMediaFile() && !isVoiceNote) {
            forwardMediaStatus(statusMsg, jidRaw);
        } else {
            // Forward the actual status text content if available, else use reply text.
            // For voice notes, indicate it's a voice status since we can't send the audio
            // file natively yet.
            String statusText;
            if (isVoiceNote) {
                statusText = "[Voice Status reply]";
            } else {
                statusText = statusMsg.getMessageStr();
                if (TextUtils.isEmpty(statusText)) {
                    statusText = replyText;
                }
            }
            forwardTextStatus(statusText, recipientJid, name);
        }
    }

    private void forwardTextStatus(String text, FMessageWpp.UserJid recipient, String contactName) {
        try {
            // Try to send via WA notification RemoteInput (matched by contact display name)
            boolean sent = WppCore.sendMessageViaNotification(contactName, text);
            if (sent) {
                Utils.showToast("✅ Message sent automatically", Toast.LENGTH_SHORT);
            } else {
                // Fallback: try via JID-based method
                Object rawJidObj = recipient.phoneJid != null ? recipient.phoneJid : recipient.userJid;
                if (rawJidObj != null) {
                    WppCore.sendMessage(rawJidObj, text);
                } else {
                    Utils.showToast("⚠️ Could not forward: no WA notification for " + contactName, Toast.LENGTH_LONG);
                    /* Log removed */
                }
            }
        } catch (Exception e) {
            log("AutoStatusForward - forwardTextStatus err: " + e);
        }
    }

    private void forwardMediaStatus(FMessageWpp status, String jidRaw) {
        var file = status.getMediaFile();
        if (file == null || !file.exists()) {
            // Utils.showToast("⚠️ Status media not cached.", Toast.LENGTH_LONG);
            /* Log removed */
            return;
        }
        try {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(Utils.getApplication(),
                        Utils.getApplication().getPackageName() + ".fileprovider", file);
            } catch (Exception e) {
                uri = Uri.fromFile(file);
            }
            Class<?> cls = findMediaComposerClass();
            Intent intent = new Intent();
            intent.setClassName(Utils.getApplication().getPackageName(), cls.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton(jidRaw)));
            intent.putExtra(Intent.EXTRA_STREAM, new ArrayList<>(Collections.singleton(uri)));
            String caption = status.getMessageStr();
            if (!TextUtils.isEmpty(caption))
                intent.putExtra(Intent.EXTRA_TEXT, caption);
            intent.putExtra("auto_forward_status", true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            /* Log removed */
            Utils.getApplication().startActivity(intent);
        } catch (Exception e) {
            log("AutoStatusForward - forwardMediaStatus err: " + e);
        }
    }

    private List<Object> extractNotificationCandidates(Object root) {
        ArrayList<Object> matches = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectFMessageCandidates(root, matches, visited, 0);
        return matches;
    }

    private List<Object> dedupeCandidates(List<Object> input) {
        ArrayList<Object> out = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object item : input) {
            if (item == null || seen.contains(item)) {
                continue;
            }
            seen.add(item);
            out.add(item);
        }
        return out;
    }

    private void collectFMessageCandidates(Object value, List<Object> out, Set<Object> visited, int depth) {
        if (value == null || depth > 3 || visited.contains(value)) {
            return;
        }
        visited.add(value);

        try {
            if (FMessageWpp.TYPE.isInstance(value)) {
                out.add(value);
                return;
            }
        } catch (Throwable ignored) {
        }

        Class<?> clazz = value.getClass();
        if (clazz.isArray()) {
            int len = Array.getLength(value);
            for (int i = 0; i < len && i < 10; i++) {
                collectFMessageCandidates(Array.get(value, i), out, visited, depth + 1);
            }
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            int i = 0;
            for (Object item : iterable) {
                if (i++ >= 10) {
                    break;
                }
                collectFMessageCandidates(item, out, visited, depth + 1);
            }
            return;
        }

        if (clazz.isPrimitive() || clazz.getName().startsWith("java.") || clazz.getName().startsWith("android.")) {
            return;
        }

        for (Field field : getAllFields(clazz)) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                continue;
            }
            try {
                field.setAccessible(true);
                collectFMessageCandidates(field.get(value), out, visited, depth + 1);
            } catch (Throwable ignored) {
            }
        }
    }

    private void logReplyDiagnostics(FMessageWpp incoming, FMessageWpp quotedStatus) {
        FMessageWpp.Key replyKey = incoming.getKey();
        FMessageWpp.Key statusKey = quotedStatus.getKey();
        /* Log removed */
    }

    private String describeStatusType(FMessageWpp message) {
        if (message == null) return "unknown";
        if (message.getMediaType() == 2) return "voice";
        if (message.isMediaFile()) return "media";
        return "text";
    }

    private String safeText(String text) {
        if (TextUtils.isEmpty(text)) return "<empty>";
        String normalized = text.replace('\n', ' ').trim();
        if (normalized.length() > 120) {
            return normalized.substring(0, 120) + "...";
        }
        return normalized;
    }

    private String safeJid(FMessageWpp.UserJid userJid) {
        if (userJid == null || userJid.isNull()) return "null";
        String raw = userJid.getPhoneRawString();
        return TextUtils.isEmpty(raw) ? String.valueOf(userJid.getPhoneNumber()) : raw;
    }

    private String describeObject(Object obj) {
        if (obj == null) {
            return "null";
        }
        Class<?> clazz = obj.getClass();
        if (clazz.isArray()) {
            return clazz.getComponentType().getName() + "[]";
        }
        return clazz.getName();
    }

    private Class<?> findMediaComposerClass() throws Exception {
        try {
            return Unobfuscator.getClassByName("MediaComposerActivity", classLoader);
        } catch (Exception ignored) {
        }
        for (String c : new String[] { "com.whatsapp.mediacomposer.MediaComposerActivity",
                "com.whatsapp.compose.MediaComposerActivity" }) {
            try {
                return classLoader.loadClass(c);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new Exception("MediaComposerActivity not found");
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Auto Status Forward";
    }
}