package com.waenhancer.api.services;

import com.waenhancer.api.model.CustomPrivacyDTO;
import com.waenhancer.api.model.MessageDTO;
import com.waenhancer.api.model.UserJidDTO;

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
}
