package com.waenhancer.api.model;

public class UserJidDTO {
    private final String rawJid;
    private final String phoneNumber;
    private final boolean isGroup;
    private final boolean isNewsletter;
    private final boolean isBroadcast;
    private final boolean isStatus;
    private final boolean isContact;

    public UserJidDTO(String rawJid, String phoneNumber, boolean isGroup, boolean isNewsletter, boolean isBroadcast, boolean isStatus, boolean isContact) {
        this.rawJid = rawJid;
        this.phoneNumber = phoneNumber;
        this.isGroup = isGroup;
        this.isNewsletter = isNewsletter;
        this.isBroadcast = isBroadcast;
        this.isStatus = isStatus;
        this.isContact = isContact;
    }

    public String getRawJid() { return rawJid; }
    public String getPhoneNumber() { return phoneNumber; }
    public boolean isGroup() { return isGroup; }
    public boolean isNewsletter() { return isNewsletter; }
    public boolean isBroadcast() { return isBroadcast; }
    public boolean isStatus() { return isStatus; }
    public boolean isContact() { return isContact; }
}
