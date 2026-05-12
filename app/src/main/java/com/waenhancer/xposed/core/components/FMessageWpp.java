package com.waenhancer.xposed.core.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.db.MessageStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * @noinspection unused
 */
public class FMessageWpp {

    public static Class<?> TYPE;
    private static Method userJidMethod;
    private static Field keyMessage;
    private static Field getFieldIdMessage;
    private static Field deviceJidField;
    private static Method messageMethod;
    private static Method messageWithMediaMethod;
    private static Field mediaTypeField;
    private static Method getOriginalMessageKey;
    private static Class abstractMediaMessageClass;
    private static Field broadcastField;
    private static Field keyIdField;
    private static Field keyFromMeField;
    private static Field keyRemoteJidField;
    private final Object fmessage;
    private Key key;
    private static final Set<String> VALID_DOMAINS = Set.of(
            "s.whatsapp.net", "newsletter", "lid", "g.us", "broadcast", "status"
    );

    public FMessageWpp(Object fMessage) {
        if (fMessage == null) throw new RuntimeException("Object fMessage is null");
        if (!FMessageWpp.TYPE.isInstance(fMessage))
            throw new RuntimeException("Object fMessage is not a FMessage Instance");
        this.fmessage = fMessage;
    }

    public static void initialize(ClassLoader classLoader) {
        try {
            TYPE = Unobfuscator.loadFMessageClass(classLoader);
            UserJid.TYPE_USERJID = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.UserJid");
            UserJid.TYPE_JID = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
            UserJid.TYPE_PHONEUSERJID = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.PhoneUserJid");
            UserJid.TYPE_DEVICEJID = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.DeviceJid");
            var userJidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.UserJid");
            userJidMethod = ReflectionUtils.findMethodUsingFilter(TYPE, method -> method.getParameterCount() == 0 && method.getReturnType() == userJidClass);
            keyMessage = Unobfuscator.loadMessageKeyField(classLoader);
            Key.TYPE = keyMessage.getType();
            messageMethod = Unobfuscator.loadNewMessageMethod(classLoader);
            messageWithMediaMethod = Unobfuscator.loadNewMessageWithMediaMethod(classLoader);
            var deviceJidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.DeviceJid");
            deviceJidField = ReflectionUtils.findFieldUsingFilter(TYPE, field -> field.getType() == deviceJidClass);
            mediaTypeField = Unobfuscator.loadMediaTypeField(classLoader);
            getOriginalMessageKey = Unobfuscator.loadOriginalMessageKey(classLoader);
            abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader);
            broadcastField = Unobfuscator.loadBroadcastTagField(classLoader);
            getFieldIdMessage = Unobfuscator.loadSetEditMessageField(classLoader);

            // Initialize Key fields dynamically
            if (Key.TYPE != null) {
                keyIdField = ReflectionUtils.getFieldByType(Key.TYPE, String.class);
                keyFromMeField = ReflectionUtils.getFieldByType(Key.TYPE, boolean.class);
                keyRemoteJidField = ReflectionUtils.getFieldByExtendType(Key.TYPE, UserJid.TYPE_JID);
                ;
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public static boolean checkUnsafeIsFMessage(ClassLoader classLoader, Class<?> clazz) throws Exception {
        Class<?> FmessageClass = Unobfuscator.loadFMessageClass(classLoader);
        if (FmessageClass.isAssignableFrom(clazz)) return true;
        var interfaces = FmessageClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface == clazz) return true;
        }
        return false;
    }


