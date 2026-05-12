package com.waenhancer.xposed.features.general;

import static com.waenhancer.xposed.core.FeatureLoader.disableExpirationVersion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.BaseBundle;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.db.MessageDeviceSourceStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.AnimationUtil;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.xposed.features.others.MenuHome;

import org.json.JSONObject;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.util.DexSignUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.OkHttpClient;

public class Others extends Feature {
    private static final String DEVICE_SOURCE_SUFFIX_FIELD = "wae_device_source_suffix";
    private static final String DEVICE_SOURCE_GUARD_FIELD = "wae_device_source_guard";


    private static java.lang.reflect.Field cachedAbsViewField;
    private static final Set<String> dumpedMessageIds = ConcurrentHashMap.newKeySet();
    private static final Set<String> dumpedMessageRowViews = ConcurrentHashMap.newKeySet();
    private static final String PRIMARY_DEVICE_EMOJI = " \uD83D\uDCF1";
    private static final String LINKED_DEVICE_EMOJI = " \uD83D\uDDA5\uFE0F";

    public static HashMap<Integer, Boolean> propsBoolean = new HashMap<>();
    public static HashMap<Integer, Integer> propsInteger = new HashMap<>();
    private Properties properties;

    public Others(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (DEBUG) {
            ;
        }

        // receivedIncomingTimestamp

        properties = Utils.getProperties(prefs, "custom_css", "custom_filters");

        var menuWIcons = prefs.getBoolean("menuwicon", false);
        var newSettings = prefs.getBoolean("novaconfig", false);
        var filterChats = prefs.getString("chatfilter", "2");
        var filterSeen = prefs.getBoolean("filterseen", false);
        var status_style = Integer.parseInt(prefs.getString("status_style", "1"));
        var disableMetaAI = prefs.getBoolean("metaai", false);
        var disable_sensor_proximity = prefs.getBoolean("disable_sensor_proximity", false);
        var proximity_audios = prefs.getBoolean("proximity_audios", false);
        var showOnline = prefs.getBoolean("showonline", false);
        var floatingMenu = prefs.getBoolean("floatingmenu", false);
        var filter_items = prefs.getString("filter_items", null);
        var disable_defemojis = prefs.getBoolean("disable_defemojis", false);
        var autonext_status = prefs.getBoolean("autonext_status", false);
        var audio_type = Integer.parseInt(prefs.getString("audio_type", "0"));
        var audio_transcription = prefs.getBoolean("audio_transcription", false);
        var oldStatus = prefs.getBoolean("oldstatus", false);
        var igstatus = prefs.getBoolean("igstatus", false);
        var animationEmojis = prefs.getBoolean("animation_emojis", false);
        var disableProfileStatus = prefs.getBoolean("disable_profile_status", false);
        var disableExpiration = prefs.getBoolean("disable_expiration", false);
        var disableAds = prefs.getBoolean("disable_ads", false);

        propsInteger.put(3877, oldStatus ? igstatus ? 2 : 0 : 2);

        propsBoolean.put(18250, false);
        propsBoolean.put(11528, false);

        propsBoolean.put(4497, menuWIcons);
        propsBoolean.put(4023, false);
        propsBoolean.put(14862, newSettings);
        propsInteger.put(18564, newSettings ? 1 : 0);

        propsBoolean.put(2889, floatingMenu);

        // new text composer
        propsBoolean.put(15708, true);

        // change page id
        propsBoolean.put(2358, false);

        // disable contact filter
        propsBoolean.put(7769, false);

        // disable new Media Picker
        propsBoolean.put(9286, false);

        // Instant Video
        propsBoolean.put(3354, true);
        propsBoolean.put(5418, true);
        propsBoolean.put(9051, true);

        // disable new toolbar
        propsBoolean.put(11824, false);
        propsBoolean.put(6481, false);

        // Enable music in Stories
        propsBoolean.put(13591, true);
        propsBoolean.put(10024, true);

        // show all status
        propsBoolean.put(6798, true);

        // auto play emojis settings
        propsBoolean.put(3575, animationEmojis);
        propsBoolean.put(9757, animationEmojis);

        // emojis maps
        propsBoolean.put(10639, animationEmojis);
        propsBoolean.put(12495, animationEmojis);
        propsBoolean.put(11066, animationEmojis);

        propsBoolean.put(7589, true);  // Media select quality
        propsBoolean.put(6972, false); // Media select quality
        propsBoolean.put(5625, true);  // Enable option to autodelete channels media

        propsBoolean.put(8643, true);  // Enable TextStatusComposerActivityV2
//        propsBoolean.put(3403, true);  // Enable Sticker Suggestion
        propsBoolean.put(8607, true);  // Enable Dialer keyboard
        propsBoolean.put(9578, true);  // Enable Privacy Checkup
        propsInteger.put(8135, 2);  // Call Filters

        // Enable Translate Message
        propsBoolean.put(9141, true);
        propsBoolean.put(8925, true);

        propsBoolean.put(10380, false); // fix crash bug in Settings/Archived

        propsBoolean.put(0x34b9, true); // Enable Select People in call
        propsBoolean.put(0x351c, true); // Enable new colors style in Text Composer

        // Enable show count until viewed
        propsBoolean.put(0x2289, true);
        propsBoolean.put(0x373f, true);

        // add yours in stories
        propsBoolean.put(0x2ce2, true);
        propsBoolean.put(0x2ce3, true);

        propsBoolean.put(0x345a, true); // new edit profile name

        // new stories selection
        propsBoolean.put(0x32ca, true);
        propsBoolean.put(0x32cb, true);

        if (disableMetaAI) {
            propsInteger.put(15535, 0);
            propsBoolean.put(8025, false);
            propsBoolean.put(6251, false);
            propsBoolean.put(8026, false);
            propsBoolean.put(14886, false);
        }

        if (audio_transcription) {
            Others.propsBoolean.put(8632, true);
            Others.propsBoolean.put(2890, true);
            Others.propsBoolean.put(9215, false);
            Others.propsBoolean.put(9216, true);
            Others.propsBoolean.put(6808, true);
            Others.propsBoolean.put(10286, true);
            Others.propsBoolean.put(11596, true);
            Others.propsBoolean.put(13949, true);
        }

        // Whatsapp Status Style
        var retStatusStyle = Unobfuscator.loadStatusStyleMethod(classLoader);
        XposedBridge.hookMethod(retStatusStyle, XC_MethodReplacement.returnConstant(status_style));
        status_style = oldStatus ? 0 : status_style;
        propsInteger.put(9973, 1);
        propsBoolean.put(6285, true);
        propsInteger.put(8522, status_style);
        propsInteger.put(8521, status_style);


        hookProps();
        hookSearchbar(filterChats);

        if (disable_sensor_proximity) {
            disableSensorProximity();
        }

        if (proximity_audios) {
            var classes = Unobfuscator.loadProximitySensorListenerClasses(classLoader);
            for (var cls : classes) {
                XposedBridge.hookAllMethods(cls, "onSensorChanged", XC_MethodReplacement.DO_NOTHING);
            }
        }


        if (filter_items != null && prefs.getBoolean("custom_filters", true)) {
            filterItems(filter_items);
        }

        if (disable_defemojis) {
            disable_defEmojis();
        }

        if (autonext_status) {
            autoNextStatus();
        }

        if (audio_type > 0) {
            try {
                sendAudioType(audio_type);
            } catch (Exception e) {
                logDebug(e);
            }
        }

        customPlayBackSpeed();

        showOnline(showOnline);

        animationList();

        stampCopiedMessage();
        debugDumpMessageMetadata();
        hookMessageDeviceSourceTextView();
        messageDeviceSourceTag();

        try {
            doubleTapReaction();
        } catch (Exception e) {
            logDebug(e);
        }

        alwaysOnline();

        callInfo();

        if (disableProfileStatus) {
            disablePhotoProfileStatus();
        }

        if (disableExpiration) {
            disableExpirationVersion(classLoader);
        }

        if (disableAds) {
            disableAds();
        }

        if (!filterSeen) {
            disableHomeFilters();
        }



    }

