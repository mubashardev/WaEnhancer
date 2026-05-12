package com.waenhancer;
import com.waenhancer.ui.helpers.BottomSheetHelper;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.R;
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
    private static final String TELEGRAM_UPDATE_URL = "https://github.com/mubashardev/WaEnhancerX/releases";
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
        if (!Utils.DEBUG) return;
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
            var requestBuilder = new okhttp3.Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WaEnhancer X-UpdateChecker");

            if (BuildConfig.GH_PUBLIC_TOKEN != null && !BuildConfig.GH_PUBLIC_TOKEN.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + BuildConfig.GH_PUBLIC_TOKEN);
            }

            var request = requestBuilder.build();

            String installedVersion = normalizeVersion(com.waenhancer.BuildConfig.VERSION_NAME);
            writeDebugLog("[UpdateChecker] run() - Installed Version: " + installedVersion);
            
            // Check if there is an ignored version and if we should skip based on frequency
            String ignoredVersion = getPrefs().getString("ignored_version", "");
            if (!ignoredVersion.isEmpty()) {
                long ignoredTimestamp = getPrefs().getLong("ignored_timestamp", 0);
                String frequency = getPrefs().getString("update_alert_frequency", "restart");
                
                if (frequency.equals("never")) {
                    writeDebugLog("[UpdateChecker] Skipping update check: frequency is 'never'");
                    return;
                }
                
                if (frequency.equals("restart")) {
                    // If 'restart' is selected, and we already ignored it in a previous check, 
                    // we keep it ignored for this session. 
                    // The logic here assumes UpdateChecker is instantiated per session or logic is simple.
                    // Actually, if it's 'restart', we should show it if ignoredTimestamp is from a PREVIOUS session.
                    // But we don't track sessions easily. Let's stick to simple logic:
                    // 'restart' means show once per WhatsApp launch. 
                    // Since WAE hooks usually run once per launch, we can use a static flag.
                } else {
                    long currentTime = System.currentTimeMillis();
                    long diffMillis = currentTime - ignoredTimestamp;
                    long requiredMillis = 0;
                    
                    switch (frequency) {
                        case "1h": requiredMillis = TimeUnit.HOURS.toMillis(1); break;
                        case "12h": requiredMillis = TimeUnit.HOURS.toMillis(12); break;
                        case "24h": requiredMillis = TimeUnit.HOURS.toMillis(24); break;
                    }
                    
                    if (diffMillis < requiredMillis) {
                        writeDebugLog("[UpdateChecker] Skipping update check: frequency " + frequency + " not reached yet");
                        return;
                    }
                }
            }

            boolean installedIsBeta = isInstalledVersionBeta(installedVersion);
            String updateAlertPref = getUpdateAlertPreference();

            // Use the user's selected preference for filtering
            String effectiveChannel = updateAlertPref;

            String selectedVersion = null;
            String selectedTagName = null;
            String selectedChangelog = null;
            String selectedPublishedAt = null;
            String selectedDownloadUrl = null;

            writeDebugLog("[UpdateChecker] Requesting: " + request.url());
            try (var response = getHttpClient().newCall(request).execute()) {
                writeDebugLog("[UpdateChecker] Checking... Installed: " + installedVersion + ", Channel: " + effectiveChannel);
                if (!response.isSuccessful()) {
                    writeDebugLog("[UpdateChecker] API call failed: " + response.code() + " " + response.message());
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

                writeDebugLog("[UpdateChecker] Found update: " + finalVersion + " (" + finalTagName + ")");
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
            boolean isXposed = !BuildConfig.APPLICATION_ID.equals(mActivity.getPackageName());
            String title = mActivity.getString(R.string.error_detected);
            String message = mActivity.getString(R.string.already_have_latest);
            String contactText = mActivity.getString(R.string.contact_developer);

            if (!isXposed) {
                BottomSheetHelper.showConfirmation(mActivity, title, message, contactText, false, () -> {
                    Utils.openLink(mActivity, "https://t.me/mubashardev");
                });
            } else {
                var dialog = new AlertDialogWpp(mActivity);
                dialog.setTitle(title);
                dialog.setMessage(message);
                dialog.setPositiveButton(contactText, (dialog1, which) -> {
                    Utils.openLink(mActivity, "https://t.me/mubashardev");
                    dialog1.dismiss();
                });
                dialog.setNegativeButton(mActivity.getString(R.string.cancel), (dialog1, which) -> dialog1.dismiss());
                dialog.show();
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error showing already latest dialog: " + e.getMessage());
        }
    }

    private void showUpdateDialog(String version, String tagName, String changelog, String publishedAt, String downloadUrl) {
        try {
            var markwon = Markwon.create(mActivity);
            String releaseTypeBadge = getReleaseTypeBadge(tagName);
            String formattedDate = formatPublishedDate(publishedAt);

            StringBuilder message = new StringBuilder();
            message.append("📦 **Version:** `").append(version).append("`\n");
            if (!formattedDate.isEmpty()) {
                message.append("📅 **Released:** ").append(formattedDate).append("\n");
            }
            message.append("\n### What's New\n\n").append(changelog);

            String title = releaseTypeBadge + " New Update Available!";
            CharSequence styledMessage = markwon.toMarkdown(message.toString());
            boolean isXposed = !BuildConfig.APPLICATION_ID.equals(mActivity.getPackageName());

            if (!isXposed) {
                BottomSheetHelper.showConfirmation(mActivity, title, styledMessage, "Update Now", false, () -> {
                    android.content.Intent intent = new android.content.Intent();
                    intent.setComponent(new android.content.ComponentName("com.waenhancer", "com.waenhancer.activities.ChangelogActivity"));
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(intent);
                });
            } else {
                var dialog = new AlertDialogWpp(mActivity);
                dialog.setTitle(title);
                dialog.setMessage(styledMessage);
                dialog.setNegativeButton("Ignore", (dialog1, which) -> {
                    getPrefs().edit()
                        .putString("ignored_version", version)
                        .putLong("ignored_timestamp", System.currentTimeMillis())
                        .apply();
                    
                    String freq = getPrefs().getString("update_alert_frequency", "restart");
                    String freqDisplay = freq;
                    switch (freq) {
                        case "restart": freqDisplay = getModuleString(R.string.update_freq_restart); break;
                        case "1h": freqDisplay = getModuleString(R.string.update_freq_1h); break;
                        case "12h": freqDisplay = getModuleString(R.string.update_freq_12h); break;
                        case "24h": freqDisplay = getModuleString(R.string.update_freq_24h); break;
                        case "never": freqDisplay = getModuleString(R.string.update_freq_never); break;
                    }
                    
                    Toast.makeText(mActivity, String.format(getModuleString(R.string.update_ignored_toast), version, freqDisplay), Toast.LENGTH_LONG).show();
                    dialog1.dismiss();
                });
                dialog.setPositiveButton("Update Now", (dialog1, which) -> {
                    // Clear ignored state if updating
                    getPrefs().edit().putString("ignored_version", "").apply();
                    
                    android.content.Intent intent = new android.content.Intent();
                    intent.setComponent(new android.content.ComponentName("com.waenhancer", "com.waenhancer.activities.ChangelogActivity"));
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(intent);
                    dialog1.dismiss();
                });
                dialog.show();
            }
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

    private String getUpdateAlertPreference() {
        // First try to get it from WaEnhancer's XSharedPreferences (available in Xposed context)
        if (com.waenhancer.xposed.core.WppCore.waePrefs != null) {
            if (com.waenhancer.xposed.core.WppCore.waePrefs instanceof de.robv.android.xposed.XSharedPreferences) {
                ((de.robv.android.xposed.XSharedPreferences) com.waenhancer.xposed.core.WppCore.waePrefs).reload();
            }
            String pref = com.waenhancer.xposed.core.WppCore.waePrefs.getString("update_alert_pref", null);
            if (pref == null) {
                // Fallback to legacy channel key if new one isn't set
                String legacy = com.waenhancer.xposed.core.WppCore.waePrefs.getString("release_channel", "stable");
                pref = "beta".equals(legacy) ? "both" : "stable";
            }
            writeDebugLog("[UpdateChecker] Alert pref from waePrefs: " + pref);
            return pref;
        }

        // Fallback to WppCore's WaGlobal prefs (legacy/other contexts)
        String pref = com.waenhancer.xposed.core.WppCore.getPrivString("update_alert_pref", null);
        if (pref != null) {
             writeDebugLog("[UpdateChecker] Alert pref from getPrivString: " + pref);
             return pref;
        }

        // Fallback to default prefs (running in Enhancer App context)
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(mActivity);
        String defaultPref = prefs.getString("update_alert_pref", "both");
        writeDebugLog("[UpdateChecker] Alert pref from default prefs: " + defaultPref);
        return defaultPref;
    }

    private boolean isExactBetaTagFormat(String tagName) {
        return tagName != null && BETA_TAG_PATTERN.matcher(tagName).matches();
    }

    private boolean shouldShowReleaseType(String releaseTagName, String updateAlertPref) {
        boolean isBetaRelease = releaseTagName != null && releaseTagName.contains("-beta-");
        
        switch (updateAlertPref) {
            case "stable":
                return !isBetaRelease;
            case "beta":
                return isBetaRelease;
            case "both":
            default:
                return true;
        }
    }

    private String getReleaseTypeBadge(String releaseTagName) {
        if (releaseTagName != null && releaseTagName.contains("-beta-")) {
            return "🧪 BETA";
        }
        return "⭐ STABLE";
    }

    private String getModuleString(int resId) {
        String s = com.waenhancer.xposed.core.FeatureLoader.getModuleString(resId);
        if (s == null || s.isEmpty()) {
            try {
                android.content.Context moduleContext = mActivity.createPackageContext("com.waenhancer", android.content.Context.CONTEXT_IGNORE_SECURITY);
                return moduleContext.getString(resId);
            } catch (Exception e) {
                // Fallback to hardcoded English if everything fails to prevent crash
                if (resId == R.string.update_freq_restart) return "will be shown after WhatsApp Restart";
                if (resId == R.string.update_freq_1h) return "will be shown after 1 hour";
                if (resId == R.string.update_freq_12h) return "will be shown after 12 hours";
                if (resId == R.string.update_freq_24h) return "will be shown after 24 hours";
                if (resId == R.string.update_freq_never) return "will never be shown";
                if (resId == R.string.update_ignored_toast) return "Next update alert for v%1$s %2$s";
                return "Unknown Resource";
            }
        }
        return s;
    }
    private android.content.SharedPreferences getPrefs() {
        if (WppCore.waePrefs != null) return WppCore.waePrefs;
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(mActivity);
    }
}
