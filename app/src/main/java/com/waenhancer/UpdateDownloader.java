package com.waenhancer;
import com.waenhancer.ui.helpers.BottomSheetHelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.utils.RootUtils;
import de.robv.android.xposed.XposedBridge;
import java.util.Locale;

public class UpdateDownloader {

    private static Activity getActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public interface DownloadCallback {
        void onProgress(int progress, long currentBytes, long totalBytes);
        void onSuccess(File apkFile);
        void onFailure(Exception e);
    }

    public static Call downloadApk(Context context, String url, String versionName, DownloadCallback callback) {
        String fileName = null;
        try {
            Uri uri = Uri.parse(url);
            fileName = uri.getLastPathSegment();
        } catch (Exception ignored) {}

        if (fileName == null || !fileName.endsWith(".apk")) {
            String safeVersion = versionName.replaceAll("[^a-zA-Z0-9.-]", "_");
            fileName = "WaEnhancer X_" + safeVersion + ".apk";
        }

        File cacheDir = context.getCacheDir();
        File apkFile = new File(cacheDir, fileName);

        if (apkFile.exists()) {
            callback.onSuccess(apkFile);
            return null;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) return;
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Unexpected code " + response));
                    return;
                }

                File tmpFile = new File(cacheDir, apkFile.getName() + ".tmp");

                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(tmpFile)) {

                    long totalBytes = response.body().contentLength();
                    byte[] buffer = new byte[8192];
                    int read;
                    long currentBytes = 0;

                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        currentBytes += read;
                        int progress = (int) (currentBytes * 100 / (totalBytes > 0 ? totalBytes : 1));
                        callback.onProgress(progress, currentBytes, totalBytes);
                    }
                    fos.flush();
                    
                    if (tmpFile.renameTo(apkFile)) {
                        callback.onSuccess(apkFile);
                    } else {
                        callback.onFailure(new IOException("Failed to rename temporary file"));
                    }
                } catch (Exception e) {
                    if (call.isCanceled()) return;
                    callback.onFailure(e);
                } finally {
                    if (tmpFile.exists() && !apkFile.exists()) {
                        tmpFile.delete();
                    }
                }
            }
        });
        return call;
    }

    public static void installApk(Context context, File apkFile) {
        Activity activity = getActivity(context);
        if (activity == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                Toast.makeText(activity, "Please allow WaEnhancer X to install apps", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    public static void installApkWithRoot(Context context, File apkFile) {
        Activity activity = getActivity(context);
        if (activity == null) return;

        new Thread(() -> {
            String apkPath = apkFile.getAbsolutePath();
            String tmpPath = "/data/local/tmp/wa_update.apk";
            
            // Clean up old file first
            RootUtils.runRootCommand("rm -f " + tmpPath);

            // Copy to /data/local/tmp using cat to bypass SELinux read restrictions on /data/data
            String copyCmd = "cat \"" + apkPath + "\" > " + tmpPath + " && chmod 666 " + tmpPath;
            RootUtils.runRootCommand(copyCmd);

            // Validate that file exists and is not empty
            String sizeResult = RootUtils.runRootCommand("wc -c < " + tmpPath);
            long bytes = 0;
            try {
                if (sizeResult != null) {
                    bytes = Long.parseLong(sizeResult.trim());
                }
            } catch (Exception ignored) {}

            boolean successVal = false;
            String resultVal = "";
            if (bytes > 1000) {
                // -r: replace existing application
                // -d: allow version code downgrade
                // --user 0: install for owner
                String cmd = "pm install -r -d --user 0 " + tmpPath;
                resultVal = RootUtils.runRootCommand(cmd);
                successVal = resultVal != null && (resultVal.toLowerCase().contains("success") || resultVal.toLowerCase().contains("pkg:"));
            } else {
                resultVal = "Failed to copy APK file to /data/local/tmp. Check root permissions.";
            }
            
            // Cleanup
            RootUtils.runRootCommand("rm -f " + tmpPath);
            
            final boolean finalSuccess = successVal;
            final String finalResult = resultVal;
            activity.runOnUiThread(() -> {
                if (finalSuccess) {
                    Toast.makeText(activity, "Installation successful. Restarting...", Toast.LENGTH_LONG).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }, 2000);
                } else {
                    String error = (finalResult != null && !finalResult.isEmpty()) ? finalResult.trim() : "Unknown error";
                    Toast.makeText(activity, "Root installation failed: " + error, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    public static void showDownloadDialog(Context context, String url, String version) {
        showDownloadDialog(context, url, version, false);
    }

    public static void showDownloadDialog(Context context, String url, String version, boolean useRoot) {
        Activity activity = getActivity(context);
        if (activity == null) return;

        // For Xposed: Need to get resources from WaEnhancerX package if running in e.g. WhatsApp
        Context modContext = activity;
        boolean isXposed = !BuildConfig.APPLICATION_ID.equals(activity.getPackageName());
        
        if (isXposed) {
            try {
                modContext = activity.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
            } catch (Exception e) {
                XposedBridge.log("[WAEX] Error creating package context: " + e.getMessage());
            }
        }

        // Dynamically get resource IDs for layout and views
        int layoutId = isXposed ? modContext.getResources().getIdentifier("bottom_sheet_update_progress", "layout", BuildConfig.APPLICATION_ID) : R.layout.bottom_sheet_update_progress;
        int progressBarId = isXposed ? modContext.getResources().getIdentifier("update_progress_bar", "id", BuildConfig.APPLICATION_ID) : R.id.update_progress_bar;
        int statusTextId = isXposed ? modContext.getResources().getIdentifier("update_status_text", "id", BuildConfig.APPLICATION_ID) : R.id.update_status_text;
        int cancelBtnId = isXposed ? modContext.getResources().getIdentifier("bs_cancel_btn", "id", BuildConfig.APPLICATION_ID) : R.id.bs_cancel_btn;

        if (layoutId == 0) {
            Toast.makeText(activity, "Error: Could not load update layout", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(modContext).inflate(layoutId, null);
        var progressBar = (LinearProgressIndicator) dialogView.findViewById(progressBarId);
        var statusText = (MaterialTextView) dialogView.findViewById(statusTextId);
        var cancelBtn = (MaterialButton) dialogView.findViewById(cancelBtnId);

        // Final references for inner class
        final Call[] currentCall = {null};

        var dialog = BottomSheetHelper.createStyledDialog(activity);
        dialog.setContentView(dialogView);
        dialog.setCanceledOnTouchOutside(false);

        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> {
                if (currentCall[0] != null) currentCall[0].cancel();
                dialog.dismiss();
            });
        }

        dialog.show();

        currentCall[0] = downloadApk(activity, url, version, new DownloadCallback() {
            @Override
            public void onProgress(int progress, long currentBytes, long totalBytes) {
                activity.runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setProgress(progress);
                    String sizeInfo = String.format(Locale.US, "%.1f MB / %.1f MB", 
                        currentBytes / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0));
                    if (statusText != null) statusText.setText(sizeInfo + " (" + progress + "%)");
                });
            }

            @Override
            public void onSuccess(File apkFile) {
                activity.runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    if (useRoot) {
                        installApkWithRoot(activity, apkFile);
                    } else {
                        installApk(activity, apkFile);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                activity.runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}