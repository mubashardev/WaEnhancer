package com.waenhancer.xposed.core;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.waenhancer.App;
import com.waenhancer.BuildConfig;
import com.waenhancer.UpdateChecker;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.SharedPreferencesWrapper;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.features.customization.BubbleColors;
import com.waenhancer.xposed.features.customization.ContactBlockedVerify;
import com.waenhancer.xposed.features.customization.CustomThemeV2;
import com.waenhancer.xposed.features.customization.CustomTime;
import com.waenhancer.xposed.features.customization.CustomToolbar;
import com.waenhancer.xposed.features.customization.CustomView;
import com.waenhancer.xposed.features.customization.FilterGroups;
import com.waenhancer.xposed.features.customization.HideSeenView;
import com.waenhancer.xposed.features.customization.HideTabs;
import com.waenhancer.xposed.features.customization.IGStatus;
import com.waenhancer.xposed.features.customization.SeparateGroup;
import com.waenhancer.xposed.features.customization.ShowOnline;
import com.waenhancer.xposed.features.general.AntiRevoke;
import com.waenhancer.xposed.features.general.CallType;
import com.waenhancer.xposed.features.general.ChatLimit;
import com.waenhancer.xposed.features.general.DeleteStatus;
import com.waenhancer.xposed.features.general.LiteMode;
import com.waenhancer.xposed.features.general.RecoverDeleteForMe;
import com.waenhancer.xposed.features.general.VideoNoteAttachment;
import com.waenhancer.xposed.features.general.NewChat;
import com.waenhancer.xposed.features.general.Others;
import com.waenhancer.xposed.features.general.PinnedLimit;
import com.waenhancer.xposed.features.general.SeenTick;
import com.waenhancer.xposed.features.general.ShareLimit;
import com.waenhancer.xposed.features.general.ShowEditMessage;
import com.waenhancer.xposed.features.general.Tasker;
import com.waenhancer.xposed.features.listeners.ContactItemListener;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.features.listeners.MenuStatusListener;
import com.waenhancer.xposed.features.media.CallRecording;
import com.waenhancer.xposed.features.media.DownloadProfile;
import com.waenhancer.xposed.features.media.DownloadViewOnce;
import com.waenhancer.xposed.features.media.MediaPreview;
import com.waenhancer.xposed.features.media.MediaQuality;
import com.waenhancer.xposed.features.media.StatusDownload;
import com.waenhancer.xposed.features.others.ActivityController;
import com.waenhancer.xposed.features.others.BackupRestore;
import com.waenhancer.xposed.features.others.AudioTranscript;
import com.waenhancer.xposed.features.others.Channels;
import com.waenhancer.xposed.features.others.ChatFilters;
import com.waenhancer.xposed.features.others.CopyStatus;
import com.waenhancer.xposed.features.others.DebugFeature;
import com.waenhancer.xposed.features.others.GoogleTranslate;
import com.waenhancer.xposed.features.others.GroupAdmin;
import com.waenhancer.xposed.features.others.MenuHome;
import com.waenhancer.xposed.features.others.Stickers;
import com.waenhancer.xposed.features.others.TextStatusComposer;
import com.waenhancer.xposed.features.others.ToastViewer;
import com.waenhancer.xposed.features.others.Spy;
import com.waenhancer.xposed.features.privacy.AntiWa;
import com.waenhancer.xposed.features.privacy.CallPrivacy;
import com.waenhancer.xposed.features.privacy.CustomPrivacy;
import com.waenhancer.xposed.features.privacy.DndMode;
import com.waenhancer.xposed.features.privacy.FreezeLastSeen;
import com.waenhancer.xposed.features.privacy.HideChat;
import com.waenhancer.xposed.features.privacy.HideReceipt;
import com.waenhancer.xposed.features.privacy.HideSeen;
import com.waenhancer.xposed.features.privacy.LockedChatsEnhancer;
import com.waenhancer.xposed.features.privacy.TagMessage;
import com.waenhancer.xposed.features.privacy.TypingPrivacy;
import com.waenhancer.xposed.features.privacy.ViewOnce;
import com.waenhancer.xposed.spoofer.HookBL;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.ResId;
import com.waenhancer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import lombok.Getter;
import lombok.Setter;

public class FeatureLoader {
    public static Application mApp;

    public final static String PACKAGE_WPP = "com.whatsapp";
    public final static String PACKAGE_BUSINESS = "com.whatsapp.w4b";

    private static final ArrayList<ErrorItem> list = new ArrayList<>();
    private static List<String> supportedVersions;
    private static String currentVersion;

