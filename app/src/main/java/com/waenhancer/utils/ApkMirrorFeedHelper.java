package com.waenhancer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.waenhancer.xposed.core.FeatureLoader;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApkMirrorFeedHelper {
    private static final String TAG = "WAE_ApkMirrorFeedHelper";
    
    private static final String FEED_WPP = "https://www.apkmirror.com/apk/whatsapp-inc/whatsapp-messenger/feed/";
    private static final String FEED_BUSINESS = "https://www.apkmirror.com/apk/whatsapp-inc/whatsapp-messenger-business/feed/";
    
    private static final String PREF_LAST_FETCH = "apkmirror_last_fetch_time";
    private static final String PREF_BETA_WPP = "apkmirror_beta_versions_wpp";
    private static final String PREF_BETA_BUSINESS = "apkmirror_beta_versions_business";
    
    private static final Pattern TITLE_TAG_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "WhatsApp\\s+(Messenger|Business)\\s+([0-9.]+)(?:\\s+(beta))?\\s+by\\s+WhatsApp\\s+LLC", 
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Asynchronously fetches stable and beta versions of WhatsApp and WhatsApp Business from APKMirror using a headless WebView.
     * Caches them inside SharedPreferences for 24 hours.
     */
    public static void fetchVersionsIfNeeded(Context context, Runnable onComplete) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("ApkMirrorCache", Context.MODE_PRIVATE);
                long lastFetch = prefs.getLong(PREF_LAST_FETCH, 0L);
                long now = System.currentTimeMillis();
                
                boolean hasCachedWpp = prefs.getStringSet(PREF_BETA_WPP, null) != null && !prefs.getStringSet(PREF_BETA_WPP, new HashSet<>()).isEmpty();
                boolean hasCachedBusiness = prefs.getStringSet(PREF_BETA_BUSINESS, null) != null && !prefs.getStringSet(PREF_BETA_BUSINESS, new HashSet<>()).isEmpty();
                
                if (now - lastFetch < TimeUnit.HOURS.toMillis(24) && hasCachedWpp && hasCachedBusiness) {
                    Log.d(TAG, "Using cached APKMirror feed versions");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                }

                Log.d(TAG, "Fetching latest versions from APKMirror feeds via direct headless WebView...");
                
                Set<String> betaWpp = new HashSet<>();
                Set<String> betaBusiness = new HashSet<>();

                fetchFeedViaWebView(context, FEED_WPP, betaWpp, () -> {
                    fetchFeedViaWebView(context, FEED_BUSINESS, betaBusiness, () -> {
                        saveCacheAndComplete(prefs, betaWpp, betaBusiness, now, onComplete);
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in fetchVersionsIfNeeded: " + e.getMessage(), e);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private static void saveCacheAndComplete(SharedPreferences prefs, Set<String> betaWpp, Set<String> betaBusiness, long now, Runnable onComplete) {
        if (!betaWpp.isEmpty() && !betaBusiness.isEmpty()) {
            prefs.edit()
                    .putLong(PREF_LAST_FETCH, now)
                    .putStringSet(PREF_BETA_WPP, betaWpp)
                    .putStringSet(PREF_BETA_BUSINESS, betaBusiness)
                    .apply();
            Log.d(TAG, "Successfully updated APKMirror feed cache.");
            Log.i(TAG, "Parsed WhatsApp Messenger Beta versions: " + betaWpp.toString());
            Log.i(TAG, "Parsed WhatsApp Business Beta versions: " + betaBusiness.toString());
        } else {
            Log.w(TAG, "Fetched beta versions were empty. Skipping caching to force retry on next run.");
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    /**
     * Asynchronously fetches the APKMirror feed ONLY for the specified package name (WhatsApp or WhatsApp Business).
     * This avoids redundant checking when a user only launches one application.
     */
    public static void fetchVersionsIfNeededForPackage(Context context, String packageName, Runnable onComplete) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String feedUrl;
                String prefBetaKey;
                String prefLastFetchKey;

                if (FeatureLoader.PACKAGE_WPP.equals(packageName)) {
                    feedUrl = FEED_WPP;
                    prefBetaKey = PREF_BETA_WPP;
                    prefLastFetchKey = PREF_LAST_FETCH + "_wpp";
                } else if (FeatureLoader.PACKAGE_BUSINESS.equals(packageName)) {
                    feedUrl = FEED_BUSINESS;
                    prefBetaKey = PREF_BETA_BUSINESS;
                    prefLastFetchKey = PREF_LAST_FETCH + "_business";
                } else {
                    return; // Unknown package
                }

                SharedPreferences prefs = context.getSharedPreferences("ApkMirrorCache", Context.MODE_PRIVATE);
                long lastFetch = prefs.getLong(prefLastFetchKey, 0L);
                long now = System.currentTimeMillis();
                
                boolean hasCached = prefs.getStringSet(prefBetaKey, null) != null && !prefs.getStringSet(prefBetaKey, new HashSet<>()).isEmpty();
                
                if (now - lastFetch < TimeUnit.HOURS.toMillis(24) && hasCached) {
                    Log.d(TAG, "Using cached APKMirror feed versions for package " + packageName);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                }

                Log.d(TAG, "Fetching latest versions from APKMirror feed via direct headless WebView for package " + packageName + "...");
                Set<String> betaVersions = new HashSet<>();

                fetchFeedViaWebView(context, feedUrl, betaVersions, () -> {
                    savePackageCacheAndComplete(prefs, prefBetaKey, prefLastFetchKey, betaVersions, packageName, now, onComplete);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in fetchVersionsIfNeededForPackage: " + e.getMessage(), e);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private static void savePackageCacheAndComplete(SharedPreferences prefs, String prefBetaKey, String prefLastFetchKey, Set<String> betaVersions, String packageName, long now, Runnable onComplete) {
        if (!betaVersions.isEmpty()) {
            prefs.edit()
                    .putLong(prefLastFetchKey, now)
                    .putStringSet(prefBetaKey, betaVersions)
                    .apply();
            Log.d(TAG, "Successfully updated APKMirror feed cache for " + packageName + ".");
            Log.i(TAG, "Parsed beta versions for package " + packageName + ": " + betaVersions.toString());
        } else {
            Log.w(TAG, "Fetched package beta versions were empty. Skipping cache update to force retry on next run.");
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private static void fetchFeedViaWebView(Context context, String url, Set<String> betaVersions, Runnable onDone) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                android.webkit.WebView webView = new android.webkit.WebView(context.getApplicationContext());
                android.webkit.WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setUserAgentString("APKUpdater-v3.0.3");
                
                webView.setWebViewClient(new android.webkit.WebViewClient() {
                    private boolean finished = false;

                    @Override
                    public void onPageFinished(android.webkit.WebView view, String url) {
                        if (finished) return;
                        finished = true;
                        
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            view.evaluateJavascript(
                                "(function() { return document.documentElement.outerHTML; })();",
                                html -> {
                                    try {
                                        if (html != null) {
                                            String unescaped = unescapeJsonString(html);
                                            parseXmlFeed(unescaped, betaVersions);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing WebView HTML: " + e.getMessage(), e);
                                    } finally {
                                        view.destroy();
                                        onDone.run();
                                    }
                                }
                            );
                        }, 2500);
                    }

                    @Override
                    public void onReceivedError(android.webkit.WebView view, int errorCode, String description, String failingUrl) {
                        Log.w(TAG, "WebView failed to load URL: " + failingUrl + " - error: " + description);
                        view.destroy();
                        onDone.run();
                    }
                });
                
                webView.loadUrl(url);
            } catch (Exception e) {
                Log.e(TAG, "WebView execution failed: " + e.getMessage(), e);
                onDone.run();
            }
        });
    }

    private static String unescapeJsonString(String jsonStr) {
        if (jsonStr == null || jsonStr.length() < 2) {
            return jsonStr;
        }
        if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
            jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
        }
        
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < jsonStr.length()) {
            char c = jsonStr.charAt(i);
            if (c == '\\' && i + 1 < jsonStr.length()) {
                char next = jsonStr.charAt(i + 1);
                if (next == 'u' && i + 5 < jsonStr.length()) {
                    String hex = jsonStr.substring(i + 2, i + 6);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 6;
                    } catch (NumberFormatException e) {
                        sb.append(c);
                        i++;
                    }
                } else if (next == 'n') {
                    sb.append('\n');
                    i += 2;
                } else if (next == 'r') {
                    sb.append('\r');
                    i += 2;
                } else if (next == 't') {
                    sb.append('\t');
                    i += 2;
                } else if (next == '"' || next == '\'' || next == '\\' || next == '/') {
                    sb.append(next);
                    i += 2;
                } else {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static void parseXmlFeed(String xmlContent, Set<String> betaVersions) {
        Set<String> localBeta = new java.util.HashSet<>();
        Set<String> localStable = new java.util.HashSet<>();

        Matcher titleMatcher = TITLE_TAG_PATTERN.matcher(xmlContent);
        while (titleMatcher.find()) {
            String rawTitle = titleMatcher.group(1);
            if (rawTitle != null) {
                String cleanTitle = rawTitle.replaceAll("&lt;", "<")
                        .replaceAll("&gt;", ">")
                        .replaceAll("&amp;", "&")
                        .trim();

                Matcher verMatcher = VERSION_PATTERN.matcher(cleanTitle);
                if (verMatcher.find()) {
                    String version = verMatcher.group(2);
                    if (version != null) {
                        version = version.trim();
                        boolean isBeta = verMatcher.group(3) != null || cleanTitle.toLowerCase().contains("beta");
                        if (isBeta) {
                            localBeta.add(version);
                        } else {
                            localStable.add(version);
                        }
                    }
                }
            }
        }

        // Only mark as beta if the version is NOT present in the stable list
        localBeta.removeAll(localStable);
        betaVersions.addAll(localBeta);
    }

    /**
     * Checks if the installed version of an app is identified as a beta version.
     */
    public static boolean isBetaVersion(Context context, String packageName, String versionName) {
        if (versionName == null || versionName.isEmpty()) {
            return false;
        }

        String cleanVersion = versionName.trim();
        int dashIndex = cleanVersion.indexOf('-');
        if (dashIndex > 0) {
            cleanVersion = cleanVersion.substring(0, dashIndex).trim();
        }


        if (versionName.toLowerCase().contains("beta")) {
            return true;
        }

        SharedPreferences prefs = context.getSharedPreferences("ApkMirrorCache", Context.MODE_PRIVATE);
        Set<String> betaVersions = null;

        if (FeatureLoader.PACKAGE_WPP.equals(packageName)) {
            betaVersions = prefs.getStringSet(PREF_BETA_WPP, null);
        } else if (FeatureLoader.PACKAGE_BUSINESS.equals(packageName)) {
            betaVersions = prefs.getStringSet(PREF_BETA_BUSINESS, null);
        }

        if (betaVersions != null && (betaVersions.contains(cleanVersion) || betaVersions.contains(versionName))) {
            Log.d(TAG, "Version " + versionName + " matches a known beta version from APKMirror for package " + packageName);
            return true;
        }

        return false;
    }
}
