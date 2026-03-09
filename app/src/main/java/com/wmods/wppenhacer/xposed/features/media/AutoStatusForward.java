package com.wmods.wppenhacer.xposed.features.media;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * AutoStatusForward
 *
 * Hooks Unobfuscator.loadReceiptMethod() to intercept fully constructed
 * incoming messages from the cache with their fields (text, quoted context)
 * populated.
 */
public class AutoStatusForward extends Feature {

    private static Field quotedContextFieldCache = null;
    private static Method getQuotedKeyMethodCache = null;
    private static boolean scannedForQuoted = false;

    public AutoStatusForward(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        log("AutoStatusForward – hooking loadReceiptMethod (fully populated incoming messages)");
        Method receiptMethod = Unobfuscator.loadReceiptMethod(classLoader);

        XposedBridge.hookMethod(receiptMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                log("AutoStatusForward - receiptMethod triggered unconditionally!");
                try {
                    prefs.reload();
                    boolean isEnabled = prefs.getBoolean("auto_status_forward", false);
                    log("AutoStatusForward - isEnabled: " + isEnabled);
                    if (!isEnabled)
                        return;

                    StringBuilder types = new StringBuilder();
                    for (int i = 0; i < param.args.length; i++) {
                        Object obj = param.args[i];
                        types.append("arg[").append(i).append("]=")
                                .append(obj != null ? obj.getClass().getName() : "null").append("; ");
                        if (obj instanceof String) {
                            types.append("(").append(obj).append("); ");
                        }
                    }
                    log("AutoStatusForward - args: " + types.toString());

                    if (param.args.length > 4 && param.args[4] instanceof String) {
                        log("AutoStatusForward - receiptMethod invoked with param 4 = " + param.args[4]);
                    }

                    String messageId = param.args.length > 5 && param.args[5] instanceof String ? (String) param.args[5]
                            : null;
                    Object remoteJidObj = param.args.length > 2 ? param.args[2] : null;

                    if (messageId == null || remoteJidObj == null) {
                        return;
                    }

                    try {
                        FMessageWpp.UserJid remoteJid = new FMessageWpp.UserJid(remoteJidObj);
                        FMessageWpp.Key key = new FMessageWpp.Key(messageId, remoteJid, false);
                        FMessageWpp fMessage = key.getFMessage();

                        if (fMessage != null) {
                            log("AutoStatusForward - Fetched message from receipt ID: " + messageId);
                            // Ensure the hook doesn't re-process the exact same message repeatedly
                            String dupKey = "last_processed_" + messageId;
                            if (prefs.getBoolean(dupKey, false))
                                return;
                            prefs.edit().putBoolean(dupKey, true).apply();

                            handleFMessage(fMessage.getObject());
                        }
                    } catch (Throwable t) {
                        log("AutoStatusForward - err reconstructing key: " + t);
                    }

                } catch (Throwable t) {
                    log("AutoStatusForward – hook err: " + t);
                }
            }
        });
    }

    private void handleFMessage(Object fMessageObj) {
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
        boolean ruleMatches = matchesRules(text);
        if (!ruleMatches) {
            log("AutoStatusForward – did not match any text rule, skipping.");
            return;
        }

        // 2. Check for quoted status reply
        log("AutoStatusForward – rules passed! Checking if it's a replied status...");
        FMessageWpp quotedStatus = extractQuotedStatus(fMessageObj);
        if (quotedStatus == null) {
            log("AutoStatusForward – not a status reply (no status context found)");
            return;
        }

        log("AutoStatusForward – IS a status reply! Forwarding back to " + phone);

        final FMessageWpp statusToSend = quotedStatus;
        final String replyText = text;
        final FMessageWpp.UserJid recipient = senderJid;

        CompletableFuture.runAsync(() -> {
            try {
                forwardStatus(statusToSend, replyText, recipient);
            } catch (Throwable t) {
                log("AutoStatusForward – forward err: " + t);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Safely and quickly extract quoted status without crashing
    // -------------------------------------------------------------------------

    private FMessageWpp extractQuotedStatus(Object fMessageObj) {
        try {
            if (!scannedForQuoted)
                scanForQuotedContextInfo(fMessageObj.getClass());

            Object rawQuotedKey = null;

            // Strategy 1: Method returning Key
            if (getQuotedKeyMethodCache != null) {
                try {
                    rawQuotedKey = getQuotedKeyMethodCache.invoke(fMessageObj);
                } catch (Throwable ignored) {
                }
            }

            // Strategy 2: ContextInfo field containing Key inside it
            if (rawQuotedKey == null && quotedContextFieldCache != null) {
                try {
                    Object contextInfo = quotedContextFieldCache.get(fMessageObj);
                    if (contextInfo != null) {
                        for (Field f : contextInfo.getClass().getDeclaredFields()) {
                            if (FMessageWpp.Key.TYPE.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                rawQuotedKey = f.get(contextInfo);
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            // Since it's fully populated, we can also use getOriginalKey() API
            if (rawQuotedKey == null) {
                FMessageWpp wrapper = new FMessageWpp(fMessageObj);
                FMessageWpp.Key originalKey = wrapper.getOriginalKey(); // Context key
                if (originalKey != null) {
                    rawQuotedKey = originalKey.thisObject;
                }
            }

            if (rawQuotedKey == null)
                return null;

            FMessageWpp.Key msgKey = new FMessageWpp.Key(rawQuotedKey);
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

        // 1. Find method returning Key that isn't the main key
        for (Method m : methods) {
            if (m.getParameterCount() == 0 && FMessageWpp.Key.TYPE.isAssignableFrom(m.getReturnType())) {
                if (!m.getName().equals("A1J") && !m.getName().equals("A1K") && !m.getName().equals("getKey")) {
                    log("AutoStatusForward – found quoted key accessor method: " + m.getName());
                    getQuotedKeyMethodCache = m;
                    m.setAccessible(true);
                    break;
                }
            }
        }

        // 2. Find field holding ContextInfo (looks like an object with a Key inside it)
        if (getQuotedKeyMethodCache == null) {
            for (Field f : fields) {
                Class<?> type = f.getType();
                if (type.isPrimitive() || type.getName().startsWith("java.") || type.getName().startsWith("android."))
                    continue;
                boolean hasKey = false;
                for (Field nestedF : type.getDeclaredFields()) {
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

    private boolean matchesRules(String messageText) {
        String json = prefs.getString("auto_status_forward_rules_json", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                log("AutoStatusForward – no rules set (catch-all).");
                return true;
            }
            if (TextUtils.isEmpty(messageText))
                return false;

            String lower = messageText.trim().toLowerCase();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rule = arr.getJSONObject(i);
                String type = rule.optString("type", "contains").toLowerCase();
                String ruleText = rule.optString("text", "").trim().toLowerCase();
                if (ruleText.isEmpty())
                    continue;

                if ("equals".equals(type) && lower.equals(ruleText)) {
                    log("AutoStatusForward – rule matched (EQUALS): " + ruleText);
                    return true;
                }
                if (!"equals".equals(type) && lower.contains(ruleText)) {
                    log("AutoStatusForward – rule matched (CONTAINS): " + ruleText);
                    return true;
                }
            }
        } catch (Exception e) {
            log("AutoStatusForward – matchesRules exception: " + e.getMessage());
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Forward
    // -------------------------------------------------------------------------

    private void forwardStatus(FMessageWpp statusMsg, String replyText, FMessageWpp.UserJid recipientJid)
            throws Exception {
        String jidRaw = recipientJid.getPhoneRawString();
        if (jidRaw == null)
            return;
        String name = WppCore.getContactName(recipientJid);
        if (TextUtils.isEmpty(name))
            name = recipientJid.getPhoneNumber();
        Utils.showToast("📤 Auto-forwarding status to " + name, Toast.LENGTH_SHORT);

        if (statusMsg.isMediaFile())
            forwardMediaStatus(statusMsg, jidRaw);
        else
            forwardTextStatus("[Status reply from " + name + "]: " + replyText, jidRaw);
    }

    private void forwardTextStatus(String text, String jidRaw) {
        try {
            Class<?> cls = findMediaComposerClass();
            Intent intent = new Intent();
            intent.setClassName(Utils.getApplication().getPackageName(), cls.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton(jidRaw)));
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Utils.getApplication().startActivity(intent);
        } catch (Exception e) {
        }
    }

    private void forwardMediaStatus(FMessageWpp status, String jidRaw) {
        var file = status.getMediaFile();
        if (file == null || !file.exists()) {
            Utils.showToast("⚠️ Status media not cached.", Toast.LENGTH_LONG);
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
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Utils.getApplication().startActivity(intent);
        } catch (Exception e) {
        }
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
