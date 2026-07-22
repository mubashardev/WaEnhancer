package com.waenhancer.xposed.features.general;

import static com.waenhancer.xposed.core.FeatureLoader.disableExpirationVersion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.BaseBundle;
import android.os.Message;
import android.os.PowerManager;

import android.text.TextUtils;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
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
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.model.FilterItem;

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
import java.util.concurrent.Executor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;
import org.json.JSONArray;
import com.waenhancer.xposed.utils.ProHelper;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.features.customization.SeparateGroup;
import android.graphics.Canvas;
import android.graphics.Paint;

public class Others extends Feature {

    private static final String DEVICE_SOURCE_SUFFIX_FIELD = "wae_device_source_suffix";
    private static final String DEVICE_SOURCE_GUARD_FIELD = "wae_device_source_guard";



    private static Field cachedAbsViewField;
    private static final Set<String> dumpedMessageIds = ConcurrentHashMap.newKeySet();
    private static final Set<String> dumpedMessageRowViews = ConcurrentHashMap.newKeySet();
    private static final String PRIMARY_DEVICE_EMOJI = " \uD83D\uDCF1";
    private static final String LINKED_DEVICE_EMOJI = " \uD83D\uDDA5\uFE0F";
    private static volatile boolean messageDeviceSourceConversationActive = false;

    public static ConcurrentHashMap<Integer, Boolean> propsBoolean = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, Integer> propsInteger = new ConcurrentHashMap<>();
    private Properties properties;