    public UserJid getUserJid() {
        try {
            return new UserJid(userJidMethod.invoke(fmessage));
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public Object getDeviceJid() {
        try {
            return deviceJidField.get(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public long getRowId() {
        try {
            return getFieldIdMessage.getLong(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return 0;
    }


    public Key getKey() {
        try {
            if (this.key == null)
                this.key = new Key(keyMessage.get(fmessage), this);
            return key;
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public Key getOriginalKey() {
        try {
            return new Key(getOriginalMessageKey.invoke(fmessage), this);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public boolean isBroadcast() {
        try {
            return broadcastField.getBoolean(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return false;
    }

    public Object getObject() {
        return fmessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FMessageWpp that = (FMessageWpp) o;
        return Objects.equals(fmessage, that.fmessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fmessage);
    }

    public String getMessageStr() {
        try {
            var message = (String) messageMethod.invoke(fmessage);
            if (message != null) return message;
            return (String) messageWithMediaMethod.invoke(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    public boolean isMediaFile() {
        try {
            if (abstractMediaMessageClass == null) return false;
            return abstractMediaMessageClass.isInstance(fmessage);
        } catch (Exception e) {
            return false;
        }
    }

    public File getMediaFile() {
        try {
            if (!isMediaFile() || abstractMediaMessageClass == null) return null;
            for (var field : abstractMediaMessageClass.getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                var fileField = ReflectionUtils.getFieldByType(field.getType(), File.class);
                if (fileField != null) {
                    var mediaObject = ReflectionUtils.getObjectField(field, fmessage);
                    if (mediaObject == null) continue;
                    var mediaFile = (File) fileField.get(mediaObject);
                    if (mediaFile != null) return mediaFile;
                    var filePath = MessageStore.getInstance().getMediaFromID(getRowId());
                    if (filePath == null) return null;
                    return new File(filePath);
                }
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    /**
     * Gets the media type of the message.
     * Media type values:
     * 2 = Voice note
     * 82 = View once voice note
     * 42 = View once image
     * 43 = View once video
     *
     * @return The media type as an integer, or -1 if an error occurs
     */
    public int getMediaType() {
        try {
            if (mediaTypeField == null) return -1;
            return mediaTypeField.getInt(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return -1;
    }

    public boolean isViewOnce() {
        var media_type = getMediaType();
        return (media_type == 82 || media_type == 42 || media_type == 43);
    }

    public int getDeviceId() {
        Object deviceJid = getDeviceJid();
        if (deviceJid == null) return -1;
        try {
            return (Integer) XposedHelpers.callMethod(deviceJid, "getDevice");
        } catch (Throwable ignored) {
        }
        try {
            Field field = ReflectionUtils.getFieldByType(deviceJid.getClass(), byte.class);
            if (field != null) {
                return field.getByte(deviceJid) & 0xFF;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public boolean hasDeviceSource() {
        return getDeviceId() >= 0;
    }

    public boolean isPrimaryDeviceMessage() {
        return getDeviceId() == 0;
    }

    public boolean isLinkedDeviceMessage() {
        int deviceId = getDeviceId();
        return deviceId > 0;
    }

    public String dumpDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("FMessageDebug{");
        tryAppend(sb, "class", fmessage.getClass().getName());
        tryAppend(sb, "rowId", getRowId());
        tryAppend(sb, "message", getMessageStr());
        tryAppend(sb, "mediaType", getMediaType());
        tryAppend(sb, "isBroadcast", isBroadcast());

        Key key = getKey();
        if (key != null) {
            tryAppend(sb, "messageId", key.messageID);
            tryAppend(sb, "fromMe", key.isFromMe);
            tryAppend(sb, "remoteJid", key.remoteJid);
        }

        Object userJid = null;
        try {
            userJid = getUserJid();
        } catch (Throwable ignored) {}
        tryAppend(sb, "userJid", userJid);

        Object deviceJid = getDeviceJid();
        tryAppend(sb, "deviceJid", deviceJid);
        if (deviceJid != null) {
            appendObjectFields(sb, "deviceJidFields", deviceJid);
            appendZeroArgMethods(sb, "deviceJidMethods", deviceJid);
        }

        appendObjectFields(sb, "keyFields", key != null ? key.thisObject : null);
        appendObjectFields(sb, "messageFields", fmessage);
        sb.append(" }");
        return sb.toString();
    }

    private static void tryAppend(StringBuilder sb, String label, Object value) {
        sb.append(label).append("=").append(safeValue(value)).append("; ");
    }

    private static String safeValue(Object value) {
        if (value == null) return "null";
        try {
            return String.valueOf(value);
        } catch (Throwable t) {
            return value.getClass().getName() + "@toStringError";
        }
    }

    private static void appendObjectFields(StringBuilder sb, String label, Object target) {
        if (target == null) {
            tryAppend(sb, label, null);
            return;
        }
        StringBuilder fieldsSb = new StringBuilder();
        Class<?> current = target.getClass();
        int depth = 0;
        while (current != null && current != Object.class && depth < 3) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    fieldsSb.append(current.getSimpleName())
                            .append(".")
                            .append(field.getName())
                            .append("=")
                            .append(safeValue(field.get(target)))
                            .append(", ");
                } catch (Throwable t) {
                    fieldsSb.append(current.getSimpleName())
                            .append(".")
                            .append(field.getName())
                            .append("=<")
                            .append(t.getClass().getSimpleName())
                            .append(">, ");
                }
            }
            current = current.getSuperclass();
            depth++;
        }
        tryAppend(sb, label, fieldsSb);
    }

    private static void appendZeroArgMethods(StringBuilder sb, String label, Object target) {
        if (target == null) {
            tryAppend(sb, label, null);
            return;
        }
        StringBuilder methodsSb = new StringBuilder();
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;
            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE) continue;
            String methodName = method.getName();
            if (!(methodName.startsWith("get") || methodName.startsWith("is") || methodName.startsWith("to"))) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(target);
                methodsSb.append(methodName)
                        .append("=")
                        .append(safeValue(result))
                        .append(", ");
            } catch (Throwable t) {
                methodsSb.append(methodName)
                        .append("=<")
                        .append(t.getClass().getSimpleName())
                        .append(">, ");
            }
        }
        tryAppend(sb, label, methodsSb);
    }

    /*
     * Represents the key of a WhatsApp message, containing identifiers for the message.
     */
    public static class Key {

        /**
         * The class type of the key object.
         */
        public static Class<?> TYPE;

        /**
         * The wrapped FMessageWpp instance associated with this key.
         */
        private FMessageWpp fmessage;
        /**
         * The underlying key object from WhatsApp's code.
         */
        public Object thisObject;
        /**
         * The unique identifier for the message.
         */
        public String messageID;
        /**
         * A boolean indicating if the message was sent by the current user.
         */
        public boolean isFromMe;
        /**
         * The JID of whatsapp
         */
        public UserJid remoteJid;

        /**
         * Constructs a new Key instance by wrapping the original WhatsApp message key object.
         *
         * @param key The original message key object.
         */
        public Key(Object key) {
            this.thisObject = key;
            try {
                this.messageID = (keyIdField != null) ? (String) keyIdField.get(key) : (String) XposedHelpers.getObjectField(key, "A01");
                this.isFromMe = (keyFromMeField != null) ? keyFromMeField.getBoolean(key) : XposedHelpers.getBooleanField(key, "A02");
                Object jidObj = (keyRemoteJidField != null) ? keyRemoteJidField.get(key) : XposedHelpers.getObjectField(key, "A00");
                this.remoteJid = new UserJid(jidObj);
            } catch (Exception e) {
                XposedBridge.log("[WAE] Error initializing FMessageWpp.Key: " + e.getMessage());
                // Fallback to old hardcoded names if dynamic resolution fails (though unlikely to help if it fails)
                try {
                    this.messageID = (String) XposedHelpers.getObjectField(key, "A01");
                    this.isFromMe = XposedHelpers.getBooleanField(key, "A02");
                    this.remoteJid = new UserJid(XposedHelpers.getObjectField(key, "A00"));
                } catch (Exception ignored) {}
            }
            var fmessage = WppCore.getFMessageFromKey(key);
            if (fmessage != null) {
                this.fmessage = new FMessageWpp(fmessage);
            }
        }

        public Key(Object key, FMessageWpp fmessage) {
            this.thisObject = key;
            try {
                this.messageID = (keyIdField != null) ? (String) keyIdField.get(key) : (String) XposedHelpers.getObjectField(key, "A01");
                this.isFromMe = (keyFromMeField != null) ? keyFromMeField.getBoolean(key) : XposedHelpers.getBooleanField(key, "A02");
                Object jidObj = (keyRemoteJidField != null) ? keyRemoteJidField.get(key) : XposedHelpers.getObjectField(key, "A00");
                this.remoteJid = new UserJid(jidObj);
            } catch (Exception e) {
                try {
                    this.messageID = (String) XposedHelpers.getObjectField(key, "A01");
                    this.isFromMe = XposedHelpers.getBooleanField(key, "A02");
                    this.remoteJid = new UserJid(XposedHelpers.getObjectField(key, "A00"));
                } catch (Exception ignored) {}
            }
            this.fmessage = fmessage;
        }

        public Key(String messageID, UserJid remoteJid, boolean isFromMe) {
            this.messageID = messageID;
            this.isFromMe = isFromMe;
            this.remoteJid = remoteJid;
            var key = XposedHelpers.newInstance(FMessageWpp.Key.TYPE, remoteJid.userJid, messageID, false);
            var fmessage = WppCore.getFMessageFromKey(key);
            if (fmessage != null) {
                this.thisObject = key;
                this.fmessage = new FMessageWpp(fmessage);
            } else {
                key = XposedHelpers.newInstance(FMessageWpp.Key.TYPE, remoteJid.phoneJid, messageID, false);
                fmessage = WppCore.getFMessageFromKey(key);
                if (fmessage != null) {
                    this.thisObject = key;
                    this.fmessage = new FMessageWpp(fmessage);
                }
            }
        }

        public FMessageWpp getFMessage() {
            return fmessage;
        }

        @NonNull
        @Override
        public String toString() {
            return "Key{" +
                    "thisObject=" + thisObject +
                    ", messageID='" + messageID + '\'' +
                    ", isFromMe=" + isFromMe +
                    ", remoteJid=" + remoteJid +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return isFromMe == key.isFromMe && Objects.equals(messageID, key.messageID) && Objects.equals(remoteJid, key.remoteJid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageID, isFromMe, remoteJid);
        }
    }

    public static class UserJid {

        public static Class<?> TYPE_DEVICEJID;
        public static Class<?> TYPE_USERJID;
        public static Class<?> TYPE_JID;
        public static Class<?> TYPE_PHONEUSERJID;

        public Object phoneJid;

        public Object userJid;

        public UserJid() {

        }

        public UserJid(@Nullable String rawjid) {
            if (isInvalidJid(rawjid)) return;
            if (checkValidLID(rawjid)) {
                this.userJid = WppCore.createUserJid(rawjid);
                this.phoneJid = WppCore.getPhoneJidFromUserJid(this.userJid);
            } else {
                this.phoneJid = WppCore.createUserJid(rawjid);
                this.userJid = WppCore.getUserJidFromPhoneJid(this.phoneJid);
            }
        }


        public UserJid(@Nullable Object lidOrJid) {
            if (lidOrJid == null) return;
            String raw;
            try {
                raw = (String) XposedHelpers.callMethod(lidOrJid, "getRawString");
            } catch (Throwable ignored) {
                return;
            }
            if (isInvalidJid(raw)) return;
            if (checkValidLID(raw)) {
                this.userJid = lidOrJid;
                this.phoneJid = WppCore.getPhoneJidFromUserJid(this.userJid);
            } else {
                this.phoneJid = lidOrJid;
                this.userJid = WppCore.getUserJidFromPhoneJid(this.phoneJid);
            }
        }

        public UserJid(@Nullable Object userJid, Object phoneJid) {
            this.userJid = userJid;
            this.phoneJid = phoneJid;
        }


        @Nullable
        public String getPhoneRawString() {
            if (this.phoneJid == null) return null;
            String raw = (String) XposedHelpers.callMethod(this.phoneJid, "getRawString");
            if (raw == null) return null;
            return raw.replaceFirst("\\.[\\d:]+@", "@");
        }

        @Nullable
        public String getUserRawString() {
            if (this.phoneJid == null) return null;
            String raw = (String) XposedHelpers.callMethod(this.userJid, "getRawString");
            if (raw == null) return null;
            return raw.replaceFirst("\\.[\\d:]+@", "@");
        }

        @Nullable
        public String getPhoneNumber() {
            var str = getPhoneRawString();
            try {
                if (str == null) return null;
                if (str.contains(".") && str.contains("@") && str.indexOf(".") < str.indexOf("@")) {
                    return str.substring(0, str.indexOf("."));
                } else if (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast") || str.contains("@lid")) {
                    return str.substring(0, str.indexOf("@"));
                }
                return str;
            } catch (Exception e) {
                XposedBridge.log(e);
                return str;
            }
        }

        private boolean isInvalidJid(String rawjid) {
            if (rawjid == null) return false;
            int atIndex = rawjid.indexOf('@');
            if (atIndex == -1 || atIndex == rawjid.length() - 1) {
                return false;
            }
            String domain = rawjid.substring(atIndex + 1);
            return !VALID_DOMAINS.contains(domain);
        }

        public boolean isStatus() {
            return Objects.equals(getPhoneNumber(), "status");
        }

        public boolean isNewsletter() {
            String raw = getPhoneRawString();
            if (raw == null) return false;
            return raw.endsWith("@newsletter");
        }

        public boolean isBroadcast() {
            String raw = getPhoneRawString();
            if (raw == null) return false;
            return raw.endsWith("@broadcast");
        }

        public boolean isGroup() {
            if (this.phoneJid == null) return false;
            String str = getPhoneRawString();
            if (str == null) return false;
            return str.endsWith("@g.us");
        }


        public boolean isContact() {
            if (this.userJid != null) {
                var raw = getUserRawString();
                return raw != null && raw.endsWith("@lid");
            }
            String str = getPhoneRawString();
            return str != null && str.endsWith("@s.whatsapp.net");
        }


        public boolean isNull() {
            return this.phoneJid == null && this.userJid == null;
        }

        private static boolean checkValidLID(String lid) {
            return lid != null && lid.endsWith("@lid");
        }

        @NonNull
        @Override
        public String toString() {
            return "UserJid{" +
                    "PhoneJid=" + phoneJid +
                    ", UserJid=" + userJid +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserJid userJid1 = (UserJid) o;
            return Objects.equals(getPhoneRawString(), userJid1.getPhoneRawString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getPhoneRawString());
        }
    }

}