    private void disableHomeFilters() throws Exception {

        propsBoolean.put(15345, true);
        propsBoolean.put(13546, false);
        propsBoolean.put(13408, true);

        Class<?> filterView = Unobfuscator.loadChatFilterView(classLoader);
        XposedBridge.hookAllConstructors(filterView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.thisObject;
                view.setVisibility(View.GONE);
                XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (view == param.thisObject && (int) param.args[0] != View.GONE) {
                            param.setResult(View.GONE);
                        }
                    }
                });
            }
        });
    }

    private void disableAds() {
        propsBoolean.put(22904, true);
        propsBoolean.put(14306, false);
    }


    private void disablePhotoProfileStatus() throws Exception {
        var refreshStatusClass = Unobfuscator.loadRefreshStatusClass(classLoader);
        var photoProfileClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".WDSProfilePhoto");
        var convClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".ConversationsFragment");
        var jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
        var method = ReflectionUtils.findMethodUsingFilter(convClass, m -> m.getParameterCount() > 0 && !Modifier.isStatic(m.getModifiers()) && m.getParameterTypes()[0] == View.class && ReflectionUtils.findIndexOfType(m.getParameterTypes(), jidClass) != -1);
        var field = ReflectionUtils.getFieldByExtendType(convClass, refreshStatusClass);
        logDebug("disablePhotoProfileStatus", Unobfuscator.getMethodDescriptor(method));
        logDebug("disablePhotoProfileStatus Field", Unobfuscator.getFieldDescriptor(field));
        if (field == null) {
            ;
            return;
        }
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            private Object backup;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                this.backup = field.get(param.thisObject);
                field.set(param.thisObject, null);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                field.set(param.thisObject, this.backup);
            }
        });


        XposedBridge.hookAllMethods(photoProfileClass, "setStatusIndicatorEnabled", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if ((boolean) param.args[0]) {
                    param.setResult(null);
                }
            }
        });
    }

    private void disableSensorProximity() throws Exception {
        XposedBridge.hookAllMethods(PowerManager.class, "newWakeLock", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    param.setResult(null);
                }
            }
        });
    }

    private void callInfo() throws Exception {
        if (!prefs.getBoolean("call_info", false)) return;

        var clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceServiceEventCallback");
        Class<?> clsWamCall = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "WamCall");

        XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (clsWamCall.isInstance(param.args[0])) {

                    Object callinfo = XposedHelpers.callMethod(param.thisObject, "getCallInfo");
                    if (callinfo == null) return;
                    var userJid = new FMessageWpp.UserJid(XposedHelpers.callMethod(callinfo, "getPeerJid"));
                    if (userJid.isNull()) return;
                    CompletableFuture.runAsync(() -> {
                        try {
                            showCallInformation(param.args[0], userJid);
                        } catch (Exception e) {
                            logDebug(e);
                        }
                    });
                }
            }
        });
    }

    private void showCallInformation(Object wamCall, FMessageWpp.UserJid userJid) throws Exception {
        if (userJid.isGroup()) return;
        var sb = new StringBuilder();
        var contact = WppCore.getContactName(userJid);
        var number = userJid.getPhoneNumber();
        if (!TextUtils.isEmpty(contact))
            sb.append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.contact_s, "Contact: %s"), contact)).append("\n");
        sb.append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.phone_number_s, "Number: +%s"), number)).append("\n");
        var ip = (String) XposedHelpers.getObjectField(wamCall, "callPeerIpStr");
        if (ip != null) {
            var client = new OkHttpClient.Builder().build();
            var url = "http://ip-api.com/json/" + ip;
            var request = new okhttp3.Request.Builder().url(url).build();
            var content = client.newCall(request).execute().body().string();
            var json = new JSONObject(content);
            var country = json.getString("country");
            var city = json.getString("city");
            sb.append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.country_s, "Country: %s"), country)).append("\n").append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.city_s, "City: %s"), city)).append("\n").append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.ip_s, "IP: %s"), ip)).append("\n");
        }
        var platform = (String) XposedHelpers.getObjectField(wamCall, "callPeerPlatform");
        if (platform != null)
            sb.append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.platform_s, "Platform: %s"), platform)).append("\n");
        var wppVersion = (String) XposedHelpers.getObjectField(wamCall, "callPeerAppVersion");
        if (wppVersion != null)
            sb.append(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.wpp_version_s, "WhatsApp Version: %s"), wppVersion)).append("\n");
        Utils.showNotification(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.call_information, "Call Information"), sb.toString());
    }

    private void alwaysOnline() throws Exception {
        if (!prefs.getBoolean("always_online", false)) return;
        var stateChange = Unobfuscator.loadStateChangeMethod(classLoader);
        XposedBridge.hookMethod(stateChange, XC_MethodReplacement.DO_NOTHING);
    }


    private void doubleTapReaction() throws Exception {

        if (!prefs.getBoolean("doubletap2like", false)) return;

        var emoji = prefs.getString("doubletap2like_emoji", "👍");

        var conversationRowClass = Unobfuscator.loadConversationRowClass(classLoader);
        logDebug("Conversation Row", conversationRowClass);

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                var doubleTapListener = new View.OnTouchListener() {
                    private long lastTapTime = 0;
                    private final long DOUBLE_TAP_TIMEOUT = 300;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastTapTime <= DOUBLE_TAP_TIMEOUT) {
                                lastTapTime = 0;
                                handleDoubleTap(viewGroup, fMessage);
                                return true;
                            }
                            lastTapTime = currentTime;
                        }
                        return false;
                    }
                };
                viewGroup.setOnTouchListener(doubleTapListener);
            }

            private void handleDoubleTap(ViewGroup viewGroup, FMessageWpp fMessage) {
                try {
                    var reactionView = (ViewGroup) viewGroup.findViewById(Utils.getID("reactions_bubble_layout", "id"));
                    if (reactionView != null && reactionView.getVisibility() == View.VISIBLE) {
                        for (int i = 0; i < reactionView.getChildCount(); i++) {
                            if (reactionView.getChildAt(i) instanceof TextView) {
                                TextView textView = (TextView) reactionView.getChildAt(i);
                                if (textView.getText().toString().contains(emoji)) {
                                    WppCore.sendReaction("", fMessage.getObject());
                                    return;
                                }
                            }
                        }
                    }
                    WppCore.sendReaction(emoji, fMessage.getObject());
                } catch (Exception e) {
                    logDebug("Double tap reaction error", e);
                }
            }
        });
    }

    private void stampCopiedMessage() throws Exception {
        if (!prefs.getBoolean("stamp_copied_message", false)) return;

        var copiedMessage = Unobfuscator.loadCopiedMessageMethod(classLoader);

        XposedBridge.hookMethod(copiedMessage, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var Collection = (java.util.Collection) param.args[param.args.length - 1];
                param.args[param.args.length - 1] = new ArrayList<Object>(Collection) {
                    @Override
                    public int size() {
                        return 1;
                    }
                };
            }
        });
    }

    private void debugDumpMessageMetadata() {
        if (!DEBUG) return;

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                try {
                    var key = fMessage.getKey();
                    if (key == null || TextUtils.isEmpty(key.messageID)) return;
                    if (!dumpedMessageIds.add(key.messageID)) return;
                    logDebug("MessageMetaDump", fMessage.dumpDebugInfo());
                } catch (Throwable t) {
                    logDebug("MessageMetaDumpError", t);
                }
            }
        });
    }

    private void messageDeviceSourceTag() {
        if (!prefs.getBoolean("message_device_source", true)) return;

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                var dateTextView = (TextView) viewGroup.findViewById(Utils.getID("date", "id"));
                if (dateTextView == null) return;
                var key = fMessage.getKey();
                String messageId = key != null ? key.messageID : null;
                XposedHelpers.setAdditionalInstanceField(dateTextView, "wae_device_source_message_id", messageId);
                int resolvedDeviceId = resolveMessageDeviceId(messageId, fMessage);
                XposedHelpers.setAdditionalInstanceField(dateTextView, DEVICE_SOURCE_SUFFIX_FIELD, getDeviceEmojiSuffix(resolvedDeviceId));

                dateTextView.post(() -> {
                    Object currentId = XposedHelpers.getAdditionalInstanceField(dateTextView, "wae_device_source_message_id");
                    if (!Objects.equals(currentId, messageId)) return;
                    if (DEBUG) {
                        logDebug("MessageDeviceSourceBind", "messageId=" + messageId
                                + ", deviceId=" + resolvedDeviceId
                                + ", deviceJid=" + fMessage.getDeviceJid()
                                + ", before=" + dateTextView.getText());
                    }
                    bindMessageDeviceSource(dateTextView, resolvedDeviceId);
                    applyDeviceSourceToMatchingTextViews(viewGroup, dateTextView, getDeviceEmojiSuffix(resolvedDeviceId));
                    if (DEBUG && messageId != null && dumpedMessageRowViews.add(messageId)) {
                        logDebug("MessageDeviceSourceRowViews", dumpRowTextViews(viewGroup));
                    }
                    if (DEBUG) {
                        logDebug("MessageDeviceSourceApplied", "messageId=" + messageId
                                + ", after=" + dateTextView.getText());
                    }
                });
            }
        });
    }

    private int resolveMessageDeviceId(String messageId, FMessageWpp fMessage) {
        int liveDeviceId = fMessage.getDeviceId();
        if (liveDeviceId >= 0) {
            MessageDeviceSourceStore.getInstance().upsertDeviceId(messageId, liveDeviceId);
            return liveDeviceId;
        }
        return MessageDeviceSourceStore.getInstance().getDeviceId(messageId);
    }

    private void bindMessageDeviceSource(TextView dateTextView, int deviceId) {
        String baseText = String.valueOf(dateTextView.getText())
                .replace(PRIMARY_DEVICE_EMOJI, "")
                .replace(LINKED_DEVICE_EMOJI, "");

        String suffix = getDeviceEmojiSuffix(deviceId);
        XposedHelpers.setAdditionalInstanceField(dateTextView, DEVICE_SOURCE_SUFFIX_FIELD, suffix);
        bindMessageDeviceSourceClick(dateTextView, deviceId);

        dateTextView.setText(baseText + suffix);
    }

    private String getDeviceEmojiSuffix(int deviceId) {
        if (deviceId == 0) return PRIMARY_DEVICE_EMOJI;
        if (deviceId > 0) return LINKED_DEVICE_EMOJI;
        return "";
    }

    private void applyDeviceSourceToMatchingTextViews(ViewGroup root, TextView anchor, String suffix) {
        if (suffix.isEmpty()) return;
        String anchorBase = stripDeviceEmoji(String.valueOf(anchor.getText()));
        if (TextUtils.isEmpty(anchorBase)) return;
        int deviceId = getDeviceIdFromSuffix(suffix);

        forEachTextView(root, textView -> {
            String current = String.valueOf(textView.getText());
            String base = stripDeviceEmoji(current);
            if (!anchorBase.equals(base)) return;

            XposedHelpers.setAdditionalInstanceField(textView, DEVICE_SOURCE_SUFFIX_FIELD, suffix);
            bindMessageDeviceSourceClick(textView, deviceId);
            if (!current.equals(base + suffix)) {
                textView.setText(base + suffix);
            }
        });
    }

    private int getDeviceIdFromSuffix(String suffix) {
        if (PRIMARY_DEVICE_EMOJI.equals(suffix)) return 0;
        if (LINKED_DEVICE_EMOJI.equals(suffix)) return 1;
        return -1;
    }

    private void bindMessageDeviceSourceClick(TextView textView, int deviceId) {
        if (deviceId < 0) {
            Utils.setViewClickListener(textView, "device_source", null);
            return;
        }

        Utils.setViewClickListener(textView, "device_source", v -> {
            if (deviceId == 0) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(
                        R.string.message_sent_via_phone,
                        "This message was sent via Phone"), Toast.LENGTH_SHORT);
            } else if (deviceId > 0) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(
                        R.string.message_sent_via_linked_device,
                        "This message was sent via a Linked Device (Desktop/Phone)"), Toast.LENGTH_SHORT);
            }
        });
    }

    private String dumpRowTextViews(ViewGroup root) {
        StringBuilder sb = new StringBuilder();
        forEachTextView(root, textView -> {
            sb.append("id=")
                    .append(textView.getId())
                    .append(", class=")
                    .append(textView.getClass().getSimpleName())
                    .append(", visibility=")
                    .append(textView.getVisibility())
                    .append(", text=")
                    .append(textView.getText())
                    .append(" | ");
        });
        return sb.toString();
    }

    private void forEachTextView(View view, java.util.function.Consumer<TextView> consumer) {
        if (view instanceof TextView textView) {
            consumer.accept(textView);
            return;
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            forEachTextView(group.getChildAt(i), consumer);
        }
    }

    private String stripDeviceEmoji(String text) {
        return text.replace(PRIMARY_DEVICE_EMOJI, "").replace(LINKED_DEVICE_EMOJI, "");
    }

    private void hookMessageDeviceSourceTextView() {
        XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!(param.thisObject instanceof TextView textView)) return;
                Object suffixObj = XposedHelpers.getAdditionalInstanceField(textView, DEVICE_SOURCE_SUFFIX_FIELD);
                if (!(suffixObj instanceof String suffix) || suffix.isEmpty()) return;
                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(textView, DEVICE_SOURCE_GUARD_FIELD))) {
                    return;
                }

                String current = String.valueOf(textView.getText());
                String base = current.replace(PRIMARY_DEVICE_EMOJI, "").replace(LINKED_DEVICE_EMOJI, "");
                String desired = base + suffix;
                if (desired.equals(current)) return;

                XposedHelpers.setAdditionalInstanceField(textView, DEVICE_SOURCE_GUARD_FIELD, true);
                try {
                    textView.setText(desired);
                } finally {
                    XposedHelpers.setAdditionalInstanceField(textView, DEVICE_SOURCE_GUARD_FIELD, false);
                }
            }
        });
    }

    private void animationList() throws Exception {
        var animation = prefs.getString("animation_list", "default");

        var onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onChangeStatus));
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(field1));
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);
        if (cachedAbsViewField == null) {
            cachedAbsViewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
            cachedAbsViewField.setAccessible(true);
        }

        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            @SuppressLint("ResourceType")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var view = (View) cachedAbsViewField.get(viewHolder);
                if (!Objects.equals(animation, "default")) {
                    view.startAnimation(AnimationUtil.getAnimation(animation));
                } else if (properties.containsKey("home_list_animation")) {
                    var animation = AnimationUtil.getAnimation(properties.getProperty("home_list_animation"));
                    if (animation != null) {
                        view.startAnimation(animation);
                    }
                }
            }
        });
    }

    private void customPlayBackSpeed() throws Exception {
        var voicenote_speed = prefs.getFloat("voicenote_speed", 2.0f);
        var playBackSpeed = Unobfuscator.loadPlaybackSpeed(classLoader);
        XposedBridge.hookMethod(playBackSpeed, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if ((float) param.args[1] == 2.0f) {
                    param.args[1] = voicenote_speed;
                }
            }
        });
        var voicenoteClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceNoteProfileAvatarView");
        var method = ReflectionUtils.findAllMethodsUsingFilter(voicenoteClass, method1 -> method1.getParameterCount() == 4 && method1.getParameterTypes()[0] == int.class && method1.getReturnType().equals(void.class));
        XposedBridge.hookMethod(method[method.length - 1], new XC_MethodHook() {
            @SuppressLint("SetTextI18n")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if ((int) param.args[0] == 3) {
                    var view = (View) param.thisObject;
                    var playback = (TextView) view.findViewById(Utils.getID("fast_playback_overlay", "id"));
                    if (playback != null) {
                        playback.setText(String.valueOf(voicenote_speed).replace(".", ",") + "×");
                    }
                }
            }
        });
    }


    private void sendAudioType(int audio_type) throws Exception {
        var sendAudioTypeMethod = Unobfuscator.loadSendAudioTypeMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(sendAudioTypeMethod));
        XposedBridge.hookMethod(sendAudioTypeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var results = ReflectionUtils.findInstancesOfType(param.args, Integer.class);
                if (results.size() < 2) {
                    logDebug("sendAudioTypeMethod size < 2");
                    return;
                }
                var mediaType = results.get(0);
                var audioType = results.get(1);
                if (mediaType.second != 2 && mediaType.second != 9) return;
                param.args[audioType.first] = audio_type - 1; // 1 = voice notes || 0 = audio voice
            }
        });

        var originFMessageField = Unobfuscator.loadOriginFMessageField(classLoader);
        var forwardAudioTypeMethod = Unobfuscator.loadForwardAudioTypeMethod(classLoader);

        XposedBridge.hookMethod(forwardAudioTypeMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var fMessage = param.getResult();
                originFMessageField.setAccessible(true);
                originFMessageField.setInt(fMessage, audio_type - 1);
            }
        });
    }


    private void autoNextStatus() throws Exception {
        Class<?> StatusPlaybackContactFragmentClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "StatusPlaybackContactFragment");
        var runNextStatusMethod = Unobfuscator.loadNextStatusRunMethod(classLoader);
        XposedBridge.hookMethod(runNextStatusMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var obj = XposedHelpers.getObjectField(param.thisObject, "A01");
                if (StatusPlaybackContactFragmentClass.isInstance(obj)) {
                    param.setResult(null);
                }
            }
        });
        var onPlayBackFinished = Unobfuscator.loadOnPlaybackFinished(classLoader);
        XposedBridge.hookMethod(onPlayBackFinished, XC_MethodReplacement.DO_NOTHING);
    }


    private void disable_defEmojis() throws Exception {
        var defEmojiClass = Unobfuscator.loadDefEmojiClass(classLoader);
        XposedBridge.hookMethod(defEmojiClass, XC_MethodReplacement.returnConstant(null));
    }

    private void filterItems(String filterItems) {
        var itens = filterItems.split("\n");
        var idsFilter = new java.util.HashSet<Integer>();
        for (String item : itens) {
            var id = Utils.getID(item, "id");
            if (id > 0) {
                idsFilter.add(id);
            }
        }
        XposedHelpers.findAndHookMethod(View.class, "invalidate", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.thisObject;
                var id = view.getId();
                if (id > 0 && idsFilter.contains(id) && view.getVisibility() == View.VISIBLE) {
                    view.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showOnline(boolean showOnline) throws Exception {
        var checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader);
        XposedBridge.hookMethod(checkOnlineMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var message = (Message) param.args[0];
                if (message.arg1 != 5) return;
                BaseBundle baseBundle = (BaseBundle) message.obj;
                var jid = baseBundle.getString("jid");
                if (TextUtils.isEmpty(jid)) return;
                var userjid = new FMessageWpp.UserJid(jid);
                if (userjid.isGroup()) return;
                var name = WppCore.getContactName(userjid);
                name = TextUtils.isEmpty(name) ? userjid.getPhoneNumber() : name;
                if (showOnline)
                    Utils.showToast(String.format(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.toast_online, "%s is online"), name), Toast.LENGTH_SHORT);
                Tasker.sendTaskerEvent(name, WppCore.stripJID(jid), "contact_online");
            }
        });
    }


    private void hookProps() throws Exception {
        var methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(methodPropsBoolean));
        var dataUsageActivityClass = WppCore.getDataUsageActivityClass(classLoader);
        XposedBridge.hookMethod(methodPropsBoolean, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var list = ReflectionUtils.findInstancesOfType(param.args, Integer.class);
                int i = (int) list.get(0).second;

                var propValue = propsBoolean.get(i);
                if (propValue != null) {
                    // Fix Bug in Settings Data Usage
                    if (i == 4023) {
                        if (ReflectionUtils.isCalledFromClass(dataUsageActivityClass)) return;
                    }
                    param.setResult(propValue);
                }
            }
        });

        var methodPropsInteger = Unobfuscator.loadPropsIntegerMethod(classLoader);

        XposedBridge.hookMethod(methodPropsInteger, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var list = ReflectionUtils.findInstancesOfType(param.args, Integer.class);
                int i = (int) list.get(0).second;
                var propValue = propsInteger.get(i);
                if (propValue == null) return;
                param.setResult(propValue);
            }
        });
    }

    private void hookSearchbar(String filterChats) throws Exception {
        Method searchbar = Unobfuscator.loadViewAddSearchBarMethod(classLoader);
        log("ADD HEADER VIEW: " + DexSignUtil.getMethodDescriptor(searchbar));
        var searchBarID = Utils.getID("my_search_bar", "id");

        XposedBridge.hookMethod(searchbar, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = null;
                if (param.args[0] instanceof View) {
                    view = (View) param.args[0];
                } else {
                    var auxFace = ((Method) param.method).getParameterTypes()[0];
                    var method = ReflectionUtils.findMethodUsingFilter(auxFace, m -> m.getReturnType() == View.class);
                    if (method != null) {
                        var currentActivity = WppCore.getCurrentActivity();
                        view = (View) method.invoke(param.args[0], currentActivity);
                    }
                }

                if ((view.getId() == searchBarID || view.findViewById(searchBarID) != null) && !Objects.equals(filterChats, "2")) {
                    param.setResult(null);
                }
            }
        });

        try {
            if (!Objects.equals(filterChats, "2")) {
                var loadMySearchBar = Unobfuscator.loadMySearchBarMethod(classLoader);
                XposedBridge.hookMethod(loadMySearchBar, XC_MethodReplacement.DO_NOTHING);
            }
        } catch (Exception ignored) {
        }


        try {
            Method addSeachBar = Unobfuscator.loadAddOptionSearchBarMethod(classLoader);
            XposedBridge.hookMethod(addSeachBar, new XC_MethodHook() {
                private Object homeActivity;
                private Field pageIdField;
                private int originPageId;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!Objects.equals(filterChats, "1"))
                        return;
                    homeActivity = param.thisObject;
                    if (Modifier.isStatic(param.method.getModifiers())) {
                        homeActivity = param.args[0];
                    }
                    pageIdField = XposedHelpers.findField(homeActivity.getClass(), "A01");
                    originPageId = 0;
                    if (pageIdField.getType() == int.class) {
                        originPageId = pageIdField.getInt(homeActivity);
                        pageIdField.setInt(homeActivity, 1);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (originPageId != 0) {
                        pageIdField.setInt(homeActivity, originPageId);
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onPrepareOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var item = menu.findItem(Utils.getID("menuitem_search", "id"));
                if (item != null) {
                    item.setVisible(Objects.equals(filterChats, "1"));
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Others";
    }
}
