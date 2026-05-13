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

    public StatusDownload(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        var downloadStatus = new MenuStatusListener.OnMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, List<FMessageWpp> fMessageList, int currentIndex) {
                reloadPrefs();
                if (!prefs.getBoolean("downloadstatus", false)) return null;
                if (menu.findItem(R.string.download) != null) return null;
                var fMessage = fMessageList.get(currentIndex);
                if (fMessage.getKey().isFromMe) return null;
                if (!fMessage.isMediaFile()) return null;
                return menu.add(0, R.string.download, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download, "Download"));
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
                return menu.add(0, R.string.share_as_status, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.share_as_status, "Share as status"));
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
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), Toast.LENGTH_SHORT);
                return;
            }

            Uri mediaUri;
            try {
                String authority = Utils.getApplication().getPackageName() + ".fileprovider";
                mediaUri = FileProvider.getUriForFile(Utils.getApplication(), authority, file);
            } catch (IllegalArgumentException e) {
                XposedBridge.log("WaEnhancer: FileProvider failed for " + file.getAbsolutePath() + ": " + e.getMessage());
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
            XposedBridge.log("WaEnhancer: sharedStatus error: " + e.getMessage());
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
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), 1);
                return;
            }
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getStatusDestination(file);
            var name = Utils.generateName(userJid, fileType);
            var error = Utils.copyFile(file, destination, name);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.saved_to, "Saved to: ") + destination,
                        Toast.LENGTH_SHORT);
            } else {
                Utils.showToast(
                        com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.error_when_saving_try_again, "Error when saving, try again") + ": " + error,
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

}
