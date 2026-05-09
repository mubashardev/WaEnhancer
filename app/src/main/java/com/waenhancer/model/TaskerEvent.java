package com.waenhancer.model;

/**
 * Represents a single Tasker automation event (incoming or outgoing message).
 */
public class TaskerEvent {

    public static final String TYPE_INCOMING = "INCOMING";
    public static final String TYPE_OUTGOING = "OUTGOING";

    public long id;
    public String type;
    public long timestamp;
    public String targetNumber;
    public String messagePreview;

    public TaskerEvent() {
    }

    public TaskerEvent(long id, String type, long timestamp, String targetNumber, String messagePreview) {
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
        this.targetNumber = targetNumber;
        this.messagePreview = messagePreview;
    }
}
