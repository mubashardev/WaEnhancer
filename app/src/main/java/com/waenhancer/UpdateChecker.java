package com.waenhancer;

import android.app.Activity;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.ResId;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONObject;

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
            XposedBridge.log("[" + TAG + "] Starting update check...");


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
                    return;
                }

                var body = response.body();
                if (body == null) {
                    XposedBridge.log("[" + TAG + "] Update check failed: Empty response body");
                    return;
                }


                var content = body.string();
                var release = new JSONObject(content);
                var tagName = release.optString("tag_name", "").trim();

                XposedBridge.log("[" + TAG + "] Latest release tag: " + tagName);

                if (tagName.isBlank()) {
                    return;
                }

                if (tagName.startsWith(RELEASE_TAG_PREFIX)) {
                    latestVersion = tagName.substring(RELEASE_TAG_PREFIX.length()).trim();
                } else {
                    latestVersion = normalizeVersion(tagName);
                }
                changelog = release.optString("body", "No changelog available.").trim();
                publishedAt = release.optString("published_at", "");

                XposedBridge.log("[" + TAG + "] Parsed latest version: " + latestVersion + ", published: " + publishedAt);

            }

            if (latestVersion == null || latestVersion.isBlank()) {
                return;
            }

            var appInfo = mActivity.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            String currentVersion = normalizeVersion(appInfo.versionName);

            boolean isNewVersion;
            if (latestVersion.matches("(?i)[a-f0-9]{6,40}")) {
                isNewVersion = !appInfo.versionName.toLowerCase().contains(latestVersion.toLowerCase().trim());
            } else {
                isNewVersion = compareVersions(latestVersion, currentVersion) > 0;
            }

            String ignored = WppCore.getPrivString("ignored_version", "");
            boolean isIgnored = Objects.equals(ignored, latestVersion)
                    || Objects.equals(ignored, appInfo.versionName)
                    || Objects.equals(ignored, normalizeVersion(ignored));

            if (isNewVersion && !isIgnored) {
                XposedBridge.log("[" + TAG + "] New version available, showing dialog");


                final String finalLatestVersion = latestVersion;
                final String finalChangelog = changelog;
                final String finalPublishedAt = publishedAt;

                mActivity.runOnUiThread(() -> {
                    showUpdateDialog(finalLatestVersion, finalChangelog, finalPublishedAt);
                });
            } else {
                XposedBridge.log(
                        "[" + TAG + "] No update needed (isNew=" + isNewVersion + ", isIgnored=" + isIgnored + ")");
                if (isManualCheck) {
                    XposedBridge.log("[" + TAG + "] Manual check: Already on latest version");
                    mActivity.runOnUiThread(this::showAlreadyLatestDialog);
                }
            }

        } catch (Exception e) {
            XposedBridge.log(e);
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
                WppCore.setPrivString("ignored_version", hash);
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
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int plusIndex = normalized.indexOf('+');
        if (plusIndex >= 0) {
            normalized = normalized.substring(0, plusIndex);
        }
        return normalized.trim();
    }

    private static int compareVersions(String v1, String v2) {
        String[] a = normalizeVersion(v1).split("\\.");
        String[] b = normalizeVersion(v2).split("\\.");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int ai = i < a.length ? safeParseInt(a[i]) : 0;
            int bi = i < b.length ? safeParseInt(b[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
