package com.waex.api.services;

import com.waex.api.model.CustomPrivacyDTO;
import com.waex.api.model.MessageDTO;
import com.waex.api.model.UserJidDTO;

public interface IStateService {
    void addListenerActivity(Object listener);
    boolean isAnyActivityResumed();
    Object getCurrentUserJid();
    Object getCurrentConversation();
    String getContactName(Object userJid);
    String getPhoneNumber(Object userJid);
    boolean isJidNull(Object userJid);
    Object getJidObject(Object userJid);
    
    MessageDTO getCurrentMessage(Object messageKeyObj);
    UserJidDTO getCurrentUserJidDTO();
    UserJidDTO getPhoneJidFromUserJid(UserJidDTO userJid);
    UserJidDTO getUserJidFromPhoneJid(UserJidDTO userJid);
    String getContactName(UserJidDTO userJid);
    String getPhoneNumber(UserJidDTO userJid);
    CustomPrivacyDTO getCustomPrivacy(String phoneNumber);
    boolean isJidNull(UserJidDTO userJid);
    boolean isActivityResumed(String simpleName);
    long getMessageRowId(Object messageObj);
    java.io.File getMessageMediaFile(Object messageObj);
}
