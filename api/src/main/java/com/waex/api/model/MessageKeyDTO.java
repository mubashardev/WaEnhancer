package com.waex.api.model;

public class MessageKeyDTO {
    private final String messageId;
    private final String remoteJid;
    private final boolean isFromMe;
    private final String participantJid;

    public MessageKeyDTO(String messageId, String remoteJid, boolean isFromMe, String participantJid) {
        this.messageId = messageId;
        this.remoteJid = remoteJid;
        this.isFromMe = isFromMe;
        this.participantJid = participantJid;
    }

    public String getMessageId() { return messageId; }
    public String getRemoteJid() { return remoteJid; }
    public boolean isFromMe() { return isFromMe; }
    public String getParticipantJid() { return participantJid; }
}
