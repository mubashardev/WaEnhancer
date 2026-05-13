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
import com.waenhancer.xposed.features.media.StatusDownload;
import com.waenhancer.xposed.features.general.AntiRevoke;
import com.waenhancer.xposed.features.media.CallRecording;
import com.waenhancer.xposed.features.media.AutoStatusForward;
import com.waenhancer.xposed.features.customization.ChatScrollButtons;
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

import com.waenhancer.xposed.features.media.DownloadProfile;
import com.waenhancer.xposed.features.media.DownloadViewOnce;
import com.waenhancer.xposed.features.media.CallRecording;
import com.waenhancer.xposed.features.media.AutoStatusForward;
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
import com.waenhancer.xposed.features.others.SettingsInjector;
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
import com.waenhancer.xposed.utils.XResManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import lombok.Getter;
import lombok.Setter;

public class FeatureLoader {
    public static Application mApp;
    public static ClassLoader hostClassLoader;
    private static Toast hookingToast;
    private static String loadedTimeStr;
    private static boolean needsSnackbar = false;
    private static final java.util.concurrent.CountDownLatch loadLatch = new java.util.concurrent.CountDownLatch(1);
    private static volatile boolean isLoaded = false;
    private static boolean isRestartDialogShowing = false;
    private static final AtomicLong lastRestartCheckMs = new AtomicLong(0);

    public final static String PACKAGE_WPP = "com.whatsapp";
    public final static String PACKAGE_BUSINESS = "com.whatsapp.w4b";

    private static final List<ErrorItem> list = java.util.Collections.synchronizedList(new ArrayList<>());
    private static List<String> supportedVersions;
    private static String currentVersion;

    /**
     * Safely resolve a module string resource. Uses moduleResources directly
     * to avoid passing module IDs through the host's Resources which lacks
     * the module's resource table.
     */
    public static String getModuleString(int resId) {
        try {
            if (XResManager.moduleResources != null) {
                return XResManager.moduleResources.getString(resId);
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Resolve a module string resource with a hardcoded fallback.
     * Use this when the string is user-visible (menu titles, toasts) to
     * guarantee non-empty text even if module resources fail to load.
     */
    public static String getModuleString(int resId, String fallback) {
        String result = getModuleString(resId);
        return (result != null && !result.isEmpty()) ? result : fallback;
    }

    public static void start(@NonNull ClassLoader loader, @NonNull android.content.SharedPreferences pref, String sourceDir) {
        hostClassLoader = loader;
        Feature.DEBUG = pref.getBoolean("enablelogs", true);
        Utils.DEBUG = Feature.DEBUG;
        Utils.xprefs = pref;

        XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class,
                new XC_MethodHook() {
                    @SuppressWarnings("deprecation")
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        mApp = (Application) param.args[0];
                        
                        // Show initial feedback immediately (if enabled)
                        if (pref.getBoolean("show_hook_toast", true)) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                hookingToast = Toast.makeText(mApp, "Hooking in to WhatsApp cache. Please wait.", Toast.LENGTH_LONG);
                                hookingToast.show();
                            });
                        }

                        String processName = Application.getProcessName();
                        if (Feature.DEBUG) {
                            ;
                        }

                        if (!Objects.equals(processName, mApp.getPackageName())) {
                            ;
                            return;
                        }

                        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
                        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                            try {
                                String stacktrace = android.util.Log.getStackTraceString(throwable);
                                android.os.Bundle extras = new android.os.Bundle();
                                extras.putString("stacktrace", stacktrace);
                                mApp.getContentResolver().call(
                                        android.net.Uri.parse("content://" + com.waenhancer.BuildConfig.APPLICATION_ID + ".hookprovider"),
                                        "record_crash", null, extras);
                            } catch (Exception ignored) {}
                            if (defaultHandler != null) {
                                defaultHandler.uncaughtException(thread, throwable);
                            }
                        });

                        // Inject Booloader Spoofer
                        if (pref.getBoolean("bootloader_spoofer", false)) {
                            HookBL.hook(loader, pref);
                            if (Feature.DEBUG) {
                                ;
                            }
                        }

