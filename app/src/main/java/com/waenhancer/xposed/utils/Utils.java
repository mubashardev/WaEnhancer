package com.waenhancer.xposed.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.documentfile.provider.DocumentFile;

import com.waenhancer.App;

import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Utils {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public static android.content.SharedPreferences xprefs;
    private static final HashMap<String, Integer> ids = new HashMap<>();
    public static boolean DEBUG = false;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void postDelayed(Runnable runnable, long delay) {
        mainHandler.postDelayed(runnable, delay);
    }

    public static void init(ClassLoader loader) {
        var context = Utils.getApplication();
        var notificationManager = NotificationManagerCompat.from(context);
        var channel = new NotificationChannel("wppenhacer", "WAE Enhancer", NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);
    }


    @NonNull
    public static Application getApplication() {
        return FeatureLoader.mApp == null ? App.getInstance() : FeatureLoader.mApp;
    }

    public static ExecutorService getExecutor() {
        return executorService;
    }

    public static void openModule(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.waenhancer", "com.waenhancer.xposed.features.others.EmbeddedSettingsActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Error opening WaEnhancer X: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean doRestart(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        if (intent == null)
            return false;
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        mainIntent.setPackage(context.getPackageName());
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
        return true;
    }

    /**
     * Retrieves the resource ID by name and type.
     * Uses caching to improve performance for repeated lookups.
     *
     * @param name The resource name to look up
     * @param type The resource type (e.g., "id", "drawable", "layout", "string")
     * @return The resource ID or -1 if not found or an error occurred
     */
    @SuppressLint("DiscouragedApi")
    public static int getID(String name, String type) {

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(type)) {
            return -1;
        }

        final String key = type + "_" + name;

        synchronized (ids) {
            if (ids.containsKey(key)) {
                Integer cachedId = ids.get(key);
                return cachedId != null ? cachedId : -1;
            }
        }

        try {
            Application app = getApplication();
            Context context = app.getApplicationContext();
            int id = context.getResources().getIdentifier(name, type, app.getPackageName());

            // Android returns 0 when the resource name/type is not found.
            // Normalize to -1 so callers can reliably detect missing resources.
            if (id == 0) id = -1;

            synchronized (ids) {
                ids.put(key, id);
            }

            return id;
        } catch (Exception e) {
            // Failed to get resource ID
            return -1;
        }
    }

    public static int dipToPixels(float dipValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, getApplication().getResources().getDisplayMetrics());
    }

    public static String getMyNumber() {
        Application app = getApplication();
        return app.getSharedPreferences(app.getPackageName() + "_preferences_light", Context.MODE_PRIVATE).getString("ph", "");
    }

    public static String getDateTimeFromMillis(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(new Date(timestamp));
    }

    @SuppressLint("SdCardPath")
    public static String getDestination(String name) throws Exception {
        if (xprefs.getBoolean("lite_mode", false)) {
            var folder = WppCore.getPrivString("download_folder", null);
            if (folder == null)
                throw new Exception("Download Folder is not selected!");
            var documentFile = DocumentFile.fromTreeUri(Utils.getApplication(), Uri.parse(folder));
            var wppFolder = Utils.getURIFolderByName(documentFile, "WhatsApp", true);
            var nameFolder = Utils.getURIFolderByName(wppFolder, name, true);
            if (nameFolder == null)
                throw new Exception("Folder not found!");
            return folder + "/WhatsApp/" + name;
        }
        String folder = XPrefManager.getPref(getApplication()).getString("download_local", "/sdcard/Download");
        var waFolder = new File(folder, "WhatsApp");
        var filePath = new File(waFolder, name);
        try {
            WppCore.getClientBridge().createDir(filePath.getAbsolutePath());
        } catch (Exception ignored) {
        }
        return filePath.getAbsolutePath() + "/";

    }

    public static DocumentFile getURIFolderByName(DocumentFile documentFile, String folderName, boolean createDir) {
        if (documentFile == null) {
            return null;
        }
        DocumentFile[] files = documentFile.listFiles();
        for (DocumentFile file : files) {
            if (Objects.equals(file.getName(), folderName)) {
                return file;
            }
        }
        if (createDir) {
            return documentFile.createDirectory(folderName);
        }
        return null;
    }


    public static String copyFile(File srcFile, String destFolder, String name) {
        if (srcFile == null || !srcFile.exists()) return "File not found or is null";

        if (xprefs.getBoolean("lite_mode", false)) {
            try {
                var folder = WppCore.getPrivString("download_folder", null);
                DocumentFile documentFolder = DocumentFile.fromTreeUri(Utils.getApplication(), Uri.parse(folder));
                destFolder = destFolder.replace(folder + "/", "");
                for (String f : destFolder.split("/")) {
                    documentFolder = Utils.getURIFolderByName(documentFolder, f, false);
                    if (documentFolder == null) return "Failed to get folder";
                }
                DocumentFile newFile = documentFolder.createFile("*/*", name);
                if (newFile == null) return "Failed to create destination file";

                ContentResolver contentResolver = Utils.getApplication().getContentResolver();

                try (InputStream in = new FileInputStream(srcFile);
                     OutputStream out = contentResolver.openOutputStream(newFile.getUri())) {

                    if (out == null) return "Failed to open output stream";

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }

                    return "";
                }
            } catch (Exception e) {
                XposedBridge.log(e);
                return e.getMessage();
            }
        } else {
            File destFile = new File(destFolder, name);
            try (FileInputStream in = new FileInputStream(srcFile);
                 var parcelFileDescriptor = WppCore.getClientBridge().openFile(destFile.getAbsolutePath(), true)) {
                var out = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
                byte[] bArr = new byte[1024];
                while (true) {
                    int read = in.read(bArr);
                    if (read <= 0) {
                        in.close();
                        out.close();
                        Utils.scanFile(destFile);
                        return "";
                    }
                    out.write(bArr, 0, read);
                }
            } catch (Exception e) {
                log(e);
                return e.getMessage();
            }
        }
    }


    public static void showSnackbar(Activity activity, String message) {
        if (activity == null || message == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                var view = activity.findViewById(android.R.id.content);
                if (view != null) {
                    Class<?> snackbarClass = null;
                    ClassLoader loader = FeatureLoader.hostClassLoader != null ? FeatureLoader.hostClassLoader : activity.getClassLoader();
                    
                    try {
                        snackbarClass = XposedHelpers.findClass("com.google.android.material.snackbar.Snackbar", loader);
                    } catch (Throwable t) {
                        snackbarClass = XposedHelpers.findClass("com.google.android.material.snackbar.Snackbar", activity.getClassLoader());
                    }
                    
                    if (snackbarClass != null) {
                        Object snackbar = XposedHelpers.callStaticMethod(snackbarClass, "make", view, message, 0); // 0 = LENGTH_LONG
                        XposedHelpers.callMethod(snackbar, "show");
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("[WAE] Failed to show Snackbar: " + t.getMessage());
                showToast(message, Toast.LENGTH_SHORT);
            }
        });
    }

    public static void showToast(String message, int length) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Já estamos na thread principal
            Toast.makeText(Utils.getApplication(), message, length).show();
        } else {
            // Não estamos na thread principal, postamos no Handler
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(Utils.getApplication(), message, length).show()
            );
        }
    }

    public static void log(String message) {
        if (!DEBUG) return;
        try {
            ;
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            // Fallback logging not available
        }
    }

    public static void log(Throwable t) {
        if (!DEBUG) return;
        try {
            XposedBridge.log(t);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            // Fallback logging not available
        }
    }

    public static void logError(String message) {
        try {
            XposedBridge.log("[WAE_ERROR] " + message);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
        }
    }

    public static void logError(Throwable t) {
        try {
            XposedBridge.log(t);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
        }
    }

    public static void setToClipboard(String string) {
        ClipboardManager clipboard = (ClipboardManager) Utils.getApplication().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", string);
        clipboard.setPrimaryClip(clip);
    }

    public static String generateName(FMessageWpp.UserJid userJid, String fileFormat) {
        var contactName = WppCore.getContactName(userJid);
        var number = userJid.getPhoneRawString();
        return toValidFileName(contactName) + "_" + number + "_" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date()) + "." + fileFormat;
    }


    @NonNull
    public static String toValidFileName(@NonNull String input) {
        return input.replaceAll("[:\\\\/*\"?|<>']", " ");
    }

    public static void scanFile(File file) {
        MediaScannerConnection.scanFile(Utils.getApplication(),
                new String[]{file.getAbsolutePath()},
                new String[]{MimeTypeUtils.getMimeTypeFromExtension(file.getAbsolutePath())},
                (s, uri) -> {
                });
    }

    public static Properties getProperties(SharedPreferences prefs, String key, String checkKey) {
        Properties properties = new Properties();
        if (checkKey != null && !prefs.getBoolean(checkKey, false))
            return properties;
        String text = prefs.getString(key, "");
        Pattern pattern = Pattern.compile("^/\\*\\s*(.*?)\\s*\\*/", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String propertiesText = matcher.group(1);
            String[] lines = propertiesText.split("\\s*\\n\\s*");

            for (String line : lines) {
                String[] keyValue = line.split("\\s*=\\s*");
                String skey = keyValue[0].strip();
                String value = keyValue[1].strip().replaceAll("^\"|\"$", ""); // Remove quotes, if any
                properties.put(skey, value);
            }
        }

        return properties;
    }

    public static int tryParseInt(String wallpaperAlpha, int i) {
        try {
            return Integer.parseInt(wallpaperAlpha.trim());
        } catch (Exception e) {
            return i;
        }
    }

    public static Application getApplicationByReflect() {
        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            Object app = activityThread.getMethod("getApplication").invoke(thread);
            if (app == null) {
                throw new NullPointerException("u should init first");
            }
            return (Application) app;
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new NullPointerException("u should init first");
    }

    public static <T> T binderLocalScope(BinderLocalScopeBlock<T> block) {
        long identity = Binder.clearCallingIdentity();
        try {
            return block.execute();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public static String getAuthorFromCss(String code) {
        if (code == null) return null;
        var match = Pattern.compile("author\\s*=\\s*(.*?)\n").matcher(code);
        if (!match.find()) return null;
        return match.group(1);
    }

    @SuppressLint("MissingPermission")
    public static void showNotification(String title, String content) {
        var context = Utils.getApplication();
        var notificationManager = NotificationManagerCompat.from(context);
        var channel = new NotificationChannel("wppenhacer", "WAE Enhancer", NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);
        var notification = new NotificationCompat.Builder(context, "wppenhacer")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content));
        notificationManager.notify(new Random().nextInt(), notification.build());
    }

    public static void openLink(Activity mActivity, String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        mActivity.startActivity(browserIntent);
    }


    public static String getInstalledTelegramPackage(Context context) {
        String[] telegramPackages = {
                "org.telegram.messenger",
                "org.thunderdog.challegram",
                "org.telegram.plus",
                "app.nicegram",
                "com.iMe.android",
                "org.vidogram.messenger",
                "org.telegram.BifToGram",
                "tw.nekomimi.nekogram",
                "org.forkgram.messenger",
                "ir.ilmili.telegraph",
                "org.telegram.messenger.mobogram",
                "org.turbotel.messenger",
                "org.bestgram.messenger",
                "in.teleplus",
                "it.matteocontrini.unigram"
        };
        PackageManager pm = context.getPackageManager();
        for (String pkg : telegramPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void dumpViewHierarchy(android.view.View view, int depth) {
        if (view == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        
        String idName = "no_id";
        try {
            if (view.getId() != android.view.View.NO_ID) {
                idName = view.getResources().getResourceEntryName(view.getId());
            }
        } catch (Exception ignored) {}
        
        sb.append("[").append(depth).append("] ")
          .append(view.getClass().getName())
          .append(" (id: ").append(idName).append(")");
          
        XposedBridge.log("[WaEnhancer] UI Dump: " + sb.toString());
        
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViewHierarchy(group.getChildAt(i), depth + 1);
            }
        }
    }

    public static Activity getActivityFromView(android.view.View view) {
        if (view == null) return null;
        Context context = view.getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @FunctionalInterface
    public interface BinderLocalScopeBlock<T> {
        T execute();
    }

    @SuppressWarnings("unchecked")
    public static void setViewClickListener(android.view.View view, String key, android.view.View.OnClickListener listener) {
        if (view == null) return;
        
        synchronized (view) {
            java.util.HashMap<String, android.view.View.OnClickListener> listeners = (java.util.HashMap<String, android.view.View.OnClickListener>) 
                    de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField(view, "wae_click_listeners");
            
            if (listeners == null) {
                listeners = new java.util.HashMap<>();
                de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField(view, "wae_click_listeners", listeners);
                
                android.view.View.OnClickListener original = getCurrentClickListener(view);
                if (original != null && !isWaeClickListener(original)) {
                    listeners.put("original", original);
                }
            }
            
            if (listener != null) {
                listeners.put(key, listener);
            } else {
                listeners.remove(key);
            }
            
            if (listeners.isEmpty()) {
                view.setOnClickListener(null);
                de.robv.android.xposed.XposedHelpers.removeAdditionalInstanceField(view, "wae_click_listeners");
            } else {
                android.view.View.OnClickListener composite = v -> {
                    java.util.HashMap<String, android.view.View.OnClickListener> map = (java.util.HashMap<String, android.view.View.OnClickListener>) 
                            de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField(v, "wae_click_listeners");
                    if (map != null) {
                        for (android.view.View.OnClickListener clickListener : new java.util.ArrayList<>(map.values())) {
                            if (clickListener != null) {
                                try {
                                    clickListener.onClick(v);
                                } catch (Throwable t) {
                                    de.robv.android.xposed.XposedBridge.log(t);
                                }
                            }
                        }
                    }
                };
                de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField(composite, "is_wae_click_listener", true);
                view.setOnClickListener(composite);
            }
        }
    }

    private static android.view.View.OnClickListener getCurrentClickListener(android.view.View view) {
        try {
            Object listenerInfo = de.robv.android.xposed.XposedHelpers.callMethod(view, "getListenerInfo");
            if (listenerInfo == null) return null;
            return (android.view.View.OnClickListener) de.robv.android.xposed.XposedHelpers.getObjectField(listenerInfo, "mOnClickListener");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isWaeClickListener(android.view.View.OnClickListener listener) {
        if (listener == null) return false;
        return Boolean.TRUE.equals(de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField(listener, "is_wae_click_listener"));
    }


    public static int getDefaultTheme() {
        try {
            android.content.Context context = getApplication();
            if (context == null) return 0;
            
            var startup_prefs = context.getSharedPreferences("startup_prefs", android.content.Context.MODE_PRIVATE);
            int mode = startup_prefs.getInt("night_mode", 0);
            if (mode != 0) {
                return mode;
            }

            // Try com.whatsapp_preferences
            var wa_prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE);
            String theme = wa_prefs.getString("theme", "system");
            if ("dark".equals(theme)) return 2;
            if ("light".equals(theme)) return 1;
            if ("system".equals(theme) || "default".equals(theme)) return 0;

        } catch (Throwable t) {
            android.util.Log.e("WAE_UTILS", "Error reading theme prefs: " + t.getMessage());
        }
        return 0;
    }
}
