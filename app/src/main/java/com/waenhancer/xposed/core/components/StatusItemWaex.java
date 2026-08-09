package com.waenhancer.xposed.core.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatusItemWaex {

    private static final Map<Class<?>, Field> fStatusFieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> fMessageFieldCache = new ConcurrentHashMap<>();

    private final FStatusWpp fStatus;
    private final FMessageWpp directFMessage;

    private StatusItemWaex(@Nullable FStatusWpp fStatus, @Nullable FMessageWpp directFMessage) {
        this.fStatus = fStatus;
        this.directFMessage = directFMessage;
    }

    @Nullable
    public static StatusItemWaex from(@Nullable Object obj) {
        if (obj == null) return null;

        Class<?> clazz = obj.getClass();

        // 1. Try finding direct FMessageWpp field
        Field fMsgField = fMessageFieldCache.computeIfAbsent(clazz, c -> {
            Field f = ReflectionUtils.findFieldUsingFilterIfExists(c, field ->
                    FMessageWpp.TYPE != null && FMessageWpp.TYPE.isAssignableFrom(field.getType()));
            return f != null ? f : NULL_FIELD;
        });

        if (fMsgField != NULL_FIELD) {
            Object fMsgObj = ReflectionUtils.getObjectField(fMsgField, obj);
            if (fMsgObj != null) {
                return new StatusItemWaex(null, new FMessageWpp(fMsgObj));
            }
        }

        // 2. Try finding FStatusWpp field
        Field fStatusField = fStatusFieldCache.computeIfAbsent(clazz, c -> {
            Field f = ReflectionUtils.findFieldUsingFilterIfExists(c, field ->
                    FStatusWpp.TYPE != null && FStatusWpp.TYPE.isAssignableFrom(field.getType()));
            return f != null ? f : NULL_FIELD;
        });

        if (fStatusField != NULL_FIELD) {
            Object fStatusObj = ReflectionUtils.getObjectField(fStatusField, obj);
            if (fStatusObj != null) {
                return new StatusItemWaex(new FStatusWpp(fStatusObj), null);
            }
        }

        // 3. Fallback: Check if object itself is FMessageWpp
        if (FMessageWpp.TYPE != null && FMessageWpp.TYPE.isInstance(obj)) {
            return new StatusItemWaex(null, new FMessageWpp(obj));
        }

        // 4. Fallback: Check if object itself is FStatusWpp
        if (FStatusWpp.TYPE != null && FStatusWpp.TYPE.isInstance(obj)) {
            return new StatusItemWaex(new FStatusWpp(obj), null);
        }

        return null;
    }

    // Dummy sentinel to cache missing fields without re-searching reflection
    private static final Field NULL_FIELD;
    static {
        Field dummy = null;
        try {
            dummy = StatusItemWaex.class.getDeclaredField("fStatus");
        } catch (Throwable ignored) {
        }
        NULL_FIELD = dummy;
    }

    @Nullable
    public FMessageWpp getFMessage() {
        if (directFMessage != null) return directFMessage;
        return fStatus != null ? fStatus.getFMessage() : null;
    }

    public boolean isFromMe() {
        if (directFMessage != null && directFMessage.getKey() != null) {
            return directFMessage.getKey().isFromMe;
        }
        if (fStatus != null) {
            FStatusWpp.FStatusKey key = fStatus.getFStatusKey();
            return key != null && key.isFromMe;
        }
        return false;
    }

    public String getMessageID() {
        if (directFMessage != null && directFMessage.getKey() != null) {
            return directFMessage.getKey().messageID;
        }
        if (fStatus != null) {
            FStatusWpp.FStatusKey key = fStatus.getFStatusKey();
            return key != null && key.messageID != null ? key.messageID : "";
        }
        return "";
    }

    @Nullable
    public FMessageWpp.UserJid getSenderJid() {
        if (directFMessage != null) {
            return directFMessage.getUserJid();
        }
        if (fStatus != null) {
            FStatusWpp.FStatusKey key = fStatus.getFStatusKey();
            return key != null ? key.senderJid : null;
        }
        return null;
    }

    public boolean isMediaFile() {
        if (fStatus != null) {
            if (fStatus.isMediaFile()) return true;
        }
        if (directFMessage != null) {
            return directFMessage.isMediaFile();
        }
        if (fStatus != null) {
            FMessageWpp msg = fStatus.getFMessage();
            return msg != null && msg.isMediaFile();
        }
        return false;
    }

    @Nullable
    public File getMediaFile() {
        if (fStatus != null) {
            File mediaFile = fStatus.getMediaFile();
            if (mediaFile != null) return mediaFile;
        }
        if (directFMessage != null) {
            return directFMessage.getMediaFile();
        }
        if (fStatus != null) {
            FMessageWpp msg = fStatus.getFMessage();
            return msg != null ? msg.getMediaFile() : null;
        }
        return null;
    }

    @NonNull
    public String getCaption() {
        if (directFMessage != null) {
            String str = directFMessage.getMessageStr();
            return str != null ? str : "";
        }
        if (fStatus != null) {
            FMessageWpp msg = fStatus.getFMessage();
            if (msg != null) {
                String str = msg.getMessageStr();
                return str != null ? str : "";
            }
        }
        return "";
    }

    @Nullable
    public FStatusWpp getFStatus() {
        return fStatus;
    }
}