    public Others(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        reloadPrefs();
        if (DEBUG) {
            ;
        }

        // receivedIncomingTimestamp
        properties = Utils.getProperties(prefs, "custom_css", "custom_filters");

        var menuWIcons = prefs.getBoolean("menuwicon", false);
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
        var disableExpiration = prefs.getBoolean("disable_expiration", true);
        var disableAds = prefs.getBoolean("disable_ads", false);

        propsInteger.put(3877, oldStatus ? igstatus ? 2 : 0 : 2);

        propsBoolean.put(18250, false);
        propsBoolean.put(11528, false);

        propsBoolean.put(4497, menuWIcons);
        // propsBoolean.put(4023, false);

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
       propsBoolean.put(3403, true);  // Enable Sticker Suggestion
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
        if (!Objects.equals(filterChats, "2")) {
            hookSearchbar(filterChats);
        }

        if (disable_sensor_proximity) {
            disableSensorProximity();
        }

        if (proximity_audios) {
            var classes = Unobfuscator.loadProximitySensorListenerClasses(classLoader);
            for (var cls : classes) {
                try {
                    XposedBridge.hookAllMethods(cls, "onSensorChanged", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                SensorEvent event = (SensorEvent) param.args[0];
                                if (event.sensor != null && event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                                    param.setResult(null);
                                }
                            }
                        }
                    });
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] Failed to hook onSensorChanged on class: " + cls.getName() + " : " + t.toString());
                }
            }
        }

        if (disableMetaAI) {
            hideMetaAIFab();
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

        // Only initialize animation hook if an animation is actually selected
        if (!Objects.equals(prefs.getString("animation_list", "default"), "default") || properties.containsKey("home_list_animation")) {
            animationList();
        }

        stampCopiedMessage();
        debugDumpMessageMetadata();

        if (prefs.getBoolean("message_device_source", true)) {
            WppCore.addListenerActivity((activity, state) -> {
                String simpleName = activity.getClass().getSimpleName();
                if (state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                    messageDeviceSourceConversationActive = "Conversation".equals(simpleName);
                    return;
                }
                if ("Conversation".equals(simpleName)
                        && (state == WppCore.ActivityChangeState.ChangeType.PAUSED
                        || state == WppCore.ActivityChangeState.ChangeType.ENDED)) {
                    messageDeviceSourceConversationActive = false;
                }
            });
            hookMessageDeviceSourceTextView();
            messageDeviceSourceTag();
        }

        if (prefs.getBoolean("selectable_message", false)) {
            ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
                @Override
                public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                    try {
                        var messageTextView = (TextView) viewGroup.findViewById(Utils.getID("message_text", "id"));
                        if (messageTextView != null) {
                            // Intercept long-press gestures directly on the message TextView
                            messageTextView.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    showSelectTextDialog(v.getContext(), messageTextView.getText().toString());
                                    return true; // Consume long click so WhatsApp's row selection is not triggered
                                }
                            });
                        }
                    } catch (Throwable t) {
                        logDebug("Selectable message bind error", t);
                    }
                }
            });
        }

        try {
            doubleTapReaction();
        } catch (Exception e) {
            logDebug(e);
        }

        alwaysOnline();

        callInfo();

        XposedBridge.log("[WAEX] Unconditional disablePhotoProfileStatus check starting...");
        try {
            disablePhotoProfileStatus();
            XposedBridge.log("[WAEX] disablePhotoProfileStatus hook applied successfully!");
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] disablePhotoProfileStatus error: " + t.toString());
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
        try {
            Class<?> conversationClass = XposedHelpers.findClass("com.whatsapp.Conversation", classLoader);
            Class<?> current = conversationClass;
            Method onResumeMethod = null;
            while (current != null && current != Object.class) {
                try {
                    onResumeMethod = current.getDeclaredMethod("onResume");
                    break;
                } catch (NoSuchMethodException e) {
                    current = current.getSuperclass();
                }
            }
            if (onResumeMethod != null) {
                XposedBridge.hookMethod(onResumeMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        if (!conversationClass.isInstance(activity)) {
                            return;
                        }
                        Intent intent = activity.getIntent();
                        if (intent != null && disableMetaAI) {
                            String jid = intent.getStringExtra("jid");
                            boolean isMetaAi = false;
                            if (jid != null && (jid.contains("1313555") || jid.contains("meta"))) {
                                isMetaAi = true;
                            }
                            if (intent.hasExtra("bot_metrics_entrypoint") || intent.hasExtra("extra_presentation_source")) {
                                isMetaAi = true;
                            }

                            if (isMetaAi) {
                                if (!activity.isFinishing()) {
                                    activity.finish();
                                    Toast.makeText(activity, "Meta AI functions are disabled", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                });
            } else {
                /* Log removed */
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to hook Conversation: " + t.toString());
        }

    }

    private void disableHomeFilters() throws Exception {
        propsBoolean.put(15345, true);
        propsBoolean.put(13546, false);
        propsBoolean.put(13408, true);

        Class<?> filterView = Unobfuscator.loadChatFilterView(classLoader);
        Class<?> homeClass = WppCore.getHomeActivityClass(classLoader);

        XposedBridge.hookAllConstructors(filterView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // Only hide during HomeActivity — other activities may share this view class
                View view = (View) param.thisObject;
                Activity current = Utils.getActivityFromView(view);
                if (current == null) {
                    current = WppCore.getCurrentActivity();
                }
                if (current == null || !homeClass.isInstance(current)) {
                    return;
                }
                view.setVisibility(View.GONE);
            }
        });

        try {
            XposedHelpers.findAndHookMethod(filterView, "setVisibility", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Activity current = Utils.getActivityFromView(view);
                    if (current == null) {
                        current = WppCore.getCurrentActivity();
                    }
                    if (current == null || !homeClass.isInstance(current)) {
                        return;
                    }
                    if ((int) param.args[0] != View.GONE) {
                        param.args[0] = View.GONE;
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            XposedHelpers.findAndHookMethod(filterView, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Activity current = Utils.getActivityFromView(view);
                    if (current == null) {
                        current = WppCore.getCurrentActivity();
                    }
                    if (current == null || !homeClass.isInstance(current)) {
                        return;
                    }
                    view.setVisibility(View.GONE);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * Hides the Meta AI FAB (resource id: fab_second, content-desc: "Message
     * your assistant"). The FAB lives inside ConversationsFragment, not
     * HomeActivity's layout directly. We hook onViewCreated (initial inflation)
     * and onResume (re-show safety net), both scoped only to
     * ConversationsFragment — zero global hook overhead.
     */
    private static Method cachedGetCurrentItemMethod = null;
    private static int cachedPagerId = -1;

    private static boolean isNotUpdatesTabActive(View view) {
        if (view == null) {
            return false;
        }
        try {
            View root = view.getRootView();
            if (cachedPagerId == -1) {
                cachedPagerId = root.getResources().getIdentifier("pager", "id", root.getContext().getPackageName());
            }
            if (cachedPagerId > 0) {
                View pager = root.findViewById(cachedPagerId);
                if (pager != null) {
                    if (cachedGetCurrentItemMethod == null) {
                        cachedGetCurrentItemMethod = pager.getClass().getMethod("getCurrentItem");
                    }
                    Integer currentItem = (Integer) cachedGetCurrentItemMethod.invoke(pager);
                    if (currentItem != null) {
                        int statusIndex = 1;
                        try {
                            int idx = SeparateGroup.tabs.indexOf(SeparateGroup.STATUS);
                            if (idx != -1) {
                                statusIndex = idx;
                            }
                        } catch (Throwable ignored) {
                        }
                        return currentItem != statusIndex;
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore log spam
        }
        return true; // Fallback to hiding during initial layout / early inflation
    }

    private static final Set<String> hookedPageChangeListeners = new HashSet<>();

    private static void hookOnPageSelectedOnListenerClass(Class<?> listenerClass, final int fabId, Class<?> listenerInterface) {
        final String className = listenerClass.getName();
        synchronized (hookedPageChangeListeners) {
            if (hookedPageChangeListeners.contains(className)) {
                return;
            }
            hookedPageChangeListeners.add(className);
        }

        // Find all void(int) methods declared in the listener interface (obfuscated or not) and hook them
        for (Method m : listenerInterface.getDeclaredMethods()) {
            if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class && m.getReturnType() == void.class) {
                final String methodName = m.getName();
                try {
                    XposedHelpers.findAndHookMethod(listenerClass, methodName, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity current = WppCore.getCurrentActivity();
                            if (current != null) {
                                View fab = current.findViewById(fabId);
                                if (fab != null) {
                                    boolean hideFab = isNotUpdatesTabActive(fab);
                                    if (hideFab) {
                                        fab.setVisibility(View.GONE);
                                    } else {
                                        fab.setVisibility(View.VISIBLE);
                                    }
                                }
                            }
                        }
                    });
                } catch (Throwable t) {
                }
            }
        }
    }

    private static void setupViewPagerHooks(Class<?> vpClass, final int fabId) {
        try {
            Class<?> vpSuper = null;
            Class<?> current = vpClass;
            while (current != null && current != Object.class) {
                if (current.getName().contains("ViewPager")) {
                    vpSuper = current;
                    break;
                }
                current = current.getSuperclass();
            }

            if (vpSuper == null) {
                return;
            }

            // Programmatically find the listener interface from setOnPageChangeListener signature
            Class<?> listenerInterface = null;
            for (Method m : vpSuper.getDeclaredMethods()) {
                if (m.getName().equals("setOnPageChangeListener") && m.getParameterTypes().length == 1) {
                    listenerInterface = m.getParameterTypes()[0];
                    break;
                }
            }

            if (listenerInterface == null) {
                for (Method m : vpSuper.getMethods()) {
                    if (m.getName().equals("setOnPageChangeListener") && m.getParameterTypes().length == 1) {
                        listenerInterface = m.getParameterTypes()[0];
                        break;
                    }
                }
            }

            if (listenerInterface == null) {
                return;
            }

            final Class<?> finalInterface = listenerInterface;

            // Find all methods on ViewPager that accept finalInterface as a single parameter and hook them
            for (Method m : vpSuper.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == finalInterface) {
                    final String mName = m.getName();
                    try {
                        XposedHelpers.findAndHookMethod(vpSuper, mName, finalInterface, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object listener = param.args[0];
                                if (listener != null) {
                                    hookOnPageSelectedOnListenerClass(listener.getClass(), fabId, finalInterface);
                                }
                            }
                        });
                    } catch (Throwable t) {
                    }
                }
            }

            // Also check standard public methods from superclasses
            for (Method m : vpSuper.getMethods()) {
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == finalInterface) {
                    final String mName = m.getName();
                    try {
                        XposedHelpers.findAndHookMethod(vpSuper, mName, finalInterface, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object listener = param.args[0];
                                if (listener != null) {
                                    hookOnPageSelectedOnListenerClass(listener.getClass(), fabId, finalInterface);
                                }
                            }
                        });
                    } catch (Throwable t) {
                        // ignore if already hooked
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    private void hideMetaAIFab() throws Exception {
        final int fabId = Utils.getID("fab_second", "id");

        if (fabId > 0) {
            // Hook ViewPager early to intercept its listeners as they are being set on startup
            try {
                Class<?> vpClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", classLoader);
                setupViewPagerHooks(vpClass, fabId);
            } catch (Throwable t) {
            }

            // Dynamic interceptor to force GONE on any post-layout visibility updates by WhatsApp
            final XC_MethodHook visibilityHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == fabId) {
                        if (isNotUpdatesTabActive(view)) {
                            param.args[0] = View.GONE;
                        }
                    }
                }
            };

            // Hook setVisibility on ImageView.class to narrow down hook scope significantly (since FAB is an ImageView)
            XposedHelpers.findAndHookMethod(ImageView.class, "setVisibility", int.class, visibilityHook);

            // Hook 1: Catch the view immediately when it attaches to the window hierarchy
            XposedHelpers.findAndHookMethod(ImageView.class, "onAttachedToWindow", new XC_MethodHook() {
                private final Set<Class<?>> hookedClasses = new HashSet<>();

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == fabId) {
                        boolean hideFab = isNotUpdatesTabActive(view);

                        // Dynamically traverse up the hierarchy to hook setVisibility overrides (always do this for fab_second)
                        Class<?> clazz = view.getClass();
                        while (clazz != null && clazz != ImageView.class && clazz != View.class) {
                            boolean alreadyHooked;
                            synchronized (hookedClasses) {
                                alreadyHooked = hookedClasses.contains(clazz);
                            }
                            if (!alreadyHooked) {
                                try {
                                    // Check if this class overrides setVisibility
                                    clazz.getDeclaredMethod("setVisibility", int.class);

                                    // Hook it
                                    XposedHelpers.findAndHookMethod(clazz, "setVisibility", int.class, visibilityHook);
                                    synchronized (hookedClasses) {
                                        hookedClasses.add(clazz);
                                    }
                                } catch (NoSuchMethodException ignored) {
                                    // Walk up
                                } catch (Throwable t) {
                                }
                            }
                            clazz = clazz.getSuperclass();
                        }

                        if (hideFab) {
                            view.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    }

    private static void hideFabSecond(View root) {
        try {
            if (root == null) {
                return;
            }
            int fabId = Utils.getID("fab_second", "id");
            if (fabId <= 0) {
                return;
            }
            View fab = root.findViewById(fabId);
            if (fab != null) {
                if (fab.getVisibility() != View.GONE) {
                    fab.setVisibility(View.GONE);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Filters layout elements by their resource ID by hooking
     * View.invalidate(boolean). This replicates the dev4mod behavior of
     * dynamically forcing visibility to GONE on any invalidated view whose ID
     * is listed in the filter config.
     */
    private void filterItems(String filterItems) {
        String currentPkg = null;
        if (FeatureLoader.mApp != null) {
            currentPkg = FeatureLoader.mApp.getPackageName();
        }
        if (currentPkg == null) {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                currentPkg = (String) activityThreadClass.getMethod("currentPackageName").invoke(null);
            } catch (Throwable ignored) {
            }
        }
        if ("com.waenhancer".equals(currentPkg)) {
            return;
        }

        var itemsList = new ArrayList<FilterItem>();
        if (filterItems.trim().startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(filterItems);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String idStr = obj.optString("id", "").trim();
                    if (!idStr.isEmpty()) {
                        String behavior = obj.optString("behavior", FilterItem.BEHAVIOR_GONE);
                        int color = obj.optInt("color", 0xFFFF0000);
                        int opacity = obj.optInt("opacity", 100);
                        double scale = obj.optDouble("scale", 1.0);
                        itemsList.add(new FilterItem(idStr, behavior, color, opacity, (float) scale));
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("[WAEX] Failed to parse JSON filter_items: " + e.toString());
            }
        } else {
            // Fallback to old format
            var items = filterItems.split("\n");
            for (String item : items) {
                String idStr = item.trim();
                if (!idStr.isEmpty()) {
                    itemsList.add(new FilterItem(idStr, FilterItem.BEHAVIOR_GONE, 0xFFFF0000, 100, 1.0f));
                }
            }
        }

        if (itemsList.isEmpty()) {
            return;
        }

        // Build a map of layout ids to FilterItem config
        var targetMap = new HashMap<Integer, FilterItem>();
        for (var item : itemsList) {
            var id = Utils.getID(item.id, "id");
            if (id > 0) {
                targetMap.put(id, item);
            }
        }
        if (targetMap.isEmpty()) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(View.class, "invalidate", boolean.class, new XC_MethodHook() {
                private final ThreadLocal<Boolean> inHook = ThreadLocal.withInitial(() -> false);

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (inHook.get()) {
                        return;
                    }
                    inHook.set(true);
                    try {
                        var view = (View) param.thisObject;
                        var id = view.getId();
                        if (id > 0 && targetMap.containsKey(id)) {
                            FilterItem item = targetMap.get(id);
                            if (item == null) {
                                return;
                            }
                            switch (item.behavior) {
                                case FilterItem.BEHAVIOR_GONE:
                                    if (view.getVisibility() == View.VISIBLE) {
                                        view.setVisibility(View.GONE);
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    } finally {
                        inHook.set(false);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to hook View.invalidate(boolean) for item filtering: " + t.toString());
        }
    }

    private void disableAds() {
        propsBoolean.put(22904, true);
        propsBoolean.put(14306, false);
    }

    private void disablePhotoProfileStatus() throws Exception {
        Class<?> refreshStatusClass;
        try {
            refreshStatusClass = Unobfuscator.loadRefreshStatusClass(classLoader);
        } catch (Exception e) {
            XposedBridge.log("[WAEX] disablePhotoProfileStatus: RefreshStatus class not found, skipping: " + e.toString());
            return;
        }
        var photoProfileClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".WDSProfilePhoto");
        XposedBridge.log("[WAEX] disablePhotoProfileStatus: refreshStatusClass=" + refreshStatusClass.getName());
        XposedBridge.log("[WAEX] disablePhotoProfileStatus: photoProfileClass=" + (photoProfileClass != null ? photoProfileClass.getName() : "null"));
        var convClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, ".ConversationsFragment");
        XposedBridge.log("[WAEX] disablePhotoProfileStatus: convClass=" + (convClass != null ? convClass.getName() : "null"));
        var jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
        XposedBridge.log("[WAEX] disablePhotoProfileStatus: jidClass=" + (jidClass != null ? jidClass.getName() : "null"));
        var method = ReflectionUtils.findMethodUsingFilter(convClass, m -> m.getParameterCount() > 0 && !Modifier.isStatic(m.getModifiers()) && m.getParameterTypes()[0] == View.class && ReflectionUtils.findIndexOfType(m.getParameterTypes(), jidClass) != -1);
        XposedBridge.log("[WAEX] disablePhotoProfileStatus: method=" + (method != null ? method.getName() : "null"));
        var field = ReflectionUtils.getFieldByExtendType(convClass, refreshStatusClass);
        XposedBridge.log("[WAEX] disablePhotoProfileStatus: field=" + (field != null ? field.getName() : "null"));
        if (field == null) {
            XposedBridge.log("[WAEX] disablePhotoProfileStatus: field is null, dumping all fields of convClass:");
            for (Field f : convClass.getDeclaredFields()) {
                XposedBridge.log("[WAEX] convClass field: " + f.getName() + " of type " + f.getType().getName());
            }
            XposedBridge.log("[WAEX] disablePhotoProfileStatus: field is null, returning early!");
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
        if (!prefs.getBoolean("call_info", false)) {
            return;
        }

        var clsCallEventCallback = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "VoiceServiceEventCallback");
        Class<?> clsWamCall = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "WamCall");

        XposedBridge.hookAllMethods(clsCallEventCallback, "fieldstatsReady", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (clsWamCall.isInstance(param.args[0])) {

                    Object callinfo = XposedHelpers.callMethod(param.thisObject, "getCallInfo");
                    if (callinfo == null) {
                        return;
                    }
                    var userJid = new FMessageWpp.UserJid(XposedHelpers.callMethod(callinfo, "getPeerJid"));
                    if (userJid.isNull()) {
                        return;
                    }
                    CompletableFuture.runAsync(() -> {
                        try {
                            showCallInformation(param.args[0], userJid);
                        } catch (Throwable t) {
                            logDebug(t);
                        }
                    });
                }
            }
        });
    }

    private static Object getObjectFieldSafe(Object obj, String fieldName) {
        try {
            return XposedHelpers.getObjectField(obj, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showCallInformation(Object wamCall, FMessageWpp.UserJid userJid) throws Throwable {
        if (userJid.isGroup()) {
            return;
        }
        var sb = new StringBuilder();
        var contact = WppCore.getContactName(userJid);
        var number = userJid.getPhoneNumber();
        if (!TextUtils.isEmpty(contact)) {
            sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.contact_s, "Contact: %s"), contact)).append("\n");
        }
        sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.phone_number_s, "Number: +%s"), number)).append("\n");
        
        var ip = (String) getObjectFieldSafe(wamCall, "callPeerIpStr");
        if (ip != null) {
            try {
                var client = new OkHttpClient.Builder().build();
                var url = "http://ip-api.com/json/" + ip;
                var request = new Request.Builder().url(url).build();
                var content = client.newCall(request).execute().body().string();
                var json = new JSONObject(content);
                var country = json.optString("country", "Unknown");
                var city = json.optString("city", "Unknown");
                var isp = json.optString("isp", "null");
                var region = json.optString("regionName", "null");
                var timeZone = json.optString("timezone", "null");
                if (!"null".equals(isp)) {
                    sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.isp_s, "ISP: %s"), isp)).append("\n");
                }
                if (!"null".equals(region)) {
                    sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.region_s, "Region: %s"), region)).append("\n");
                }
                if (!"null".equals(timeZone)) {
                    sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.timezone_s, "Timezone: %s"), timeZone)).append("\n");
                }
                sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.country_s, "Country: %s"), country)).append("\n")
                  .append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.city_s, "City: %s"), city)).append("\n")
                  .append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.ip_s, "IP: %s"), ip)).append("\n");
            } catch (Throwable e) {
                sb.append("IP: ").append(ip).append("\n");
            }
        }
        
        var platform = (String) getObjectFieldSafe(wamCall, "callPeerPlatform");
        if (platform != null) {
            sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.platform_s, "Platform: %s"), platform)).append("\n");
        }
        var wppVersion = (String) getObjectFieldSafe(wamCall, "callPeerAppVersion");
        if (wppVersion != null) {
            sb.append(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.wpp_version_s, "WhatsApp Version: %s"), wppVersion)).append("\n");
        }
        
        XposedBridge.log("[WAEX] Call Info: " + sb.toString().replace("\n", ", "));
        Utils.showNotification(FeatureLoader.getModuleString(Utils.getApplication(), R.string.call_information, "Call Information"), sb.toString());
    }

    private void alwaysOnline() throws Exception {
        if (!prefs.getBoolean("always_online", false)) {
            return;
        }
        var stateChange = Unobfuscator.loadStateChangeMethod(classLoader);
        XposedBridge.hookMethod(stateChange, XC_MethodReplacement.DO_NOTHING);
    }

    private void doubleTapReaction() throws Exception {

        if (!prefs.getBoolean("doubletap2like", false)) {
            return;
        }

        var emoji = prefs.getString("doubletap2like_emoji", "👍");

        var conversationRowClass = Unobfuscator.loadConversationRowClass(classLoader);

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
        if (!prefs.getBoolean("stamp_copied_message", false)) {
            return;
        }

        var copiedMessage = Unobfuscator.loadCopiedMessageMethod(classLoader);

        XposedBridge.hookMethod(copiedMessage, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var Collection = (Collection) param.args[param.args.length - 1];
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
        if (!DEBUG) {
            return;
        }

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                try {
                    var key = fMessage.getKey();
                    if (key == null || TextUtils.isEmpty(key.messageID)) {
                        return;
                    }
                    if (!dumpedMessageIds.add(key.messageID)) {
                        return;
                    }
                } catch (Throwable t) {
                    logDebug("MessageMetaDumpError", t);
                }
            }
        });
    }

    private void messageDeviceSourceTag() {
        if (!prefs.getBoolean("message_device_source", true)) {
            return;
        }

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                var dateTextView = (TextView) viewGroup.findViewById(Utils.getID("date", "id"));
                if (dateTextView == null) {
                    return;
                }

                var key = fMessage.getKey();
                String messageId = key != null ? key.messageID : null;
                if (messageId == null) {
                    return;
                }

                XposedHelpers.setAdditionalInstanceField(dateTextView, "wae_device_source_message_id", messageId);

                // Offload database lookup to a background thread
                CompletableFuture.supplyAsync(() -> {
                    return resolveMessageDeviceId(messageId, fMessage);
                }).thenAcceptAsync(resolvedDeviceId -> {
                    Object currentId = XposedHelpers.getAdditionalInstanceField(dateTextView, "wae_device_source_message_id");
                    if (!Objects.equals(currentId, messageId)) {
                        return;
                    }

                    String suffix = getDeviceEmojiSuffix(resolvedDeviceId);
                    XposedHelpers.setAdditionalInstanceField(dateTextView, DEVICE_SOURCE_SUFFIX_FIELD, suffix);

                    bindMessageDeviceSource(dateTextView, resolvedDeviceId);
                    // Optimized view update: only recurse if absolutely necessary
                    if (!suffix.isEmpty()) {
                        applyDeviceSourceToMatchingTextViews(viewGroup, dateTextView, suffix);
                    }
                }, executor); // Use a shared executor for UI updates
            }
        });
    }

    private static final Executor executor = action -> {
        var handler = new Handler(Looper.getMainLooper());
        handler.post(action);
    };

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
        if (deviceId == 0) {
            return PRIMARY_DEVICE_EMOJI;
        }
        if (deviceId > 0) {
            return LINKED_DEVICE_EMOJI;
        }
        return "";
    }

    private void applyDeviceSourceToMatchingTextViews(ViewGroup root, TextView anchor, String suffix) {
        if (suffix.isEmpty()) {
            return;
        }
        String anchorBase = stripDeviceEmoji(String.valueOf(anchor.getText()));
        if (TextUtils.isEmpty(anchorBase)) {
            return;
        }
        int deviceId = getDeviceIdFromSuffix(suffix);

        forEachTextView(root, textView -> {
            String current = String.valueOf(textView.getText());
            String base = stripDeviceEmoji(current);
            if (!anchorBase.equals(base)) {
                return;
            }

            XposedHelpers.setAdditionalInstanceField(textView, DEVICE_SOURCE_SUFFIX_FIELD, suffix);
            bindMessageDeviceSourceClick(textView, deviceId);
            if (!current.equals(base + suffix)) {
                textView.setText(base + suffix);
            }
        });
    }

    private int getDeviceIdFromSuffix(String suffix) {
        if (PRIMARY_DEVICE_EMOJI.equals(suffix)) {
            return 0;
        }
        if (LINKED_DEVICE_EMOJI.equals(suffix)) {
            return 1;
        }
        return -1;
    }

    private void bindMessageDeviceSourceClick(TextView textView, int deviceId) {
        if (deviceId < 0) {
            Utils.setViewClickListener(textView, "device_source", null);
            return;
        }

        Utils.setViewClickListener(textView, "device_source", v -> {
            if (deviceId == 0) {
                Utils.showToast(FeatureLoader.getModuleString(
                        R.string.message_sent_via_phone,
                        "This message was sent via Phone"), Toast.LENGTH_SHORT);
            } else if (deviceId > 0) {
                Utils.showToast(FeatureLoader.getModuleString(
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

    private void forEachTextView(View view, Consumer<TextView> consumer) {
        if (view instanceof TextView textView) {
            consumer.accept(textView);
            return;
        }
        if (!(view instanceof ViewGroup group)) {
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            forEachTextView(group.getChildAt(i), consumer);
        }
    }

    private String stripDeviceEmoji(String text) {
        return text.replace(PRIMARY_DEVICE_EMOJI, "").replace(LINKED_DEVICE_EMOJI, "");
    }

    private void hookMessageDeviceSourceTextView() {
        XC_MethodHook setTextHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!messageDeviceSourceConversationActive) {
                    return;
                }

                if (!(param.thisObject instanceof TextView textView)) {
                    return;
                }
                Object suffixObj = XposedHelpers.getAdditionalInstanceField(textView, DEVICE_SOURCE_SUFFIX_FIELD);
                if (!(suffixObj instanceof String suffix) || suffix.isEmpty()) {
                    return;
                }
                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(textView, DEVICE_SOURCE_GUARD_FIELD))) {
                    return;
                }

                String current = String.valueOf(textView.getText());
                String base = current.replace(PRIMARY_DEVICE_EMOJI, "").replace(LINKED_DEVICE_EMOJI, "");
                String desired = base + suffix;
                if (desired.equals(current)) {
                    return;
                }

                XposedHelpers.setAdditionalInstanceField(textView, DEVICE_SOURCE_GUARD_FIELD, true);
                try {
                    textView.setText(desired);
                } finally {
                    XposedHelpers.setAdditionalInstanceField(textView, DEVICE_SOURCE_GUARD_FIELD, false);
                }
            }
        };

        try {
            Method internalSetText = ReflectionUtils.findMethodUsingFilter(TextView.class,
                    method -> method.getName().equals("setText")
                    && method.getParameterCount() == 4
                    && method.getParameterTypes()[0] == CharSequence.class
                    && method.getParameterTypes()[1] == TextView.BufferType.class
                    && method.getParameterTypes()[2] == boolean.class
                    && method.getParameterTypes()[3] == int.class);
            if (internalSetText != null) {
                XposedBridge.hookMethod(internalSetText, setTextHook);
                return;
            }
        } catch (Throwable t) {
            logDebug("message_device_source setText hook fallback", t);
        }

        XposedBridge.hookAllMethods(TextView.class, "setText", setTextHook);
    }

    private static Animation cachedAnimationObject = null;
    private static String cachedAnimationName = null;

    private void animationList() throws Exception {
        final String animation = prefs.getString("animation_list", "default");
        if (animation.equals("default") && !properties.containsKey("home_list_animation")) {
            return;
        }

        var onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader);
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);

        if (cachedAbsViewField == null) {
            cachedAbsViewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
            if (cachedAbsViewField != null) {
                cachedAbsViewField.setAccessible(true);
            }
        }

        if (cachedAbsViewField == null) {
            return;
        }

        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            @SuppressLint("ResourceType")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var view = (View) cachedAbsViewField.get(viewHolder);
                if (view == null) {
                    return;
                }

                Animation anim = null;
                String currentAnim = animation;
                if (currentAnim.equals("default")) {
                    currentAnim = properties.getProperty("home_list_animation");
                }

                if (currentAnim != null && !currentAnim.equals("default")) {
                    synchronized (Others.class) {
                        if (!currentAnim.equals(cachedAnimationName) || cachedAnimationObject == null) {
                            cachedAnimationObject = AnimationUtil.getAnimation(currentAnim);
                            cachedAnimationName = currentAnim;
                        }
                        anim = cachedAnimationObject;
                    }
                }

                if (anim != null && view.getAnimation() == null) {
                    view.startAnimation(anim);
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
        XposedBridge.hookMethod(sendAudioTypeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var results = ReflectionUtils.findInstancesOfType(param.args, Integer.class);
                if (results.size() < 2) {
                    return;
                }
                var mediaType = results.get(0);
                var audioType = results.get(1);
                if (audio_type == 2) {
                    // Transcode local audio to Opus before sending as voice note
                    Uri originalUri = null;
                    int uriArgIndex = -1;

                    var uris = ReflectionUtils.findInstancesOfType(param.args, Uri.class);
                    if (!uris.isEmpty()) {
                        originalUri = uris.get(0).second;
                        uriArgIndex = uris.get(0).first;
                    }

                    Field fileField = null;
                    Object fileFieldContainer = null;
                    File originalFile = null;

                    if (originalUri == null) {
                        // Scan arguments for a non-null java.io.File field
                        for (Object arg : param.args) {
                            if (arg == null) {
                                continue;
                            }
                            Class<?> clazz = arg.getClass();
                            while (clazz != null && clazz != Object.class) {
                                try {
                                    for (Field f : clazz.getDeclaredFields()) {
                                        if (f.getType() == File.class) {
                                            f.setAccessible(true);
                                            File fileVal = (File) f.get(arg);
                                            if (fileVal != null) {
                                                originalFile = fileVal;
                                                fileField = f;
                                                fileFieldContainer = arg;
                                                break;
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                                if (originalFile != null) {
                                    break;
                                }
                                clazz = clazz.getSuperclass();
                            }
                            if (originalFile != null) {
                                break;
                            }
                        }
                        if (originalFile != null) {
                            originalUri = Uri.fromFile(originalFile);
                        }
                    }

                    if (originalUri != null) {
                        Context context = Utils.getApplication();
                        if (context != null) {
                            File transcodedFile = ProHelper.convertAudioToOpus(context, originalUri);
                            if (transcodedFile != null && transcodedFile.exists()) {
                                boolean replacedOnDisk = false;
                                if (originalFile != null && originalFile.exists()) {
                                    try {
                                        copyFile(transcodedFile, originalFile);
                                        replacedOnDisk = true;
                                    } catch (Throwable t) {
                                        XposedBridge.log("[WAEX-AudioToOpus] Error overwriting original file: " + t.toString());
                                    }
                                }

                                if (!replacedOnDisk) {
                                    if (uriArgIndex != -1) {
                                        param.args[uriArgIndex] = Uri.fromFile(transcodedFile);
                                    }
                                    if (fileField != null && fileFieldContainer != null) {
                                        try {
                                            fileField.set(fileFieldContainer, transcodedFile);
                                        } catch (Throwable t) {
                                            XposedBridge.log("[WAEX-AudioToOpus] Error replacing File field: " + t.toString());
                                        }
                                    }
                                }
                                param.args[audioType.first] = 1; // 1 = voice notes
                                return;
                            }
                        }
                    }
                }

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

    private static void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
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
        var spanClasses = Unobfuscator.loadEmojiSpanClasses(classLoader);
        if (spanClasses == null || spanClasses.length == 0) {
            /* Log removed */
        } else {
            /* Log removed */
            for (var spanClass : spanClasses) {
                /* Log removed */
                try {
                    if (Modifier.isAbstract(spanClass.getModifiers())) {
                        /* Log removed */
                        continue;
                    }
                    var methods = ReflectionUtils.findAllMethodsUsingFilter(spanClass, method -> 
                        method.getName().equals("draw") && 
                        method.getParameterCount() == 9 &&
                        !Modifier.isAbstract(method.getModifiers())
                    );
                    /* Log removed */
                    for (var method : methods) {
                        /* Log removed */
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Canvas canvas = (Canvas) param.args[0];
                                CharSequence text = (CharSequence) param.args[1];
                                int start = (int) param.args[2];
                                int end = (int) param.args[3];
                                float x = (float) param.args[4];
                                int top = (int) param.args[5];
                                int y = (int) param.args[6];
                                int bottom = (int) param.args[7];
                                Paint paint = (Paint) param.args[8];

                                if (text != null && start >= 0 && end > start && end <= text.length()) {
                                    CharSequence emoji = text.subSequence(start, end);
                                    canvas.drawText(emoji.toString(), x, y, paint);
                                }
                                param.setResult(null);
                            }
                        });
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] Error hooking ReplacementSpan draw method for class " + spanClass.getName() + ": " + t.toString());
                }
            }
        }    }

    private void showOnline(boolean showOnline) throws Exception {
        var checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader);
        XposedBridge.hookMethod(checkOnlineMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var message = (Message) param.args[0];
                if (message.arg1 != 5) {
                    return;
                }
                BaseBundle baseBundle = (BaseBundle) message.obj;
                var jid = baseBundle.getString("jid");
                if (TextUtils.isEmpty(jid)) {
                    return;
                }
                var userjid = new FMessageWpp.UserJid(jid);
                if (userjid.isGroup()) {
                    return;
                }
                var name = WppCore.getContactName(userjid);
                name = TextUtils.isEmpty(name) ? userjid.getPhoneNumber() : name;
                if (showOnline) {
                    Utils.showToast(String.format(FeatureLoader.getModuleString(Utils.getApplication(), R.string.toast_online, "%s is online"), name), Toast.LENGTH_SHORT);
                }
                Tasker.sendTaskerEvent(name, WppCore.stripJID(jid), "contact_online");
            }
        });
    }

    private void hookProps() throws Exception {
        var methodPropsBoolean = Unobfuscator.loadPropsBooleanMethod(classLoader);
        var dataUsageActivityClass = WppCore.getDataUsageActivityClass(classLoader);
        XposedBridge.hookMethod(methodPropsBoolean, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var list = ReflectionUtils.findInstancesOfType(param.args, Integer.class);
                if (list.isEmpty()) return;
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
                if (list.isEmpty()) return;
                int i = (int) list.get(0).second;
                var propValue = propsInteger.get(i);
                if (propValue == null) return;
                param.setResult(propValue);
            }
        });
    }

    private void hookSearchbar(String filterChats) throws Exception {
        Method searchbar = Unobfuscator.loadViewAddSearchBarMethod(classLoader);
        /* Log removed */
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
                    if (!Objects.equals(filterChats, "1")) {
                        return;
                    }
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

        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var searchId = Utils.getID("menuitem_search", "id");
                if (searchId > 0) {
                    var item = menu.findItem(searchId);
                    if (item != null) {
                        item.setVisible(Objects.equals(filterChats, "1"));
                    }
                }
            }
        });
    }

    @SuppressLint("SetTextI18n")
    public static void showSelectTextDialog(Context context, String text) {
        try {
            boolean isDark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            int textColor = isDark ? 0xFFE9EDEF : 0xFF111B21; // WhatsApp light text vs dark text

            AlertDialogWpp dialog = new AlertDialogWpp(context);
            dialog.setTitle("Select Text");

            TextView contentTextView = new TextView(context);
            contentTextView.setText(text, TextView.BufferType.SPANNABLE);
            contentTextView.setTextSize(16);
            contentTextView.setTextColor(textColor);
            contentTextView.setTextIsSelectable(true);
            contentTextView.setFocusable(true);
            contentTextView.setFocusableInTouchMode(true);
            contentTextView.setHighlightColor(0x4D00A884); // Semi-transparent WhatsApp Green

            // Add vertical padding inside the bottom sheet
            int verticalPadding = (int) (8 * context.getResources().getDisplayMetrics().density);
            contentTextView.setPadding(0, verticalPadding, 0, verticalPadding);

            dialog.setView(contentTextView);

            dialog.setPositiveButton("COPY ALL", (dialogInterface, which) -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("Copied Text", text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
                dialogInterface.dismiss();
            });

            dialog.setNegativeButton("CLOSE", (dialogInterface, which) -> dialogInterface.dismiss());

            dialog.show();
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error showing select text dialog: " + t.toString());
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Others";
    }
}