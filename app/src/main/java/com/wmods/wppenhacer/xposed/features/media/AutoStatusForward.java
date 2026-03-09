package com.wmods.wppenhacer.xposed.features.media;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
    private static Method getQuotedKeyMethodCache = null;
    private static boolean scannedForQuoted = false;

    public AutoStatusForward(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        log("AutoStatusForward – hooking all constructors of " + FMessageWpp.TYPE.getName());
        XposedBridge.hookAllConstructors(FMessageWpp.TYPE, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (!prefs.getBoolean("auto_status_forward", false))
                    return;

                Object fMessageRaw = param.thisObject;
                if (fMessageRaw == null)
                    return;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        FMessageWpp fMessage = new FMessageWpp(fMessageRaw);
                        FMessageWpp.Key key = fMessage.getKey();

                        if (key == null || key.isFromMe)
                            return;

                        String messageId = key.messageID;
                        if (messageId == null)
                            return;

                        String dupKey = "last_processed_" + messageId;
                        if (WppCore.getPrivBoolean(dupKey, false))
                            return;
                        WppCore.setPrivBoolean(dupKey, true);

                        log("AutoStatusForward - intercepted FMessage constructor! ID: " + messageId);
                        handleFMessage(fMessageRaw);

                    } catch (Throwable t) {
                        log("AutoStatusForward – Delayed constructor check err: " + t);
                    }
                }, 1500); // 1.5 seconds delay allows text extraction
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
            forwardTextStatus("[Status reply from " + name + "]: " + replyText, recipientJid);
    }

    private void forwardTextStatus(String text, FMessageWpp.UserJid recipient) {
        try {
            String phoneNumber = recipient.getPhoneNumber();
            if (phoneNumber != null) {
                // WppCore.sendMessage sends silently in the background without launching any
                // Activity
                WppCore.sendMessage(phoneNumber, text);
            } else {
                log("AutoStatusForward - forwardTextStatus failed, no phone number for " + recipient);
            }
        } catch (Exception e) {
            log("AutoStatusForward - forwardTextStatus err: " + e);
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
            intent.putExtra("auto_forward_status", true);
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
