package com.waenhancer.xposed.features.privacy;

import android.content.SharedPreferences;
import android.os.Message;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.ProtocolTreeNodeWpp;
import com.waenhancer.xposed.core.db.MessageHistory;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.customization.HideSeenView;
import com.waenhancer.xposed.features.general.Others;
import com.waenhancer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideSeen extends Feature {

    private static final int MEDIA_TYPE_VOICE_NOTE = 2;

    private boolean ghostMode;
    private boolean hideRead;
    private boolean hideAudioSeen;
    private boolean hideOnceSeen;
    private boolean hideReadGroup;
    private boolean hideStatusView;
    private boolean hideReceipt;

    public HideSeen(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    protected static FMessageWpp.Key getKeyMessage(XC_MethodHook.MethodHookParam param, Object userJidObject,
                                                   List<Pair<Integer, Class<? extends String>>> strings) {
        Object keyObject = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
        if (keyObject == null) {
            if (strings.size() < 2) return null;
            String idMessage = (String) param.args[strings.get(0).first];
            FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(userJidObject);
            return new FMessageWpp.Key(idMessage, userJid, false);
        }
        return new FMessageWpp.Key(keyObject);
    }

    @Override
    public void doHook() throws Exception {
        loadPreferences();
        hookSendReadReceiptJob();
        hookReceiptMethod();
        hookSenderPlayed();
        hookSenderPlayedBusiness();
    }

    private void loadPreferences() {
        ghostMode = prefs.getBoolean("ghostmode_actual", false);
        hideRead = prefs.getBoolean("hideread", false);
        hideAudioSeen = prefs.getBoolean("hideaudioseen", false);
        hideOnceSeen = prefs.getBoolean("hideonceseen", false);
        hideReadGroup = prefs.getBoolean("hideread_group", false);
        hideStatusView = prefs.getBoolean("hidestatusview", false);
        hideReceipt = prefs.getBoolean("hidereceipt", false);
    }

    private void hookSendReadReceiptJob() throws Exception {
        Method sendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader);
        Class<?> sendJobClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "SendReadReceiptJob");
        /* Log removed */

        XposedBridge.hookMethod(sendReadReceiptJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!sendJobClass.isInstance(param.thisObject)) return;

                Object sendReadReceiptJob = sendJobClass.cast(param.thisObject);
                if (hasBlueOnReplyFlag(sendReadReceiptJob)) return;

                String lid = (String) XposedHelpers.getObjectField(sendReadReceiptJob, "jid");
                if (isInvalidJid(lid)) return;

                FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(lid);
                if (userJid.isNull()) return;

                JSONObject privacy = CustomPrivacy.getJSON(userJid.getPhoneNumber());
                boolean isHide = processReadReceiptByType(param, sendReadReceiptJob, userJid, privacy);

                if (isHide) {
                    recordHiddenMessages(sendReadReceiptJob, userJid);
                }
            }
        });
    }

    private boolean hasBlueOnReplyFlag(Object sendReadReceiptJob) {
        return XposedHelpers.getAdditionalInstanceField(sendReadReceiptJob, "blue_on_reply") != null;
    }

    private boolean isInvalidJid(String lid) {
        return TextUtils.isEmpty(lid) || lid.contains("lid_me") || lid.contains("status_me");
    }

    private boolean processReadReceiptByType(XC_MethodHook.MethodHookParam param, Object job,
                                             FMessageWpp.UserJid userJid, JSONObject privacy) {
        if (userJid.isGroup()) {
            return processGroupReadReceipt(param, privacy);
        }
        if (userJid.isStatus()) {
            processStatusReadReceipt(param, job);
            return false;
        }
        return processDirectReadReceipt(param, privacy);
    }

    private boolean processGroupReadReceipt(XC_MethodHook.MethodHookParam param, JSONObject privacy) {
        if (privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode) {
            param.setResult(null);
            return true;
        }
        return false;
    }

    private void processStatusReadReceipt(XC_MethodHook.MethodHookParam param, Object job) {
        String participant = (String) XposedHelpers.getObjectField(job, "participant");
        boolean customHideStatusView = CustomPrivacy.getJSON(WppCore.stripJID(participant))
                .optBoolean("HideViewStatus", hideStatusView);

        if (customHideStatusView || ghostMode) {
            param.setResult(null);
        }
    }

    private boolean processDirectReadReceipt(XC_MethodHook.MethodHookParam param, JSONObject privacy) {
        boolean customHideRead = privacy.optBoolean("HideSeen", hideRead);
        if (customHideRead || ghostMode) {
            param.setResult(null);
            return true;
        }
        return false;
    }

    private void recordHiddenMessages(Object sendReadReceiptJob, FMessageWpp.UserJid userJid) {
        String[] messageIds = (String[]) XposedHelpers.getObjectField(sendReadReceiptJob, "messageIds");
        for (String messageId : messageIds) {
            FMessageWpp fMessage = new FMessageWpp.Key(messageId, userJid, false).getFMessage();
            MessageHistory.MessageType type = (fMessage != null && fMessage.isViewOnce())
                    ? MessageHistory.MessageType.VIEW_ONCE_TYPE
                    : MessageHistory.MessageType.MESSAGE_TYPE;
            MessageHistory.getInstance().insertHideSeenMessage(userJid.getPhoneRawString(), messageId, type, false);
        }
        HideSeenView.updateAllBubbleViews();
    }

    private void hookReceiptMethod() throws Exception {
        Method receiptMethod = Unobfuscator.loadReceiptMethod(classLoader);
        Method receiptMainCallerMethod = null;
        try {
            receiptMainCallerMethod = Unobfuscator.loadReceiptMainCallerMethod(classLoader);
        } catch (Throwable t) {
            XposedBridge.log("WaEnhancer: HideSeen receiptMainCallerMethod lookup failed: " + t.getMessage());
        }

        Method[] receiptCallerMethods = null;
        try {
            receiptCallerMethods = Unobfuscator.loadReceiptCallersMethod(classLoader);
        } catch (Throwable t) {
            XposedBridge.log("WaEnhancer: HideSeen receiptCallerMethods lookup failed: " + t.getMessage());
        }

        final ThreadLocal<Boolean> inManualReceiptCheck = new ThreadLocal<>();

        if (receiptCallerMethods != null && receiptMainCallerMethod != null) {
            final Method finalMainCallerMethod = receiptMainCallerMethod;
            XC_MethodHook hookCallerMethod = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof Message)) return;
                    Message firstArg = (Message) param.args[0];
                    if (firstArg.arg1 != 419 && firstArg.arg1 != 89) return;
                    Object obj = firstArg.obj;
                    inManualReceiptCheck.set(true);
                    Object checkResult = null;
                    try {
                        checkResult = finalMainCallerMethod.invoke(null, obj);
                    } finally {
                        inManualReceiptCheck.set(false);
                    }

                    if (checkResult == null) {
                        param.setResult(null);
                    }
                }
            };

            for (Method m : receiptCallerMethods) {
                if (m != null) {
                    XposedBridge.hookMethod(m, hookCallerMethod);
                }
            }
        }

        Others.propsBoolean.put(19148, true);

        XposedBridge.hookMethod(receiptMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.getResult() == null) return;
                ProtocolTreeNodeWpp protocolTreeNodeWpp = new ProtocolTreeNodeWpp(param.getResult());

                ProtocolTreeNodeWpp.KeyValueWpp typeKV = null;
                for (ProtocolTreeNodeWpp.KeyValueWpp kv : protocolTreeNodeWpp.getAttributes()) {
                    if ("type".equals(kv.getKey())) {
                        typeKV = kv;
                        break;
                    }
                }

                FMessageWpp.Key fmessageKey = generateFMessageKey(protocolTreeNodeWpp);
                if (fmessageKey == null) return;

                MessageHistory.MessageType dbType = MessageHistory.MessageType.MESSAGE_TYPE;
                FMessageWpp fMessage = fmessageKey.getFMessage();
                if (fMessage != null && fMessage.isViewOnce()) {
                    dbType = MessageHistory.MessageType.VIEW_ONCE_TYPE;
                }

                MessageHistory.MessageSeenItem hideSeenItem = MessageHistory.getInstance().getHideSeenMessage(
                        fmessageKey.remoteJid.getPhoneRawString(),
                        fmessageKey.messageID,
                        dbType
                );

                if (hideSeenItem != null) {
                    if (hideSeenItem.viewed) return;
                    param.setResult(null);
                    return;
                }

                boolean hideSeen = checkPrivacyAndHideSeen(fmessageKey);
                boolean hideReceipt = checkPrivacyAndHideReceipt(fmessageKey);

                if (hideReceipt) {
                    if (typeKV == null) {
                        protocolTreeNodeWpp.addKeyValue("type", "inactive");
                    } else {
                        typeKV.setValue("inactive");
                    }
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts");
                } else if (hideSeen && typeKV != null && "read".equals(typeKV.getValue())) {
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts");
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("type");
                }

                Boolean isManual = inManualReceiptCheck.get();
                if (isManual != null && isManual) return;

                if (hideReceipt || hideSeen) {
                    MessageHistory.getInstance().insertHideSeenMessage(
                            fmessageKey.remoteJid.getPhoneRawString(),
                            fmessageKey.messageID,
                            dbType,
                            false
                    );
                    HideSeenView.updateAllBubbleViews();
                }
            }
        });
    }

    private static FMessageWpp.Key generateFMessageKey(ProtocolTreeNodeWpp node) {
        try {
            ProtocolTreeNodeWpp.KeyValueWpp toKV = null;
            for (ProtocolTreeNodeWpp.KeyValueWpp kv : node.getAttributes()) {
                if ("to".equals(kv.getKey())) {
                    toKV = kv;
                    break;
                }
            }
            if (toKV == null) return null;
            FMessageWpp.UserJid userJid = toKV.getUserJid();
            if (userJid == null || userJid.isNull()) return null;

            ProtocolTreeNodeWpp.KeyValueWpp idKV = null;
            for (ProtocolTreeNodeWpp.KeyValueWpp kv : node.getAttributes()) {
                if ("id".equals(kv.getKey())) {
                    idKV = kv;
                    break;
                }
            }
            if (idKV == null) return null;
            return new FMessageWpp.Key(idKV.getValue(), userJid, false);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean checkPrivacyAndHideReceipt(FMessageWpp.Key fmessageKey) {
        JSONObject privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid.getPhoneNumber());
        boolean customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt);
        return customHideReceipt || ghostMode;
    }

    private boolean checkPrivacyAndHideSeen(FMessageWpp.Key fmessageKey) {
        JSONObject privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid.getPhoneNumber());
        boolean hideKey = fmessageKey.remoteJid.isGroup() ? hideReadGroup : hideRead;
        return privacy.optBoolean("HideSeen", hideKey) || ghostMode;
    }

    private void hookSenderPlayed() throws Exception {
        Method loadSenderPlayed = Unobfuscator.loadSenderPlayedMethod(classLoader);

        XposedBridge.hookMethod(loadSenderPlayed, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                FMessageWpp fMessage = new FMessageWpp(param.args[0]);
                processSenderPlayed(param, fMessage);
            }
        });
    }

    private void hookSenderPlayedBusiness() throws Exception {
        Method loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader);

        XposedBridge.hookMethod(loadSenderPlayedBusiness, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Set<?> set = (Set<?>) param.args[0];
                if (set == null || set.isEmpty()) return;

                FMessageWpp fMessage = new FMessageWpp(set.iterator().next());
                processSenderPlayed(param, fMessage);
            }
        });
    }

    private void processSenderPlayed(XC_MethodHook.MethodHookParam param, FMessageWpp fMessage) {
        int mediaType = fMessage.getMediaType();
        boolean isHide = false;

        if (shouldHideViewOnce(fMessage)) {
            param.setResult(null);
            isHide = true;
        } else if (shouldHideVoiceNote(mediaType)) {
            param.setResult(null);
            isHide = true;
        }

        FMessageWpp.Key key = fMessage.getKey();
        if (isHide) {
            MessageHistory.getInstance().insertHideSeenMessage(
                    key.remoteJid.getPhoneRawString(), key.messageID, MessageHistory.MessageType.MESSAGE_TYPE, false);
        }

        handleViewOnceViewed(fMessage, key);
        HideSeenView.updateAllBubbleViews();
    }

    private boolean shouldHideViewOnce(FMessageWpp fMessage) {
        return (hideOnceSeen || ghostMode) && fMessage.isViewOnce();
    }

    private boolean shouldHideVoiceNote(int mediaType) {
        return (hideAudioSeen || ghostMode) && mediaType == MEDIA_TYPE_VOICE_NOTE;
    }

    private void handleViewOnceViewed(FMessageWpp fMessage, FMessageWpp.Key key) {
        if (fMessage.isViewOnce() && !hideOnceSeen && !ghostMode) {
            String phoneRaw = key.remoteJid.getPhoneRawString();
            String messageId = key.messageID;
            MessageHistory.getInstance().updateViewedMessage(phoneRaw, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
            MessageHistory.getInstance().updateViewedMessage(phoneRaw, messageId, MessageHistory.MessageType.MESSAGE_TYPE, true);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen";
    }
}
