package com.waenhancer.xposed.features.privacy;

import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.general.Tasker;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class CallPrivacy extends Feature {

    private Object mVoipManager;
    private static String lastBlockedNumber = "";
    private static long lastBlockedTime = 0;

    private String getFriendlyBlockType(String type) {
        if (type == null) return "Blocked";
        switch (type) {
            case "no_internet": return "No internet";
            case "uncallable": return "Unable to receive calls";
            case "declined": return "Declined";
            case "busy": return "Busy";
            case "ended": return "Not answered";
            default: return "Blocked";
        }
    }

    private synchronized void showBlockedCallNotification(FMessageWpp.UserJid userJid, String blockType) {
        if (!prefs.getBoolean("call_blocked_notification", false)) {
            return;
        }

        String phoneNum = userJid.getPhoneNumber();
        long now = System.currentTimeMillis();
        if (Objects.equals(phoneNum, lastBlockedNumber) && (now - lastBlockedTime < 5000)) {
            return; // Avoid duplicate notifications within 5 seconds
        }
        lastBlockedNumber = phoneNum;
        lastBlockedTime = now;

        CompletableFuture.runAsync(() -> {
            try {
                String contactName = WppCore.getContactName(userJid);
                String displayName = TextUtils.isEmpty(contactName) ? "+" + phoneNum : contactName;

                // Load contact avatar bitmap
                Bitmap avatarBitmap = null;
                try {
                    File file = WppCore.getContactPhotoFile(userJid.getPhoneRawString());
                    if (file != null && file.exists()) {
                        avatarBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    }
                } catch (Throwable ignored) {}

                boolean additionalInfoEnabled = prefs.getBoolean("call_info", false);

                Context context = Utils.getApplication();
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                String channelId = "waex";
                // Ensure notification channel is created
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Wa Enhancer X", NotificationManager.IMPORTANCE_HIGH);
                    notificationManager.createNotificationChannel(channel);
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                if (avatarBitmap != null) {
                    builder.setLargeIcon(avatarBitmap);
                }

                if (additionalInfoEnabled) {
                    // Title: Call Blocked: Name/Number
                    builder.setContentTitle("Call Blocked: " + displayName);
                    
                    // Content text / Big text style
                    String body = "Call from " + displayName + " (+" + phoneNum + ") was blocked.\nBlock Method: " + blockType;
                    builder.setContentText(body);
                    builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
                } else {
                    // Title: Call Blocked
                    builder.setContentTitle("Call Blocked");
                    
                    // Content text: Name + blocked tag
                    String body = "Call from " + displayName + " was blocked (" + blockType + ")";
                    builder.setContentText(body);
                    builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
                }

                notificationManager.notify(new Random().nextInt(), builder.build());
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        });
    }

    /**
     * @noinspection unchecked
     */
    @Override
    public void doHook() throws Throwable {

        var voipManagerClass = Unobfuscator.loadVoipManager(classLoader);
        XposedBridge.hookAllConstructors(voipManagerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mVoipManager = param.thisObject;
            }
        });

        var clazzVoip = WppCore.getVoipManagerClass(classLoader);
        var endCallMethod = ReflectionUtils.findMethodUsingFilter(clazzVoip, m -> m.getName().equals("endCall"));
        var rejectCallMethod = ReflectionUtils.findMethodUsingFilter(clazzVoip, m -> m.getName().equals("rejectCall"));

        var onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader);

        XposedBridge.hookMethod(onCallReceivedMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object callinfo;
                Class<?> callInfoClass = WppCore.getVoipCallInfoClass(classLoader);
                if (param.args[0] instanceof Message) {
                    callinfo = ((Message) param.args[0]).obj;
                } else if (param.args.length > 1 && callInfoClass.isInstance(param.args[1])) {
                    callinfo = param.args[1];
                } else {
                    Utils.showToast("Invalid call info", Toast.LENGTH_SHORT);
                    return;
                }
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return;
                if ((boolean) XposedHelpers.callMethod(callinfo, "isCaller")) return;
                var userJid = new FMessageWpp.UserJid(XposedHelpers.callMethod(callinfo, "getPeerJid"));
                var callId = XposedHelpers.callMethod(callinfo, "getCallId");
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                Tasker.sendTaskerEvent(WppCore.getContactName(userJid), userJid.getPhoneNumber(), "call_received");
                var blockCall = checkCallBlock(userJid, PrivacyType.getByValue(type));
                if (!blockCall) return;
                var rejectType = prefs.getString("call_type", "no_internet");
                
                showBlockedCallNotification(userJid, getFriendlyBlockType(rejectType));

                switch (rejectType) {
                    case "uncallable":
                    case "declined":
                    case "busy":
                        var params = ReflectionUtils.initArray(rejectCallMethod.getParameterTypes());
                        params[0] = callId;
                        params[1] = "declined".equals(rejectType) ? null : rejectType;
                        ReflectionUtils.callMethod(rejectCallMethod, mVoipManager, params);
                        param.setResult(true);
                        break;
                    case "ended":
                        var params1 = ReflectionUtils.initArray(endCallMethod.getParameterTypes());
                        params1[0] = true;
                        ReflectionUtils.callMethod(endCallMethod, mVoipManager, params1);
                        param.setResult(true);
                        break;
                    default:
                }
            }
        });

        XposedBridge.hookAllMethods(WppCore.getVoipManagerClass(classLoader), "nativeHandleIncomingXmppOffer", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getString("call_type", "no_internet").equals("no_internet")) return;
                var jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
                var jidObj = ReflectionUtils.getArg(param.args, jidClass, 0);
                var userJid = new FMessageWpp.UserJid(jidObj);
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                var block = checkCallBlock(userJid, PrivacyType.getByValue(type));
                if (block) {
                    showBlockedCallNotification(userJid, getFriendlyBlockType("no_internet"));
                    param.setResult(1);
                }
            }
        });


    }


    public CallPrivacy(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    public boolean checkCallBlock(FMessageWpp.UserJid userJid, PrivacyType type) throws IllegalAccessException, InvocationTargetException {

        var phoneNumber = userJid.getPhoneNumber();

        if (phoneNumber == null) return false;

        var customprivacy = CustomPrivacy.getJSON(phoneNumber);

        if (type == PrivacyType.ALL_BLOCKED) {
            return customprivacy.optBoolean("BlockCall", true);
        }

        if (type == PrivacyType.ALL_PERMITTED) {
            return customprivacy.optBoolean("BlockCall", false);
        }

        switch (type) {
            case ONLY_UNKNOWN:
                if (customprivacy.optBoolean("BlockCall", false)) return true;
                var contactName = WppCore.getSContactName(userJid, true);
                return TextUtils.isEmpty(contactName) || contactName.equals(phoneNumber);
            case BACKLIST:
                if (customprivacy.optBoolean("BlockCall", false)) return true;
                var callBlockList = prefs.getString("call_block_contacts", "[]");
                ;
                var blockList = Arrays.stream(callBlockList.substring(1, callBlockList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                ;
                for (var blockNumber : blockList) {
                    if (!TextUtils.isEmpty(blockNumber)) {
                        String cleanBlockNumber = blockNumber;
                        if (cleanBlockNumber.contains("@")) {
                            cleanBlockNumber = cleanBlockNumber.substring(0, cleanBlockNumber.indexOf("@"));
                        }
                        if (cleanBlockNumber.startsWith("+")) {
                            cleanBlockNumber = cleanBlockNumber.substring(1);
                        }
                        String cleanPhoneNumber = userJid.getPhoneNumber();
                        if (cleanPhoneNumber != null && cleanPhoneNumber.startsWith("+")) {
                            cleanPhoneNumber = cleanPhoneNumber.substring(1);
                        }
                        String phoneRaw = userJid.getPhoneRawString();
                        String userRaw = userJid.getUserRawString();
                        ;
                        if (Objects.equals(phoneRaw, blockNumber) ||
                                Objects.equals(userRaw, blockNumber) ||
                                (cleanPhoneNumber != null && Objects.equals(cleanPhoneNumber, cleanBlockNumber))) {
                            ;
                            return true;
                        }
                    }
                }
                return false;
            case WHITELIST:
                var callWhiteList = prefs.getString("call_white_contacts", "[]");
                ;
                var whiteList = Arrays.stream(callWhiteList.substring(1, callWhiteList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var whiteNumber : whiteList) {
                    if (!TextUtils.isEmpty(whiteNumber)) {
                        String cleanWhiteNumber = whiteNumber;
                        if (cleanWhiteNumber.contains("@")) {
                            cleanWhiteNumber = cleanWhiteNumber.substring(0, cleanWhiteNumber.indexOf("@"));
                        }
                        if (cleanWhiteNumber.startsWith("+")) {
                            cleanWhiteNumber = cleanWhiteNumber.substring(1);
                        }
                        String cleanPhoneNumber = userJid.getPhoneNumber();
                        if (cleanPhoneNumber != null && cleanPhoneNumber.startsWith("+")) {
                            cleanPhoneNumber = cleanPhoneNumber.substring(1);
                        }
                        String phoneRaw = userJid.getPhoneRawString();
                        String userRaw = userJid.getUserRawString();
                        ;
                        if (Objects.equals(phoneRaw, whiteNumber) ||
                                Objects.equals(userRaw, whiteNumber) ||
                                (cleanPhoneNumber != null && Objects.equals(cleanPhoneNumber, cleanWhiteNumber))) {
                            ;
                            return false;
                        }
                    }
                }
                return true;
        }
        return false;
    }

    public enum PrivacyType {
        ALL_PERMITTED(0),
        ALL_BLOCKED(1),
        ONLY_UNKNOWN(2),
        BACKLIST(3),
        WHITELIST(4);

        private final int value;

        PrivacyType(int i) {
            this.value = i;
        }

        public static PrivacyType getByValue(int value) {
            for (PrivacyType type : PrivacyType.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return null;
        }
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Call Privacy";
    }
}