    public static void start(@NonNull ClassLoader loader, @NonNull XSharedPreferences pref, String sourceDir) {

        Feature.DEBUG = pref.getBoolean("enablelogs", true);
        Utils.xprefs = pref;

        XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class,
                new XC_MethodHook() {
                    @SuppressWarnings("deprecation")
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        mApp = (Application) param.args[0];

                        // Inject Booloader Spoofer
                        if (pref.getBoolean("bootloader_spoofer", false)) {
                            HookBL.hook(loader, pref);
                            XposedBridge.log("Bootloader Spoofer is Injected");
                        }

                        PackageManager packageManager = mApp.getPackageManager();
                        pref.registerOnSharedPreferenceChangeListener((sharedPreferences, s) -> pref.reload());
                        PackageInfo packageInfo = packageManager.getPackageInfo(mApp.getPackageName(), 0);
                        XposedBridge.log(packageInfo.versionName);
                        currentVersion = packageInfo.versionName;
                        supportedVersions = Arrays.asList(mApp.getResources()
                                .getStringArray(Objects.equals(mApp.getPackageName(), FeatureLoader.PACKAGE_WPP)
                                        ? ResId.array.supported_versions_wpp
                                        : ResId.array.supported_versions_business));
                        mApp.registerActivityLifecycleCallbacks(new WaCallback());
                        registerReceivers();
                        try {
                            var timemillis = System.currentTimeMillis();
                            Unobfuscator.loadLibrary(mApp);
                            if (!Unobfuscator.initWithPath(sourceDir)) {
                                XposedBridge.log("Can't init dexkit");
                                return;
                            }
                            UnobfuscatorCache.init(mApp);
                            SharedPreferencesWrapper.hookInit(mApp.getClassLoader());
                            ReflectionUtils.initCache(mApp);
                            boolean isSupported = supportedVersions.stream()
                                    .anyMatch(s -> packageInfo.versionName.startsWith(s.replace(".xx", "")));
                            if (!isSupported) {
                                disableExpirationVersion(mApp.getClassLoader());
                                if (!pref.getBoolean("bypass_version_check", false)) {
                                    String sb = "Unsupported version: " +
                                            packageInfo.versionName +
                                            "\n" +
                                            "Only the function of ignoring the expiration of the WhatsApp version has been applied!";
                                    throw new Exception(sb);
                                }
                            }
                            initComponents(loader, pref);
                            plugins(loader, pref, packageInfo.versionName);
                            sendEnabledBroadcast(mApp);
                            // XposedHelpers.setStaticIntField(XposedHelpers.findClass("com.whatsapp.infra.logging.Log",
                            // loader), "level", 5);
                            var timemillis2 = System.currentTimeMillis() - timemillis;
                            XposedBridge.log("Loaded Hooks in " + timemillis2 + "ms");
                        } catch (Throwable e) {
                            XposedBridge.log(e);
                            var error = new ErrorItem();
                            error.setPluginName("MainFeatures[Critical]");
                            error.setWhatsAppVersion(packageInfo.versionName);
                            error.setModuleVersion(BuildConfig.VERSION_NAME);
                            error.setMessage(e.getMessage());
                            error.setError(Arrays.toString(Arrays.stream(e.getStackTrace())
                                    .filter(s -> !s.getClassName().startsWith("android")
                                            && !s.getClassName().startsWith("com.android"))
                                    .map(StackTraceElement::toString).toArray()));
                            list.add(error);
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(loader), "onCreate", Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        if (!list.isEmpty()) {
                            var activity = (Activity) param.thisObject;
                            var msg = String.join("\n",
                                    list.stream().map(item -> item.getPluginName() + " - " + item.getMessage())
                                            .toArray(String[]::new));

                            new AlertDialogWpp(activity)
                                    .setTitle(activity.getString(ResId.string.error_detected))
                                    .setMessage(activity.getString(ResId.string.version_error) + msg
                                            + "\n\nCurrent Version: " + currentVersion + "\nSupported Versions:\n"
                                            + String.join("\n", supportedVersions))
                                    .setPositiveButton(activity.getString(ResId.string.copy_to_clipboard),
                                            (dialog, which) -> {
                                                var clipboard = (ClipboardManager) mApp
                                                        .getSystemService(Context.CLIPBOARD_SERVICE);
                                                ClipData clip = ClipData.newPlainText("text", String.join("\n",
                                                        list.stream().map(ErrorItem::toString).toArray(String[]::new)));
                                                clipboard.setPrimaryClip(clip);
                                                Toast.makeText(mApp, ResId.string.copied_to_clipboard,
                                                        Toast.LENGTH_SHORT).show();
                                                dialog.dismiss();
                                            })
                                    .setNegativeButton(activity.getString(ResId.string.check_for_latest_version),
                                            (dialog, which) -> {
                                                CompletableFuture.runAsync(new UpdateChecker(activity, true));
                                                dialog.dismiss();
                                            })
                                    .show();
                        }
                    }
                });
    }

    public static void disableExpirationVersion(ClassLoader classLoader) throws Exception {
        var expirationClass = Unobfuscator.loadExpirationClass(classLoader);
        var method = ReflectionUtils.findMethodUsingFilter(expirationClass, m -> m.getReturnType().equals(Date.class));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var calendar = Calendar.getInstance();
                calendar.set(2099, 12, 31);
                param.setResult(calendar.getTime());
            }
        });
    }

    private static void initComponents(ClassLoader loader, XSharedPreferences pref) throws Exception {
        FMessageWpp.initialize(loader);
        WppCore.Initialize(loader, pref);
        DesignUtils.setPrefs(pref);
        Utils.init(loader);
        AlertDialogWpp.initDialog(loader);
        WaContactWpp.initialize(loader);
        WppCore.addListenerActivity((activity, state) -> {

            if (state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                checkUpdate(activity);
            }

            // Check for WAE Update
            // noinspection ConstantValue
            if (App.isOriginalPackage() && pref.getBoolean("update_check", true)) {
                boolean isHomeActivity = false;
                try {
                    var homeClass = WppCore.getHomeActivityClass(activity.getClassLoader());
                    isHomeActivity = homeClass != null && homeClass.isInstance(activity);
                } catch (Throwable ignored) {
                }

                if (!isHomeActivity) {
                    var simpleName = activity.getClass().getSimpleName();
                    var fullName = activity.getClass().getName();
                    isHomeActivity = simpleName.contains("Home") || fullName.contains("HomeActivity");
                }

                if (isHomeActivity && state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                    // Delay to ensure smooth activity transition
                    XposedBridge.log("[WAE] Scheduling update check in 2 seconds...");
                    activity.getWindow().getDecorView().postDelayed(() -> {
                        XposedBridge.log("[WAE] Launching UpdateChecker now");
                        CompletableFuture.runAsync(new UpdateChecker(activity));
                    }, 2000); // 2 second delay
                }
            }
        });
    }

    private static void checkUpdate(@NonNull Activity activity) {
        if (WppCore.getPrivBoolean("need_restart", false)) {
            WppCore.setPrivBoolean("need_restart", false);
            try {
                new AlertDialogWpp(activity).setMessage(activity.getString(ResId.string.restart_wpp))
                        .setPositiveButton(activity.getString(ResId.string.yes), (dialog, which) -> {
                            if (!Utils.doRestart(activity))
                                Toast.makeText(activity, "Unable to rebooting activity", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(activity.getString(ResId.string.no), null)
                        .show();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void registerReceivers() {
        // Reboot receiver
        BroadcastReceiver restartReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (context.getPackageName().equals(intent.getStringExtra("PKG"))) {
                    var appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
                    Toast.makeText(context, context.getString(ResId.string.rebooting) + " " + appName + "...",
                            Toast.LENGTH_SHORT).show();
                    if (!Utils.doRestart(context))
                        Toast.makeText(context, "Unable to rebooting " + appName, Toast.LENGTH_SHORT).show();
                }
            }
        };
        ContextCompat.registerReceiver(mApp, restartReceiver,
                new IntentFilter(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART"), ContextCompat.RECEIVER_EXPORTED);

        /// Wpp receiver
        BroadcastReceiver wppReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendEnabledBroadcast(context);
            }
        };
        ContextCompat.registerReceiver(mApp, wppReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".CHECK_WPP"),
                ContextCompat.RECEIVER_EXPORTED);

        // Dialog receiver restart
        BroadcastReceiver restartManualReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                WppCore.setPrivBoolean("need_restart", true);
            }
        };
        ContextCompat.registerReceiver(mApp, restartManualReceiver,
                new IntentFilter(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"), ContextCompat.RECEIVER_EXPORTED);

        // Clear Cache receiver
        BroadcastReceiver clearCacheReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UnobfuscatorCache.getInstance() != null) {
                    UnobfuscatorCache.getInstance().clearCache();
                    XposedBridge.log("WaEnhancer: Obfuscate cache cleared via broadcast");
                }
            }
        };
        ContextCompat.registerReceiver(mApp, clearCacheReceiver,
                new IntentFilter(BuildConfig.APPLICATION_ID + ".CLEAR_OBFUSCATE_CACHE"), ContextCompat.RECEIVER_EXPORTED);
    }

    private static void sendEnabledBroadcast(Context context) {
        try {
            Intent wppIntent = new Intent(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
            wppIntent.putExtra("VERSION",
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            wppIntent.putExtra("PKG", context.getPackageName());
            wppIntent.setPackage(BuildConfig.APPLICATION_ID);
            context.sendBroadcast(wppIntent);
        } catch (Exception ignored) {
        }
    }

    private static void plugins(@NonNull ClassLoader loader, @NonNull XSharedPreferences pref,
            @NonNull String versionWpp) throws Exception {

        var classes = new Class<?>[] {
                DebugFeature.class,
                ContactItemListener.class,
                ConversationItemListener.class,
                MenuStatusListener.class,
                ShowEditMessage.class,
                AntiRevoke.class,
                CustomToolbar.class,
                CustomView.class,
                SeenTick.class,
                BubbleColors.class,
                CallPrivacy.class,
                ActivityController.class,
                CustomThemeV2.class,
                ChatLimit.class,
                SeparateGroup.class,
                ShowOnline.class,
                DndMode.class,
                FreezeLastSeen.class,
                TypingPrivacy.class,
                HideChat.class,
                HideReceipt.class,
                HideSeen.class,
                HideSeenView.class,
                TagMessage.class,
                HideTabs.class,
                IGStatus.class,
                LiteMode.class,
                MediaQuality.class,
                NewChat.class,
                Others.class,
                PinnedLimit.class,
                CustomTime.class,
                ShareLimit.class,
                StatusDownload.class,
                ViewOnce.class,
                CallType.class,
                MediaPreview.class,
                FilterGroups.class,
                Tasker.class,
                DeleteStatus.class,
                DownloadViewOnce.class,
                Channels.class,
                DownloadProfile.class,
                ChatFilters.class,
                GroupAdmin.class,
                Stickers.class,
                CopyStatus.class,
                TextStatusComposer.class,
                ToastViewer.class,
                MenuHome.class,
                AntiWa.class,
                CustomPrivacy.class,
                AudioTranscript.class,
                GoogleTranslate.class,
                ContactBlockedVerify.class,
                LockedChatsEnhancer.class,
                CallRecording.class,
                BackupRestore.class,
                Spy.class,
                RecoverDeleteForMe.class,
                VideoNoteAttachment.class
        };
        XposedBridge.log("Loading Plugins");
        var executorService = Executors.newWorkStealingPool(Math.min(Runtime.getRuntime().availableProcessors(), 4));
        var times = new ArrayList<String>();
        for (var classe : classes) {
            CompletableFuture.runAsync(() -> {
                var timemillis = System.currentTimeMillis();
                try {
                    var constructor = classe.getConstructor(ClassLoader.class, XSharedPreferences.class);
                    var plugin = (Feature) constructor.newInstance(loader, pref);
                    plugin.doHook();
                } catch (Throwable e) {
                    XposedBridge.log(e);
                    var error = new ErrorItem();
                    error.setPluginName(classe.getSimpleName());
                    error.setWhatsAppVersion(versionWpp);
                    error.setModuleVersion(BuildConfig.VERSION_NAME);
                    error.setMessage(e.getMessage());
                    error.setError(Arrays.toString(Arrays.stream(e.getStackTrace()).filter(
                            s -> !s.getClassName().startsWith("android") && !s.getClassName().startsWith("com.android"))
                            .map(StackTraceElement::toString).toArray()));
                    list.add(error);
                }
                var timemillis2 = System.currentTimeMillis() - timemillis;
                times.add("* Loaded Plugin " + classe.getSimpleName() + " in " + timemillis2 + "ms");
            }, executorService);
        }
        executorService.shutdown();
        executorService.awaitTermination(15, TimeUnit.SECONDS);
        if (DebugFeature.DEBUG) {
            for (var time : times) {
                if (time != null)
                    XposedBridge.log(time);
            }
        }
    }

    @Getter
    @Setter
    private static class ErrorItem {
        private String pluginName;
        private String whatsAppVersion;
        private String error;
        private String moduleVersion;
        private String message;

        @NonNull
        @Override
        public String toString() {
            return "pluginName='" + getPluginName() + '\'' +
                    "\nmoduleVersion='" + getModuleVersion() + '\'' +
                    "\nwhatsAppVersion='" + getWhatsAppVersion() + '\'' +
                    "\nMessage=" + getMessage() +
                    "\nerror='" + getError() + '\'';
        }

    }
}
