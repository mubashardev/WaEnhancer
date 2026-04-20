package com.waenhancer;

import android.app.Activity;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.ResId;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import io.noties.markwon.Markwon;
import okhttp3.OkHttpClient;

public class UpdateChecker implements Runnable {

    private static final String TAG = "WAE_UpdateChecker";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/mubashardev/WaEnhancer/releases/latest";

    private static final String RELEASE_TAG_PREFIX = "debug-";
    private static final String TELEGRAM_UPDATE_URL = "https://github.com/mubashardev/WaEnhancer/releases";


    // Singleton OkHttpClient - expensive to create, reuse across all checks
    private static OkHttpClient httpClient;

    private final Activity mActivity;
    private boolean isManualCheck = false;

    private static void writeDebugLog(String message) {
        try {
            File debugDir = new File("/sdcard/Android/data/com.waenhancer/files");
            debugDir.mkdirs();
            File logFile = new File(debugDir, "update_checker_debug.log");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write("[" + System.currentTimeMillis() + "] " + message + "\n");
            fw.close();
        } catch (Exception e) {
            XposedBridge.log("[WAE_UpdateChecker] Failed to write debug log: " + e.getMessage());
        }
    }

    public UpdateChecker(Activity activity) {
        this.mActivity = activity;
    }

    public UpdateChecker(Activity activity, boolean isManualCheck) {
        this.mActivity = activity;
        this.isManualCheck = isManualCheck;
    }

