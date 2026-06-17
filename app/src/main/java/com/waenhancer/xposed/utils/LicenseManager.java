package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles communication with the Cloudflare Worker API to verify licensing keys.
 * Also implements a silent startup re-verification to detect server-side revocations
 * (e.g., device unlink, expiration, or manual key invalidation).
 */
public class LicenseManager {

    private static final String TAG = "LicenseManager";
    private static final String API_LINK_URL = Config.LINK_ENDPOINT;
    private static final String API_VERIFY_URL = Config.VERIFY_ENDPOINT;
    private static final String API_UNLINK_URL = Config.UNLINK_ENDPOINT;

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LicenseCallback {
        void onSuccess(String encryptedConfig);
        void onError(String message);
    }

    /**
     * Callback for unlink operations.
     */
    public interface UnlinkCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * Optional listener for silent check completion, allowing UI refresh after
     * a license revocation is detected at startup.
     */
    public interface SilentCheckListener {
        void onStatusChanged();
    }

    /**
     * Verifies the provided license key against the remote verification server.
     * Executes the network request on a background thread and returns results via callbacks
     * marshalled back to the main UI thread.
     *
     * @param context    The Android context.
     * @param licenseKey The user's input license key.
     * @param callback   The callback to receive success or error notification.
     */
    /**
     * Checks if the given license key matches the expected pattern: WAEX-XXXX-XXXX-XXXX
     * where each 'X' is an alphanumeric uppercase/lowercase character.
     * Normalizes the pattern comparison by treating it as case-insensitive.
     *
     * @param licenseKey The license key to validate.
     * @return true if valid, false otherwise.
     */
    public static boolean isValidLicensePattern(String licenseKey) {
        if (licenseKey == null) {
            return false;
        }
        // Normalize spaces and convert to upper case for standard pattern check
        String normalizedKey = licenseKey.trim().toUpperCase();
        return normalizedKey.matches("^WAEX-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$");
    }

    /**
     * Verifies the provided license key against the remote verification server.
     * Executes the network request on a background thread and returns results via callbacks
     * marshalled back to the main UI thread.
     *
     * @param context    The Android context.
     * @param licenseKey The user's input license key.
     * @param callback   The callback to receive success or error notification.
     */
    public static void verifyLicense(final Context context, final String licenseKey, final LicenseCallback callback) {
        if (callback == null) {
            return;
        }

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            callback.onError("License key cannot be empty.");
            return;
        }