                        PackageManager packageManager = mApp.getPackageManager();
                        
                        // Use provider-backed prefs in the hooked process so changes made in the
                        // manager app can propagate without relying on stale XSharedPreferences snapshots.
                        var localBridgePrefs = mApp.getSharedPreferences("wae_bridge_prefs", Context.MODE_PRIVATE);
                        var providerPrefs = new com.waenhancer.xposed.bridge.client.ProviderSharedPreferences(mApp, localBridgePrefs, pref);
                        Utils.xprefs = providerPrefs;

                        PackageInfo packageInfo = packageManager.getPackageInfo(mApp.getPackageName(), 0);

                        ;
                        currentVersion = packageInfo.versionName;

                        // Host preference scan removed - too expensive

                        int versionsArrayId = Objects.equals(mApp.getPackageName(), FeatureLoader.PACKAGE_WPP)
                                ? com.waenhancer.R.array.supported_versions_wpp
                                : com.waenhancer.R.array.supported_versions_business;
                        supportedVersions = Arrays.asList(
                                XResManager.moduleResources.getStringArray(versionsArrayId));
                        mApp.registerActivityLifecycleCallbacks(new WaCallback());
                        registerReceivers();
                        try {
                            boolean isSupported = supportedVersions.stream()
                                    .anyMatch(s -> {
                                        String target = s.endsWith(".xx") ? s.replace(".xx", ".") : s + ".";
                                        return (packageInfo.versionName + ".").startsWith(target);
                                    });
                            if (Feature.DEBUG) {
                                ;
                            }
                            if (!isSupported) {
                                disableExpirationVersion(mApp.getClassLoader());
                                String sb = "Unsupported version: " +
                                        packageInfo.versionName +
                                        "\n" +
                                        "Only the function of ignoring the expiration of the WhatsApp version has been applied!";
                                throw new Exception(sb);
                            }
                            
                            // Execute loading synchronously to ensure hooks are applied before app continues
                            load(loader, providerPrefs, packageInfo, sourceDir);
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
                        } finally {
                            isLoaded = true;
                            loadLatch.countDown();
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

                            try {
                                new AlertDialogWpp(activity)
                                        .asBottomSheet()
                                        .setTitle(getModuleString(ResId.string.error_detected, "Error Detected"))
                                        .setMessage(getModuleString(ResId.string.version_error, "Version Compatibility Error\n") + msg
                                                + "\n\nCurrent Version: " + currentVersion + "\nSupported Versions:\n"
                                                + String.join("\n", supportedVersions))
                                        .setPositiveButton(getModuleString(ResId.string.copy_to_clipboard, "Copy to Clipboard"),
                                                (dialog, which) -> {
                                                    var clipboard = (ClipboardManager) mApp
                                                            .getSystemService(Context.CLIPBOARD_SERVICE);
                                                    ClipData clip = ClipData.newPlainText("text", String.join("\n",
                                                            list.stream().map(ErrorItem::toString).toArray(String[]::new)));
                                                    clipboard.setPrimaryClip(clip);
                                                    Toast.makeText(mApp, getModuleString(ResId.string.copied_to_clipboard, "Copied to Clipboard"),
                                                            Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                })
                                        .setNegativeButton(getModuleString(ResId.string.check_for_latest_version, "Check for Latest Version"),
                                                (dialog, which) -> {
                                                    try {
                                                        Intent intent = new Intent();
                                                        intent.setComponent(new android.content.ComponentName("com.waenhancer", "com.waenhancer.activities.ChangelogActivity"));
                                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                        activity.startActivity(intent);
                                                    } catch (Throwable t) {
                                                        XposedBridge.log("[WAE] Failed to open ChangelogActivity: " + t.getMessage());
                                                    }
                                                    dialog.dismiss();
                                                })
                                        .show();
                            } catch (Throwable e) {
                                // Prevent error dialog from blocking UpdateChecker
                                XposedBridge.log("[WAE] Error showing error dialog: " + e.getMessage());
                                e.printStackTrace();
                            }
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

    private static void load(ClassLoader loader, SharedPreferences providerPrefs, PackageInfo packageInfo, String sourceDir) {
        try {
            var timemillis = System.currentTimeMillis();
            
            Unobfuscator.loadLibrary(mApp);
            if (!Unobfuscator.initWithPath(sourceDir)) {
                ;
                return;
            }
            UnobfuscatorCache.init(mApp);
            SharedPreferencesWrapper.hookInit(mApp.getClassLoader());

            ;
            ResId.initLocal(mApp);
            initComponents(loader, providerPrefs);
            plugins(loader, providerPrefs, packageInfo.versionName);

            // Setup lazy feature loading system
            registerLazyFeatures();
            setupLazyFeatureTriggers(loader, providerPrefs);

            sendEnabledBroadcast(mApp);
            
            var timemillis2 = System.currentTimeMillis() - timemillis;
            loadedTimeStr = String.format(java.util.Locale.US, "%.2fs", timemillis2 / 1000.0);
            if (Feature.DEBUG) {
                ;
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                if (hookingToast != null) {
                    hookingToast.cancel();
                }
                needsSnackbar = true;
                triggerLoadedFeedback();
            });
        } catch (Throwable e) {
            XposedBridge.log("[WAE] Error in background load: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private static void initComponents(ClassLoader loader, android.content.SharedPreferences pref) throws Exception {
        FMessageWpp.initialize(loader);
        WppCore.Initialize(loader, pref);
        DesignUtils.setPrefs(pref);
        Utils.init(loader);
        AlertDialogWpp.initDialog(loader);
        WaContactWpp.initialize(loader);
        
        // Track update check per session
        final boolean[] hasCheckedThisSession = {false};

        WppCore.addListenerActivity((activity, state) -> {
            if (state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                if (!isHomeActivity(activity)) {
                    return;
                }

                activity.getWindow().getDecorView().post(() -> {
                    try {
                        if (pref instanceof XSharedPreferences) {
                            ((XSharedPreferences) pref).reload();
                        }

                        long now = System.currentTimeMillis();
                        long previous = lastRestartCheckMs.get();
                        if (now - previous < 1500 || !lastRestartCheckMs.compareAndSet(previous, now)) {
                            return;
                        }

                        boolean needRestartPref = pref.getBoolean("need_restart", false);
                        boolean needRestartGlobal = WppCore.getPrivBoolean("need_restart", false);
                        java.util.Set<String> changes = pref.getStringSet("pending_restart_changes", null);
                        boolean hasPendingChanges = changes != null && !changes.isEmpty();

                        if (!needRestartPref && !hasPendingChanges && needRestartGlobal) {
                            WppCore.setPrivBooleanSync("need_restart", false);
                            return;
                        }

                        if ((needRestartPref || needRestartGlobal) && !isRestartDialogShowing) {
                            isRestartDialogShowing = true;
                            String msg = getModuleString(ResId.string.restart_wpp);
                            String btnRestart = getModuleString(ResId.string.restart_whatsapp);
                            String btnCancel = getModuleString(android.R.string.cancel);
                            
                            // Enhance message with changed items if possible
                            try {
                                if (changes != null && !changes.isEmpty()) {
                                    StringBuilder sb = new StringBuilder();
                                    if (msg.isEmpty()) msg = "WhatsApp needs to be restarted to apply the following changes:";
                                    else sb.append(msg).append("\n\n");
                                    
                                    sb.append("Changes:\n");
                                    for (String change : changes) {
                                        sb.append("• ").append(change).append("\n");
                                    }
                                    msg = sb.toString().trim();
                                }
                            } catch (Exception ignored) {}

                            if (msg.isEmpty()) msg = "WhatsApp needs to be restarted to apply your recent changes in WaEnhancer. Would you like to restart now?";
                            if (btnRestart.isEmpty()) btnRestart = "Restart WhatsApp";
                            if (btnCancel.isEmpty()) btnCancel = "Cancel";

                            ;
                            
                            new AlertDialogWpp(activity)
                                    .setTitle("Restart Required")
                                    .setMessage(msg)
                                    .setPositiveButton(btnRestart, (dialog, which) -> {
                                        isRestartDialogShowing = false;
                                        pref.edit().putBoolean("need_restart", false)
                                                .remove("pending_restart_changes").apply();
                                        WppCore.setPrivBooleanSync("need_restart", false);
                                        Utils.doRestart(activity);
                                    })
                                    .setNegativeButton(btnCancel, (dialog, which) -> {
                                        isRestartDialogShowing = false;
                                        pref.edit().putBoolean("need_restart", false)
                                                .remove("pending_restart_changes").apply();
                                        WppCore.setPrivBooleanSync("need_restart", false);
                                    })
                                    .show();
                        }
                    } catch (Throwable e) {
                        XposedBridge.log("[WAE] Error during post-resume home check: " + e.getMessage());
                    }
                });

                if (pref.getBoolean("update_check", true)) {
                    if (!hasCheckedThisSession[0]) {
                        hasCheckedThisSession[0] = true;
                        ;
                        activity.getWindow().getDecorView().postDelayed(() -> {
                            try {
                                CompletableFuture.runAsync(new UpdateChecker(activity));
                            } catch (Throwable e) {
                                XposedBridge.log("[WAE] Error launching UpdateChecker: " + e.getMessage());
                            }
                        }, 5000);
                    }
                }
            }
        });
    }



    private static void registerReceivers() {
        // Reboot receiver
        BroadcastReceiver restartReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (context.getPackageName().equals(intent.getStringExtra("PKG"))) {
                    var appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
                    Toast.makeText(context, getModuleString(ResId.string.rebooting) + " " + appName + "...",
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

        // Dialog receiver restart (Fail-safe)
        BroadcastReceiver restartManualReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ;
                WppCore.setPrivBooleanSync("need_restart", true);
                ;
            }
        };
        ContextCompat.registerReceiver(mApp, restartManualReceiver,
                new IntentFilter(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"), ContextCompat.RECEIVER_EXPORTED);



        BroadcastReceiver clearCacheReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UnobfuscatorCache.getInstance() != null) {
                    UnobfuscatorCache.getInstance().clearCache();
                    ;
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
            // Ensure broadcast reaches the module even if it is in stopped/background state (Android 14+)
            wppIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(wppIntent);
        } catch (Exception ignored) {
        }
    }

    public static void triggerLoadedFeedback() {
        if (!needsSnackbar || loadedTimeStr == null || WppCore.mCurrentActivity == null) return;
        
        // Only show feedback if the user has enabled hook toasts
        if (Utils.xprefs != null && !Utils.xprefs.getBoolean("show_hook_toast", true)) {
            needsSnackbar = false;
            return;
        }
        
        // If still loading in background, we might want to wait or show a dialog
        // But for now, we only trigger if needsSnackbar is true (which is set after background load)
        needsSnackbar = false;
        Utils.showSnackbar(WppCore.mCurrentActivity, "Hooks loaded in " + loadedTimeStr);
    }

    private static boolean isHomeActivity(@NonNull Activity activity) {
        return "HomeActivity".equals(activity.getClass().getSimpleName());
    }

    public static void checkLoading(Activity activity) {
        if (isLoaded || activity == null) return;
        
        // Gate checkLoading behind the show_hook_toast preference
        if (Utils.xprefs != null && !Utils.xprefs.getBoolean("show_hook_toast", true)) return;
        
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // If still not loaded, show a simple non-cancelable dialog
                if (isLoaded) return;
                
                var dialog = new AlertDialogWpp(activity)
                        .setTitle("WaEnhancerX")
                        .setMessage("Hooking in to WhatsApp cache. Please wait...")
                        .setCancelable(false)
                        .show();
                
                // Auto-dismiss when loaded
                new Thread(() -> {
                    try {
                        loadLatch.await();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                dialog.dismiss();
                                triggerLoadedFeedback();
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }).start();
            } catch (Throwable t) {
                XposedBridge.log("[WAE] Failed to show loading dialog: " + t.getMessage());
            }
        });
    }

    /**
     * Register features that can be loaded lazily (on-demand)
     * These features only load when triggered rather than at startup
     */
    private static void registerLazyFeatures() {
        // Conversation-only features do not need to load during home startup.
        FeatureRegistry.registerLazyFeature("Audio Transcript", AudioTranscript.class,
                FeatureRegistry.TriggerType.CONVERSATION_OPENED, null, true);

        FeatureRegistry.registerLazyFeature("Video Note Attachment", VideoNoteAttachment.class,
                FeatureRegistry.TriggerType.CONVERSATION_OPENED, null, true);

        // Settings screen injection is only relevant after home is available.
        FeatureRegistry.registerLazyFeature("Settings Injector", SettingsInjector.class,
                FeatureRegistry.TriggerType.ACTIVITY_RESUMED, "HomeActivity", false);

        XposedBridge.log("[FeatureRegistry] Registered " + FeatureRegistry.getRegisteredCount() + " lazy features");
    }

    /**
     * Setup activity listeners for lazy feature triggering
     */
    private static void setupLazyFeatureTriggers(@NonNull ClassLoader loader, @NonNull android.content.SharedPreferences pref) {
        // Only setup triggers if lazy loading is enabled
        boolean lazyLoadingEnabled = pref.getBoolean("lazy_feature_loading", true);
        if (!lazyLoadingEnabled) {
            return;
        }

        WppCore.addListenerActivity((activity, state) -> {
            String activityName = activity.getClass().getSimpleName();

            if (state == WppCore.ActivityChangeState.ChangeType.CREATED) {
                FeatureRegistry.activateFeature(
                        FeatureRegistry.TriggerType.ACTIVITY_CREATED,
                        activityName,
                        loader,
                        pref
                );
            }

            if (state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                // Try to activate any lazy features matching this activity
                FeatureRegistry.activateFeature(
                        FeatureRegistry.TriggerType.ACTIVITY_RESUMED,
                        activityName,
                        loader,
                        pref
                );

                if (activityName.contains("Status")) {
                    FeatureRegistry.activateFeature(
                            FeatureRegistry.TriggerType.STATUS_VIEW,
                            activityName,
                            loader,
                            pref
                    );
                }

                if (activityName.contains("Call")) {
                    FeatureRegistry.activateFeature(
                            FeatureRegistry.TriggerType.CALL_STARTED,
                            activityName,
                            loader,
                            pref
                    );
                }

                if (activityName.contains("Conversation") || activityName.contains("Chat")) {
                    FeatureRegistry.activateFeature(
                            FeatureRegistry.TriggerType.CONVERSATION_OPENED,
                            activityName,
                            loader,
                            pref
                    );
                }
            }
        });

        XposedBridge.log("[FeatureRegistry] Lazy feature triggers initialized");
    }

    private static void plugins(@NonNull ClassLoader loader, @NonNull android.content.SharedPreferences pref,
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
                ChatScrollButtons.class,
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
                AutoStatusForward.class,
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
                SettingsInjector.class,
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
        if (Feature.DEBUG) {
            ;
        }
        var executorService = Executors.newWorkStealingPool(Math.min(Runtime.getRuntime().availableProcessors(), 4));
        var times = java.util.Collections.synchronizedList(new ArrayList<String>());

        // Check if lazy loading is enabled
        boolean lazyLoadingEnabled = pref.getBoolean("lazy_feature_loading", true);

        for (var classe : classes) {
            // Skip lazy features if lazy loading is enabled - they'll load on-demand
            if (lazyLoadingEnabled && FeatureRegistry.isLazyFeature(classe.getSimpleName())) {
                XposedBridge.log("[FeatureLoader] Skipping " + classe.getSimpleName() + " (lazy loading enabled)");
                continue;
            }

            CompletableFuture.runAsync(() -> {
                var timemillis = System.currentTimeMillis();
                try {
                    var constructor = classe.getConstructor(ClassLoader.class, android.content.SharedPreferences.class);
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
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                XposedBridge.log("WAE: Features failed to load within 5 seconds");
            }
        } catch (InterruptedException e) {
            XposedBridge.log(e);
        }
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
