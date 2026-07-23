package com.waenhancer.xposed.core.plugins.impl;

import android.app.Activity;
import com.waex.api.model.CustomPrivacyDTO;
import com.waex.api.model.MessageDTO;
import com.waex.api.model.MessageKeyDTO;
import com.waex.api.model.UserJidDTO;
import com.waex.api.services.IStateService;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.features.privacy.CustomPrivacy;
import org.json.JSONObject;
import com.waenhancer.xposed.core.ActivityStateRegistry;
import de.robv.android.xposed.XposedBridge;
import java.io.File;

public class StateServiceImpl implements IStateService {

    @Override
    public void addListenerActivity(final Object listener) {
        WppCore.addListenerActivity(new WppCore.ActivityChangeState() {
            @Override
            public void onChange(Activity activity, ChangeType type) {
                try {
                    var m = listener.getClass().getMethod("onActivityStateChanged", Activity.class, String.class);
                    m.invoke(listener, activity, type.name());
                } catch (Exception e) {
                    XposedBridge.log("addListenerActivity callback invocation error: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean isAnyActivityResumed() {
        return ActivityStateRegistry.isAnyActivityInState(WppCore.ActivityChangeState.ChangeType.RESUMED);
    }

    @Override
    public Object getCurrentUserJid() {
        return WppCore.getCurrentUserJid();
    }

    @Override
    public Object getCurrentConversation() {
        return WppCore.getCurrentConversation();
    }

    @Override
    public String getContactName(Object userJid) {
        if (userJid == null) return null;
        if (userJid instanceof FMessageWpp.UserJid) {
            return WppCore.getContactName((FMessageWpp.UserJid) userJid);
        } else {
            return WppCore.getContactName(new FMessageWpp.UserJid(String.valueOf(userJid)));
        }
    }

    @Override
    public String getPhoneNumber(Object userJid) {
        if (userJid == null) return null;
        if (userJid instanceof String) return (String) userJid;
        return ((FMessageWpp.UserJid) userJid).getPhoneNumber();
    }

    @Override
    public boolean isJidNull(Object userJid) {
        if (userJid == null) return true;
        return ((FMessageWpp.UserJid) userJid).isNull();
    }

    @Override
    public Object getJidObject(Object userJid) {
        if (userJid == null) return null;
        if (userJid instanceof String) {
            return new FMessageWpp.UserJid((String) userJid);
        }
        return userJid;
    }

    @Override
    public MessageDTO getCurrentMessage(Object messageKeyObj) {
        Object fMsgObj = WppCore.getFMessageFromKey(messageKeyObj);
        if (fMsgObj == null) return null;
        FMessageWpp fMsg = new FMessageWpp(fMsgObj);
        return mapMessageToDTO(fMsg);
    }

    @Override
    public UserJidDTO getCurrentUserJidDTO() {
        FMessageWpp.UserJid jid = WppCore.getCurrentUserJid();
        return mapJidToDTO(jid);
    }

    @Override
    public UserJidDTO getPhoneJidFromUserJid(UserJidDTO userJid) {
        if (userJid == null) return null;
        Object rawJid = WppCore.createUserJid(userJid.getRawJid());
        Object phoneJid = WppCore.getPhoneJidFromUserJid(rawJid);
        return mapJidToDTO(new FMessageWpp.UserJid(phoneJid));
    }

    @Override
    public UserJidDTO getUserJidFromPhoneJid(UserJidDTO userJid) {
        if (userJid == null) return null;
        Object rawJid = WppCore.createUserJid(userJid.getRawJid());
        Object userJidObj = WppCore.getUserJidFromPhoneJid(rawJid);
        return mapJidToDTO(new FMessageWpp.UserJid(userJidObj));
    }

    @Override
    public String getContactName(UserJidDTO userJid) {
        if (userJid == null) return null;
        return WppCore.getContactName(new FMessageWpp.UserJid(userJid.getRawJid()));
    }

    @Override
    public String getPhoneNumber(UserJidDTO userJid) {
        if (userJid == null) return null;
        return new FMessageWpp.UserJid(userJid.getRawJid()).getPhoneNumber();
    }

    @Override
    public CustomPrivacyDTO getCustomPrivacy(String phoneNumber) {
        JSONObject json = CustomPrivacy.getJSON(phoneNumber);
        return new CustomPrivacyDTO(json != null ? json.toString() : "{}");
    }

    @Override
    public boolean isJidNull(UserJidDTO userJid) {
        return userJid == null || userJid.getRawJid() == null;
    }

    @Override
    public boolean isActivityResumed(String simpleName) {
        return WppCore.getActivityStateBySimpleName(simpleName) == WppCore.ActivityChangeState.ChangeType.RESUMED;
    }

    private UserJidDTO mapJidToDTO(FMessageWpp.UserJid jid) {
        if (jid == null || jid.isNull()) return null;
        return new UserJidDTO(
            jid.getUserRawString(),
            jid.getPhoneNumber(),
            jid.isGroup(),
            jid.isNewsletter(),
            jid.isBroadcast(),
            jid.isStatus(),
            jid.isContact()
        );
    }

    private MessageDTO mapMessageToDTO(FMessageWpp fMsg) {
        if (fMsg == null) return null;
        
        FMessageWpp.Key k = fMsg.getKey();
        MessageKeyDTO keyDTO = k != null ? new MessageKeyDTO(
            k.messageID,
            k.remoteJid != null ? k.remoteJid.getUserRawString() : null,
            k.isFromMe,
            k.participant != null ? k.participant.getUserRawString() : null
        ) : null;

        UserJidDTO senderDTO = mapJidToDTO(fMsg.getUserJid());
        File mediaFile = fMsg.getMediaFile();

        return new MessageDTO(
            keyDTO,
            senderDTO,
            fMsg.getMessageStr(),
            fMsg.getTimestamp(),
            fMsg.getRowId(),
            fMsg.getMediaType(),
            fMsg.isMediaFile(),
            fMsg.isViewOnce(),
            mediaFile != null ? mediaFile.getAbsolutePath() : null
        );
    }

    @Override
    public long getMessageRowId(Object messageObj) {
        if (messageObj == null) return -1L;
        return new FMessageWpp(messageObj).getRowId();
    }

    @Override
    public File getMessageMediaFile(Object messageObj) {
        if (messageObj == null) return null;
        return new FMessageWpp(messageObj).getMediaFile();
    }
}