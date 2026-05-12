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

        if (FMessageWpp.TYPE.isInstance(param.args[0]))
            return param.args[0];

        if (param.args.length > 1) {
            if (FMessageWpp.TYPE.isInstance(param.args[1]))
                return param.args[1];
            var FMessageField = ReflectionUtils.findFieldUsingFilterIfExists(param.args[1].getClass(),
                    f -> FMessageWpp.TYPE.isAssignableFrom(f.getType()));
            if (FMessageField != null) {
                return FMessageField.get(param.args[1]);
            }
        }

        var field = ReflectionUtils.findFieldUsingFilterIfExists(param.args[0].getClass(),
                f -> f.getType() == FMessageWpp.TYPE);
        if (field != null)
            return field.get(param.args[0]);

        var field1 = ReflectionUtils.findFieldUsingFilterIfExists(param.args[0].getClass(),
                f -> f.getType() == FMessageWpp.Key.TYPE);
        if (field1 != null) {
            var key = field1.get(param.args[0]);
            return WppCore.getFMessageFromKey(key);
        }
        return null;

    }

    private static void persistRevokedMessage(FMessageWpp fMessage) {
        var messageKey = (String) XposedHelpers.getObjectField(fMessage.getObject(), "A01");
        var stripJID = fMessage.getKey().remoteJid.getPhoneNumber();
        Set<String> messages = getRevokedMessagesForJid(fMessage);
        messages.add(messageKey);
        DelMessageStore.getInstance(Utils.getApplication()).insertMessage(stripJID, messageKey,
                System.currentTimeMillis());
    }

    private static Set<String> getRevokedMessagesForJid(FMessageWpp fMessage) {
        String stripJID = fMessage.getKey().remoteJid.getPhoneNumber();
        if (stripJID == null)
            return Collections.synchronizedSet(new java.util.HashSet<>());
        return messageRevokedMap.computeIfAbsent(stripJID, k -> {
            var messages = DelMessageStore.getInstance(Utils.getApplication()).getMessagesByJid(k);
            if (messages == null)
                return Collections.synchronizedSet(new java.util.HashSet<>());
            return Collections.synchronizedSet(messages);
        });
    }

    @Override
    public void doHook() throws Exception {

        var antiRevokeMessageMethod = Unobfuscator.loadAntiRevokeMessageMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(antiRevokeMessageMethod));

        var unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(unknownStatusPlaybackMethod));

        Class<?> statusPlaybackClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader);
        logDebug(statusPlaybackClass);

        XposedBridge.hookMethod(antiRevokeMessageMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Exception {
                if (param.args == null || param.args.length == 0 || param.args[0] == null)
                    return;

                var fMessage = new FMessageWpp(param.args[0]);
                var messageKey = fMessage.getKey();
                var deviceJid = fMessage.getDeviceJid();
                var messageID = (String) XposedHelpers.getObjectField(fMessage.getObject(), "A01");

                if (WppCore.getPrivBoolean(messageID + "_delpass", false)) {
                    WppCore.removePrivKey(messageID + "_delpass");
                    var activity = WppCore.getCurrentActivity();
                    Class<?> StatusPlaybackActivityClass = classLoader
                            .loadClass("com.whatsapp.status.playback.StatusPlaybackActivity");
                    if (activity != null && StatusPlaybackActivityClass.isInstance(activity)) {
                        activity.finish();
                    }
                    return;
                }
                // For group messages: intercept any non-self revocation regardless of
                // deviceJid.
                // Previously the deviceJid != null guard caused all group revocations where
                // deviceJid is null (the common case for other participants) to be silently
                // skipped.
                if (messageKey.remoteJid.isGroup()) {
                    if (!messageKey.isFromMe && handleRevocationAttempt(fMessage) != 0) {
                        param.setResult(true);
                    }
                } else if (!messageKey.isFromMe && handleRevocationAttempt(fMessage) != 0) {
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
                bindRevokedMessageUI(fMessage, dateTextView, "antirevoke");
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
                    log("Could not find TextView");
                    return;
                }
                int dateId = Utils.getID("date", "id");
                for (Field textView : textViews) {
                    TextView textView1 = (TextView) textView.get(objView);
                    if (textView1 != null && textView1.getId() == dateId) {
                        bindRevokedMessageUI(new FMessageWpp(objFMessage), textView1, "antirevokestatus");
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
    private final java.util.Set<String> recentDeletedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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
            XposedBridge.hookAllMethods(android.database.sqlite.SQLiteDatabase.class, "delete", new XC_MethodHook() {
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
                                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) param.thisObject;
                                android.database.Cursor cursor = db.rawQuery(
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
                                            android.database.Cursor jidCursor = db.rawQuery(
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
                                                        Collections.synchronizedSet(new java.util.HashSet<>())).add(keyId);
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
                                            XposedBridge.log("WAE: Error recording revocation: " + e.getMessage());
                                        }
                                        
                                        param.setResult(0); // Block the delete
                                        return;
                                    }
                                } else if (cursor != null) {
                                    cursor.close();
                                }
                            } catch (Exception e) {
                                XposedBridge.log("WAE: Error checking message before delete: " + e.getMessage());
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });

            // HOOK 2: Block INSERT of message_type=15 records (the "deleted" placeholder).
            // This is step 2 of WhatsApp's revocation — it inserts a new row with type=15.
            XposedBridge.hookAllMethods(android.database.sqlite.SQLiteDatabase.class, "insertWithOnConflict", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        String table = (String) param.args[0];
                        if (!"message".equals(table)) return;
                        
                        android.content.ContentValues cv = (android.content.ContentValues) param.args[2];
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

            XposedBridge.log("WAE: Anti-revoke SQL hooks installed successfully");
        } catch (Exception e) {
            XposedBridge.log("WAE: Failed to install anti-revoke SQL hooks: " + e.getMessage());
        }
    }


    private static String stringMessageDeleted;
    private static int antirevokeVal = -1;
    private static int antirevokeStatusVal = -1;

    private void bindRevokedMessageUI(FMessageWpp fMessage, TextView dateTextView, String antirevokeType) {
        if (dateTextView == null)
            return;

        var key = fMessage.getKey();
        var messageRevokedList = getRevokedMessagesForJid(fMessage);
        var id = fMessage.getRowId();
        String keyOrig = null;

        if (stringMessageDeleted == null) {
            stringMessageDeleted = UnobfuscatorCache.getInstance().getString("messagedeleted");
        }
        if (antirevokeVal == -1) {
            antirevokeVal = Integer.parseInt(prefs.getString("antirevoke", "0"));
        }
        if (antirevokeStatusVal == -1) {
            antirevokeStatusVal = Integer.parseInt(prefs.getString("antirevokestatus", "0"));
        }
        int antirevokeValue = antirevokeType.equals("antirevoke") ? antirevokeVal : antirevokeStatusVal;

        // Resolve the timestamp directly by key_id, which bypasses the JID mismatch
        long timestamp = 0;
        Long globalTs = revokedKeyIds.get(key.messageID);
        if (globalTs == null && keyOrig != null) globalTs = revokedKeyIds.get(keyOrig);
        
        if (globalTs != null) {
            timestamp = globalTs;
        } else {
            timestamp = DelMessageStore.getInstance(Utils.getApplication())
                    .getTimestampByMessageId(keyOrig == null ? key.messageID : keyOrig);
        }

        // Check both JID-based map AND global key_id map AND DB timestamp
        boolean isRevoked = timestamp > 0
                || messageRevokedList.contains(key.messageID)
                || revokedKeyIds.containsKey(key.messageID)
                || (keyOrig != null && (messageRevokedList.contains(keyOrig) || revokedKeyIds.containsKey(keyOrig)));

        ViewGroup parent = (ViewGroup) dateTextView.getParent();
        if (parent == null) return;

        if (isRevoked) {
            String toastMsg = "";
            if (timestamp > 0) {
                var date = Objects.requireNonNull(DATE_FORMAT_THREAD_LOCAL.get()).format(new Date(timestamp));
                String toastFormat = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.message_removed_on);
                if (toastFormat == null || toastFormat.isEmpty() || toastFormat.equals("null") || !toastFormat.contains("%s")) {
                    toastFormat = "Deleted on: %s"; // Fallback if resource is missing
                }
                toastMsg = String.format(toastFormat, date);
            }

            android.view.View indicator = parent.findViewWithTag("wae_revoke_indicator");
            if (indicator == null) {
                if (antirevokeValue == 1) { // Text indicator
                    indicator = new android.widget.TextView(dateTextView.getContext());
                    ((android.widget.TextView) indicator).setText(stringMessageDeleted != null ? stringMessageDeleted : "Deleted");
                    ((android.widget.TextView) indicator).setTextColor(dateTextView.getCurrentTextColor());
                    ((android.widget.TextView) indicator).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dateTextView.getTextSize());
                } else { // Icon indicator
                    indicator = new android.widget.ImageView(dateTextView.getContext());
                    ((android.widget.ImageView) indicator).setImageDrawable(com.waenhancer.xposed.utils.DesignUtils.getDrawable(R.drawable.deleted));
                }
                indicator.setTag("wae_revoke_indicator");
                
                // Add right margin/padding
                if (indicator instanceof android.widget.TextView) {
                    indicator.setPadding(0, 0, 10, 0);
                } else {
                    indicator.setPadding(0, 0, 8, 0);
                }
                
                // Add to parent, right before the dateTextView
                if (parent instanceof android.widget.LinearLayout) {
                    int index = parent.indexOfChild(dateTextView);
                    parent.addView(indicator, index);
                } else {
                    parent.addView(indicator); // Fallback
                }
            }

            indicator.setVisibility(android.view.View.VISIBLE);
            
            if (!toastMsg.isEmpty()) {
                final String finalToast = toastMsg;
                indicator.setOnClickListener(v -> Utils.showToast(finalToast, Toast.LENGTH_LONG));
            } else {
                indicator.setOnClickListener(null);
            }

            // Cleanup old inline modifications from dateTextView (if any remain)
            dateTextView.setCompoundDrawables(null, null, null, null);
            dateTextView.getPaint().setUnderlineText(false);
            dateTextView.setOnClickListener(null);
            var revokeNotice = (stringMessageDeleted != null ? stringMessageDeleted : "Deleted") + " | ";
            var dateText = dateTextView.getText().toString();
            if (dateText.contains(revokeNotice)) {
                dateTextView.setText(dateText.replace(revokeNotice, ""));
            }

        } else {
            // Un-revoke or non-revoked message
            android.view.View indicator = parent.findViewWithTag("wae_revoke_indicator");
            if (indicator != null) {
                indicator.setVisibility(android.view.View.GONE);
                indicator.setOnClickListener(null);
            }

            // Cleanup old inline modifications
            dateTextView.setCompoundDrawables(null, null, null, null);
            dateTextView.getPaint().setUnderlineText(false);
            dateTextView.setOnClickListener(null);
            var revokeNotice = (stringMessageDeleted != null ? stringMessageDeleted : "Deleted") + " | ";
            var dateText = dateTextView.getText().toString();
            if (dateText.contains(revokeNotice)) {
                dateTextView.setText(dateText.replace(revokeNotice, ""));
            }
        }
    }

    private int handleRevocationAttempt(FMessageWpp fMessage) {
        try {
            showRevocationToast(fMessage);
        } catch (Exception e) {
            log(e);
        }
        String messageKey = (String) XposedHelpers.getObjectField(fMessage.getObject(), "A01");
        String stripJID = fMessage.getKey().remoteJid.getPhoneNumber();
        int revokeboolean = stripJID.equals("status") ? Integer.parseInt(prefs.getString("antirevokestatus", "0"))
                : Integer.parseInt(prefs.getString("antirevoke", "0"));
        if (revokeboolean == 0)
            return revokeboolean;
        var messageRevokedList = getRevokedMessagesForJid(fMessage);
        if (!messageRevokedList.contains(messageKey)) {
            try {
                CompletableFuture.runAsync(() -> {
                    persistRevokedMessage(fMessage);
                    try {
                        var mConversation = WppCore.getCurrentConversation();
                        if (mConversation != null
                                && Objects.equals(stripJID, WppCore.getCurrentUserJid().getPhoneNumber())) {
                            mConversation.runOnUiThread(() -> {
                                if (mConversation.hasWindowFocus()) {
                                    mConversation.startActivity(mConversation.getIntent());
                                    mConversation.overridePendingTransition(0, 0);
                                    mConversation.getWindow().getDecorView().findViewById(android.R.id.content)
                                            .postInvalidate();
                                } else {
                                    mConversation.recreate();
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
        var messageSuffix = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.deleted_message);
        if (jidAuthor.isStatus()) {
            messageSuffix = com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.deleted_status);
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
