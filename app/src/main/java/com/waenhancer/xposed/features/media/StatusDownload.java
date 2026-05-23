package com.waenhancer.xposed.features.media;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.listeners.MenuStatusListener;
import com.waenhancer.xposed.utils.MimeTypeUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import android.content.SharedPreferences;

public class StatusDownload extends Feature {

    public static volatile Object activeStatusObj = null;

    public StatusDownload(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        try {
            Class<?> activityClass = de.robv.android.xposed.XposedHelpers.findClass("com.whatsapp.status.playback.StatusPlaybackActivity", classLoader);
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(activityClass, "onCreate", android.os.Bundle.class, new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.app.Activity activity = (android.app.Activity) param.thisObject;
                    setupProgressPoller(activity);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error hooking StatusPlaybackActivity onCreate: " + t.getMessage());
        }
        var downloadStatus = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageList, int currentIndex) {
                reloadPrefs();
                if (!prefs.getBoolean("downloadstatus", false)) return null;
                if (menu.findItem(R.string.download) != null) return null;
                var fMessage = fMessageList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                if (!fMessage.isMediaFile()) return null;
                
                MenuItem item = menu.add(0, R.string.download, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.xposed.utils.Utils.getApplication(), R.string.download, "Download"));
                File file = fMessage.getMediaFile();
                if (file == null) {
                    file = getMediaFile(activeStatusObj);
                }
                if (file == null || !file.exists()) {
                    item.setEnabled(false);
                }
                return item;
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageList, int currentIndex) {
                reloadPrefs();
                if (!prefs.getBoolean("downloadstatus", false)) return;
                var fMessage = fMessageList.get(currentIndex);
                downloadFile(fMessage, fragmentInstance, currentIndex);
            }
        };
        MenuStatusListener.getMenuStatuses().add(downloadStatus);

        var sharedMenu = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageList, int currentIndex) {
                reloadPrefs();
                if (!prefs.getBoolean("downloadstatus", false)) return null;
                var fMessage = fMessageList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                if (menu.findItem(R.string.share_as_status) != null) return null;
                
                MenuItem item = menu.add(0, R.string.share_as_status, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.xposed.utils.Utils.getApplication(), R.string.share_as_status, "Share as status"));
                File file = fMessage.getMediaFile();
                if (file == null) {
                    file = getMediaFile(activeStatusObj);
                }
                if (file == null || !file.exists()) {
                    item.setEnabled(false);
                }
                return item;
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, List<FMessageWpp> fMessageList, int currentIndex) {
                reloadPrefs();
                if (!prefs.getBoolean("downloadstatus", false)) return;
                var fMessageWpp = fMessageList.get(currentIndex);
                sharedStatus(fMessageWpp, fragmentInstance, currentIndex);
            }
        };
        MenuStatusListener.getMenuStatuses().add(sharedMenu);
    }

    private void sharedStatus(FMessageWpp fMessageWpp, Object fragmentInstance, int currentIndex) {
        try {
            if (!fMessageWpp.isMediaFile()) {
                // Text-only status: open the text status composer
                Intent intent = new Intent();
                Class<?> clazz;
                try {
                    clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "TextStatusComposerActivity");
                } catch (Exception ignored) {
                    clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "ConsolidatedStatusComposerActivity");
                    intent.putExtra("status_composer_mode", 2);
                }
                intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
                intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }

            var file = fMessageWpp.getMediaFile();
            if (file == null) {
                file = getFileFromRawStatus(fragmentInstance, currentIndex, fMessageWpp);
            }
            if (file == null) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.xposed.utils.Utils.getApplication(), R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), Toast.LENGTH_SHORT);
                return;
            }

            Uri mediaUri;
            try {
                String authority = Utils.getApplication().getPackageName() + ".fileprovider";
                mediaUri = FileProvider.getUriForFile(Utils.getApplication(), authority, file);
            } catch (IllegalArgumentException e) {
                XposedBridge.log("WAEX: FileProvider failed for " + file.getAbsolutePath() + ": " + e.getMessage());
                mediaUri = Uri.fromFile(file);
            }

            Intent intent = new Intent();
            var clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "MediaComposerActivity");
            intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton("status@broadcast")));
            intent.putExtra("android.intent.extra.STREAM", new ArrayList<>(Collections.singleton(mediaUri)));
            String caption = fMessageWpp.getMessageStr();
            if (!TextUtils.isEmpty(caption)) {
                intent.putExtra("android.intent.extra.TEXT", caption);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            WppCore.getCurrentActivity().startActivity(intent);
        } catch (Throwable e) {
            XposedBridge.log("WAEX: sharedStatus error: " + e.getMessage());
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void downloadFile(FMessageWpp fMessage, Object fragmentInstance, int currentIndex) {
        try {
            var file = fMessage.getMediaFile();
            if (file == null) {
                file = getFileFromRawStatus(fragmentInstance, currentIndex, fMessage);
            }
            if (file == null) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.xposed.utils.Utils.getApplication(), R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), 1);
                return;
            }
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getStatusDestination(file);
            var name = Utils.generateName(userJid, fileType);
            var error = Utils.copyFile(file, destination, name);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.xposed.utils.Utils.getApplication(), R.string.saved_to, "Saved to: ") + destination,
                        Toast.LENGTH_SHORT);
            } else {
                Utils.showToast(
                        com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.xposed.utils.Utils.getApplication(), R.string.error_when_saving_try_again, "Error when saving, try again") + ": " + error,
                        Toast.LENGTH_SHORT);
            }
        } catch (Throwable e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download Status";
    }

    @NonNull
    private String getStatusDestination(@NonNull File f) throws Exception {
        var fileName = f.getName().toLowerCase();
        var mimeType = MimeTypeUtils.getMimeTypeFromExtension(fileName);
        var folderPath = "";
        if (mimeType.contains("video")) {
            folderPath = "Status Videos";
        } else if (mimeType.contains("image")) {
            folderPath = "Status Images";
        } else if (mimeType.contains("audio")) {
            folderPath = "Status Sounds";
        } else {
            folderPath = "Status Media";
        }
        return Utils.getDestination(folderPath);
    }

    private File getFileFromRawStatus(Object fragmentInstance, int currentIndex, FMessageWpp fMessage) {
        if (fragmentInstance == null || currentIndex < 0) return null;
        try {
            Class<?> fragmentClass = fragmentInstance.getClass();
            if (fragmentClass.getName().endsWith("StatusPlaybackContactFragment")) {
                java.lang.reflect.Field listStatusField = com.waenhancer.xposed.utils.ReflectionUtils.getFieldByExtendType(
                        fragmentClass, List.class);
                if (listStatusField != null) {
                    List<?> rawList = (List<?>) listStatusField.get(fragmentInstance);
                    if (rawList != null && currentIndex < rawList.size()) {
                        Object rawStatusObj = rawList.get(currentIndex);
                        return findNestedFile(rawStatusObj, new java.util.HashSet<>(), 0);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback: search the fMessage object itself just in case
        return findNestedFile(fMessage.getObject(), new java.util.HashSet<>(), 0);
    }

    private File findNestedFile(Object object, java.util.Set<Object> visited, int depth) {
        if (object == null || depth > 6) return null;
        if (!visited.add(object)) return null;

        if (object instanceof File file) {
            return file.exists() ? file : null;
        }

        if (object instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                File match = findNestedFile(item, visited, depth + 1);
                if (match != null) return match;
            }
            return null;
        }

        if (object.getClass().isArray() && !object.getClass().getComponentType().isPrimitive()) {
            Object[] array = (Object[]) object;
            for (Object item : array) {
                File match = findNestedFile(item, visited, depth + 1);
                if (match != null) return match;
            }
            return null;
        }

        Class<?> current = object.getClass();
        while (current != null && current != Object.class && !current.getName().startsWith("java.") && !current.getName().startsWith("android.")) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object nested = field.get(object);
                    File match = findNestedFile(nested, visited, depth + 1);
                    if (match != null) return match;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private static android.view.View findMenuButton(android.view.View root) {
        if (root == null) return null;
        try {
            if (root.getId() != android.view.View.NO_ID) {
                String idName = root.getResources().getResourceEntryName(root.getId());
                if (idName != null && (idName.contains("menu") || idName.contains("more") || idName.contains("option"))) {
                    return root;
                }
            }
            CharSequence desc = root.getContentDescription();
            if (desc != null) {
                String d = desc.toString().toLowerCase();
                if (d.contains("more options") || d.contains("menu") || d.contains("options")) {
                    return root;
                }
            }
        } catch (Exception ignored) {}
        
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findMenuButton(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Object findDownloadStatusObject(Object obj, java.util.Set<Object> visited, int depth) {
        if (obj == null || depth > 4) return null;
        if (!visited.add(obj)) return null;

        Class<?> clazz = obj.getClass();
        if (!clazz.getName().startsWith("java.") && !clazz.getName().startsWith("android.")) {
            int floatFieldsCount = 0;
            java.lang.reflect.Field floatField = null;
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (f.getType() == float.class) {
                    floatFieldsCount++;
                    floatField = f;
                }
            }
            
            int fileMethodsCount = 0;
            java.lang.reflect.Method fileMethod = null;
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getReturnType() == java.io.File.class && m.getParameterCount() == 0) {
                    fileMethodsCount++;
                    fileMethod = m;
                }
            }
            
            if (floatFieldsCount == 1 && fileMethodsCount == 1) {
                return obj;
            }
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class && !current.getName().startsWith("java.") && !current.getName().startsWith("android.")) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (field.getType().isPrimitive() || field.getType().getName().startsWith("java.") || field.getType().getName().startsWith("android.")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    Object found = findDownloadStatusObject(val, visited, depth + 1);
                    if (found != null) return found;
                } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static File getMediaFile(Object statusObj) {
        if (statusObj == null) return null;
        try {
            Object downloadStatusObj = findDownloadStatusObject(statusObj, new java.util.HashSet<>(), 0);
            if (downloadStatusObj == null) return null;
            for (java.lang.reflect.Method m : downloadStatusObj.getClass().getDeclaredMethods()) {
                if (m.getReturnType() == java.io.File.class && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return (File) m.invoke(downloadStatusObj);
                }
            }
        } catch (Exception e) {
            XposedBridge.log("[WAEX] Error getMediaFile: " + e.getMessage());
        }
        return null;
    }

    public static float getDownloadProgress(Object statusObj) {
        if (statusObj == null) return 0.0f;
        try {
            Object downloadStatusObj = findDownloadStatusObject(statusObj, new java.util.HashSet<>(), 0);
            if (downloadStatusObj == null) return 0.0f;
            for (java.lang.reflect.Field f : downloadStatusObj.getClass().getDeclaredFields()) {
                if (f.getType() == float.class) {
                    f.setAccessible(true);
                    return f.getFloat(downloadStatusObj);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return 0.0f;
    }

    public static void setupProgressPoller(final android.app.Activity activity) {
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                
                try {
                    android.view.View decorView = activity.getWindow().getDecorView();
                    android.view.View menuButton = findMenuButton(decorView);
                    if (menuButton != null && menuButton.getParent() instanceof android.view.ViewGroup) {
                        android.view.ViewGroup parent = (android.view.ViewGroup) menuButton.getParent();
                        
                        android.view.View progressBarView = parent.findViewById(0x7EAD0099);
                        if (progressBarView == null) {
                            CircularProgressView circularProgress = new CircularProgressView(activity);
                            circularProgress.setId(0x7EAD0099);
                            
                            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                                com.waenhancer.xposed.utils.Utils.dipToPixels(24.0f),
                                com.waenhancer.xposed.utils.Utils.dipToPixels(24.0f)
                            );
                            params.gravity = android.view.Gravity.CENTER_VERTICAL;
                            params.rightMargin = com.waenhancer.xposed.utils.Utils.dipToPixels(12.0f);
                            params.leftMargin = com.waenhancer.xposed.utils.Utils.dipToPixels(12.0f);
                            circularProgress.setLayoutParams(params);
                            
                            int idx = parent.indexOfChild(menuButton);
                            parent.addView(circularProgress, idx);
                            progressBarView = circularProgress;
                        }
                        
                        CircularProgressView progressBar = (CircularProgressView) progressBarView;
                        Object status = activeStatusObj;
                        File file = getMediaFile(status);
                        
                        if (file != null && file.exists()) {
                            progressBar.setVisibility(android.view.View.GONE);
                        } else {
                            float progress = getDownloadProgress(status);
                            if (progress > 0.0f && progress <= 1.0f) {
                                progress = progress * 100.0f;
                            }
                            if (progress > 0.0f && progress < 100.0f) {
                                progressBar.setVisibility(android.view.View.VISIBLE);
                                progressBar.setProgress(progress);
                            } else {
                                progressBar.setVisibility(android.view.View.VISIBLE);
                                progressBar.setIndeterminate(true);
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore
                }
                
                handler.postDelayed(this, 100);
            }
        };
        handler.postDelayed(runnable, 100);
    }

    public static class CircularProgressView extends android.view.View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rectF = new android.graphics.RectF();
        private float progress = 0f;
        private boolean indeterminate = true;
        private float spinAngle = 0f;

        public CircularProgressView(android.content.Context context) {
            super(context);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        public void setProgress(float progress) {
            this.progress = progress;
            this.indeterminate = false;
            invalidate();
        }

        public void setIndeterminate(boolean indeterminate) {
            if (this.indeterminate != indeterminate) {
                this.indeterminate = indeterminate;
                invalidate();
            }
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float strokeWidth = com.waenhancer.xposed.utils.Utils.dipToPixels(3.5f); // Premium bold weight!
            paint.setStrokeWidth(strokeWidth);
            
            float padding = strokeWidth / 2.0f;
            rectF.set(padding, padding, size - padding, size - padding);

            // Draw track (subtle translucent white background)
            paint.setColor(0x2BFFFFFF); // ~17% opacity white
            canvas.drawOval(rectF, paint);

            // Draw progress (solid white)
            paint.setColor(0xFFFFFFFF);
            if (indeterminate) {
                canvas.drawArc(rectF, spinAngle, 90f, false, paint);
                spinAngle = (spinAngle + 5f) % 360f;
                postInvalidateOnAnimation();
            } else {
                float sweepAngle = (progress / 100f) * 360f;
                canvas.drawArc(rectF, -90f, sweepAngle, false, paint);
            }
        }
    }

}
