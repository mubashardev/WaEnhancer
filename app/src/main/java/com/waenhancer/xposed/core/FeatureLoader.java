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
import com.waenhancer.utils.ApkMirrorFeedHelper;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.SharedPreferencesWrapper;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.core.db.MessageHistory;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.view.Gravity;
import android.util.TypedValue;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.widget.ImageView;
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
import com.waenhancer.xposed.features.customization.FloatingBottomBar;
import com.waenhancer.xposed.features.customization.HideSeenView;
import com.waenhancer.xposed.features.customization.HideTabs;
import com.waenhancer.xposed.features.customization.SeparateGroup;
import com.waenhancer.xposed.features.customization.IGStatus;
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
import com.waenhancer.xposed.features.media.DownloadVideoNote;
import com.waenhancer.xposed.features.media.CallRecording;
import com.waenhancer.xposed.features.media.AutoStatusForward;
import com.waenhancer.xposed.features.media.FileSizeSpoofer;
import com.waenhancer.xposed.features.media.MediaPreview;
import com.waenhancer.xposed.features.media.MediaQuality;
import com.waenhancer.xposed.features.media.StatusDownload;
import com.waenhancer.xposed.features.others.ActivityController;
import com.waenhancer.xposed.features.others.BackupRestore;
import com.waenhancer.xposed.features.others.AudioTranscript;
import com.waenhancer.xposed.features.others.Channels;
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

