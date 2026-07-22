package com.waenhancer.xposed.features.general;

import android.content.Context;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.db.DeletedMessage;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.widget.Toast;
import com.waenhancer.BuildConfig;

public class RecoverDeleteForMe extends Feature {

    public RecoverDeleteForMe(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        try {
            Class<?> cms = Unobfuscator.loadCoreMessageStore(classLoader);

            // Dynamic method search based on signature: (Any, Collection, int)
            Method targetMethod = null;
            for (Method m : cms.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && Collection.class.isAssignableFrom(p[1]) && p[2] == int.class) {
                    targetMethod = m;
                    ;
                    break; // Assuming only one method matches this signature in CoreMessageStore
                }
            }

            if (targetMethod == null) {
                ;
                return;
            }

            XposedBridge.hookMethod(targetMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        ;
                        Collection<?> msgs = (Collection<?>) param.args[1];
                        if (msgs == null) {
                            ;
                            return;
                        }
                        if (msgs.isEmpty()) {
                            ;
                            return;
                        }
                        Context ctx = Utils.getApplication();
                        if (ctx == null) {
                            ;
                            return;
                        }
                        for (Object msg : msgs) {
                            try {
                                saveOne(ctx, msg);
                            } catch (Throwable t) {
                                XposedBridge.log("[WAEX] RecoverDeleteForMe saveOne: " + t.getMessage());
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[WAEX] RecoverDeleteForMe hook: " + t.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            XposedBridge.log("[WAEX] RecoverDeleteForMe init: " + e.getMessage());
        }
    }

    private void saveOne(Context context, Object msg) throws Exception {
        if (msg == null)
            return;
        
        FMessageWpp fMessage = new FMessageWpp(msg);
        Class<?> msgClass = msg.getClass();
        var key = fMessage.getKey();
        if (key == null) return;

        // 2. Extract Message ID (Key ID)
        String keyId = key.messageID;
        if (keyId == null) return;

        // 3. Extract RemoteJid / ChatJid
        String chatJid = key.remoteJid.getPhoneRawString();
        if (chatJid == null) return;
        if (chatJid.equalsIgnoreCase("false") || chatJid.equalsIgnoreCase("true")) {
            chatJid = null;
        }

        // 4. Extract isFromMe
        boolean fromMe = key.isFromMe;

        // 5. Media Type
        int mediaType = fMessage.getMediaType();

        // 6. Extract Text Body
        String textContent = fMessage.getMessageStr();

        // Safety check: If it's a media message, discard "text" if it looks like a URL
        // or Hash
        if (mediaType > 0 && textContent != null) {
            if (textContent.startsWith("http") || (textContent.length() > 20 && !textContent.contains(" "))) {
                textContent = null;
            }
        }

        // Heuristic search: Only if text is null AND it's NOT a media message
        if (textContent == null && mediaType <= 0) {
            String bestCandidate = null;
            Class<?> cls = msgClass;
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType().equals(String.class)) {
                        f.setAccessible(true);
                        try {
                            String val = (String) f.get(msg);
                            if (val != null && !val.isEmpty()) {
                                // Determine if this value is safe (not a URL, not a hash)
                                boolean isUrl = val.startsWith("http") || val.startsWith("www.");
                                boolean isHash = val.length() > 20 && !val.contains(" ");

                                if (!isUrl && !isHash) {
                                    if (bestCandidate == null || val.length() > bestCandidate.length()) {
                                        if (val.length() > 1)
                                            bestCandidate = val;
                                    }
                                }
                            }
                        } catch (IllegalAccessException e) {
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
            if (bestCandidate != null)
                textContent = bestCandidate;
        }

        // 7. Sender JID
        String senderJid = fromMe ? "Me" : null;
        var participantJid = fMessage.getUserJid();
        if (participantJid != null) {
            senderJid = participantJid.getUserRawString();
        }
        Object participant = fMessage.getDeviceJid(); // Using deviceJid as a proxy for participant if needed, or just remove the check if getUserJid is enough
        if (participant != null) {
            String val = null;
            // Try getRawString() first to get 'number@s.whatsapp.net'
            try {
                Object rawStr = participant.getClass().getMethod("getRawString").invoke(participant);
                if (rawStr instanceof String && !((String) rawStr).isEmpty()) {
                    val = (String) rawStr;
                }
            } catch (Throwable ignored) {
            }
            if (val == null)
                val = participant.toString();
            if (!val.equalsIgnoreCase("false") && !val.equalsIgnoreCase("true") && !val.isEmpty()) {
                senderJid = val;
            }
        }
        // Final fallback: use chatJid only for non-group personal chats
        if (senderJid == null) {
            boolean isGroupChat = chatJid != null && chatJid.endsWith("@g.us");
            senderJid = isGroupChat ? "Unknown" : chatJid;
        }

        // 8. Media Details - media file storage is handled entirely by pro module
        String mediaCaption = null;
        if (mediaType > 0) {
            String rawCaption = fMessage.getMessageStr();
            if (rawCaption != null && !rawCaption.isEmpty()
                    && !rawCaption.startsWith("http")
                    && !(rawCaption.length() > 20 && !rawCaption.contains(" "))) {
                mediaCaption = rawCaption;
            }
        }

        long timestamp = System.currentTimeMillis();

        // 9. Contact Name Resolution
        String contactName = null;
        try {
            // Priority 1: Current Chat Room Title (Most Reliable as per User Suggestion)
            contactName = WppCore.getCurrentChatTitle();

            // Priority 2: WaContactWpp Internal Lookup (New Reliable Fallback)
            if (contactName == null && chatJid != null) {
                try {
                    ;
                    FMessageWpp.UserJid userJidObj = new FMessageWpp.UserJid(chatJid);
                    WaContactWpp waContact = WaContactWpp.getWaContactFromJid(userJidObj);
                    if (waContact != null) {
                        contactName = waContact.getDisplayName();
                        if (contactName == null || contactName.isEmpty()) {
                            contactName = waContact.getWaName();
                        }
                        ;
                    } else {
                        ;
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] WaContactWpp lookup failed: " + t.getMessage());
                }
            }

            // Priority 3: Simple ContactsContract lookup (Fallback)
            if (contactName == null && chatJid != null && !chatJid.contains("@g.us")) {
                contactName = getContactName(context, chatJid);
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error in contact resolution: " + t.getMessage());
        }

        // Capture Package Name
        String packageName = context.getPackageName();

        // Capture original timestamp
        long originalTimestamp = 0;
        try {
            // 1. Try finding 'timestamp' (long)
            Field tsField = findField(msgClass, "timestamp");
            if (tsField == null)
                tsField = findField(msgClass, "g"); // Common obfuscation

            if (tsField != null) {
                Object val = tsField.get(msg);
                if (val instanceof Long) {
                    originalTimestamp = (Long) val;
                }
            }

            // 2. Fallback: Search for any long field that looks like a timestamp (e.g. >
            // 1600000000000)
            if (originalTimestamp == 0) {
                Class<?> cls = msgClass;
                while (cls != null && cls != Object.class) {
                    for (Field f : cls.getDeclaredFields()) {
                        if (f.getType() == long.class) {
                            f.setAccessible(true);
                            long val = f.getLong(msg);
                            if (val > 1500000000000L && val < System.currentTimeMillis() + 86400000L) {
                                originalTimestamp = val;
                                break;
                            }
                        }
                    }
                    if (originalTimestamp != 0)
                        break;
                    cls = cls.getSuperclass();
                }
            }
        } catch (Exception e) {
            XposedBridge.log("[WAEX] Error extracting original timestamp: " + e.getMessage());
        }

        // Create and Save (media_path left null — pro module populates it when recover_deleted_media is enabled)
        DeletedMessage deletedMessage = new DeletedMessage(
                0, keyId, chatJid, senderJid, timestamp, originalTimestamp, mediaType, textContent, null,
                mediaCaption, fromMe,
                contactName, packageName);

        saveToDatabase(context, deletedMessage);
    }


    private String getContactName(Context context, String jid) {
        if (jid == null)
            return null;
        String phoneNumber = jid.replace("@s.whatsapp.net", "").replace("@g.us", "");
        if (phoneNumber.contains("@"))
            phoneNumber = phoneNumber.split("@")[0];

        try {
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber));
            String[] projection = new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME };

            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveToDatabase(Context context, DeletedMessage message) {
        try {
            ContentValues values = new ContentValues();
            values.put("key_id", message.getKeyId());
            values.put("chat_jid", message.getChatJid());
            values.put("sender_jid", message.getSenderJid());
            values.put("timestamp", message.getTimestamp());
            values.put("original_timestamp", message.getOriginalTimestamp());
            values.put("media_type", message.getMediaType());
            values.put("text_content", message.getTextContent());
            values.put("media_caption", message.getMediaCaption());
            values.put("is_from_me", message.isFromMe() ? 1 : 0);
            values.put("contact_name", message.getContactName());
            values.put("package_name", message.getPackageName());

            // Check if Pro module has a pending media path for this message
            String mediaPath = null;
            if (message.getKeyId() != null) {
                try {
                    ClassLoader proLoader = (ClassLoader) System.getProperties().get("com.waex.helper.classloader");
                    if (proLoader != null) {
                        Class<?> proClass = proLoader.loadClass("com.waex.helper.RecoverDeletedMediaPro");
                        Method consumeMethod = proClass.getMethod("consumePendingMediaPath", String.class);
                        mediaPath = (String) consumeMethod.invoke(null, message.getKeyId());
                    }
                } catch (ClassNotFoundException e) {
                    // Pro module not available — that's expected for free users
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] consumePendingMediaPath reflection failed: " + t.getMessage());
                }
            }
            if (mediaPath != null) {
                values.put("media_path", mediaPath);
            }

            String authority = BuildConfig.APPLICATION_ID + ".provider";
            Uri uri = Uri.parse("content://" + authority + "/deleted_messages");
            context.getContentResolver().insert(uri, values);
            ;
        } catch (Exception e) {
            XposedBridge.log("[WAEX] Failed to insert to provider: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Scans hierarchy for ALL fields of type, returns first non-null value
    private Object getFirstNonNullFieldByType(Object target, Class<?> type) {
        if (target == null || type == null)
            return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(target);
                        if (val != null)
                            return val;
                    } catch (IllegalAccessException e) {
                        // ignore
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private String getFirstNonNullStringField(Object target) {
        if (target == null)
            return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(String.class)) {
                    f.setAccessible(true);
                    try {
                        String val = (String) f.get(target);
                        if (val != null && !val.isEmpty())
                            return val;
                    } catch (IllegalAccessException e) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private Field findFieldByType(Class<?> cls, Class<?> type) {
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    f.setAccessible(true);
                    return f;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private String getStr(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null)
                return null;
            Object v = f.get(obj);
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Object getObj(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null)
                return null;
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getPluginName() {
        return "Recover Delete For Me";
    }

    public static void restoreMessage(Context context, DeletedMessage message) {
        try {
            if (message.getTextContent() != null && !message.getTextContent().isEmpty()) {
                Toast
                        .makeText(context, "Message: " + message.getTextContent(), Toast.LENGTH_LONG)
                        .show();
            } else {
                Toast
                        .makeText(context, "Media restore not supported yet", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            XposedBridge.log("[WAEX] Restore failed: " + e.getMessage());
        }
    }
}