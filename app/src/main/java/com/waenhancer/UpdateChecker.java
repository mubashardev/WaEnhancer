package com.waenhancer;

import android.app.Activity;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.utils.ResId;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;
import io.noties.markwon.Markwon;
import okhttp3.OkHttpClient;

public class UpdateChecker implements Runnable {

    private static final String TAG = "WAE_UpdateChecker";
    private static final String RELEASES_API = "https://api.github.com/repos/mubashardev/WaEnhancer/releases";
    private static final String RELEASE_TAG_PREFIX = "debug-";
    private static final String TELEGRAM_UPDATE_URL = "https://github.com/mubashardev/WaEnhancer/releases";
    private static final Pattern BETA_TAG_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+-beta-\\d+$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-beta-\\d+)?$");

    private static OkHttpClient httpClient;

    private final Activity mActivity;
    private final boolean isManualCheck;
    private OnUpdateFoundListener mListener;
    private boolean mSilent = false;

    public interface OnUpdateFoundListener {
        void onUpdateFound(String version, String tagName, String changelog, String publishedAt, String downloadUrl);
    }

    public void setOnUpdateFoundListener(OnUpdateFoundListener listener) {
        this.mListener = listener;
    }

    public void setSilent(boolean silent) {
        this.mSilent = silent;
    }

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
        this(activity, false);
    }

    public UpdateChecker(Activity activity, boolean isManualCheck) {
        this.mActivity = activity;
        this.isManualCheck = isManualCheck;
    }

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
            WppCore.setPrivString("ignored_version", "");

            var request = new okhttp3.Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WaEnhancer-UpdateChecker")
                    .build();

            var appInfo = mActivity.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            String installedVersion = normalizeVersion(appInfo.versionName);
            boolean installedIsBeta = isInstalledVersionBeta(installedVersion);
            String userChannel = getReleaseChannelPreference();

            // Use the user's selected channel as the effective channel.
            String effectiveChannel = userChannel;

            String selectedVersion = null;
            String selectedTagName = null;
            String selectedChangelog = null;
            String selectedPublishedAt = null;
            String selectedDownloadUrl = null;

            try (var response = getHttpClient().newCall(request).execute()) {
                writeDebugLog("[UpdateChecker] Checking for updates... Installed: " + installedVersion + ", Channel: " + effectiveChannel);
                if (!response.isSuccessful()) {
                    writeDebugLog("[UpdateChecker] API call failed with code: " + response.code());
                    return;
                }

                var body = response.body();
                if (body == null) {
                    writeDebugLog("[UpdateChecker] Update check failed: Empty response body");
                    return;
                }

                JSONArray releases = new JSONArray(body.string());
                long installedVersionNum = versionToLong(installedVersion);

                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null) continue;

                    String tagName = release.optString("tag_name", "").trim();
                    if (tagName.isEmpty()) continue;

                    String parsedVersion;
                    if (tagName.startsWith(RELEASE_TAG_PREFIX)) {
                        parsedVersion = normalizeVersion(tagName.substring(RELEASE_TAG_PREFIX.length()).trim());
                    } else {
                        parsedVersion = normalizeVersion(tagName);
                    }

                    if (parsedVersion.isEmpty()) continue;
                    if (!VERSION_PATTERN.matcher(parsedVersion).matches()) continue;
                    if (!shouldShowReleaseType(parsedVersion, effectiveChannel)) continue;

                    long releaseVersionNum = versionToLong(parsedVersion);
                    if (releaseVersionNum > installedVersionNum) {
                        selectedVersion = parsedVersion;
                        selectedTagName = tagName;
                        selectedChangelog = release.optString("body", "No changelog available.").trim();
                        selectedPublishedAt = release.optString("published_at", "");
                        
                        // Extract APK download URL
                        JSONArray assets = release.optJSONArray("assets");
                        if (assets != null) {
                            for (int j = 0; j < assets.length(); j++) {
                                JSONObject asset = assets.optJSONObject(j);
                                if (asset != null) {
                                    String assetName = asset.optString("name", "");
                                    if (assetName.endsWith(".apk")) {
                                        selectedDownloadUrl = asset.optString("browser_download_url", "");
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
            }

            if (selectedVersion != null) {
                final String finalVersion = selectedVersion;
                final String finalTagName = selectedTagName;
                final String finalChangelog = selectedChangelog;
                final String finalPublishedAt = selectedPublishedAt;
                final String finalDownloadUrl = selectedDownloadUrl;

                if (mListener != null) {
                    mActivity.runOnUiThread(() -> mListener.onUpdateFound(finalVersion, finalTagName, finalChangelog, finalPublishedAt, finalDownloadUrl));
                }

                if (!mSilent) {
                    mActivity.runOnUiThread(() -> showUpdateDialog(finalVersion, finalTagName, finalChangelog, finalPublishedAt, finalDownloadUrl));
                }
            } else if (isManualCheck) {
                mActivity.runOnUiThread(this::showAlreadyLatestDialog);
            }
        } catch (Exception e) {
            String errMsg = "[UpdateChecker] Exception: " + e.getMessage();
            XposedBridge.log("[" + TAG + "] " + errMsg);
            writeDebugLog(errMsg);
        }
    }

    private void showAlreadyLatestDialog() {
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
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error showing already latest dialog: " + e.getMessage());
        }
    }

    private void showUpdateDialog(String version, String tagName, String changelog, String publishedAt, String downloadUrl) {
        try {
            var markwon = Markwon.create(mActivity);
            var dialog = new AlertDialogWpp(mActivity);

            String releaseTypeBadge = getReleaseTypeBadge(tagName);
            String formattedDate = formatPublishedDate(publishedAt);

            StringBuilder message = new StringBuilder();
            message.append("📦 **Version:** `").append(version).append("`\n");
            if (!formattedDate.isEmpty()) {
                message.append("📅 **Released:** ").append(formattedDate).append("\n");
            }
            message.append("\n### What's New\n\n").append(changelog);

            dialog.setTitle(releaseTypeBadge + " New Update Available!");
            dialog.setMessage(markwon.toMarkdown(message.toString()));
            dialog.setNegativeButton("Ignore", (dialog1, which) -> dialog1.dismiss());
            dialog.setNeutralButton("View Changelog", (dialog1, which) -> {
                android.content.Intent intent = new android.content.Intent(mActivity, com.waenhancer.activities.ChangelogActivity.class);
                mActivity.startActivity(intent);
                dialog1.dismiss();
            });
            dialog.setPositiveButton("Download", (dialog1, which) -> {
                if (downloadUrl != null && !downloadUrl.isEmpty()) {
                    // Start in-app download
                    UpdateDownloader.showDownloadDialog(mActivity, downloadUrl, version);
                } else {
                    Utils.openLink(mActivity, TELEGRAM_UPDATE_URL);
                }
                dialog1.dismiss();
            });
            dialog.show();
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error showing update dialog: " + e.getMessage());
        }
    }

    private String formatPublishedDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = isoFormat.parse(isoDate);
            if (date != null) {
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

    private static long versionToLong(String version) {
        String normalized = normalizeVersion(version);

        String base = normalized;
        int betaNum = 0;
        boolean isBeta = false;

        int betaIndex = normalized.indexOf("-beta-");
        if (betaIndex > 0) {
            isBeta = true;
            base = normalized.substring(0, betaIndex);
            String betaPart = normalized.substring(betaIndex + 6).trim();
            try {
                betaNum = Integer.parseInt(betaPart);
            } catch (NumberFormatException ignored) {
                betaNum = 1;
            }
        }

        long major = 0;
        long minor = 0;
        long patch = 0;
        try {
            String[] parts = base.split("\\.");
            if (parts.length > 0) major = Long.parseLong(parts[0]);
            if (parts.length > 1) minor = Long.parseLong(parts[1]);
            if (parts.length > 2) patch = Long.parseLong(parts[2]);
        } catch (Exception e) {
            return 0L;
        }

        long baseCode = major * 1_000_000L + minor * 1_000L + patch;
        if (!isBeta) {
            return baseCode * 1_000L + 999L;
        }

        long safeBeta = Math.max(1, Math.min(betaNum, 998));
        return baseCode * 1_000L + safeBeta;
    }

    private boolean isInstalledVersionBeta(String versionName) {
        return versionName != null && versionName.contains("-beta-");
    }

    private String getReleaseChannelPreference() {
        String channel = WppCore.getPrivString("release_channel", null);
        if (channel == null) {
            // Fallback to standard app preferences if WppCore is not initialized (e.g. running in Enhancer App)
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(mActivity);
            channel = prefs.getString("release_channel", "stable");
        }
        return "beta".equals(channel) ? "beta" : "stable";
    }

    private boolean isExactBetaTagFormat(String tagName) {
        return tagName != null && BETA_TAG_PATTERN.matcher(tagName).matches();
    }

    private boolean shouldShowReleaseType(String releaseTagName, String userChannel) {
        boolean isBetaRelease = releaseTagName != null && releaseTagName.contains("-beta-");
        boolean userWantsBeta = "beta".equals(userChannel);
        if (userWantsBeta) {
            return true;
        }
        return !isBetaRelease;
    }

    private String getReleaseTypeBadge(String releaseTagName) {
        if (releaseTagName != null && releaseTagName.contains("-beta-")) {
            return "🧪 BETA";
        }
        return "⭐ STABLE";
    }
}