    /**
     * Get or create the singleton OkHttpClient with proper timeout configuration
     */
    private static synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public void run() {
        try {
            // Clear any old ignored_version preference to ensure fresh check
            WppCore.setPrivString("ignored_version", "");
            
            String msg = "[UpdateChecker] Starting update check (cleared old ignored_version preference)...";
            XposedBridge.log("[" + TAG + "] " + msg);
            writeDebugLog(msg);

            var request = new okhttp3.Request.Builder()
                    .url(LATEST_RELEASE_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WaEnhancer-UpdateChecker")
                    .build();

                String latestVersion;
            String changelog;
            String publishedAt;

            try (var response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errMsg = "[UpdateChecker] API call failed with code: " + response.code();
                    XposedBridge.log("[" + TAG + "] " + errMsg);
                    writeDebugLog(errMsg);
                    return;
                }

                var body = response.body();
                if (body == null) {
                    String errMsg = "[UpdateChecker] Update check failed: Empty response body";
                    XposedBridge.log("[" + TAG + "] " + errMsg);
                    writeDebugLog(errMsg);
                    return;
                }

                var content = body.string();
                String logMsg = "[UpdateChecker] GitHub API response (first 500 chars): " + content.substring(0, Math.min(500, content.length()));
                XposedBridge.log("[" + TAG + "] " + logMsg);
                writeDebugLog(logMsg);
                
                var release = new JSONObject(content);
                var tagName = release.optString("tag_name", "").trim();

                String tagMsg = "[UpdateChecker] Latest release tag: '" + tagName + "'";
                XposedBridge.log("[" + TAG + "] " + tagMsg);
                writeDebugLog(tagMsg);

                if (tagName.isBlank()) {
                    String blankMsg = "[UpdateChecker] Tag name is blank";
                    XposedBridge.log("[" + TAG + "] " + blankMsg);
                    writeDebugLog(blankMsg);
                    return;
                }

                if (tagName.startsWith(RELEASE_TAG_PREFIX)) {
                    latestVersion = tagName.substring(RELEASE_TAG_PREFIX.length()).trim();
                    latestVersion = normalizeVersion(latestVersion);
                    String stripMsg = "[UpdateChecker] Stripped debug prefix and normalized: '" + latestVersion + "'";
                    XposedBridge.log("[" + TAG + "] " + stripMsg);
                    writeDebugLog(stripMsg);
                } else {
                    latestVersion = normalizeVersion(tagName);
                    String normMsg = "[UpdateChecker] Normalized tag: '" + latestVersion + "'";
                    XposedBridge.log("[" + TAG + "] " + normMsg);
                    writeDebugLog(normMsg);
                }
                changelog = release.optString("body", "No changelog available.").trim();
                publishedAt = release.optString("published_at", "");

                String parseMsg = "[UpdateChecker] Parsed latest version: " + latestVersion + ", published: " + publishedAt;
                XposedBridge.log("[" + TAG + "] " + parseMsg);
                writeDebugLog(parseMsg);

            }

            if (latestVersion == null || latestVersion.isBlank()) {
                String blankMsg = "[UpdateChecker] Latest version is blank, skipping check";
                XposedBridge.log("[" + TAG + "] " + blankMsg);
                writeDebugLog(blankMsg);
                return;
            }

            var appInfo = mActivity.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            String currentVersion = normalizeVersion(appInfo.versionName);

            String versionMsg = "[UpdateChecker] Current installed: " + appInfo.versionName + " (normalized: " + currentVersion + ")";
            XposedBridge.log("[" + TAG + "] " + versionMsg);
            writeDebugLog(versionMsg);
            
            String latestMsg = "[UpdateChecker] Latest from GitHub: " + latestVersion;
            XposedBridge.log("[" + TAG + "] " + latestMsg);
            writeDebugLog(latestMsg);

            boolean isNewVersion;
            if (latestVersion.matches("(?i)[a-f0-9]{6,40}")) {
                isNewVersion = !appInfo.versionName.toLowerCase().contains(latestVersion.toLowerCase().trim());
                String hashMsg = "[UpdateChecker] Hash comparison mode - isNewVersion: " + isNewVersion;
                XposedBridge.log("[" + TAG + "] " + hashMsg);
                writeDebugLog(hashMsg);
            } else {
                // Compare GitHub version (latest) > Installed version (current)
                long githubVersionNum = versionToLong(latestVersion);   // GitHub release version
                long installedVersionNum = versionToLong(currentVersion); // Installed app version
                
                // Update is available if GitHub version > Installed version
                isNewVersion = (githubVersionNum > installedVersionNum);
                
                String compMsg = "[UpdateChecker] GitHub: '" + latestVersion + "'(" + githubVersionNum + ") vs Installed: '" + currentVersion + "'(" + installedVersionNum + ") → New version available: " + isNewVersion;
                XposedBridge.log("[" + TAG + "] " + compMsg);
                writeDebugLog(compMsg);
            }

            String ignored = WppCore.getPrivString("ignored_version", "");
            String ignoreMsg = "[UpdateChecker] Note: ignored_version preference exists but is not used (always show updates)";
            XposedBridge.log("[" + TAG + "] " + ignoreMsg);
            writeDebugLog(ignoreMsg);

            String decisionMsg = "[UpdateChecker] DECISION: isNewVersion=" + isNewVersion + " → SHOW_DIALOG=" + isNewVersion;
            XposedBridge.log("[" + TAG + "] " + decisionMsg);
            writeDebugLog(decisionMsg);

            if (isNewVersion) {
                String showMsg = "[UpdateChecker] ✓ Showing update dialog (new version detected)";
                XposedBridge.log("[" + TAG + "] " + showMsg);
                writeDebugLog(showMsg);

                final String finalLatestVersion = latestVersion;
                final String finalChangelog = changelog;
                final String finalPublishedAt = publishedAt;

                mActivity.runOnUiThread(() -> {
                    try {
                        showUpdateDialog(finalLatestVersion, finalChangelog, finalPublishedAt);
                    } catch (Exception e) {
                        String errorMsg = "[UpdateChecker] Error showing update dialog: " + e.getMessage();
                        XposedBridge.log("[" + TAG + "] " + errorMsg);
                        writeDebugLog(errorMsg);
                        e.printStackTrace();
                    }
                });
            } else {
                String noUpdateMsg = "[UpdateChecker] ✗ NOT showing dialog - Current version is already latest";
                XposedBridge.log("[" + TAG + "] " + noUpdateMsg);
                writeDebugLog(noUpdateMsg);
                if (isManualCheck) {
                    String manualMsg = "[UpdateChecker] Manual check: Already on latest version";
                    XposedBridge.log("[" + TAG + "] " + manualMsg);
                    writeDebugLog(manualMsg);
                    mActivity.runOnUiThread(this::showAlreadyLatestDialog);
                }
            }

        } catch (Exception e) {
            String errMsg = "[UpdateChecker] Exception: " + e.getMessage();
            XposedBridge.log("[" + TAG + "] " + errMsg);
            writeDebugLog(errMsg);
            e.printStackTrace();
        }
    }