        final String normalizedKey = licenseKey.trim().toUpperCase();
        if (!isValidLicensePattern(normalizedKey)) {
            callback.onError("Invalid license key format. Expected format: WAEX-XXXX-XXXX-XXXX");
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Ensure RSA Keypair exists in hardware Keystore
                KeystoreHelper.generateRSAKeyPair();

                // Retrieve Device Public Key in Base64 format
                final String devicePubKey = KeystoreHelper.getPublicKeyBase64();
                if (devicePubKey == null) {
                    postError(callback, "Hardware keystore access failed: public key not available.");
                    return;
                }

                // Construct request payload
                JSONObject payload = new JSONObject();
                payload.put("license_key", normalizedKey);
                payload.put("device_pub_key", devicePubKey);
                payload.put("device_info", Build.MANUFACTURER + " " + Build.MODEL);

                // Attach device's hardware-backed signature to verify cryptographic ownership
                String signature = KeystoreHelper.signData(normalizedKey);
                payload.put("device_signature", signature != null ? signature : "");

                String versionName = "";
                try {
                    versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                } catch (Exception ignored) {}
                if (versionName == null) versionName = "";
                String encryptedVn = "";
                try {
                    Class<?> secClazz = Class.forName("com.waenhancer.pro.utils.SecurityNative");
                    encryptedVn = (String) secClazz.getMethod("encryptVersionName", String.class).invoke(null, versionName);
                } catch (Throwable t) {
                    try {
                        byte[] keyBytes = new byte[] {
                            'W','a','E','n','h','a','n','c','e','r','X','_',
                            'S','u','p','e','r','_','S','e','c','r','e','t','_',
                            'K','e','y','_','1','2','3'
                        };
                        byte[] ivBytes = new byte[] {
                            'W','a','E','n','h','a','n','c','e','r','X','_',
                            'I','V','_','_'
                        };
                        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
                        javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(ivBytes);
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                        byte[] encryptedBytes = cipher.doFinal(versionName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        encryptedVn = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP);
                    } catch (Exception ignored) {}
                }
                payload.put("vn", encryptedVn != null ? encryptedVn : "");

                String jsonInputString = payload.toString();

                // Initialize network request
                URL url = new URL(API_LINK_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Pragma", "no-cache");
                conn.setUseCaches(false);
                conn.setConnectTimeout(15000); // 15 seconds
                conn.setReadTimeout(15000);    // 15 seconds
                conn.setDoOutput(true);

                // Write JSON payload
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                // Determine target stream (use error stream for 4xx/5xx errors if available)
                InputStreamReader streamReader;
                if (responseCode >= 200 && responseCode < 300) {
                    streamReader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                } else {
                    java.io.InputStream errorStream = conn.getErrorStream();
                    streamReader = new InputStreamReader(errorStream != null ? errorStream : conn.getInputStream(), StandardCharsets.UTF_8);
                }

                // Read server response
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(streamReader)) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        responseBuilder.append(responseLine.trim());
                    }
                }

                String jsonResponse = responseBuilder.toString();

                if (jsonResponse.isEmpty()) {
                    postError(callback, "Server returned an empty response (Code: " + responseCode + ").");
                    return;
                }

                // Parse server response
                JSONObject responseObj = new JSONObject(jsonResponse);
                String status = responseObj.optString("status", "error");

                if ("success".equalsIgnoreCase(status)) {
                    final String encryptedConfig = responseObj.optString("encrypted_config", null);
                    final long expiresAt = parseExpiresAt(responseObj);
                    final String planName = responseObj.optString("plan_name", "Pro Active");
                    final String tgUsername = responseObj.optString("tg_username", "");
                    final String whitelistChannels = responseObj.optString("whitelist_channels", "");
                    final String planPrice = responseObj.optString("price", "");

                    // Save verified status, tier parameters, and encrypted_config in a single transaction securely
                    SafeSharedPreferences safePrefs = 
                            new SafeSharedPreferences(
                                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context));
                    
                    safePrefs.edit()
                            .putBoolean("is_pro_verified", true)
                            .putLong("expires_at", expiresAt)
                            .putString("plan_name", planName)
                            .putString("license_key", normalizedKey)
                            .putString("tg_username", tgUsername)
                            .putString("encrypted_config", encryptedConfig)
                            .putString("whitelist_channels", whitelistChannels)
                            .putString("plan_price", planPrice)
                            .commit(); // Synchronous commit to ensure immediate disk write

                    // Make sure preferences are world-readable on disk
                    makePrefsWorldReadable(context);

                    // Active key successfully validated, clear forced FREE status override
                    ProHelper.setForceFree(false);

                    if (encryptedConfig != null) {
                        try {
                            Class<?> proConfigClass = Class.forName("com.waenhancer.pro.utils.ProConfig");
                            java.lang.reflect.Method loadConfigMethod = proConfigClass.getMethod("loadConfig", String.class);
                            loadConfigMethod.invoke(null, encryptedConfig);
                        } catch (Exception ignored) {}
                        postSuccess(callback, encryptedConfig);
                    } else {
                        postError(callback, "Success response missing encrypted configuration payload.");
                    }
                } else {
                    String errorMessage = responseObj.optString("message", "Unknown validation error.");
                    postError(callback, errorMessage);
                }

            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "Connection timed out verifying license", e);
                postError(callback, "Connection timed out. Please check your network and try again.");
            } catch (java.io.IOException e) {
                Log.e(TAG, "I/O exception verifying license", e);
                postError(callback, "Network request failed. Please check your internet connection.");
            } catch (org.json.JSONException e) {
                Log.e(TAG, "JSON parsing error verifying license", e);
                postError(callback, "Invalid response format from authorization server.");
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error verifying license", e);
                postError(callback, "An unexpected error occurred: " + e.getLocalizedMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * Performs a silent background re-verification of the stored license key.
     * If the server responds with an error (401, 403, expired, device mismatch, invalid),
     * the local license data is wiped entirely, reverting the app to "Free" status.
     * On success, the expiry and plan name are silently refreshed.
     *
     * @param context  The Android context.
     * @param listener Optional listener invoked on the main thread when the status changes to FREE.
     */
    public static void silentCheck(final Context context, final SilentCheckListener listener) {
        final SharedPreferences rawPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        final SafeSharedPreferences safePrefs =
                new SafeSharedPreferences(rawPrefs);

        final String savedKey = safePrefs.getString("license_key", "").trim();
        final boolean isVerified = safePrefs.getBoolean("is_pro_verified", false);

        // Nothing to check if there's no stored license
        if (savedKey.isEmpty() || !isVerified) {
            return;
        }

        // Reversion / Channel compatibility check
        String whitelist = safePrefs.getString("whitelist_channels", "");
        String tempVn = "";
        try {
            tempVn = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        if (tempVn == null) tempVn = "";
        final String versionName = tempVn;
        if (isVerified && !whitelist.isEmpty()) {
            boolean allowed = false;
            String channelName = "";
            if (versionName.contains("-")) {
                String[] parts = versionName.split("-");
                if (parts.length >= 3) {
                    channelName = parts[1].trim().toLowerCase();
                }
            }
            for (String ch : whitelist.split(",", -1)) {
                if (ch.trim().toLowerCase().equals(channelName)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                // Device is on a release channel not whitelisted by the plan! Remove configs and unlink.
                safePrefs.edit().putBoolean("unlinked_reverted_to_stable", true).commit();
                
                final String keyToUnlink = savedKey;
                
                // Stable reversion has a custom bottom sheet, but send a push notification if allowed.
                String reversionMsg = "Your device has been unlinked because you are running the Stable version of the module. Pro trial features require the Beta channel.";
                try {
                    Class<?> utilsClass = Class.forName("com.waenhancer.xposed.utils.Utils");
                    utilsClass.getMethod("showNotification", String.class, String.class).invoke(null, "Subscription Downgraded", reversionMsg);
                } catch (Exception ignored) {}
                
                clearLicenseData(safePrefs, context, null);
                ProHelper.setForceFree(true);
                
                executorService.execute(() -> {
                    HttpURLConnection conn = null;
                    try {
                        KeystoreHelper.generateRSAKeyPair();
                        final String devicePubKey = KeystoreHelper.getPublicKeyBase64();
                        if (devicePubKey == null) return;
                        
                        JSONObject payload = new JSONObject();
                        payload.put("license_key", keyToUnlink);
                        payload.put("device_pub_key", devicePubKey);
                        
                        URL url = new URL(API_UNLINK_URL);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
                        conn.setDoOutput(true);
                        
                        try (OutputStream os = conn.getOutputStream()) {
                            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                            os.write(input, 0, input.length);
                        }
                        int code = conn.getResponseCode();
                    } catch (Exception e) {
                        Log.e(TAG, "Silent stable reversion unlink network error", e);
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                });
                
                // Broadcast status change
                android.content.Intent broadcastIntent = new android.content.Intent(
                        context.getPackageName() + ".ACTION_PRO_STATUS_CHANGED");
                broadcastIntent.setPackage(context.getPackageName());
                context.sendBroadcast(broadcastIntent);
                
                if (listener != null) {
                    mainHandler.post(listener::onStatusChanged);
                }
                return;
            }
        }

        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Ensure hardware key exists
                KeystoreHelper.generateRSAKeyPair();
                final String devicePubKey = KeystoreHelper.getPublicKeyBase64();
                if (devicePubKey == null) {
                    return;
                }

                // Build the same payload the server expects
                JSONObject payload = new JSONObject();
                payload.put("license_key", savedKey);
                payload.put("device_pub_key", devicePubKey);

                String signature = KeystoreHelper.signData(savedKey);
                payload.put("device_signature", signature != null ? signature : "");

                String encryptedVn = "";
                try {
                    Class<?> secClazz = Class.forName("com.waenhancer.pro.utils.SecurityNative");
                    encryptedVn = (String) secClazz.getMethod("encryptVersionName", String.class).invoke(null, versionName);
                } catch (Throwable t) {
                    try {
                        byte[] keyBytes = new byte[] {
                            'W','a','E','n','h','a','n','c','e','r','X','_',
                            'S','u','p','e','r','_','S','e','c','r','e','t','_',
                            'K','e','y','_','1','2','3'
                        };
                        byte[] ivBytes = new byte[] {
                            'W','a','E','n','h','a','n','c','e','r','X','_',
                            'I','V','_','_'
                        };
                        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
                        javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(ivBytes);
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                        byte[] encryptedBytes = cipher.doFinal(versionName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        encryptedVn = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP);
                    } catch (Exception ignored) {}
                }
                payload.put("vn", encryptedVn != null ? encryptedVn : "");

                // Fire network request
                URL url = new URL(API_VERIFY_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Pragma", "no-cache");
                conn.setUseCaches(false);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                // Read response body from appropriate stream
                InputStreamReader streamReader;
                if (responseCode >= 200 && responseCode < 300) {
                    streamReader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                } else {
                    java.io.InputStream errorStream = conn.getErrorStream();
                    streamReader = new InputStreamReader(
                            errorStream != null ? errorStream : conn.getInputStream(), StandardCharsets.UTF_8);
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(streamReader)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line.trim());
                    }
                }

                String jsonResponse = sb.toString();

                if (jsonResponse.isEmpty()) {
                    // Empty response — don't wipe data, could be transient server issue
                    return;
                }

                JSONObject responseObj = new JSONObject(jsonResponse);
                String status = responseObj.optString("status", "error");

                if ("success".equalsIgnoreCase(status)) {
                    // Silently refresh expiry & plan name if the server provides updated values
                    long newExpiry = parseExpiresAt(responseObj);
                    String newPlan = responseObj.optString("plan_name", "");
                    String tgUsername = responseObj.optString("tg_username", "");
                    String newWhitelist = responseObj.optString("whitelist_channels", "");
                    String newPrice = responseObj.optString("price", "");
                    final String encryptedConfig = responseObj.optString("encrypted_config", null);

                    if (encryptedConfig != null) {
                        try {
                            Class<?> proConfigClass = Class.forName("com.waenhancer.pro.utils.ProConfig");
                            java.lang.reflect.Method loadConfigMethod = proConfigClass.getMethod("loadConfig", String.class);
                            loadConfigMethod.invoke(null, encryptedConfig);
                        } catch (Exception ignored) {}
                    }

                    SharedPreferences.Editor editor = safePrefs.edit();
                    if (newExpiry > 0) {
                        editor.putLong("expires_at", newExpiry);
                    }
                    if (!newPlan.isEmpty()) {
                        editor.putString("plan_name", newPlan);
                    }
                    if (!tgUsername.isEmpty()) {
                        editor.putString("tg_username", tgUsername);
                    }
                    if (!newWhitelist.isEmpty()) {
                        editor.putString("whitelist_channels", newWhitelist);
                    }
                    if (!newPrice.isEmpty()) {
                        editor.putString("plan_price", newPrice);
                    }
                    if (encryptedConfig != null) {
                        editor.putString("encrypted_config", encryptedConfig);
                    }
                    editor.commit(); // Synchronous commit to ensure immediate disk write

                    // Make sure preferences are world-readable on disk
                    makePrefsWorldReadable(context);

                    if (listener != null) {
                        mainHandler.post(listener::onStatusChanged);
                    }

                } else {
                    String message = responseObj.optString("message", "");

                    boolean isExpired = message.toLowerCase().contains("expired");

                    if (isExpired) {
                        SharedPreferences.Editor editor = safePrefs.edit();
                        editor.putBoolean("is_pro_verified", false);
                        editor.remove("encrypted_config");
                        String expiredPlan = responseObj.optString("plan_name", "");
                        String expiredAt = responseObj.optString("expires_at", "");
                        String tgUsername = responseObj.optString("tg_username", "");
                        if (!expiredPlan.isEmpty()) {
                            editor.putString("plan_name", expiredPlan);
                        }
                        if (!expiredAt.isEmpty()) {
                            long expiryMs = parseExpiresAt(responseObj);
                            if (expiryMs > 0) {
                                editor.putLong("expires_at", expiryMs);
                            }
                        }
                        if (!tgUsername.isEmpty()) {
                            editor.putString("tg_username", tgUsername);
                        }
                        editor.putBoolean("message_bomber", false);
                        editor.putBoolean("delete_message_file", false);
                        editor.putBoolean("delete_message_file_sent", false);
                        editor.putString("floating_bottom_bar_pill_design", "regular");
                        editor.commit();
                        makePrefsWorldReadable(context);

                        // Force FREE status to immediately disable any in-memory pro state
                        ProHelper.setForceFree(true);

                        // Notify about downgrade
                        try {
                            Class<?> utilsClass = Class.forName("com.waenhancer.xposed.utils.Utils");
                            utilsClass.getMethod("handleSubscriptionDowngrade", Context.class, String.class).invoke(null, context, "Your subscription plan has expired.");
                        } catch (Exception ignored) {}
                    } else {
                        // True rejection (invalid key, device mismatch, etc.) — wipe all data
                        String reason = "Your license key has been revoked or is invalid.";
                        if (message != null && !message.trim().isEmpty()) {
                            if (message.toLowerCase().contains("unlink")) {
                                reason = "Your device has been unlinked from this license key.";
                            } else {
                                reason = message;
                            }
                        }
                        clearLicenseData(safePrefs, context, reason);
                        ProHelper.setForceFree(true);
                    }

                    // Restart WhatsApp automatically!
                    try {
                        Class<?> appClass = Class.forName("com.waenhancer.App");
                        Object appInstance = appClass.getMethod("getInstance").invoke(null);
                        appClass.getMethod("restartApp", String.class).invoke(appInstance, "com.whatsapp");
                    } catch (Exception ignored) {}
                    try {
                        Class<?> appClass = Class.forName("com.waenhancer.App");
                        Object appInstance = appClass.getMethod("getInstance").invoke(null);
                        appClass.getMethod("restartApp", String.class).invoke(appInstance, "com.whatsapp.w4b");
                    } catch (Exception ignored) {}

                    // Broadcast status change to update UI in both cases
                    android.content.Intent broadcastIntent = new android.content.Intent(context.getPackageName() + ".ACTION_PRO_STATUS_CHANGED");
                    broadcastIntent.setPackage(context.getPackageName());
                    context.sendBroadcast(broadcastIntent);

                    if (listener != null) {
                        mainHandler.post(listener::onStatusChanged);
                    }
                }
                // For other error codes (500, network flakes), do nothing — don't punish transient failures

            } catch (java.net.SocketTimeoutException | java.net.UnknownHostException e) {
                // Network not available — fail silently, do NOT wipe data
            } catch (Exception e) {
                Log.e(TAG, "silentCheck: Unexpected error", e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * Convenience overload without a listener.
     */
    public static void silentCheck(final Context context) {
        silentCheck(context, null);
    }

    /**
     * Unlinks this device from the server, wipes local license data,
     * and notifies via callback. On success, the app should restart.
     *
     * @param context  The Android context.
     * @param callback The callback to receive success or error notification.
     */
    public static void unlinkDevice(final Context context, final UnlinkCallback callback) {
        if (callback == null) {
            return;
        }

        final SharedPreferences rawPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        final SafeSharedPreferences safePrefs =
                new SafeSharedPreferences(rawPrefs);
        final String licenseKey = safePrefs.getString("license_key", "").trim();

        if (licenseKey.isEmpty()) {
            postUnlinkError(callback, "No license key found.");
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                KeystoreHelper.generateRSAKeyPair();
                final String devicePubKey = KeystoreHelper.getPublicKeyBase64();
                if (devicePubKey == null) {
                    postUnlinkError(callback, "Hardware keystore access failed.");
                    return;
                }

                JSONObject payload = new JSONObject();
                payload.put("license_key", licenseKey);
                payload.put("device_pub_key", devicePubKey);

                URL url = new URL(API_UNLINK_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                InputStreamReader streamReader;
                if (responseCode >= 200 && responseCode < 300) {
                    streamReader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                } else {
                    java.io.InputStream errorStream = conn.getErrorStream();
                    streamReader = new InputStreamReader(
                            errorStream != null ? errorStream : conn.getInputStream(), StandardCharsets.UTF_8);
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(streamReader)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line.trim());
                    }
                }

                String jsonResponse = sb.toString();

                JSONObject responseObj = new JSONObject(jsonResponse);
                String status = responseObj.optString("status", "error");

                if ("success".equalsIgnoreCase(status)) {
                    // Wipe all local license data
                    clearLicenseData(safePrefs, context, "Your device has been unlinked from this license key.");
                    ProHelper.setForceFree(true);

                    // Broadcast status change
                    android.content.Intent broadcastIntent = new android.content.Intent(
                            context.getPackageName() + ".ACTION_PRO_STATUS_CHANGED");
                    broadcastIntent.setPackage(context.getPackageName());
                    context.sendBroadcast(broadcastIntent);

                    mainHandler.post(callback::onSuccess);
                } else {
                    String errorMsg = responseObj.optString("message", "Unlink failed.");
                    postUnlinkError(callback, errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "unlinkDevice error", e);
                postUnlinkError(callback, "Network error: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void postUnlinkError(final UnlinkCallback callback, final String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    /**
     * Clears all locally stored licensing data, reverting the app to "Free" status.
     */
    private static void clearLicenseData(SafeSharedPreferences safePrefs, Context context, String reasonMsg) {
        safePrefs.edit()
                .remove("is_pro_verified")
                .remove("license_key")
                .remove("expires_at")
                .remove("plan_name")
                .remove("tg_username")
                .remove("encrypted_config")
                .putBoolean("message_bomber", false)
                .putBoolean("delete_message_file", false)
                .putBoolean("delete_message_file_sent", false)
                .putString("floating_bottom_bar_pill_design", "regular")
                .commit(); // Synchronous commit to ensure immediate disk write
        
        // Update permissions on disk to ensure Xposed process is synced
        makePrefsWorldReadable(context);

        if (reasonMsg != null) {
            try {
                Class<?> utilsClass = Class.forName("com.waenhancer.xposed.utils.Utils");
                utilsClass.getMethod("handleSubscriptionDowngrade", Context.class, String.class).invoke(null, context, reasonMsg);
            } catch (Exception ignored) {}
        }
    }

    private static long parseExpiresAt(JSONObject obj) {
        if (obj == null) return 0;
        
        // Try getting it as a string first
        String expiresAtStr = obj.optString("expires_at", "").trim();
        if (expiresAtStr.isEmpty()) {
            return 0;
        }
        
        try {
            // First try parsing as ISO 8601 string (e.g. 2026-06-07T23:59:59.000Z)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.Instant instant = java.time.Instant.parse(expiresAtStr);
                return instant.toEpochMilli();
            } else {
                // Pre-O fallback
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = sdf.parse(expiresAtStr);
                if (date != null) {
                    return date.getTime();
                }
            }
        } catch (Exception e) {
            // Try fallback without milliseconds in pre-O
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = sdf.parse(expiresAtStr);
                if (date != null) {
                    return date.getTime();
                }
            } catch (Exception ignored) {}
        }
        
        // If string parsing fails, try to parse it directly as a millisecond timestamp
        return obj.optLong("expires_at", 0);
    }

    public static void makePrefsWorldReadable(Context context) {
        try {
            // Make the app data directory traversable by other processes (kernel sandbox traversal)
            java.io.File dataDir = context.getDataDir();
            if (dataDir.exists()) {
                dataDir.setReadable(true, false);
                dataDir.setExecutable(true, false);
            }
            
            // Make the shared_prefs directory traversable and readable
            java.io.File prefsDir = new java.io.File(dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
            
            // Make the preferences XML file world-readable
            java.io.File prefsFile = new java.io.File(prefsDir, "com.waenhancer_preferences.xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to make preferences world-readable: " + e.getMessage());
        }
    }

    private static void postSuccess(final LicenseCallback callback, final String encryptedConfig) {
        mainHandler.post(() -> callback.onSuccess(encryptedConfig));
    }

    private static void postError(final LicenseCallback callback, final String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}
