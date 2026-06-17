package com.waex.api.model;

public class MessageDTO {
    private final MessageKeyDTO key;
    private final UserJidDTO senderJid;
    private final String messageText;
    private final long timestamp;
    private final long rowId;
    private final int mediaType;
    private final boolean isMediaFile;
    private final boolean isViewOnce;
    private final String mediaFilePath;

    public MessageDTO(MessageKeyDTO key, UserJidDTO senderJid, String messageText, long timestamp, long rowId, int mediaType, boolean isMediaFile, boolean isViewOnce, String mediaFilePath) {
        this.key = key;
        this.senderJid = senderJid;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.rowId = rowId;
        this.mediaType = mediaType;
        this.isMediaFile = isMediaFile;
        this.isViewOnce = isViewOnce;
        this.mediaFilePath = mediaFilePath;
    }

    public MessageKeyDTO getKey() { return key; }
    public UserJidDTO getSenderJid() { return senderJid; }
    public String getMessageText() { return messageText; }
    public long getTimestamp() { return timestamp; }
    public long getRowId() { return rowId; }
    public int getMediaType() { return mediaType; }
    public boolean isMediaFile() { return isMediaFile; }
    public boolean isViewOnce() { return isViewOnce; }
    public String getMediaFilePath() { return mediaFilePath; }
}