import com.waex.api.plugin.IPlugin;
import com.waex.api.plugin.IPluginContext;
import com.waex.api.plugin.ICapabilityRegistry;
import com.waex.api.plugin.IPluginCapability;

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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.util.Log;
import com.waenhancer.xposed.bridge.client.ProviderSharedPreferences;
import com.waenhancer.xposed.core.components.ProtocolTreeNodeWpp;
import com.waenhancer.xposed.core.plugins.PluginContextImpl;
import com.waenhancer.xposed.utils.ProHelper;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class FeatureLoader {
    public static Application mApp;
    public static ClassLoader hostClassLoader;
    private static Toast hookingToast;
    private static String loadedTimeStr;
    private static boolean needsSnackbar = false;
    private static final CountDownLatch loadLatch = new CountDownLatch(1);
    private static volatile boolean isLoaded = false;
    private static boolean isRestartDialogShowing = false;
    private static final AtomicLong lastRestartCheckMs = new AtomicLong(0);
    private static boolean hasPromptedOptimization = false;

    public final static String PACKAGE_WPP = "com.whatsapp";
    public final static String PACKAGE_BUSINESS = "com.whatsapp.w4b";

    private static final List<ErrorItem> list = Collections.synchronizedList(new ArrayList<>());
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

    /**
     * Resolve a module string resource using the host context's locale without fallback.
     */
    public static String getModuleString(Context context, int resId) {
        return getModuleString(context, resId, "");
    }

    /**
     * Resolve a module string resource using the host context's locale.
     * This is required when WhatsApp overrides the system language and we need
     * the module strings to respect the in-app language preference.
     */
    public static String getModuleString(Context context, int resId, String fallback) {
        try {
            Context moduleContext = context.createPackageContext("com.waenhancer", 0);
            
            // Explicitly apply the host's configuration (which contains the active locale) 
            // to the module context so it doesn't default to the system language.
            Configuration hostConfig = context.getResources().getConfiguration();
            Context localizedContext = moduleContext.createConfigurationContext(hostConfig);
            
            String result = localizedContext.getResources().getString(resId);
            return (result != null && !result.isEmpty()) ? result : fallback;
        } catch (Throwable t) {
            return getModuleString(resId, fallback);
        }
    }

    public static void start(@NonNull ClassLoader loader, @NonNull SharedPreferences pref, String sourceDir) {
        hostClassLoader = loader;
        Feature.DEBUG = false;
        PerfLogger.setEnabled(false);
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

                        // Save Xposed API version dynamically to companion app SharedPreferences
                        try {
                            int apiVersion = XposedBridge.getXposedVersion();
                            Bundle extras = new Bundle();
                            extras.putString("key", "active_xposed_api_version");
                            extras.putString("type", "int");
                            extras.putInt("value", apiVersion);
                            mApp.getContentResolver().call(
                                    Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
                                    "put_preference", null, extras);
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] Failed to save active Xposed API version: " + t.toString());
                        }

                        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
                        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                            try {
                                String stacktrace = Log.getStackTraceString(throwable);
                                Bundle extras = new Bundle();
                                extras.putString("stacktrace", stacktrace);
                                mApp.getContentResolver().call(
                                        Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider"),
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

                        // XSharedPreferences reads the prefs file directly from disk.
                        // The file is world-readable because we hook getDefaultSharedPreferencesMode()
                        // in WppXposed to return MODE_WORLD_READABLE.
                        // Reload to get the freshest data at startup.
                        if (pref instanceof XSharedPreferences) {
                            ((XSharedPreferences) pref).reload();
                        }
                        Utils.xprefs = pref;

                        // Auto-reload prefs when they change on disk
                        if (pref instanceof XSharedPreferences) {
                            final XSharedPreferences xpref = (XSharedPreferences) pref;
                            xpref.registerOnSharedPreferenceChangeListener((sp, key) -> xpref.reload());
                        }

                        PackageInfo packageInfo = packageManager.getPackageInfo(mApp.getPackageName(), 0);

                        ;
                        currentVersion = packageInfo.versionName;

                        // Host preference scan removed - too expensive

                        int versionsArrayId = Objects.equals(mApp.getPackageName(), FeatureLoader.PACKAGE_WPP)
                                ? com.waenhancer.R.array.supported_versions_wpp
                                : com.waenhancer.R.array.supported_versions_business;
                        supportedVersions = new ArrayList<>(Arrays.asList(
                                XResManager.moduleResources.getStringArray(versionsArrayId)));

                        // Merge user-defined custom versions when customization is enabled
                        if (pref.getBoolean("customize_supported_versions", false)) {
                            String customKey = Objects.equals(mApp.getPackageName(), FeatureLoader.PACKAGE_WPP)
                                    ? "custom_versions_wpp"
                                    : "custom_versions_business";
                            Set<String> customVersions = pref.getStringSet(customKey, null);
                            if (customVersions != null && !customVersions.isEmpty()) {
                                supportedVersions.addAll(customVersions);
                            }
                        }
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
                                if (pref.getBoolean("bypass_version_check", false)) {
                                    // User opted in: load all features despite unsupported version
                                    load(loader, pref, packageInfo, sourceDir);
                                } else {
                                    String sb = "Unsupported version: " +
                                            packageInfo.versionName +
                                            "\n" +
                                            "Only the function of ignoring the expiration of the WhatsApp version has been applied!";
                                    throw new Exception(sb);
                                }
                            } else {
                                // Version is supported — load normally
                                load(loader, pref, packageInfo, sourceDir);
                            }
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
                        Activity activity = (Activity) param.thisObject;

                        long now = System.currentTimeMillis();
                        if (now - lastSyncTime > 15000) {
                            lastSyncTime = now;
                            new Thread(() -> {
                                try {
                                    Thread.sleep(2000); // Allow databases to initialize and release startup locks
                                    syncWhatsAppContacts(activity, loader);
                                } catch (Throwable t) {
                                    XposedBridge.log("[WAEX] Contact sync background task failed: " + t.toString());
                                }
                            }).start();
                        }
                        boolean needFilterIndex = pref.getBoolean("filter_group_members_messages", false);
                        boolean needSeparateIndex = pref.getBoolean("separategroups", false);
                        XposedBridge.log("[WAEX] HomeActivity onCreate called. needFilterIndex: " + needFilterIndex + ", needSeparateIndex: " + needSeparateIndex + ", hasPromptedOptimization: " + hasPromptedOptimization);

                        if (!hasPromptedOptimization && (needFilterIndex || needSeparateIndex)) {
                            try {
                                File dbFile = activity.getDatabasePath("msgstore.db");
                                boolean filterIndexed = !needFilterIndex;
                                boolean separateIndexed = !needSeparateIndex;
                                if (dbFile.exists()) {
                                    SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                                            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                                    try {
                                        if (needFilterIndex) {
                                            try (Cursor c = db.rawQuery(
                                                    "SELECT name FROM sqlite_master WHERE type='index' AND name='wae_msg_filter_idx'", null)) {
                                                filterIndexed = c != null && c.moveToFirst();
                                            }
                                        }
                                        if (needSeparateIndex) {
                                            try (Cursor c = db.rawQuery(
                                                    "SELECT name FROM sqlite_master WHERE type='index' AND name='wae_chat_unseen_idx'", null)) {
                                                separateIndexed = c != null && c.moveToFirst();
                                            }
                                        }
                                    } finally {
                                        db.close();
                                    }
                                }
                                if (!filterIndexed || !separateIndexed) {
                                    final SharedPreferences localPrefs = activity.getSharedPreferences("wae_local_prefs", Context.MODE_PRIVATE);
                                    if (localPrefs.getBoolean("dont_ask_optimize_db", false)) {
                                        return;
                                    }
                                    hasPromptedOptimization = true;
                                    new AlertDialogWpp(activity)
                                             .asBottomSheet()
                                             .setTitle("Database Optimization Needed")
                                             .setMessage("WaEnhancer database optimization indexes are missing or were removed due to a recent WhatsApp update. Optimize now for maximum performance?")
                                             .setPositiveButton("Optimize Now", (dialog, which) -> {
                                                 try {
                                                     Class<?> settingsClass = WppCore.getAboutActivityClass(activity.getClassLoader());
                                                     Intent intent = new Intent(activity, settingsClass);
                                                     intent.putExtra("wae_optimize_db", true);
                                                     intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                     activity.startActivity(intent);
                                                 } catch (Throwable t) {
                                                     XposedBridge.log("[WAEX] Failed to start optimization: " + t.toString());
                                                 }
                                             })
                                             .setNegativeButton("Don't Ask Again", (dialog, which) -> {
                                                 try {
                                                     localPrefs.edit().putBoolean("dont_ask_optimize_db", true).apply();
                                                     Toast.makeText(activity, "You can find database optimizations in Settings > WaeX Settings > Optimizations.", Toast.LENGTH_LONG).show();
                                                 } catch (Throwable t) {
                                                     XposedBridge.log("[WAEX] Failed to save dont_ask_optimize_db: " + t.toString());
                                                 }
                                             })
                                             .show();
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[WAEX] Error checking database index at startup: " + t.toString());
                            }
                        }

                        if (!list.isEmpty()) {
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
                                                        intent.setComponent(new ComponentName("com.waenhancer", "com.waenhancer.activities.ChangelogActivity"));
                                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                        activity.startActivity(intent);
                                                    } catch (Throwable t) {
                                                        XposedBridge.log("[WAEX] Failed to open ChangelogActivity: " + t.getMessage());
                                                    }
                                                    dialog.dismiss();
                                                })
                                        .show();
                            } catch (Throwable e) {
                                // Prevent error dialog from blocking UpdateChecker
                                XposedBridge.log("[WAEX] Error showing error dialog: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    public static void disableExpirationVersion(ClassLoader classLoader) throws Exception {
        var expirationClass = Unobfuscator.loadExpirationClass(classLoader);
        /* Log removed */
        for (var method : expirationClass.getDeclaredMethods()) {
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(Date.class) || returnType.equals(long.class) || returnType.equals(Long.class)) {
                /* Log removed */
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (returnType.equals(Date.class)) {
                            var calendar = Calendar.getInstance();
                            calendar.set(2099, 11, 31);
                            param.setResult(calendar.getTime());
                        } else if (returnType.equals(long.class) || returnType.equals(Long.class)) {
                            param.setResult(4102444800000L); // Year 2100-01-01 in milliseconds
                        }
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        /* Log removed */
                    }
                });
            }
        }
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

            ResId.initLocal(mApp);

            if (providerPrefs instanceof XSharedPreferences) {
                XSharedPreferences xpref = (XSharedPreferences) providerPrefs;
                xpref.reload();
                var all = xpref.getAll();
                if (all == null || all.isEmpty()) {
                    var localPrefs = mApp.getSharedPreferences("wae_embedded_prefs", Context.MODE_PRIVATE);
                    providerPrefs = new ProviderSharedPreferences(mApp, localPrefs, providerPrefs);
                    
                    // Update global references
                    Utils.xprefs = providerPrefs;
                    Feature.DEBUG = false;
                    PerfLogger.setEnabled(false);
                    Utils.DEBUG = Feature.DEBUG;
                }
            }



            initComponents(loader, providerPrefs);
            plugins(loader, providerPrefs, packageInfo.versionName);

            // Initialize limited-free feature config in the Xposed context.
            // This mirrors the companion app call in App.java and ensures the config
            // is available before pro features try to resolve hook strings.
            try {
                ProHelper.initLimitedFree(mApp, providerPrefs);
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to initialize LimitedFree in Xposed context: " + t.getMessage());
            }

            try {
                /* Log removed */
                /* Log removed */
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to inspect Xposed classloaders: " + t.toString());
            }

            // Setup lazy feature loading system
            registerLazyFeatures();
            setupLazyFeatureTriggers(loader, providerPrefs);

            sendEnabledBroadcast(mApp);
            
            var timemillis2 = System.currentTimeMillis() - timemillis;
            loadedTimeStr = String.format(Locale.US, "%.2fs", timemillis2 / 1000.0);
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
            XposedBridge.log("[WAEX] Error in background load: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private static void initComponents(ClassLoader loader, SharedPreferences pref) throws Exception {
        try {
            FMessageWpp.initialize(loader);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize FMessageWpp: " + t.getMessage());
        }
        try {
            ProtocolTreeNodeWpp.initialize(loader);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize ProtocolTreeNodeWpp: " + t.getMessage());
        }
        try {
            WppCore.Initialize(loader, pref);
            // Clear stale pending change titles from previous process.
            // Must be after WppCore.Initialize() so privPrefs is available.
            WppCore.setPrivString("pending_changes", "");
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize WppCore: " + t.getMessage());
        }
        try {
            DesignUtils.setPrefs(pref);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize DesignUtils: " + t.getMessage());
        }
        try {
            Utils.init(loader);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize Utils: " + t.getMessage());
        }
        try {
            AlertDialogWpp.initDialog(loader);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize AlertDialogWpp: " + t.getMessage());
        }
        try {
            WaContactWpp.initialize(loader);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to initialize WaContactWpp: " + t.getMessage());
        }
        try {
            MessageHistory.getInstance();
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Failed to pre-initialize MessageHistory: " + t.getMessage());
        }
        
        // Track update check per session
        final boolean[] hasCheckedThisSession = {false};

        WppCore.addListenerActivity((activity, state) -> {
            // On HomeActivity CREATE, verify that prefs file is readable
            if (state == WppCore.ActivityChangeState.ChangeType.CREATED
                    && "HomeActivity".equals(activity.getClass().getSimpleName())) {
                checkPrefsReadable(pref, activity);
            }

            if (state == WppCore.ActivityChangeState.ChangeType.RESUMED) {
                if (isHomeActivity(activity)) {
                    triggerBetaCheckInHost(activity);
                }
                activity.getWindow().getDecorView().post(() -> {
                    showRestartDialog(activity);
                });
                activity.getWindow().getDecorView().post(() -> {
                    long perfStart = PerfLogger.start();
                    try {
                        long now = System.currentTimeMillis();
                        long previous = lastRestartCheckMs.get();
                        if (now - previous < 1500 || !lastRestartCheckMs.compareAndSet(previous, now)) {
                            return;
                        }

                        if (pref instanceof XSharedPreferences) {
                            ((XSharedPreferences) pref).reload();
                        }
                    } catch (Throwable e) {
                        XposedBridge.log("[WAEX] Error during activity resume check: " + e.getMessage());
                    } finally {
                        PerfLogger.end("FeatureLoader.activityResumeCheck", perfStart, 1);
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
                                XposedBridge.log("[WAEX] Error launching UpdateChecker: " + e.getMessage());
                            }
                        }, 5000);
                    }
                }
            }
        });
    }

    /**
     * Verify that XSharedPreferences can actually read preference data.
     * LSPosed stores prefs at /data/misc/apexdata/ and serves them via its own bridge,
     * so file-level permission checks give false positives. Instead, we check if
     * XSharedPreferences.getAll() returns any data.
     */
    private static void checkPrefsReadable(SharedPreferences pref, Activity activity) {
        if (!(pref instanceof XSharedPreferences)) return;
        try {
            XSharedPreferences xpref = (XSharedPreferences) pref;
            xpref.reload();
            File prefFile = xpref.getFile();
            if (prefFile == null || !prefFile.exists()) {
                return;
            }
            var allPrefs = xpref.getAll();
            if (allPrefs == null || allPrefs.isEmpty()) {
                // Empty prefs data
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] checkPrefsReadable failed: " + t.getMessage());
        }
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
                WppCore.setPrivBooleanSync("need_restart", true);
                // Accumulate changed preference titles from the broadcast
                try {
                    ArrayList<String> titles = intent.getStringArrayListExtra("changed_titles");
                    if (titles != null && !titles.isEmpty()) {
                        String existing = WppCore.getPrivString("pending_changes", "");
                        Set<String> all = new LinkedHashSet<>();
                        if (!existing.isEmpty()) {
                            for (String t : existing.split("\\|")) {
                                if (!t.trim().isEmpty()) all.add(t.trim());
                            }
                        }
                        all.addAll(titles);
                        WppCore.setPrivString("pending_changes", String.join("|", all));
                    }
                } catch (Exception ignored) {}

                // Force reload of preferences on change
                if (Utils.xprefs instanceof XSharedPreferences) {
                    ((XSharedPreferences) Utils.xprefs).reload();
                }

                // Show the restart dialog promptly if an activity is active
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        Activity current = WppCore.getCurrentActivity();
                        if (current != null && !current.isFinishing()) {
                            showRestartDialog(current);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[WAEX] Failed to show restart dialog on broadcast: " + t.getMessage());
                    }
                });
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

        // Preference change receiver — reload XSharedPreferences from disk
        BroadcastReceiver prefsChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Utils.xprefs instanceof XSharedPreferences) {
                    ((XSharedPreferences) Utils.xprefs).reload();
                }
            }
        };
        ContextCompat.registerReceiver(mApp, prefsChangedReceiver,
                new IntentFilter(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED"), ContextCompat.RECEIVER_EXPORTED);
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
        if (isHomeActivity(WppCore.mCurrentActivity)) {
            WppCore.mCurrentActivity.invalidateOptionsMenu();
        }
    }

    private static boolean isHomeActivity(@NonNull Activity activity) {
        return "HomeActivity".equals(activity.getClass().getSimpleName());
    }

    // Introduce a delayed loading dialog to avoid flashing for fast loads
    private static Runnable loadingDialogRunnable = null;
    private static AlertDialogWpp loadingDialog = null;

    public static void checkLoading(Activity activity) {
        if (isLoaded || activity == null) return;
        
        // Respect user preference
        if (Utils.xprefs != null && !Utils.xprefs.getBoolean("show_hook_toast", true)) return;
        
        Handler handler = new Handler(Looper.getMainLooper());
        // Schedule dialog after a short delay (e.g., 300ms)
        loadingDialogRunnable = () -> {
            try {
                if (isLoaded) return; // loading finished before delay
                AlertDialogWpp dialog = new AlertDialogWpp(activity)
                        .setTitle("WaEnhancerX")
                        .setMessage("Hooking in to WhatsApp cache. Please wait...")
                        .setCancelable(false);
                dialog.show();
                loadingDialog = dialog;
                // Dismiss when loading completes
                new Thread(() -> {
                    try {
                        loadLatch.await();
                        handler.post(() -> {
                            try {
                                if (loadingDialog != null) loadingDialog.dismiss();
                                triggerLoadedFeedback();
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }).start();
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to show loading dialog: " + t.getMessage());
            }
        };
        handler.postDelayed(loadingDialogRunnable, 300);
        // If loading finishes early, cancel pending dialog
        new Thread(() -> {
            try {
                loadLatch.await();
                handler.removeCallbacks(loadingDialogRunnable);
                handler.post(() -> {
                    if (loadingDialog != null) loadingDialog.dismiss();
                    triggerLoadedFeedback();
                });
            } catch (Throwable ignored) {}
        }).start();
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

        FeatureRegistry.registerLazyFeature("Download Video Note", DownloadVideoNote.class,
                FeatureRegistry.TriggerType.CONVERSATION_OPENED, null, true);

        // Settings screen injection is only relevant after home is available.
        FeatureRegistry.registerLazyFeature("Settings Injector", SettingsInjector.class,
                FeatureRegistry.TriggerType.ACTIVITY_RESUMED, "HomeActivity", false);
    }

    /**
     * Setup activity listeners for lazy feature triggering
     */
    private static void setupLazyFeatureTriggers(@NonNull ClassLoader loader, @NonNull SharedPreferences pref) {
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
    }

    private static void plugins(@NonNull ClassLoader loader, @NonNull SharedPreferences pref,
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
                ShowOnline.class,
                DndMode.class,
                FreezeLastSeen.class,
                TypingPrivacy.class,
                HideChat.class,
                HideSeen.class,
                HideSeenView.class,
                TagMessage.class,
                HideTabs.class,
                SeparateGroup.class,
                FloatingBottomBar.class,
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
                FileSizeSpoofer.class,
                AutoStatusForward.class,
                Tasker.class,
                DeleteStatus.class,
                DownloadViewOnce.class,
                DownloadVideoNote.class,
                Channels.class,
                DownloadProfile.class,
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
        var times = Collections.synchronizedList(new ArrayList<String>());

        // Check if lazy loading is enabled
        boolean lazyLoadingEnabled = pref.getBoolean("lazy_feature_loading", true);

        List<Class<?>> allFeatureClasses = new ArrayList<>(Arrays.asList(classes));

        for (var classe : allFeatureClasses) {
            // Skip lazy features if lazy loading is enabled - they'll load on-demand
            if (lazyLoadingEnabled && FeatureRegistry.isLazyFeature(classe.getSimpleName())) {
                continue;
            }

            CompletableFuture.runAsync(() -> {
                var timemillis = System.currentTimeMillis();
                try {
                    var constructor = classe.getConstructor(ClassLoader.class, SharedPreferences.class);
                    Object pluginObj = constructor.newInstance(loader, pref);
                    if (pluginObj instanceof Feature) {
                        ((Feature) pluginObj).doHook();
                    } else {
                        try {
                            var doHookMethod = pluginObj.getClass().getMethod("doHook");
                            doHookMethod.invoke(pluginObj);
                        } catch (Exception ex) {
                            XposedBridge.log("Failed to invoke doHook on Pro feature: " + classe.getSimpleName() + " - " + ex.getMessage());
                        }
                    }
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

        // Load Pro features dynamically if installed
        try {
            // XposedBridge.class.getClassLoader() returns null under LSPosed because it is
            // injected at native level and treated as a bootstrap class.
            // XC_MethodHook lives in the same InMemoryDexClassLoader but is NOT bootstrap-null.
            // Use it to get a non-null reference to the Xposed framework classloader.
            ClassLoader xposedFrameworkLoader = XC_MethodHook.class.getClassLoader();
            if (xposedFrameworkLoader == null) {
                xposedFrameworkLoader = Thread.currentThread().getContextClassLoader();
                /* Log removed */
            }
            /* Log removed */
            ClassLoader proLoader = ProHelper.getPluginClassLoader(mApp, loader, xposedFrameworkLoader);
            if (proLoader != null) {
                System.getProperties().put("com.waex.helper.classloader", proLoader);
                /* Log removed */

                // Reflectively verify if the native library loaded successfully
                boolean isNativeLibLoaded = false;
                try {
                    Class<?> proFeatureClass = proLoader.loadClass("com.waex.helper.ProFeature");
                    Field nlField = proFeatureClass.getDeclaredField("nl");
                    nlField.setAccessible(true);
                    isNativeLibLoaded = nlField.getBoolean(null);
                } catch (Throwable t) {
                    XposedBridge.log("[WAEX] Warning: Failed to query ProFeature.nl via reflection: " + t.toString());
                }

                if (isNativeLibLoaded) {
                    /* Log removed */
                } else {
                    /* Log removed */
                }

                /* Log removed */
                Class<?> pluginEntryClass = proLoader.loadClass("com.waex.helper.PluginEntry");
                IPlugin pluginInstance = (IPlugin) pluginEntryClass.getDeclaredConstructor().newInstance();
                
                pluginInstance.load();
                
                PluginContextImpl pluginContext = 
                    new PluginContextImpl(loader, mApp, pref);
                pluginInstance.attachContext(pluginContext);
                
                pluginInstance.init();
                
                CapabilityRegistryImpl registry = new CapabilityRegistryImpl();
                pluginInstance.registerCapabilities(registry);
                
                // Invoke execute life-cycle hook (pure capability provider: no-op but standard lifecycle call)
                pluginInstance.execute();
                
                // Execute registered capabilities with error isolation
                for (IPluginCapability capability : registry.getCapabilities()) {
                    CompletableFuture.runAsync(() -> {
                        long timemillis = System.currentTimeMillis();
                        String capName = capability.getPluginName();
                        try {
                            /* Log removed */
                            capability.doHook();
                            long timemillis2 = System.currentTimeMillis() - timemillis;
                            /* Log removed */
                        } catch (Throwable e) {
                            XposedBridge.log("[WAEX] Error executing Pro capability " + capName + ": " + e.toString());
                            XposedBridge.log(e);
                            
                            var error = new ErrorItem();
                            error.setPluginName(capName);
                            error.setWhatsAppVersion(versionWpp);
                            error.setModuleVersion(BuildConfig.VERSION_NAME);
                            error.setMessage(e.getMessage());
                            error.setError(Arrays.toString(Arrays.stream(e.getStackTrace()).filter(
                                    s -> !s.getClassName().startsWith("android") && !s.getClassName().startsWith("com.android"))
                                    .map(StackTraceElement::toString).toArray()));
                            list.add(error);
                        }
                    }, executorService);
                }
            } else {
                /* Log removed */
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error initializing Pro plugins loader: " + t.toString());
            XposedBridge.log(t);
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            XposedBridge.log(e);
        }
    }

    private static void triggerBetaCheckInHost(Activity activity) {
        String currentPackage = activity.getPackageName();
        if (!PACKAGE_WPP.equals(currentPackage) && !PACKAGE_BUSINESS.equals(currentPackage)) {
            return;
        }

        // Allow WhatsApp beta if the module itself is a beta build
        boolean isBetaModule = BuildConfig.VERSION_NAME.toLowerCase().contains("beta");
        if (isBetaModule) {
            return;
        }

        ApkMirrorFeedHelper.fetchVersionsIfNeededForPackage(activity, currentPackage, () -> {
            try {
                PackageManager pm = activity.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(currentPackage, 0);
                String installedVersion = pi.versionName;
                
                if (ApkMirrorFeedHelper.isBetaVersion(activity, currentPackage, installedVersion)) {
                    showBetaWarningDialogInHost(activity, currentPackage);
                }
            } catch (Exception e) {
                XposedBridge.log("[WAEX] Error checking beta in host: " + e.getMessage());
            }
        });
    }

    private static void showBetaWarningDialogInHost(Activity activity, String packageName) {
        String appName = PACKAGE_WPP.equals(packageName) ? "WhatsApp" : "WhatsApp Business";
        String prefKey = "last_beta_warning_dismissed_" + (PACKAGE_WPP.equals(packageName) ? "wpp" : "business");
        
        SharedPreferences prefs = activity.getSharedPreferences("ApkMirrorCache", Context.MODE_PRIVATE);
        long lastDismissed = prefs.getLong(prefKey, 0L);
        long now = System.currentTimeMillis();
        
        if (now - lastDismissed < TimeUnit.DAYS.toMillis(1)) {
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                new AlertDialogWpp(activity)
                        .setTitle("WhatsApp Beta Detected")
                        .setMessage("You are using a beta version of " + appName + " while WaEnhancerX is currently set to the Stable update channel.\n\nWaEnhancerX Stable is designed exclusively for stable WhatsApp releases. To ensure full compatibility and stay up-to-date with every new WhatsApp beta update, we highly recommend switching WaEnhancerX to the Beta update channel in the module settings.")
                        .setPositiveButton("Leave WhatsApp Beta", (dialog, which) -> {
                            try {
                                String url = PACKAGE_WPP.equals(packageName) ?
                                        "https://play.google.com/apps/testing/com.whatsapp" :
                                        "https://play.google.com/apps/testing/com.whatsapp.w4b";
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                XposedBridge.log("[WAEX] Failed to open beta URL: " + e.getMessage());
                            }
                            dialog.dismiss();
                        })
                        .setNegativeButton("Switch to WAEX Beta", (dialog, which) -> {
                            try {
                                Intent intent = new Intent();
                                intent.setComponent(new ComponentName("com.waenhancer", "com.waenhancer.activities.ChangelogActivity"));
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            } catch (Throwable t) {
                                XposedBridge.log("[WAEX] Failed to open ChangelogActivity: " + t.getMessage());
                            }
                            dialog.dismiss();
                        })
                        .setNeutralButton("Dismiss for 1 Day", (dialog, which) -> {
                            prefs.edit().putLong(prefKey, System.currentTimeMillis()).apply();
                            dialog.dismiss();
                        })
                        .setCancelable(false)
                        .show();
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Error showing beta dialog in host: " + t.getMessage());
            }
        });
    }

    public static void showRestartDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            // Do not show on WaeX activities or hijacked WaeX sub-screens
            if (activity.getIntent() != null && activity.getIntent().getStringExtra("waex_screen_id") != null) {
                return;
            }
            if (activity.getClass().getName().startsWith("com.waenhancer.")) {
                return;
            }
            boolean needRestart = WppCore.getPrivBoolean("need_restart", false);
            if (needRestart && !isRestartDialogShowing) {
                isRestartDialogShowing = true;
                activity.invalidateOptionsMenu();
                String msg = getModuleString(ResId.string.restart_wpp);
                String btnRestart = getModuleString(ResId.string.restart_whatsapp);
                String btnCancel = getModuleString(android.R.string.cancel);

                // Show which preferences changed
                String changedTitles = WppCore.getPrivString("pending_changes", "");
                if (!changedTitles.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    if (!msg.isEmpty()) sb.append(msg).append("\n\n");
                    else sb.append("WhatsApp needs to be restarted to apply the following changes:\n\n");
                    sb.append("Changes:\n");
                    for (String title : changedTitles.split("\\|")) {
                        if (!title.trim().isEmpty()) {
                            sb.append("• ").append(title.trim()).append("\n");
                        }
                    }
                    msg = sb.toString().trim();
                }

                if (msg.isEmpty()) msg = "WhatsApp needs to be restarted to apply your recent changes in WaEnhancer X. Would you like to restart now?";
                if (btnRestart.isEmpty()) btnRestart = "Restart WhatsApp";
                if (btnCancel.isEmpty()) btnCancel = "Cancel";

                new AlertDialogWpp(activity)
                        .asBottomSheet()
                        .setTitle("Restart Required")
                        .setMessage(msg)
                        .setPositiveButton(btnRestart, (dialog, which) -> {
                            isRestartDialogShowing = false;
                            WppCore.setPrivBooleanSync("need_restart", false);
                            WppCore.setPrivString("pending_changes", "");
                            Utils.doRestart(activity);
                        })
                        .setNegativeButton(btnCancel, (dialog, which) -> {
                            isRestartDialogShowing = false;
                            WppCore.setPrivBooleanSync("need_restart", false);
                            WppCore.setPrivString("pending_changes", "");
                        })
                        .setOnDismissListener(dialog -> {
                            isRestartDialogShowing = false;
                            // Do not clear need_restart here, because if they just swiped it away without clicking "Cancel", we might want to prompt them again later?
                            // Wait, if we want it to behave like "Cancel", we should clear it. The old dialog allowed "Cancel" and it cleared it.
                            // BUT if we clear it, they never restart. Let's match the NegativeButton behavior so they don't get trapped.
                            WppCore.setPrivBooleanSync("need_restart", false);
                            WppCore.setPrivString("pending_changes", "");
                        })
                        .show();
            }
        } catch (Throwable e) {
            XposedBridge.log("[WAEX] Error showing restart dialog: " + e.getMessage());
        }
    }



    private static class CapabilityRegistryImpl implements ICapabilityRegistry {
        private final List<IPluginCapability> capabilities = new ArrayList<>();

        @Override
        public void register(IPluginCapability capability) {
            if (capability != null) {
                capabilities.add(capability);
            }
        }

        public List<IPluginCapability> getCapabilities() {
            return capabilities;
        }
    }

    private static class ErrorItem {
        private String pluginName;
        private String whatsAppVersion;
        private String error;
        private String moduleVersion;
        private String message;

        public String getPluginName() { return pluginName; }
        public void setPluginName(String pluginName) { this.pluginName = pluginName; }
        public String getWhatsAppVersion() { return whatsAppVersion; }
        public void setWhatsAppVersion(String whatsAppVersion) { this.whatsAppVersion = whatsAppVersion; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getModuleVersion() { return moduleVersion; }
        public void setModuleVersion(String moduleVersion) { this.moduleVersion = moduleVersion; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

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

     private static long lastSyncTime = 0;

    private static void syncWhatsAppContacts(Context context, ClassLoader classLoader) {
        try {
            File waDbFile = context.getDatabasePath("wa.db");
            if (!waDbFile.exists()) return;

            SQLiteDatabase db = null;
            int retries = 3;
            while (retries > 0) {
                try {
                    db = SQLiteDatabase.openDatabase(
                            waDbFile.getAbsolutePath(), null,
                            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                    break;
                } catch (Throwable t) {
                    retries--;
                    if (retries <= 0) {
                        XposedBridge.log("[WAEX] Failed to open wa.db after retries: " + t.getMessage());
                        return;
                    }
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }

            ArrayList<ContentValues> contactList = new ArrayList<>();

            try (Cursor cursor = db.rawQuery(
                    "SELECT jid, display_name, wa_name, number FROM wa_contacts WHERE is_whatsapp_user = 1 OR jid LIKE '%@g.us'", null)) {
                if (cursor != null) {
                    int jidIdx = cursor.getColumnIndex("jid");
                    int dispIdx = cursor.getColumnIndex("display_name");
                    int waIdx = cursor.getColumnIndex("wa_name");
                    int numIdx = cursor.getColumnIndex("number");

                    while (cursor.moveToNext()) {
                        String jid = cursor.getString(jidIdx);
                        String displayName = cursor.getString(dispIdx);
                        String waName = cursor.getString(waIdx);
                        String number = cursor.getString(numIdx);

                        ContentValues values = new ContentValues();
                        values.put("jid", jid);
                        values.put("display_name", displayName);
                        values.put("wa_name", waName);
                        values.put("number", number);
                        contactList.add(values);
                    }
                }
            } finally {
                db.close();
            }

            File msgstoreDbFile = context.getDatabasePath("msgstore.db");
            if (msgstoreDbFile.exists()) {
                SQLiteDatabase msgDb = null;
                int msgRetries = 3;
                while (msgRetries > 0) {
                    try {
                        msgDb = SQLiteDatabase.openDatabase(
                                msgstoreDbFile.getAbsolutePath(), null,
                                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                        break;
                    } catch (Throwable t) {
                        msgRetries--;
                        if (msgRetries <= 0) {
                            XposedBridge.log("[WAEX] Failed to open msgstore.db: " + t.getMessage());
                            break;
                        }
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    }
                }

                if (msgDb != null) {
                    try {
                        try (Cursor cursor = msgDb.rawQuery(
                                "SELECT j.raw_string, c.subject FROM chat c JOIN jid j ON c.jid_row_id = j._id WHERE j.raw_string LIKE '%@g.us'", null)) {
                            if (cursor != null) {
                                int jidIdx = cursor.getColumnIndex("raw_string");
                                int subjIdx = cursor.getColumnIndex("subject");

                                Map<String, ContentValues> groupMap = new HashMap<>();
                                for (ContentValues cv : contactList) {
                                    String jid = cv.getAsString("jid");
                                    if (jid != null && jid.endsWith("@g.us")) {
                                        groupMap.put(jid, cv);
                                    }
                                }

                                while (cursor.moveToNext()) {
                                    String jid = cursor.getString(jidIdx);
                                    String subject = cursor.getString(subjIdx);
                                    if (subject != null && !subject.trim().isEmpty()) {
                                        ContentValues cv = groupMap.get(jid);
                                        if (cv != null) {
                                            cv.put("display_name", subject);
                                        } else {
                                            ContentValues values = new ContentValues();
                                            values.put("jid", jid);
                                            values.put("display_name", subject);
                                            contactList.add(values);
                                        }
                                    }
                                }
                            }
                        }

                        // Query JID mapping to resolve LID names
                        Map<String, String> lidToPhoneMap = new HashMap<>();
                        try (Cursor mapCursor = msgDb.rawQuery(
                                "SELECT j1.raw_string AS lid, j2.raw_string AS phone " +
                                "FROM jid_map jm " +
                                "JOIN jid j1 ON jm.lid_row_id = j1._id " +
                                "JOIN jid j2 ON jm.jid_row_id = j2._id", null)) {
                            if (mapCursor != null) {
                                int lidCol = mapCursor.getColumnIndex("lid");
                                int phoneCol = mapCursor.getColumnIndex("phone");
                                while (mapCursor.moveToNext()) {
                                    String lid = mapCursor.getString(lidCol);
                                    String phone = mapCursor.getString(phoneCol);
                                    if (lid != null && phone != null) {
                                        lidToPhoneMap.put(lid, phone);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] Querying jid_map failed: " + t.getMessage());
                        }

                        if (!lidToPhoneMap.isEmpty()) {
                            Map<String, ContentValues> phoneContactMap = new HashMap<>();
                            for (ContentValues cv : contactList) {
                                String jid = cv.getAsString("jid");
                                if (jid != null && jid.endsWith("@s.whatsapp.net")) {
                                    phoneContactMap.put(jid, cv);
                                }
                            }

                            for (Map.Entry<String, String> entry : lidToPhoneMap.entrySet()) {
                                String lidJid = entry.getKey();
                                String phoneJid = entry.getValue();
                                ContentValues phoneCv = phoneContactMap.get(phoneJid);
                                if (phoneCv != null) {
                                    ContentValues values = new ContentValues();
                                    values.put("jid", lidJid);
                                    values.put("display_name", phoneCv.getAsString("display_name"));
                                    values.put("wa_name", phoneCv.getAsString("wa_name"));
                                    values.put("number", phoneCv.getAsString("number"));
                                    contactList.add(values);
                                }
                            }
                        }
                    } finally {
                        msgDb.close();
                    }
                }
            }

            if (!contactList.isEmpty()) {
                String authority = "com.waenhancer.provider";
                try {
                    Class<?> buildConfigClass = classLoader.loadClass("com.waenhancer.BuildConfig");
                    Field appIdField = buildConfigClass.getField("APPLICATION_ID");
                    String appId = (String) appIdField.get(null);
                    if (appId != null && !appId.trim().isEmpty()) {
                        authority = appId + ".provider";
                    }
                } catch (Throwable ignored) {}

                int chunkSize = 500;
                for (int i = 0; i < contactList.size(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, contactList.size());
                    ArrayList<ContentValues> chunk = new ArrayList<>(contactList.subList(i, end));

                    Bundle bundle = new Bundle();
                    bundle.putParcelableArrayList("contacts", chunk);

                    context.getContentResolver().call(
                            Uri.parse("content://" + authority),
                            "sync_contacts",
                            null,
                            bundle
                    );
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] syncWhatsAppContacts failed: " + t.getMessage());
        }
    }
}