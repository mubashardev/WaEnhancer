package com.waenhancer.xposed.features.general;

import static com.waenhancer.xposed.features.others.MenuHome.menuItems;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Menu;

import androidx.annotation.NonNull;

import com.waenhancer.utils.RealPathUtil;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.R;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class LiteMode extends Feature {

    public static final int REQUEST_FOLDER = 852583;


    public LiteMode(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    public static Uri getDownloadsUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        } else {
            return Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        }
    }

    private static void showDialogUriPermission(Activity activity) {
        new AlertDialogWpp(activity).setTitle(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_folder_permission))
                .setMessage(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.ask_download_folder))
                .setPositiveButton(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.allow), (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDownloadsUri());
                    activity.startActivityForResult(intent, REQUEST_FOLDER);
                }).setNegativeButton(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.cancel), (dialog, which) -> dialog.dismiss()).show();
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("lite_mode", false)) return;

        menuItems.add(this::InsertDownloadFolderButton);

        XposedHelpers.findAndHookMethod(WppCore.getHomeActivityClass(classLoader), "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;

                var wae = WppCore.getPrivString("download_folder", null);
                if (wae == null || !isUriPermissionGranted(activity, Uri.parse(wae))) {
                    showDialogUriPermission(activity);
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var activity = (Activity) param.thisObject;
                var id = (int) param.args[0];
                var intent = (Intent) param.args[2];
                if (id == REQUEST_FOLDER && (int) param.args[1] == Activity.RESULT_OK) {
                    processDownloadResult(activity, intent);
                }
            }
        });

    }

    public static String processDownloadResult(Activity activity, Intent intent) {
        var uri = intent.getData();
        if (uri == null) return null;
        WppCore.setPrivString("download_folder", uri.toString());
        activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return uri.toString();
    }

    private void InsertDownloadFolderButton(Menu menu, Activity activity) {
        var entryPoint = getSafeString("open_wae", "1");
        if (!"1".equals(entryPoint)) return;
        var itemMenu = menu.add(0, 0, 9999, "Download Folder");
        var iconDraw = activity.getDrawable(R.drawable.download);
        iconDraw.setTint(0xff8696a0);
        itemMenu.setIcon(iconDraw);
        itemMenu.setOnMenuItemClickListener(item -> {

            var folder = WppCore.getPrivString("download_folder", null);
            if (folder != null) {
                try {
                    folder = RealPathUtil.getRealFolderPath(activity, Uri.parse(folder));
                } catch (Exception ignored) {
                }
            }
            AlertDialogWpp dialog = new AlertDialogWpp(activity);
            dialog.setTitle("Download Folder");
            dialog.setMessage("Current Folder to download is " + folder);
            dialog.setNegativeButton("Cancel", (dialog1, which) -> dialog.dismiss());
            dialog.setPositiveButton("Select", (dialog1, which) -> {
                showDialogUriPermission(activity);
            }).show();
            return true;
        });
    }

    private boolean isUriPermissionGranted(Context context, Uri uri) {
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        int permissionCheck = context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), takeFlags);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Lite Mode";
    }
}
