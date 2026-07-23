package com.waenhancer.xposed.features.general;

import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.FStatusWpp;
import com.waenhancer.xposed.core.db.DelMessageStore;
import com.waenhancer.xposed.core.db.MessageStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.view.View;
import android.widget.ListView;
import androidx.recyclerview.widget.RecyclerView;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.utils.DesignUtils;
import java.util.HashSet;

public class AntiRevoke extends Feature {

    private static final ConcurrentHashMap<String, Set<String>> messageRevokedMap = new ConcurrentHashMap<>();
    // Global key_id → deletion timestamp map (bypasses JID mismatch between LID and phone number)
    private static final ConcurrentHashMap<String, Long> revokedKeyIds = new ConcurrentHashMap<>();
    private static final ThreadLocal<DateFormat> DATE_FORMAT_THREAD_LOCAL = ThreadLocal
            .withInitial(() -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                    Utils.getApplication().getResources().getConfiguration().getLocales().get(0)));

    public AntiRevoke(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Nullable
    private static Object findObjectFMessage(XC_MethodHook.MethodHookParam param) throws IllegalAccessException {
        if (param.args == null || param.args.length == 0)
            return null;

        for (Object arg : param.args) {
            if (arg == null) continue;
            if (FMessageWpp.TYPE.isInstance(arg)) return arg;
        }

        Object arg0 = param.args[0];
        if (arg0 == null) return null;

        // Try to find FMessage field in the object
        var FMessageField = ReflectionUtils.findFieldUsingFilterIfExists(arg0.getClass(),
                f -> FMessageWpp.TYPE.isAssignableFrom(f.getType()));
        if (FMessageField != null) {
            return FMessageField.get(arg0);
        }

        // Try status data logic
        try {
            var classLoader = arg0.getClass().getClassLoader();
            if (classLoader != null) {
                var mapFStatusToFMessage = Unobfuscator.loadFStatusToFMessage(classLoader);
                var fStatusClass = mapFStatusToFMessage.getParameterTypes()[0];
                var fStatusField = ReflectionUtils.findFieldUsingFilterIfExists(arg0.getClass(),
                        f -> fStatusClass.isAssignableFrom(f.getType()));
                if (fStatusField != null) {
                    return WppCore.getFMessageFromFStatus(fStatusField.get(arg0));
                }
            }
        } catch (Exception ignored) {}

        var field1 = ReflectionUtils.findFieldUsingFilterIfExists(arg0.getClass(),
                f -> f.getType() == FMessageWpp.Key.TYPE);
        if (field1 != null) {
            var key = field1.get(arg0);
            return WppCore.getFMessageFromKey(key);
        }
        return null;

    }

    private static void persistRevokedMessage(FMessageWpp fMessage, String messageId) {
        var stripJID = fMessage.getKey().remoteJid.getPhoneNumber();
        Set<String> messages = getRevokedMessagesForJid(fMessage);
        messages.add(messageId);
        DelMessageStore.getInstance(Utils.getApplication()).insertMessage(stripJID, messageId,
                System.currentTimeMillis());
    }

    private static Set<String> getRevokedMessagesForJid(FMessageWpp fMessage) {
        String stripJID = fMessage.getKey().remoteJid.getPhoneNumber();
        if (stripJID == null)
            return Collections.synchronizedSet(new HashSet<>());
        return messageRevokedMap.computeIfAbsent(stripJID, k -> {
            var messages = DelMessageStore.getInstance(Utils.getApplication()).getMessagesByJid(k);
            if (messages == null)
                return Collections.synchronizedSet(new HashSet<>());
            return Collections.synchronizedSet(messages);
        });
    }

    @Override
    public void doHook() throws Exception {

        var antiRevokeMessageMethod = Unobfuscator.loadAntiRevokeMessageMethod(classLoader);
        /* Log removed */

        var unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader);
        /* Log removed */

        Class<?> statusPlaybackClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader);
        /* Log removed */

        var antiRevokeFStatusMethod = Unobfuscator.loadAntiRevokeFStatusMethod(classLoader);
        /* Log removed */

        XposedBridge.hookMethod(antiRevokeFStatusMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null || param.args.length < 2 || param.args[1] == null) return;
                var fStatusKey = new FStatusWpp.FStatusKey(param.args[1]);
                var fStatus = fStatusKey.fStatus;
                if (fStatus == null) return;
                var fMessage = fStatus.getFMessage();
                if (fMessage == null) return;
                if (!fStatusKey.isFromMe && handleRevocationAttempt(fMessage, fStatusKey.messageID) != 0) {
                    param.setResult(0);
                }
            }
        });

        XposedBridge.hookMethod(antiRevokeMessageMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Exception {
                if (param.args == null || param.args.length == 0) return;
                
                Object fMessageObj = ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0);
                if (fMessageObj == null) {
                    /* Log removed */
                    return;
                }

                var fMessage = new FMessageWpp(fMessageObj);
                var messageKey = fMessage.getKey();
                var deviceJid = fMessage.getDeviceJid();
                String messageId;
                try {
                    messageId = (String) XposedHelpers.getObjectField(fMessage.getObject(), "A01");
                } catch (Throwable t) {
                    messageId = messageKey.messageID;
                }

                if (WppCore.getPrivBoolean(messageId + "_delpass", false)) {
                    WppCore.removePrivKey(messageId + "_delpass");
                    var activity = WppCore.getCurrentActivity();
                    Class<?> StatusPlaybackActivityClass = classLoader
                            .loadClass("com.whatsapp.status.playback.StatusPlaybackActivity");
                    if (activity != null && StatusPlaybackActivityClass.isInstance(activity)) {
                        activity.finish();
                    }
                    return;
                }

                if (messageKey.remoteJid.isGroup()) {
                    if (deviceJid != null && handleRevocationAttempt(fMessage, messageId) != 0) {
                        param.setResult(true);
                    }
                } else if (!messageKey.isFromMe && handleRevocationAttempt(fMessage, messageId) != 0) {
                    param.setResult(true);
                }
            }
        });

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                if (fMessage.getKey().isFromMe)
                    return;
                var dateTextView = (TextView) viewGroup.findViewById(Utils.getID("date", "id"));
                var messageTextView = (TextView) viewGroup.findViewById(Utils.getID("message_text", "id"));
                bindRevokedMessageUI(fMessage, dateTextView, messageTextView, "antirevoke");
            }
        });

        XposedBridge.hookMethod(unknownStatusPlaybackMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object obj = ReflectionUtils.getArg(param.args, param.method.getDeclaringClass(), 0);
                var objFMessage = findObjectFMessage(param);
                var field = ReflectionUtils.getFieldByType(param.method.getDeclaringClass(), statusPlaybackClass);

                if (obj == null || field == null || objFMessage == null)
                    return;

                Object objView = field.get(obj);
                if (objView == null)
                    return;

                var textViews = ReflectionUtils.getFieldsByType(statusPlaybackClass, TextView.class);
                if (textViews.isEmpty()) {
                    /* Log removed */
                    return;
                }
                int dateId = Utils.getID("date", "id");
                for (Field textView : textViews) {
                    TextView textView1 = (TextView) textView.get(objView);
                    if (textView1 != null && textView1.getId() == dateId) {
                        bindRevokedMessageUI(new FMessageWpp(objFMessage), textView1, null, "antirevokestatus");
                        break;
                    }
                }
            }
        });

        // SQL-level anti-revoke: block DELETE+INSERT revocation pattern
        addAntiRevokeSqlHooks();
    }

    /**
     * SQL-level anti-revoke protection.
     * 
     * WhatsApp's revocation flow (v2.26.19.2):
     *   1. DELETE FROM message WHERE _id=? args=[N]
     *   2. INSERT INTO message VALUES ... message_type=15 ... _id=N
     * 
     * Strategy: Block the INSERT of message_type=15 records (the "deleted" placeholder).
     * This preserves the original message because:
     *   - If we block the DELETE too, WhatsApp may retry or crash
     *   - If we only block the INSERT, the original row is gone but no "deleted" placeholder appears
     *   - Better: block the DELETE when followed by a type=15 INSERT
     * 
     * Refined strategy: Track recent DELETEs. When a type=15 INSERT arrives for the same _id,
     * block both the INSERT AND retroactively we know the DELETE was a revocation.
     * Since we can't undo the DELETE, we block the DELETE preemptively by checking if
     * from_me=0 for that row before allowing it.
     */
    private final Set<String> recentDeletedIds = Collections.synchronizedSet(new HashSet<>());

    private void addAntiRevokeSqlHooks() {
        int antiRevokeValue = Integer.parseInt(prefs.getString("antirevoke", "0"));
        if (antiRevokeValue == 0) {
            ;
            return;
        }

        try {
            // HOOK 1: Block DELETE FROM message — this is step 1 of WhatsApp's revocation.
            // We block ALL deletes on the message table for non-self messages.
            // Normal "delete for me" uses a different code path (not raw SQL delete).
            XposedBridge.hookAllMethods(SQLiteDatabase.class, "delete", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String table = (String) param.args[0];
                        if (!"message".equals(table)) return;
                        
                        String where = (String) param.args[1];
                        String[] whereArgs = param.args.length > 2 ? (String[]) param.args[2] : null;
                        
                        // Only intercept _id based deletes (revocation pattern)
                        if (where != null && where.contains("_id=?") && whereArgs != null && whereArgs.length > 0) {
                            String msgId = whereArgs[0];
                            
                            // Check if this message is from_me=0 (not our own message)
                            // by querying the DB before the delete happens
                            try {
                                SQLiteDatabase db = (SQLiteDatabase) param.thisObject;
                                Cursor cursor = db.rawQuery(
                                    "SELECT from_me, message_type, key_id, chat_row_id FROM message WHERE _id=?", 
                                    new String[]{msgId});
                                if (cursor != null && cursor.moveToFirst()) {
                                    int fromMe = cursor.getInt(0);
                                    int msgType = cursor.getInt(1);
                                    String keyId = cursor.getString(2);
                                    long chatRowId = cursor.getLong(3);
                                    cursor.close();
                                    
                                    // Block delete only for incoming messages (from_me=0) 
                                    // that are normal messages (not already type 15)
                                    if (fromMe == 0 && msgType != 15) {
                                        ;
                                        recentDeletedIds.add(msgId);
                                        
                                        // Record the revocation for UI display (red icon + timestamp)
                                        try {
                                            // Resolve phone number from chat_row_id
                                            // The jid table has columns: _id, user, server, agent, device, type, raw_string
                                            // We need the 'user' column which contains the phone number
                                            String phoneNumber = null;
                                            Cursor jidCursor = db.rawQuery(
                                                "SELECT j.user, j.raw_string FROM jid j " +
                                                "INNER JOIN chat c ON c.jid_row_id = j._id " +
                                                "WHERE c._id=?",
                                                new String[]{String.valueOf(chatRowId)});
                                            if (jidCursor != null && jidCursor.moveToFirst()) {
                                                phoneNumber = jidCursor.getString(0); // user column = phone number
                                                String rawString = jidCursor.getString(1);
                                                jidCursor.close();
                                                ;
                                                // Fallback: if user column is empty, parse from raw_string
                                                if (phoneNumber == null || phoneNumber.isEmpty()) {
                                                    if (rawString != null && rawString.contains("@")) {
                                                        phoneNumber = rawString.substring(0, rawString.indexOf("@"));
                                                    }
                                                }
                                            } else if (jidCursor != null) {
                                                jidCursor.close();
                                            }
                                            
                                            if (keyId != null) {
                                                long now = System.currentTimeMillis();
                                                
                                                // Store in global key-based map (always works regardless of JID format)
                                                revokedKeyIds.put(keyId, now);
                                                
                                                // Also try phone-number-based storage for backward compatibility
                                                if (phoneNumber != null) {
                                                    DelMessageStore.getInstance(Utils.getApplication())
                                                        .insertMessage(phoneNumber, keyId, now);
                                                    messageRevokedMap.computeIfAbsent(phoneNumber, k -> 
                                                        Collections.synchronizedSet(new HashSet<>())).add(keyId);
                                                }
                                                
                                                ;
                                                
                                                // Refresh the conversation UI if it's currently open
                                                try {
                                                    var mConversation = WppCore.getCurrentConversation();
                                                    if (mConversation != null) {
                                                        mConversation.runOnUiThread(() -> {
                                                            if (mConversation.hasWindowFocus()) {
                                                                mConversation.startActivity(mConversation.getIntent());
                                                                mConversation.overridePendingTransition(0, 0);
                                                            }
                                                        });
                                                    }
                                                } catch (Exception ignored) {}
                                            }
                                        } catch (Exception e) {
                                            XposedBridge.log("[WAEX] Error recording revocation: " + e.getMessage());
                                        }
                                        
                                        param.setResult(0); // Block the delete
                                        return;
                                    }
                                } else if (cursor != null) {
                                    cursor.close();
                                }
                            } catch (Exception e) {
                                XposedBridge.log("[WAEX] Error checking message before delete: " + e.getMessage());
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });

            // HOOK 2: Block INSERT of message_type=15 records (the "deleted" placeholder).
            // This is step 2 of WhatsApp's revocation — it inserts a new row with type=15.
            XposedBridge.hookAllMethods(SQLiteDatabase.class, "insertWithOnConflict", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String table = (String) param.args[0];
                        if (!"message".equals(table)) return;
                        
                        ContentValues cv = (ContentValues) param.args[2];
                        if (cv == null) return;
                        
                        Integer msgType = cv.getAsInteger("message_type");
                        Integer fromMe = cv.getAsInteger("from_me");
                        
                        // Block insertion of revocation placeholders (type=15) for incoming messages
                        if (msgType != null && msgType == 15 && fromMe != null && fromMe == 0) {
                            String keyId = cv.getAsString("key_id");
                            ;
                            param.setResult(-1L); // Return fake row ID, block the insert
                        }
                    } catch (Throwable ignored) {}
                }
            });

         } catch (Exception e) {
            XposedBridge.log("[WAEX] Failed to install anti-revoke SQL hooks: " + e.getMessage());
        }
    }


    private static String stringMessageDeleted;
    private static int antirevokeVal = -1;
    private static int antirevokeStatusVal = -1;

    private void bindRevokedMessageUI(FMessageWpp fMessage, TextView dateTextView, TextView messageTextView, String antirevokeType) {
        if (dateTextView == null) return;
        
        int antirevokeValue = 0;
        try {
            antirevokeValue = Integer.parseInt(prefs.getString(antirevokeType, "0"));
        } catch (Exception ignored) {}
        if (antirevokeValue == 0) return;

        var key = fMessage.getKey();
        var messageRevokedList = getRevokedMessagesForJid(fMessage);
        String originalMessage = (String) XposedHelpers.getAdditionalInstanceField(dateTextView, "originalMessage");

        Integer originalDateColor = (Integer) XposedHelpers.getAdditionalInstanceField(dateTextView, "originalColor");
        if (originalDateColor == null) {
            originalDateColor = dateTextView.getCurrentTextColor();
            XposedHelpers.setAdditionalInstanceField(dateTextView, "originalColor", originalDateColor);
        }

        Integer originalMessageColor = null;
        if (messageTextView != null) {
            originalMessageColor = (Integer) XposedHelpers.getAdditionalInstanceField(messageTextView, "originalColor");
            if (originalMessageColor == null) {
                originalMessageColor = messageTextView.getCurrentTextColor();
                XposedHelpers.setAdditionalInstanceField(messageTextView, "originalColor", originalMessageColor);
            }
        }

        int customColor = 0;
        try {
            customColor = prefs.getInt("deleted_message_color", 0);
        } catch (Exception e) {
            try {
                String colorStr = prefs.getString("deleted_message_color", "");
                if (!colorStr.isEmpty()) {
                    try {
                        customColor = Color.parseColor(colorStr);
                    } catch (Exception parseException) {
                        customColor = Integer.parseInt(colorStr);
                    }
                }
            } catch (Exception ignored) {}
        }

        String messageID = null;
        if (messageRevokedList.contains(key.messageID) || revokedKeyIds.containsKey(key.messageID) || DelMessageStore.getInstance(Utils.getApplication()).getTimestampByMessageId(key.messageID) > 0) {
            messageID = key.messageID;
        } else {
            String originalKey = MessageStore.getInstance().getOriginalMessageKey(fMessage.getRowId());
            if (originalKey != null && !originalKey.isEmpty() && (messageRevokedList.contains(originalKey) || revokedKeyIds.containsKey(originalKey) || DelMessageStore.getInstance(Utils.getApplication()).getTimestampByMessageId(originalKey) > 0)) {
                messageID = originalKey;
            }
        }

        if (messageID != null) {
            long timestamp = DelMessageStore.getInstance(Utils.getApplication()).getTimestampByMessageId(messageID);
            if (timestamp <= 0 && revokedKeyIds.containsKey(messageID)) {
                timestamp = revokedKeyIds.get(messageID);
            }
            if (timestamp > 0) {
                var date = Objects.requireNonNull(DATE_FORMAT_THREAD_LOCAL.get()).format(new Date(timestamp));
                dateTextView.getPaint().setUnderlineText(true);
                dateTextView.setOnClickListener(v -> {
                    String toastFormat = FeatureLoader.getModuleString(Utils.getApplication(), R.string.message_removed_on);
                    if (toastFormat == null || toastFormat.isEmpty() || !toastFormat.contains("%s")) {
                        toastFormat = "Deleted on: %s"; // Fallback
                    }
                    String toastMessage = String.format(toastFormat, date);
                    Utils.showToast(toastMessage, Toast.LENGTH_LONG);
                });
            }

            if (customColor != 0) {
                dateTextView.setTextColor(customColor);
                if (messageTextView != null) {
                    messageTextView.setTextColor(customColor);
                }
            }

            if (antirevokeValue == 1) {
                String messageText = originalMessage != null ? originalMessage : dateTextView.getText().toString();
                if (stringMessageDeleted == null) {
                    stringMessageDeleted = UnobfuscatorCache.getInstance().getString("messagedeleted");
                }
                String newTextData = (stringMessageDeleted != null ? stringMessageDeleted : "Deleted") + " | " + messageText;
                dateTextView.setText(newTextData);
                XposedHelpers.setAdditionalInstanceField(dateTextView, "originalMessage", messageText);
            } else if (antirevokeValue == 2) {
                var drawable = DesignUtils.getDrawable(R.drawable.deleted);
                dateTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                dateTextView.setCompoundDrawablePadding(5);
            }
        } else {
            if (originalDateColor != null) {
                dateTextView.setTextColor(originalDateColor);
            }
            if (messageTextView != null && originalMessageColor != null) {
                messageTextView.setTextColor(originalMessageColor);
            }
            dateTextView.setCompoundDrawables(null, null, null, null);
            if (originalMessage != null) {
                dateTextView.setText(originalMessage);
            }
            dateTextView.getPaint().setUnderlineText(false);
            dateTextView.setOnClickListener(null);
        }
    }

    private int handleRevocationAttempt(FMessageWpp fMessage, String messageId) {
        try {
            showRevocationToast(fMessage);
        } catch (Exception e) {
            log(e);
        }
        String stripJID = fMessage.getKey().remoteJid.getPhoneNumber();
        int revokeboolean = 0;
        try {
            revokeboolean = "status".equals(stripJID) ? Integer.parseInt(prefs.getString("antirevokestatus", "0"))
                    : Integer.parseInt(prefs.getString("antirevoke", "0"));
        } catch (Exception ignored) {}
        
        if (revokeboolean == 0) return revokeboolean;
        
        var messageRevokedList = getRevokedMessagesForJid(fMessage);
        if (!messageRevokedList.contains(messageId)) {
            try {
                CompletableFuture.runAsync(() -> {
                    persistRevokedMessage(fMessage, messageId);
                    try {
                        var mConversation = WppCore.getCurrentConversation();
                        if (mConversation != null && Objects.equals(stripJID, WppCore.getCurrentUserJid().getPhoneNumber())) {
                            mConversation.runOnUiThread(() -> {
                                if (mConversation.hasWindowFocus()) {
                                    // Fix shuttering: notify the RecyclerView adapter directly instead of restarting Activity
                                    View list = mConversation.findViewById(android.R.id.list);
                                    if (list instanceof RecyclerView) {
                                        RecyclerView rv = (RecyclerView) list;
                                        if (rv.getAdapter() != null) {
                                            rv.getAdapter().notifyDataSetChanged();
                                        }
                                    } else if (list instanceof ListView) {
                                        ((ListView) list).invalidateViews();
                                    } else {
                                        // Fallback
                                        View contentView = mConversation.getWindow().getDecorView().findViewById(android.R.id.content);
                                        if (contentView != null) contentView.postInvalidate();
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        logDebug(e);
                    }
                });
            } catch (Exception e) {
                logDebug(e);
            }
        }
        return revokeboolean;
    }

    private void showRevocationToast(FMessageWpp fMessage) {
        var jidAuthor = fMessage.getKey().remoteJid;
        var messageSuffix = FeatureLoader.getModuleString(Utils.getApplication(), R.string.deleted_message);
        if (jidAuthor.isStatus()) {
            messageSuffix = FeatureLoader.getModuleString(Utils.getApplication(), R.string.deleted_status);
            jidAuthor = fMessage.getUserJid();
        }
        if (jidAuthor.userJid == null)
            return;
        String name = WppCore.getContactName(jidAuthor);
        if (TextUtils.isEmpty(name)) {
            name = jidAuthor.getPhoneNumber();
        }
        String message;
        // Show "Participant deleted a message in GroupName" for group messages where we
        // know
        // who the sender is (!isNull). The old condition was inverted — it showed this
        // only
        // when getUserJid().isNull() which is exactly when we do NOT know the
        // participant.
        if (jidAuthor.isGroup() && !fMessage.getUserJid().isNull()) {
            var participantJid = fMessage.getUserJid();
            String participantName = WppCore.getContactName(participantJid);
            if (TextUtils.isEmpty(participantName)) {
                participantName = participantJid.getPhoneNumber();
            }
            message = Utils.getApplication().getString(R.string.deleted_a_message_in_group, participantName, name);
        } else {
            message = name + " " + messageSuffix;
        }
        if (prefs.getBoolean("toastdeleted", false)) {
            Utils.showToast(message, Toast.LENGTH_LONG);
        }
        Tasker.sendTaskerEvent(name, jidAuthor.getPhoneNumber(),
                jidAuthor.isStatus() ? "deleted_status" : "deleted_message");
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Anti Revoke";
    }

}