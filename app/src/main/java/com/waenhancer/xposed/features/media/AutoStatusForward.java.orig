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
        log("AutoStatusForward – hooking notification method: " + notificationMethod);
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
                    log("AutoStatusForward – notification hook found no FMessage candidates"
                            + " [thisObject=" + describeObject(param.thisObject)
                            + ", resultType=" + describeObject(result)
                            + ", argCount=" + (param.args != null ? param.args.length : 0) + "]");
                    return;
                }

                log("AutoStatusForward – notification hook extracted " + candidates.size()
                        + " candidate message(s)"
                        + " [thisObject=" + describeObject(param.thisObject)
                        + ", resultType=" + describeObject(result)
                        + ", argCount=" + (param.args != null ? param.args.length : 0) + "]");
                for (Object candidate : candidates) {
                    handleNotificationCandidate(candidate);
                }
            }
        });

        // Auto click send for media statuses
        try {
            XposedHelpers.findAndHookMethod("com.whatsapp.mediacomposer.ui.app.MediaComposerActivity", classLoader,
                    "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            android.app.Activity activity = (android.app.Activity) param.thisObject;
                            android.content.Intent intent = activity.getIntent();
                            if (intent != null && intent.getBooleanExtra("auto_forward_status", false)) {
                                log("AutoStatusForward - auto_forward_status=true, will auto click send");
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        int sendId = activity.getResources().getIdentifier("send", "id",
                                                activity.getPackageName());
                                        android.view.View sendBtn = activity.findViewById(sendId);
                                        if (sendBtn != null) {
                                            sendBtn.performClick();
                                            log("AutoStatusForward - Clicked send on MediaComposerActivity");
                                        }
                                    } catch (Exception e) {
                                        log("AutoStatusForward - click err: " + e);
                                    }
                                }, 800);
                            }
                        }
                    });
        } catch (Exception e) {
            log("AutoStatusForward MediaComposerActivity hook failed " + e.getMessage());
        }
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
                    log("AutoStatusForward – notification path skipped duplicate messageId=" + key.messageID);
                    return;
                }

                log("AutoStatusForward – notification path saw messageId=" + key.messageID
                        + ", sender=" + safeJid(key.remoteJid));
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
        log("AutoStatusForward – incoming msg from " + phone + " [text: «" + text + "»]");

        // 1. Rule matching
        com.waenhancer.model.StatusForwardRule matchedRule = matchesRules(text);
        if (matchedRule == null) {
            log("AutoStatusForward – did not match any text rule, skipping.");
            return;
        }

        // 2. Check for quoted status reply
        FMessageWpp quotedStatus = extractQuotedStatus(fMessageObj);
        if (quotedStatus == null) {
            if (attempt < MAX_RESOLVE_ATTEMPTS) {
                log("AutoStatusForward – quoted status not ready yet, retrying for message ID: " + key.messageID);
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> handleFMessage(fMessageObj, attempt + 1),
                        300L * (attempt + 1));
            } else {
                log("AutoStatusForward – no quoted status found after retries for message ID: " + key.messageID
                        + ", replyText=" + safeText(text));
            }
            return;
        }

        logReplyDiagnostics(incoming, quotedStatus);

        // 3. Verify rule toggles against status type
        boolean isVoiceNote = quotedStatus.getMediaType() == 2;
        boolean isMedia = quotedStatus.isMediaFile();

        if (isVoiceNote && !matchedRule.applyVoice) {
            log("AutoStatusForward – matched rule but applyVoice is false. Skipping voice status.");
            return;
        } else if (isMedia && !isVoiceNote && !matchedRule.applyMedia) {
            log("AutoStatusForward – matched rule but applyMedia is false. Skipping media status.");
            return;
        } else if (!isMedia && !isVoiceNote && !matchedRule.applyText) {
            log("AutoStatusForward – matched rule but applyText is false. Skipping text status.");
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
        log("AutoStatusForward – extractQuotedStatus starting");
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
                    log("AutoStatusForward – extractQuotedStatus Strategy 2 contextInfo: " + contextInfo);
                    if (contextInfo != null) {
                        for (Field f : getAllFields(contextInfo.getClass())) {
                            if (FMessageWpp.Key.TYPE.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                rawQuotedKey = f.get(contextInfo);
                                log("AutoStatusForward – extractQuotedStatus Strategy 2 rawQuotedKey from field: "
                                        + rawQuotedKey);
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
                log("AutoStatusForward – extractQuotedStatus Strategy 2 skipped (field null)");
            }

            // Since it's fully populated, we can also use getOriginalKey() API
            if (rawQuotedKey == null) {
                FMessageWpp wrapper = new FMessageWpp(fMessageObj);
                FMessageWpp.Key originalKey = wrapper.getOriginalKey(); // Context key
                log("AutoStatusForward – extractQuotedStatus Strategy 3 originalKey: " + originalKey);
                if (originalKey != null) {
                    rawQuotedKey = originalKey.thisObject;
                }
            }

            if (rawQuotedKey == null) {
                log("AutoStatusForward – extractQuotedStatus unable to find rawQuotedKey");
                return null;
            }

            FMessageWpp.Key msgKey = new FMessageWpp.Key(rawQuotedKey);
            log("AutoStatusForward – extractQuotedStatus msgKey remoteJid: "
                    + (msgKey.remoteJid != null ? msgKey.remoteJid.getPhoneNumber() : "null"));
            if (msgKey.remoteJid != null) {
                String phone = msgKey.remoteJid.getPhoneNumber();
                if (phone != null && (phone.equals("status") || phone.contains("broadcast"))) {
                    Object q = WppCore.getFMessageFromKey(rawQuotedKey);
                    if (q != null)
                        return new FMessageWpp(q);
                    log("AutoStatusForward – replied to status but it's not in cache db.");
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
                log("AutoStatusForward – found quoted context info field: " + f.getName() + " of type "
                        + type.getName());
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

    private com.waenhancer.model.StatusForwardRule matchesRules(String messageText) {
        String json = prefs.getString("auto_status_forward_rules_json", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                log("AutoStatusForward – no rules set (catch-all).");
                return new com.waenhancer.model.StatusForwardRule("contains", "", true, true, false);
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
                    log("AutoStatusForward – rule matched (EQUALS): " + ruleText);
                    return new com.waenhancer.model.StatusForwardRule(type, ruleText, applyText, applyMedia,
                            applyVoice);
                }
                if (!"equals".equals(type) && lower.contains(ruleText)) {
                    log("AutoStatusForward – rule matched (CONTAINS): " + ruleText);
                    return new com.waenhancer.model.StatusForwardRule(type, ruleText, applyText, applyMedia,
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
        log("AutoStatusForward – forwardStatus start: recipient=" + safeJid(recipientJid)
                + ", contactName=" + name
                + ", statusType=" + describeStatusType(statusMsg)
                + ", statusText=" + safeText(statusMsg.getMessageStr())
                + ", replyText=" + safeText(replyText));

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
            String jidRaw = recipient.getPhoneRawString();
            if (TextUtils.isEmpty(jidRaw)) {
                log("AutoStatusForward – text forward failed: recipient jid missing for " + contactName);
                return;
            }

            log("AutoStatusForward – launching composer for text forward:"
                    + " contactName=" + contactName
                    + ", recipient=" + safeJid(recipient)
                    + ", text=" + safeText(text));

            Class<?> cls = findMediaComposerClass();
            Intent intent = new Intent();
            intent.setClassName(Utils.getApplication().getPackageName(), cls.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton(jidRaw)));
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.putExtra("auto_forward_status", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Utils.getApplication().startActivity(intent);
            Utils.showToast("✅ Preparing status forward to " + contactName, Toast.LENGTH_SHORT);
        } catch (Exception e) {
            log("AutoStatusForward - forwardTextStatus err: " + e);
        }
    }

    private void forwardMediaStatus(FMessageWpp status, String jidRaw) {
        var file = status.getMediaFile();
        if (file == null || !file.exists()) {
            Utils.showToast("⚠️ Status media not cached.", Toast.LENGTH_LONG);
            log("AutoStatusForward – media forward skipped: cached file missing for status"
                    + " [statusType=" + describeStatusType(status)
                    + ", statusText=" + safeText(status.getMessageStr()) + "]");
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
            log("AutoStatusForward – launching MediaComposerActivity for media forward:"
                    + " recipient=" + jidRaw
                    + ", file=" + file.getAbsolutePath()
                    + ", caption=" + safeText(caption));
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
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len && i < 10; i++) {
                collectFMessageCandidates(java.lang.reflect.Array.get(value, i), out, visited, depth + 1);
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
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
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
        log("AutoStatusForward – reply diagnostics:"
                + " replyMessageId=" + (replyKey != null ? replyKey.messageID : "null")
                + ", replier=" + safeJid(replyKey != null ? replyKey.remoteJid : null)
                + ", replyText=" + safeText(incoming.getMessageStr())
                + ", quotedStatusMessageId=" + (statusKey != null ? statusKey.messageID : "null")
                + ", quotedStatusOwner=" + safeJid(statusKey != null ? statusKey.remoteJid : null)
                + ", quotedStatusType=" + describeStatusType(quotedStatus)
                + ", quotedStatusText=" + safeText(quotedStatus.getMessageStr()));
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