    private void showAlreadyLatestDialog() {
        XposedBridge.log("[" + TAG + "] Attempting to show already latest dialog");
        try {
            var dialog = new AlertDialogWpp(mActivity);
            dialog.setTitle(mActivity.getString(ResId.string.error_detected));
            dialog.setMessage(mActivity.getString(ResId.string.already_have_latest));
            dialog.setPositiveButton(mActivity.getString(ResId.string.contact_developer), (dialog1, which) -> {
                Utils.openLink(mActivity, "https://t.me/mubashardev");
                dialog1.dismiss();
            });
            dialog.setNegativeButton(mActivity.getString(ResId.string.cancel), (dialog1, which) -> dialog1.dismiss());
            dialog.show();
            XposedBridge.log("[" + TAG + "] Already latest dialog shown successfully");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error showing already latest dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showUpdateDialog(String hash, String changelog, String publishedAt) {
        try {
            var markwon = Markwon.create(mActivity);
            var dialog = new AlertDialogWpp(mActivity);

            // Format the published date
            String formattedDate = formatPublishedDate(publishedAt);

            // Build simple message with version and date
            StringBuilder message = new StringBuilder();
            message.append("📦 **Version:** `").append(hash).append("`\n");
            if (!formattedDate.isEmpty()) {
                message.append("📅 **Released:** ").append(formattedDate).append("\n");
            }
            message.append("\n### What's New\n\n").append(changelog);

            dialog.setTitle("🎉 New Update Available!");
            dialog.setMessage(markwon.toMarkdown(message.toString()));
            dialog.setNegativeButton("Ignore", (dialog1, which) -> {
                // Just dismiss - don't store as ignored, so update check will show again next time
                dialog1.dismiss();
            });
            dialog.setPositiveButton("Download", (dialog1, which) -> {
                Utils.openLink(mActivity, TELEGRAM_UPDATE_URL);
                dialog1.dismiss();
            });
            dialog.show();

            XposedBridge.log("[" + TAG + "] Update dialog shown successfully");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Format ISO 8601 date to human-readable format
     * 
     * @param isoDate ISO 8601 date string (e.g., "2024-02-14T12:34:56Z")
     * @return Formatted date (e.g., "Feb 14, 2024" or "February 14, 2024")
     */
    private String formatPublishedDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "";
        }

        try {
            // Parse ISO 8601 date
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = isoFormat.parse(isoDate);

            if (date != null) {
                // Format to readable date
                SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                return displayFormat.format(date);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }

        return "";
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        String normalized = version.trim();
        String original = normalized;
        
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int plusIndex = normalized.indexOf('+');
        if (plusIndex >= 0) {
            normalized = normalized.substring(0, plusIndex);
        }
        normalized = normalized.trim();
        
        writeDebugLog("[normalizeVersion] '" + original + "' → '" + normalized + "'");
        return normalized;
    }

    private static int compareVersions(String v1, String v2) {
        // Convert versions to integers by removing dots
        // "1.5.7" -> 157, "1.5.6" -> 156
        long num1 = versionToLong(v1);
        long num2 = versionToLong(v2);
        
        if (num1 > num2) return 1;
        if (num1 < num2) return -1;
        return 0;
    }

    private static long versionToLong(String version) {
        // Remove all non-numeric characters and convert to long
        // "1.5.7" -> "157" -> 157L
        String normalized = normalizeVersion(version);
        String digitsOnly = normalized.replaceAll("[^0-9]", "");
        
        writeDebugLog("[versionToLong] Input: '" + version + "' → Normalized: '" + normalized + "' → Digits: '" + digitsOnly + "'");
        
        try {
            long result = digitsOnly.isEmpty() ? 0L : Long.parseLong(digitsOnly);
            writeDebugLog("[versionToLong] Parsed to: " + result);
            return result;
        } catch (NumberFormatException e) {
            writeDebugLog("[versionToLong] Failed to parse version '" + version + "' as number: " + e.getMessage());
            return 0L;
        }
    }
}
