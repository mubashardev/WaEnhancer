package com.waenhancer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.views.dialog.BottomDialogWpp;
import com.waenhancer.xposed.bridge.WaeIIFace;
import com.waenhancer.xposed.bridge.client.BaseClient;
import com.waenhancer.xposed.bridge.client.BridgeClient;
import com.waenhancer.xposed.bridge.client.ProviderClient;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class WppCore {

    static final HashSet<ActivityChangeState> listenerAcitivity = new HashSet<>();
    @SuppressLint("StaticFieldLeak")
    static Activity mCurrentActivity;
    private static Method mGenJidMethod;
    private static Class bottomDialog;
    private static SharedPreferences privPrefs;
    public static android.content.SharedPreferences waePrefs;
    private static Object mStartUpConfig;
    private static Object mActionUser;
    private static SQLiteDatabase mWaDatabase;
    public static BaseClient client;
    private static Object mCachedMessageStore;
    private static Class<?> mSettingsNotificationsClass;
    private static Method convertLidToJid;

    private static Object mWaJidMapRepository;
    private static Method convertJidToLid;
    private static Class actionUser;
    private static Method cachedMessageStoreKey;
    private static Field conversationDelegateField;
    private static Field conversationJidField;
    private static Field meManagerPhoneJidField;
    private static Object meManagerInstance;
    private static Object mConversationDelegate;

    public static void Initialize(ClassLoader loader, android.content.SharedPreferences pref) throws Exception {
        waePrefs = pref;
        privPrefs = Utils.getApplication().getSharedPreferences("WaGlobal", Context.MODE_PRIVATE);


        // init UserJID
        var companionField = FMessageWpp.UserJid.TYPE_JID.getDeclaredField("Companion");
        mGenJidMethod = ReflectionUtils.findMethodUsingFilter(companionField.getType(), m -> m.getParameterCount() == 1 && String.class.equals(m.getParameterTypes()[0]) && FMessageWpp.UserJid.TYPE_JID.equals(m.getReturnType()));

        // Bottom Dialog
        bottomDialog = Unobfuscator.loadDialogViewClass(loader);

        ensureConversationJidResolvers(loader);

        // Settings notifications activity (required for
        // ActivityController.EXPORTED_ACTIVITY)
        mSettingsNotificationsClass = getSettingsNotificationsActivityClass(loader);

        // StartUpPrefs
        var startPrefsConfig = Unobfuscator.loadStartPrefsConfig(loader);
        XposedBridge.hookMethod(startPrefsConfig, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mStartUpConfig = param.thisObject;
            }
        });

        initActionUser(loader);

        // CachedMessageStore
        cachedMessageStoreKey = Unobfuscator.loadCachedMessageStoreKey(loader);
        XposedBridge.hookAllConstructors(cachedMessageStoreKey.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mCachedMessageStore = param.thisObject;
            }
        });

        // WaJidMap
        convertLidToJid = Unobfuscator.loadConvertLidToJid(loader);
        convertJidToLid = Unobfuscator.loadConvertJidToLid(loader);
        XposedBridge.hookAllConstructors(convertLidToJid.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mWaJidMapRepository = param.thisObject;
            }
        });

        // load me current PhoneJid

        Class<?> meManagerClass = Unobfuscator.loadMeManagerClass(loader);
        meManagerPhoneJidField = ReflectionUtils.getFieldByType(meManagerClass, FMessageWpp.UserJid.TYPE_PHONEUSERJID);
        XposedBridge.hookAllConstructors(meManagerClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                meManagerInstance = param.thisObject;
            }
        });

        if (conversationJidField != null) {
            XposedBridge.hookAllConstructors(conversationJidField.getDeclaringClass(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mConversationDelegate = param.thisObject;
                    XposedBridge.log("WAE: Captured conversation delegate: " + mConversationDelegate.getClass().getName());
                }
            });
        }


        // Load wa database
        loadWADatabase();

        if (!pref.getBoolean("lite_mode", false)) {
            CompletableFuture.runAsync(() -> {
                try {
                    initBridge(Utils.getApplication());
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            });
        }

    }

    public static Object getPhoneJidFromUserJid(Object lid) {
        if (lid == null)
            return null;
        try {
            var rawString = (String) XposedHelpers.callMethod(lid, "getRawString");
            if (rawString == null || !rawString.contains("@lid"))
                return lid;
            rawString = rawString.replaceFirst("\\.[\\d:]+@", "@");
            var newUser = WppCore.createUserJid(rawString);
            var result = ReflectionUtils.callMethod(convertLidToJid, mWaJidMapRepository, newUser);
            return result == null ? lid : result;
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return lid;
    }

    public static Object getUserJidFromPhoneJid(Object userJid) {
        if (userJid == null)
            return null;
        try {
            var rawString = (String) XposedHelpers.callMethod(userJid, "getRawString");
            if (rawString == null || rawString.contains("@lid"))
                return userJid;
            rawString = rawString.replaceFirst("\\.[\\d:]+@", "@");
            var newUser = WppCore.createUserJid(rawString);
            var result = ReflectionUtils.callMethod(convertJidToLid, mWaJidMapRepository, newUser);
            return result == null ? userJid : result;
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return userJid;
    }

    public static void initBridge(Context context) throws Exception {
        var prefsCacheHooks = UnobfuscatorCache.getInstance().sPrefsCacheHooks;
        int preferredOrder = prefsCacheHooks.getInt("preferredOrder", 1); // 0 for ProviderClient first, 1 for
        // BridgeClient first

        boolean connected = false;
        if (preferredOrder == 0) {
            if (tryConnectBridge(new ProviderClient(context))) {
                connected = true;
            } else if (tryConnectBridge(new BridgeClient(context))) {
                connected = true;
                preferredOrder = 1; // Update preference to BridgeClient first
            }
        } else {
            if (tryConnectBridge(new BridgeClient(context))) {
                connected = true;
            } else if (tryConnectBridge(new ProviderClient(context))) {
                connected = true;
                preferredOrder = 0; // Update preference to ProviderClient first
            }
        }

        if (!connected) {
            throw new Exception(context.getString(R.string.bridge_error));
        }

        // Update the preferred order if it changed
        prefsCacheHooks.edit().putInt("preferredOrder", preferredOrder).apply();
    }

    private static boolean tryConnectBridge(BaseClient baseClient) throws Exception {
        try {
            XposedBridge.log("Trying to connect to " + baseClient.getClass().getSimpleName());
            client = baseClient;
            CompletableFuture<Boolean> canLoadFuture = baseClient.connect();
            Boolean canLoad = canLoadFuture.get();
            if (!canLoad)
                throw new Exception();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static void sendMessage(Object userJidRaw, String message) {
        try {
            // Get the JID raw string from the Jid object
            String jidRawString = (String) XposedHelpers.callMethod(userJidRaw, "getRawString");
            if (jidRawString == null) {
                XposedBridge.log("WppCore.sendMessage - could not get rawString from jid: " + userJidRaw);
                Utils.showToast("Error: could not find JID", Toast.LENGTH_SHORT);
                return;
            }
            // Strip device suffix if LID: e.g. "4306.0:0@lid" -> "4306@lid"
            jidRawString = jidRawString.replaceFirst("\\.[\\d:]+@", "@");
            XposedBridge.log("WppCore.sendMessage - jidRawString: " + jidRawString);

            sendMessage(jidRawString, message);
        } catch (Exception e) {
            XposedBridge.log("WppCore.sendMessage(Object) failed: " + e);
            Utils.showToast("Error in sending message: " + e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    /**
     * Sends a message headlessly via WhatsApp notification RemoteInput reply.
     * 
     * @param contactName The display name of the contact as shown in WA
     *                    notification title.
     * @param message     The text to send.
     */
    @SuppressWarnings("deprecation")
    public static boolean sendMessageViaNotification(String contactName, String message) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) Utils.getApplication()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            android.service.notification.StatusBarNotification[] notifications = nm.getActiveNotifications();

            for (android.service.notification.StatusBarNotification sbn : notifications) {
                if (!sbn.getPackageName().contains("whatsapp"))
                    continue;
                android.app.Notification notif = sbn.getNotification();
                if (notif.actions == null)
                    continue;

                // Match by android.title (contact display name)
                String title = notif.extras.getString("android.title");
                if (title == null || !title.equalsIgnoreCase(contactName))
                    continue;

                for (android.app.Notification.Action action : notif.actions) {
                    if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                        android.app.RemoteInput[] remoteInputs = action.getRemoteInputs();
                        android.content.Intent fillIn = new android.content.Intent();
                        android.os.Bundle results = new android.os.Bundle();
                        for (android.app.RemoteInput ri : remoteInputs) {
                            results.putCharSequence(ri.getResultKey(), message);
                        }
                        android.app.RemoteInput.addResultsToIntent(remoteInputs, fillIn, results);
                        action.actionIntent.send(Utils.getApplication(), 0, fillIn);
                        XposedBridge.log(
                                "WppCore.sendMessageViaNotification - sent to [" + contactName + "] via RemoteInput!");
                        return true;
                    }
                }
            }
            XposedBridge.log("WppCore.sendMessageViaNotification - no WA notification with title=[" + contactName
                    + "]. Total=" + notifications.length);
        } catch (Exception e) {
            XposedBridge.log("WppCore.sendMessageViaNotification error: " + e);
        }
        return false;
    }

    public static void sendMessage(String jidOrNumber, String message) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) Utils.getApplication()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            android.service.notification.StatusBarNotification[] notifications = nm.getActiveNotifications();

            for (android.service.notification.StatusBarNotification sbn : notifications) {
                if (!sbn.getPackageName().contains("whatsapp"))
                    continue;
                android.app.Notification notif = sbn.getNotification();
                if (notif.actions == null)
                    continue;
                String tag = sbn.getTag() != null ? sbn.getTag() : "";
                String extras = notif.extras.toString();
                String bareId = jidOrNumber.contains("@") ? jidOrNumber.substring(0, jidOrNumber.indexOf('@'))
                        : jidOrNumber;
                if (!tag.contains(bareId) && !extras.contains(bareId))
                    continue;

                for (android.app.Notification.Action action : notif.actions) {
                    if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                        android.app.RemoteInput[] remoteInputs = action.getRemoteInputs();
                        android.content.Intent fillIn = new android.content.Intent();
                        android.os.Bundle results = new android.os.Bundle();
                        for (android.app.RemoteInput ri : remoteInputs) {
                            results.putCharSequence(ri.getResultKey(), message);
                        }
                        android.app.RemoteInput.addResultsToIntent(remoteInputs, fillIn, results);
                        action.actionIntent.send(Utils.getApplication(), 0, fillIn);
                        XposedBridge.log("WppCore.sendMessage - sent via RemoteInput (jid match)!");
                        return;
                    }
                }
            }
            XposedBridge.log("WppCore.sendMessage - no matching WA notification for jid=" + jidOrNumber);
        } catch (Exception e) {
            XposedBridge.log("WppCore.sendMessage error: " + e);
        }
    }

    public static void sendReaction(String s, Object objMessage) {
        try {
            if (!ensureActionUserInitialized(objMessage.getClass().getClassLoader()) || actionUser == null) {
                Utils.showToast("Reaction sending is not supported on this WhatsApp version yet", Toast.LENGTH_SHORT);
                return;
            }
            var senderMethod = ReflectionUtils.findMethodUsingFilter(actionUser,
                    (method) -> method.getParameterCount() == 3 && Arrays.equals(method.getParameterTypes(),
                            new Class[]{FMessageWpp.TYPE, String.class, boolean.class}));
            Object actionUserInstance = Modifier.isStatic(senderMethod.getModifiers()) ? null : getActionUser();
            if (!Modifier.isStatic(senderMethod.getModifiers()) && actionUserInstance == null) {
                Utils.showToast("Reaction sending is not supported on this WhatsApp version yet", Toast.LENGTH_SHORT);
                return;
            }
            senderMethod.invoke(actionUserInstance, objMessage, s, !TextUtils.isEmpty(s));
        } catch (Exception e) {
            Utils.showToast("Error in sending reaction:" + e.getMessage(), Toast.LENGTH_SHORT);
            XposedBridge.log(e);
        }
    }

    public static Object getActionUser() {
        try {
            if (actionUser == null) {
                return null;
            }
            if (mActionUser == null) {
                mActionUser = actionUser.getConstructors()[0].newInstance();
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return mActionUser;
    }

    private static void initActionUser(ClassLoader loader) {
        ensureActionUserInitialized(loader);
    }

    private static synchronized boolean ensureActionUserInitialized(ClassLoader loader) {
        if (actionUser != null) {
            return true;
        }
        try {
            actionUser = Unobfuscator.loadActionUser(loader);
            XposedBridge.log("ActionUser: " + actionUser.getName());
            XposedBridge.hookAllConstructors(actionUser, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mActionUser = param.thisObject;
                }
            });

            var a00Method = ReflectionUtils.findMethodUsingFilterIfExists(actionUser,
                    (method) -> method.getName().equals("A00") && method.getParameterCount() == 2);
            if (a00Method != null) {
                XposedBridge.hookMethod(a00Method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object oneKs = param.args[0];
                        if (oneKs != null) {
                            for (var f : oneKs.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                Object val = f.get(oneKs);
                                XposedBridge.log("WppCore.A00-hook: 1Ks." + f.getName() + " = " + val
                                        + " [class=" + (val == null ? "null" : val.getClass().getName()) + "]");
                            }
                        }
                        XposedBridge.log("WppCore.A00-hook: message arg = " + param.args[1]);
                    }
                });
            }
            return true;
        } catch (Exception e) {
            XposedBridge.log(e);
            return false;
        }
    }

    public static void loadWADatabase() {
        if (mWaDatabase != null)
            return;
        CompletableFuture.runAsync(() -> {
            var dataDir = Utils.getApplication().getFilesDir().getParentFile();
            var database = new File(dataDir, "databases/wa.db");
            if (database.exists()) {
                mWaDatabase = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            }
        });
    }

    public static Activity getCurrentActivity() {
        return mCurrentActivity;
    }

    public static ActivityChangeState.ChangeType getActivityState(Activity activity) {
        return ActivityStateRegistry.getState(activity);
    }

    public static ActivityChangeState.ChangeType getActivityStateBySimpleName(String simpleName) {
        return ActivityStateRegistry.getStateBySimpleName(simpleName);
    }

    public static boolean isConversationResumed() {
        var state = ActivityStateRegistry.getStateBySimpleName("Conversation");
        return state == ActivityChangeState.ChangeType.RESUMED;
    }

    public static boolean isHomeActivityResumed() {
        var state = ActivityStateRegistry.getStateBySimpleName("HomeActivity");
        return state == ActivityChangeState.ChangeType.RESUMED;
    }

    public static ActivityChangeState.ChangeType getCurrentActivityState() {
        return ActivityStateRegistry.getState(mCurrentActivity);
    }

    public static Activity getActivityBySimpleName(String simpleName) {
        return ActivityStateRegistry.getActivityBySimpleName(simpleName);
    }

    public synchronized static Class getHomeActivityClass(@NonNull ClassLoader loader) {
        Class oldHomeClass = XposedHelpers.findClassIfExists("com.whatsapp.HomeActivity", loader);

        return oldHomeClass != null
                ? oldHomeClass
                : XposedHelpers.findClass("com.whatsapp.home.ui.HomeActivity", loader);
    }

    public synchronized static Class getTabsPagerClass(@NonNull ClassLoader loader) {
        Class oldHomeClass = XposedHelpers.findClassIfExists("com.whatsapp.TabsPager", loader);

        return oldHomeClass != null
                ? oldHomeClass
                : XposedHelpers.findClass("com.whatsapp.home.ui.TabsPager", loader);
    }

    public synchronized static Class getViewOnceViewerActivityClass(@NonNull ClassLoader loader) {
        Class oldClass = XposedHelpers.findClassIfExists("com.whatsapp.messaging.ViewOnceViewerActivity", loader);

        return oldClass != null
                ? oldClass
                : XposedHelpers.findClass("com.whatsapp.viewonce.ui.messaging.ViewOnceViewerActivity", loader);
    }

    public synchronized static Class getSettingsActivityClass(@NonNull ClassLoader loader) {
        try {
            return Unobfuscator.loadSettingsActivityClass(loader);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized static Class getSettingsFragmentClass(@NonNull ClassLoader loader) {
        try {
            return Unobfuscator.loadSettingsFragmentClass(loader);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized static Class getAboutActivityClass(@NonNull ClassLoader loader) {
        Class oldClass = XposedHelpers.findClassIfExists("com.whatsapp.settings.About", loader);

        return oldClass != null
                ? oldClass
                : XposedHelpers.findClass("com.whatsapp.settings.ui.About", loader);
    }

    public synchronized static Class getSettingsNotificationsActivityClass(@NonNull ClassLoader loader) {
        if (mSettingsNotificationsClass != null)
            return mSettingsNotificationsClass;

        Class oldClass = XposedHelpers.findClassIfExists("com.whatsapp.settings.SettingsNotifications", loader);

        return oldClass != null
                ? oldClass
                : XposedHelpers.findClass("com.whatsapp.settings.ui.SettingsNotifications", loader);
    }

    public synchronized static Class getDataUsageActivityClass(@NonNull ClassLoader loader) {
        Class oldClass = XposedHelpers.findClassIfExists("com.whatsapp.settings.SettingsDataUsageActivity", loader);

        return oldClass != null
                ? oldClass
                : XposedHelpers.findClass("com.whatsapp.settings.ui.SettingsDataUsageActivity", loader);
    }

    public synchronized static Class getTextStatusComposerFragmentClass(@NonNull ClassLoader loader) throws Exception {
        var classes = new String[]{
                "com.whatsapp.status.composer.TextStatusComposerFragment",
                "com.whatsapp.statuscomposer.composer.TextStatusComposerFragment"
        };
        Class<?> result = null;
        for (var clazz : classes) {
            if ((result = XposedHelpers.findClassIfExists(clazz, loader)) != null)
                return result;
        }
        throw new Exception("TextStatusComposerFragmentClass not found");
    }

    public synchronized static Class getVoipManagerClass(@NonNull ClassLoader loader) throws Exception {
        var classes = new String[]{
                "com.whatsapp.voipcalling.Voip",
                "com.whatsapp.calling.voipcalling.Voip"
        };
        Class<?> result = null;
        for (var clazz : classes) {
            if ((result = XposedHelpers.findClassIfExists(clazz, loader)) != null)
                return result;
        }
        throw new Exception("VoipManagerClass not found");
    }

    public synchronized static Class getVoipCallInfoClass(@NonNull ClassLoader loader) throws Exception {
        var classes = new String[]{
                "com.whatsapp.voipcalling.CallInfo",
                "com.whatsapp.calling.infra.voipcalling.CallInfo"
        };
        Class<?> result = null;
        for (var clazz : classes) {
            if ((result = XposedHelpers.findClassIfExists(clazz, loader)) != null)
                return result;
        }
        throw new Exception("VoipCallInfoClass not found");
    }

    // public static Activity getActivityBySimpleName(String name) {
    // for (var activity : activities) {
    // if (activity.getClass().getSimpleName().equals(name)) {
    // return activity;
    // }
    // }
    // return null;
    // }


    @NonNull
    public static String getContactName(FMessageWpp.UserJid userJid) {
        loadWADatabase();
        if (mWaDatabase == null || userJid.isNull())
            return "Whatsapp Contact";
        String name = getSContactName(userJid, false);
        if (!TextUtils.isEmpty(name))
            return name;
        return getWppContactName(userJid);
    }

    @NonNull
    public static String getSContactName(FMessageWpp.UserJid userJid, boolean saveOnly) {
        loadWADatabase();
        if (mWaDatabase == null || userJid == null)
            return "";
        String selection;
        if (saveOnly) {
            selection = "jid = ? AND raw_contact_id > 0";
        } else {
            selection = "jid = ?";
        }
        String name = null;
        var rawJid = userJid.getPhoneRawString();
        var cursor = mWaDatabase.query("wa_contacts", new String[]{"display_name"}, selection,
                new String[]{rawJid}, null, null, null);
        if (cursor.moveToFirst()) {
            name = cursor.getString(0);
            cursor.close();
        }
        return name == null ? "" : name;
    }

    @NonNull
    public static String getWppContactName(FMessageWpp.UserJid userJid) {
        loadWADatabase();
        if (mWaDatabase == null || userJid.isNull())
            return "";
        String name = null;
        var rawJid = userJid.getPhoneRawString();
        var cursor2 = mWaDatabase.query("wa_vnames", new String[]{"verified_name"}, "jid = ?",
                new String[]{rawJid}, null, null, null);
        if (cursor2.moveToFirst()) {
            name = cursor2.getString(0);
            cursor2.close();
        }
        return name == null ? "" : name;
    }

    public static Object getFMessageFromKey(Object messageKey) {
        if (messageKey == null)
            return null;
        try {
            if (mCachedMessageStore == null) {
                XposedBridge.log("CachedMessageStore is null");
                return null;
            }
            return cachedMessageStoreKey.invoke(mCachedMessageStore, messageKey);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    @Nullable
    public static Object createUserJid(@Nullable String rawjid) {
        if (rawjid == null)
            return null;
        try {
            return mGenJidMethod.invoke(null, rawjid);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    private static FMessageWpp.UserJid cachedUserJid;
    private static int cachedActivityHash;

    @NonNull
    public static FMessageWpp.UserJid getCurrentUserJid() {
        var currentActivity = getCurrentActivity();
        if (currentActivity == null) return new FMessageWpp.UserJid();

        int currentHash = System.identityHashCode(currentActivity);
        if (cachedUserJid != null && cachedActivityHash == currentHash) {
            return cachedUserJid;
        }

        long start = System.currentTimeMillis();
        try {
            var conversation = getCurrentConversation();
            if (conversation == null)
                return new FMessageWpp.UserJid();
            ensureConversationJidResolvers(conversation.getClassLoader());

            Object jidObject = null;
            
            // Try using the captured delegate (Upstream optimization)
            if (mConversationDelegate != null && conversationJidField != null) {
                try {
                    jidObject = conversationJidField.get(mConversationDelegate);
                } catch (Exception ignored) {}
            }

            if (jidObject == null) {
                jidObject = resolveJidFromObjectMethods(conversation);
            }
            if (conversation.getClass().getSimpleName().equals("HomeActivity")) {
                try {
                    var convFragmentMethod = Unobfuscator.loadHomeConversationFragmentMethod(conversation.getClassLoader());
                    var convFragment = convFragmentMethod.invoke(null, conversation);
                    if (jidObject == null) {
                        jidObject = resolveJidFromObjectMethods(convFragment);
                    }
                    var convField = Unobfuscator.loadAntiRevokeConvFragmentField(conversation.getClassLoader());
                    var conversationDelegate = convField.get(convFragment);
                    if (jidObject == null) {
                        jidObject = extractJidFromConversationDelegate(conversationDelegate);
                    }
                    if (jidObject == null) {
                        jidObject = findJidObjectInGraph(convFragment);
                    }
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            } else {
                Object conversationDelegate = resolveConversationDelegate(conversation);
                if (jidObject == null) {
                    jidObject = extractJidFromConversationDelegate(conversationDelegate);
                }
                if (jidObject == null) {
                    jidObject = findJidObjectInGraph(conversation);
                }
            }
            if (jidObject != null) {
                var jid = new FMessageWpp.UserJid(jidObject);
                cachedUserJid = jid;
                cachedActivityHash = currentHash;
                XposedBridge.log("WAE: Resolved JID " + jid.getPhoneNumber() + " in " + (System.currentTimeMillis() - start) + "ms");
                return jid;
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return new FMessageWpp.UserJid();
    }

    private static synchronized void ensureConversationJidResolvers(ClassLoader loader) {
        if (conversationDelegateField != null && conversationJidField != null) {
            return;
        }
        long start = System.currentTimeMillis();
        XposedBridge.log("WAE: ensureConversationJidResolvers started");
        try {
            if (conversationDelegateField == null) {
                conversationDelegateField = Unobfuscator.loadConversationDelegateField(loader);
                XposedBridge.log("WAE: conversationDelegateField found: " + (conversationDelegateField != null));
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: Error loading conversationDelegateField: " + e.getMessage());
        }
        try {
            if (conversationJidField == null) {
                conversationJidField = Unobfuscator.loadUserJidConversationDelegate(loader);
                XposedBridge.log("WAE: conversationJidField found: " + (conversationJidField != null));
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: Error loading conversationJidField: " + e.getMessage());
        }
        XposedBridge.log("WAE: ensureConversationJidResolvers finished in " + (System.currentTimeMillis() - start) + "ms");
    }

    @Nullable
    private static Object resolveConversationDelegate(@NonNull Activity conversation) {
        try {
            if (conversationDelegateField == null) {
                return null;
            }
            if (conversationDelegateField.getDeclaringClass().isAssignableFrom(conversation.getClass())) {
                return conversationDelegateField.get(conversation);
            }
            var fieldObject = ReflectionUtils.getFieldByType(conversation.getClass(),
                    conversationDelegateField.getDeclaringClass());
            if (fieldObject != null) {
                return conversationDelegateField.get(fieldObject.get(conversation));
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    @Nullable
    private static Object extractJidFromConversationDelegate(@Nullable Object conversationDelegate) {
        if (conversationDelegate == null) {
            return null;
        }
        Object jid = resolveJidFromObjectMethods(conversationDelegate);
        if (jid != null) {
            return jid;
        }
        try {
            if (conversationJidField != null) {
                jid = conversationJidField.get(conversationDelegate);
                if (jid != null) {
                    return jid;
                }
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return findJidObjectInGraph(conversationDelegate);
    }

    @Nullable
    private static Object resolveJidFromObjectMethods(@Nullable Object target) {
        if (target == null) {
            return null;
        }
        try {
            for (Method method : target.getClass().getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }
                if (!isJidClass(method.getReturnType())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(target);
                    if (isJidObject(result)) {
                        return result;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static Object findJidObjectInGraph(@Nullable Object root) {
        return findJidObjectInGraph(root, 2, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Nullable
    private static Object findJidObjectInGraph(@Nullable Object root, int depth, Set<Object> visited) {
        if (root == null || depth < 0 || visited.contains(root)) {
            return null;
        }
        visited.add(root);

        if (isJidObject(root)) {
            return root;
        }

        for (Field field : ReflectionUtils.findAllFieldsUsingFilter(root.getClass(), f -> {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) || f.getType().isPrimitive()) {
                return false;
            }
            String name = f.getType().getName();
            return !name.startsWith("java.") && !name.startsWith("android.");
        })) {
            try {
                Object value = field.get(root);
                if (value == null) {
                    continue;
                }
                if (isJidObject(value)) {
                    return value;
                }
                Object nested = findJidObjectInGraph(value, depth - 1, visited);
                if (nested != null) {
                    return nested;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean isJidObject(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        return isJidClass(value.getClass());
    }

    private static boolean isJidClass(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        return (FMessageWpp.UserJid.TYPE_USERJID != null && FMessageWpp.UserJid.TYPE_USERJID.isAssignableFrom(type))
                || (FMessageWpp.UserJid.TYPE_PHONEUSERJID != null && FMessageWpp.UserJid.TYPE_PHONEUSERJID.isAssignableFrom(type))
                || (FMessageWpp.UserJid.TYPE_JID != null && FMessageWpp.UserJid.TYPE_JID.isAssignableFrom(type));
    }

    public static String stripJID(String str) {
        try {
            if (str == null)
                return null;
            if (str.contains(".") && str.contains("@") && str.indexOf(".") < str.indexOf("@")) {
                return str.substring(0, str.indexOf("."));
            } else if (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast")
                    || str.contains("@lid")) {
                return str.substring(0, str.indexOf("@"));
            }
            return str;
        } catch (Exception e) {
            XposedBridge.log(e);
            return str;
        }
    }

    @Nullable
    public static Drawable getContactPhotoDrawable(String jid) {
        if (jid == null)
            return null;
        var file = getContactPhotoFile(jid);
        if (file == null)
            return null;
        return Drawable.createFromPath(file.getAbsolutePath());
    }

    public static File getContactPhotoFile(String jid) {
        String datafolder = Utils.getApplication().getCacheDir().getParent() + "/";
        File file = new File(datafolder + "/cache/" + "Profile Pictures" + "/" + stripJID(jid) + ".jpg");
        if (!file.exists())
            file = new File(datafolder + "files" + "/" + "Avatars" + "/" + jid + ".j");
        if (file.exists())
            return file;
        return null;
    }

    public static String getMyName() {
        var startup_prefs = Utils.getApplication().getSharedPreferences("startup_prefs", Context.MODE_PRIVATE);
        return startup_prefs.getString("push_name", "WhatsApp");
    }

    // public static String getMyNumber() {
    // var mainPrefs = getMainPrefs();
    // return mainPrefs.getString("registration_jid", "");
    // }

    public static SharedPreferences getMainPrefs() {
        return Utils.getApplication().getSharedPreferences(
                Utils.getApplication().getPackageName() + "_preferences_light", Context.MODE_PRIVATE);
    }

    public static String getMyBio() {
        var mainPrefs = getMainPrefs();
        return mainPrefs.getString("my_current_status", "");
    }

    public static Drawable getMyPhoto() {
        String datafolder = Utils.getApplication().getCacheDir().getParent() + "/";
        File file = new File(datafolder + "files" + "/" + "me");
        if (file.exists())
            return Drawable.createFromPath(file.getAbsolutePath());
        file = new File(datafolder + "files" + "/" + "me.jpg");
        if (file.exists())
            return Drawable.createFromPath(file.getAbsolutePath());
        return null;
    }

    public static BottomDialogWpp createBottomDialog(Context context) {
        return new BottomDialogWpp((Dialog) XposedHelpers.newInstance(bottomDialog, context, 0));
    }

    @Nullable
    public static Activity getCurrentConversation() {
        if (mCurrentActivity == null)
            return null;
        try {
            Class<?> conversation = XposedHelpers.findClass("com.whatsapp.Conversation",
                    mCurrentActivity.getClassLoader());
            if (conversation.isInstance(mCurrentActivity))
                return mCurrentActivity;

            // for tablet UI, they're using HomeActivity instead of Conversation
            Class<?> home = getHomeActivityClass(mCurrentActivity.getClassLoader());
            if (mCurrentActivity.getResources().getConfiguration().smallestScreenWidthDp >= 600
                    && home.isInstance(mCurrentActivity))
                return mCurrentActivity;
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public static String getCurrentChatTitle() {
        try {
            Activity conversation = getCurrentConversation();
            if (conversation == null)
                return null;

            // Strategy 1: Safely get title via reflection to avoid UI thread issues
            try {
                Field f = Activity.class.getDeclaredField("mTitle");
                f.setAccessible(true);
                Object titleObj = f.get(conversation);
                if (titleObj instanceof CharSequence) {
                    String title = titleObj.toString();
                    if (!TextUtils.isEmpty(title) && !title.equalsIgnoreCase("WhatsApp")) {
                        return title;
                    }
                }
            } catch (Throwable ignored) {
            }

            // Strategy 2: Fallback to Activity.getTitle() (might be safer than
            // findViewById)
            CharSequence activityTitle = conversation.getTitle();
            if (activityTitle != null && activityTitle.length() > 0
                    && !activityTitle.toString().equalsIgnoreCase("WhatsApp")) {
                return activityTitle.toString();
            }

        } catch (Throwable t) {
            // Extremely defensive
        }
        return null;
    }

    public static SharedPreferences getPrivPrefs() {
        return privPrefs;
    }

    public static void setPrivString(String key, String value) {
        if (privPrefs == null) return;
        privPrefs.edit().putString(key, value).apply();
    }

    public static String getPrivString(String key, String defaultValue) {
        if (privPrefs == null) return defaultValue;
        return privPrefs.getString(key, defaultValue);
    }

    public static JSONObject getPrivJSON(String key, JSONObject defaultValue) {
        var jsonStr = privPrefs.getString(key, null);
        if (jsonStr == null)
            return defaultValue;
        try {
            return new JSONObject(jsonStr);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static void setPrivJSON(String key, JSONObject value) {
        if (privPrefs == null) return;
        privPrefs.edit().putString(key, value == null ? null : value.toString()).apply();
    }

    public static void removePrivKey(String s) {
        if (privPrefs == null) return;
        if (s != null && privPrefs.contains(s))
            privPrefs.edit().remove(s).apply();
    }

    public static void setPrivBoolean(String key, boolean value) {
        if (privPrefs == null) return;
        privPrefs.edit().putBoolean(key, value).apply();
    }

    public static void setPrivBooleanSync(String key, boolean value) {
        if (privPrefs == null) return;
        privPrefs.edit().putBoolean(key, value).commit();
    }


    public static boolean getPrivBoolean(String key, boolean defaultValue) {
        if (privPrefs == null) return defaultValue;
        return privPrefs.getBoolean(key, defaultValue);
    }

    public static void addListenerActivity(ActivityChangeState listener) {
        listenerAcitivity.add(listener);
    }

    public static WaeIIFace getClientBridge() throws Exception {
        if (!isBridgeConnected()) {
            synchronized (WppCore.class) {
                if (!isBridgeConnected()) {
                    if (client == null) {
                        throw new Exception("Bridge client not initialized");
                    }
                    XposedBridge.log("Bridge disconnected. Trying automatic synchronous reconnect");
                    boolean reconnected = false;
                    try {
                        reconnected = Boolean.TRUE.equals(client.connect().get(4, TimeUnit.SECONDS));
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                    }
                    if (!reconnected || !isBridgeConnected()) {
                        throw new Exception("Failed connect to Bridge");
                    }
                }
            }
        }
        return client.getService();
    }

    private static boolean isBridgeConnected() {
        var currentClient = client;
        if (currentClient == null) {
            return false;
        }
        var service = currentClient.getService();
        return service != null && service.asBinder().isBinderAlive() && service.asBinder().pingBinder();
    }

    public static FMessageWpp.UserJid getMyUserJid() {
        try {
            Object instance = meManagerInstance;
            if (instance == null && meManagerPhoneJidField != null) {
                Class<?> meManagerClass = meManagerPhoneJidField.getDeclaringClass();
                for (java.lang.reflect.Field f : meManagerClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == meManagerClass) {
                        try {
                            f.setAccessible(true);
                            instance = f.get(null);
                            if (instance != null) {
                                meManagerInstance = instance;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (instance != null && meManagerPhoneJidField != null) {
                return new FMessageWpp.UserJid(meManagerPhoneJidField.get(instance));
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public interface ActivityChangeState {

        void onChange(Activity activity, ChangeType type);

        enum ChangeType {
            CREATED, STARTED, ENDED, RESUMED, PAUSED
        }
    }

}
