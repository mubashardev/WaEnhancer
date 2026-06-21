package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Html;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.waenhancer.App;
import com.waenhancer.BuildConfig;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import dalvik.system.DexClassLoader;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Helper utility to bridge main set classes and pro submodule features cleanly,
 * preventing compilation failures when HAS_PRO_FEATURES is false.
 */
public class ProHelper {

    private static volatile boolean forceFree = false;

    private static final Object lfLock = new Object();
    private static JSONObject limitedFreeConfigCache = null;
    private static String lastLimitedFreeConfig = null;

    private static JSONObject decryptedConfigCache = null;
    private static String lastEncryptedConfig = null;

    private static ClassLoader companionPluginClassLoader = null;

    public static void setForceFree(boolean force) {
        forceFree = force;
    }

    private static SharedPreferences getPrefs() {
        if (Utils.xprefs != null) {
            return Utils.xprefs;
        }
        Context context = App.getInstance();
        if (context != null) {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
        return null;
    }

    private static String decrypt(String encryptedBase64) {
        try {
            byte[] keyBytes = new byte[]{
                'W','a','E','n','h','a','n','c','e','r','X','_',
                'S','u','p','e','r','_','S','e','c','r','e','t','_',
                'K','e','y','_','1','2','3'
            };
            byte[] ivBytes = new byte[]{
                'W','a','E','n','h','a','n','c','e','r','X','_',
                'I','V','_','_'
            };
            byte[] cipherText = Base64.decode(encryptedBase64, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized JSONObject getDecryptedConfig() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return null;
        
        String encryptedConfig = prefs.getString("encrypted_config", null);
        if (encryptedConfig == null || encryptedConfig.trim().isEmpty()) {
            decryptedConfigCache = null;
            lastEncryptedConfig = null;
            return null;
        }
        
        if (encryptedConfig.equals(lastEncryptedConfig) && decryptedConfigCache != null) {
            return decryptedConfigCache;
        }
        
        try {
            String decrypted = decrypt(encryptedConfig);
            if (decrypted != null && !decrypted.isEmpty()) {
                decryptedConfigCache = new JSONObject(decrypted);
                lastEncryptedConfig = encryptedConfig;
                return decryptedConfigCache;
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to decrypt/parse config", t);
        }
        
        decryptedConfigCache = null;
        lastEncryptedConfig = null;
        return null;
    }

    private static synchronized JSONObject getLimitedFreeConfig() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return null;

        String encryptedConfig = prefs.getString("limited_free_config_cache", null);
        if (encryptedConfig == null || encryptedConfig.trim().isEmpty()) {
            limitedFreeConfigCache = null;
            lastLimitedFreeConfig = null;
            return null;
        }

        if (encryptedConfig.equals(lastLimitedFreeConfig) && limitedFreeConfigCache != null) {
            return limitedFreeConfigCache;
        }

        try {
            String decrypted = decrypt(encryptedConfig);
            if (decrypted != null && !decrypted.isEmpty()) {
                limitedFreeConfigCache = new JSONObject(decrypted);
                lastLimitedFreeConfig = encryptedConfig;
                return limitedFreeConfigCache;
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to decrypt/parse limited free config", t);
        }

        limitedFreeConfigCache = null;
        lastLimitedFreeConfig = null;
        return null;
    }

    public static boolean isLimitedFreeHookEnabled(String key) {
        if (key == null) return false;
        JSONObject config = getLimitedFreeConfig();
        if (config == null) return false;
        JSONObject hooks = config.optJSONObject("hooks");
        if (hooks == null) return false;
        String val = hooks.optString(key, null);
        return val != null && !val.trim().isEmpty();
    }

    public static String getLimitedFreeHookString(String key) {
        if (key == null) return null;
        JSONObject config = getLimitedFreeConfig();
        if (config == null) return null;
        JSONObject hooks = config.optJSONObject("hooks");
        if (hooks == null) return null;
        return hooks.optString(key, null);
    }

    public static boolean isLimitedFreePreferenceEnabled(String prefKey) {
        if (prefKey == null) return false;
        String hookKey = null;
        if (prefKey.equals("file_size_spoofer")) {
            hookKey = "file_size_spoofer";
        } else if (prefKey.equals("message_bomber")) {
            hookKey = "message_bomber";
        } else if (prefKey.equals("delete_message_file") || prefKey.equals("delete_message_file_sent")) {
            hookKey = "delete_message_file";
        } else if (prefKey.equals("pro_status_splitter")) {
            hookKey = "pro_status_splitter";
        } else if (prefKey.equals("remove_status_bottom_tile")
                || prefKey.equals("remove_status_quick_reactions")
                || prefKey.equals("remove_status_heart_button")
                || prefKey.equals("status_bottom_play_pause_button")
                || prefKey.equals("add_status_reply_menu_item")
                || prefKey.equals("status_video_fast_gesture")
                || prefKey.equals("status_video_fast_speed")
                || prefKey.equals("disable_status_swipe_up")) {
            hookKey = "customize_status_control_class";
        } else if (prefKey.equals("always_typing_global")
                || prefKey.equals("always_typing_global_target")
                || prefKey.equals("always_typing_global_mode")
                || prefKey.equals("always_typing_contacts")
                || prefKey.equals("always_typing_global_type")) {
            hookKey = "always_typing_global";
        } else if (prefKey.equals("send_audio_as_voice_status")) {
            hookKey = "send_audio_as_voice_status";
        }

        if (hookKey != null) {
            return isLimitedFreeHookEnabled(hookKey);
        }
        return false;
    }

    public static void initLimitedFree(final Context context, final SharedPreferences prefs) {
        if (prefs == null) return;
        // Load cached config first
        try {
            String cachedEncrypted = prefs.getString("limited_free_config_cache", null);
            if (cachedEncrypted != null && !cachedEncrypted.trim().isEmpty()) {
                String decrypted = decrypt(cachedEncrypted);
                if (decrypted != null && !decrypted.isEmpty()) {
                    synchronized (lfLock) {
                        limitedFreeConfigCache = new JSONObject(decrypted);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to load cached limited free config", t);
        }

        // Fetch latest configuration in background
        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("https://waex.mubashar.dev/limited_free_features.txt")
                    .header("User-Agent", "WPPro-App")
                    .build();

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {}

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    try (response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String encryptedBody = response.body().string().trim();
                            String decrypted = decrypt(encryptedBody);
                            if (decrypted != null && !decrypted.isEmpty()) {
                                // Validate JSON structure
                                new JSONObject(decrypted);
                                
                                // Cache locally
                                prefs.edit().putString("limited_free_config_cache", encryptedBody).apply();
                                
                                // Update in memory
                                synchronized (lfLock) {
                                    limitedFreeConfigCache = new JSONObject(decrypted);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Checks if the Pro licensing status is currently active.
     */
    public static boolean isProEnabled() {
        // Note: HAS_PRO_FEATURES is a compile-time flag for builds that bundle pro sources directly.
        // In the new modular architecture, the pro plugin is a separate APK — we check license
        // status from prefs regardless of the build flag.
        if (!"ACTIVE".equalsIgnoreCase(getProStatus())) {
            return false;
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return true;
        }
        String whitelist = prefs.getString("whitelist_channels", "");
        if (whitelist.isEmpty()) {
            return true;
        }
        String versionName = "";
        try {
            Context ctx = App.getInstance();
            if (ctx == null) {
                ctx = Utils.getApplication();
            }
            if (ctx != null) {
                versionName = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            }
        } catch (Throwable ignored) {}
        if (versionName == null) {
            versionName = "";
        }
        
        String channelName = "";
        if (versionName.contains("-")) {
            String[] parts = versionName.split("-");
            if (parts.length >= 3) {
                channelName = parts[1].trim().toLowerCase();
            }
        }
        for (String ch : whitelist.split(",", -1)) {
            if (ch.trim().toLowerCase().equals(channelName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the Pro pill design is enabled in the decrypted config.
     */
    public static boolean isPillDesignProEnabled() {
        if (!isProEnabled()) {
            return false;
        }
        JSONObject config = getDecryptedConfig();
        if (config == null) {
            return false;
        }
        return config.optBoolean("pill_design_pro_enabled", false);
    }

    /**
     * Checks if the Filter Items Pro hook is enabled in the decrypted server configuration.
     */
    public static boolean isFilterItemsProEnabled() {
        String hookClass = getHookStringSafely("filter_items");
        return hookClass != null && !hookClass.trim().isEmpty();
    }

    /**
     * Triggers a silent check/config refresh in the background, invoking the callback upon completion.
     */
    public static void silentCheck(final Context context, final Runnable callback) {
        com.waenhancer.xposed.utils.LicenseManager.silentCheck(context, new LicenseManager.SilentCheckListener() {
            @Override
            public void onStatusChanged() {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }

    /**
     * Retrieves the plan name matching active, expired, or free states.
     */
    public static String getProPlanName() {
        String status = getProStatus();
        if ("ACTIVE".equalsIgnoreCase(status)) {
            SharedPreferences prefs = getPrefs();
            String plan = prefs != null ? prefs.getString("plan_name", "") : "";
            return plan.isEmpty() ? "Pro Active" : plan;
        } else if ("EXPIRED".equalsIgnoreCase(status)) {
            return "Pro Expired";
        } else {
            return "Free";
        }
    }

    /**
     * Gets the current pro status string ("ACTIVE", "EXPIRED", "FREE").
     */
    public static String getProStatus() {
        if (forceFree) {
            return "FREE";
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return "FREE";
        }
        String licenseKey = prefs.getString("license_key", "").trim();
        boolean isVerified = prefs.getBoolean("is_pro_verified", false);
        if (!isVerified || licenseKey.isEmpty()) {
            return "FREE";
        }
        long expiresAt = 0;
        try {
            expiresAt = prefs.getLong("expires_at", 0);
        } catch (ClassCastException e) {
            try {
                String expiresStr = prefs.getString("expires_at", "0");
                expiresAt = Long.parseLong(expiresStr);
            } catch (Exception ignored) {}
        }
        if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    /**
     * Recursively traverses and locks down Pro features in a preference list if not verified.
     */
    public static void updatePreferences(Context context, PreferenceGroup group) {
        if (group == null) return;
        boolean proActive = isProEnabled();

        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);

            String prefKey = pref.getKey();
            if (prefKey != null) {
                boolean isFree = isLimitedFreePreferenceEnabled(prefKey);

                if (isFree) {
                    CharSequence title = pref.getTitle();
                    if (title != null && !title.toString().contains("Limited Free")) {
                        String coloredBadge = " <font color='#02C697'><b>[Limited Free]</b></font>";
                        pref.setTitle(Html.fromHtml(title.toString() + coloredBadge, Html.FROM_HTML_MODE_LEGACY));
                    }
                }
            }

            if (pref instanceof PreferenceGroup) {
                PreferenceGroup prefGroup = (PreferenceGroup) pref;
                String activationKey = "pro_activation_link_" + prefGroup.getKey();
                Preference activationPref = prefGroup.findPreference(activationKey);

                if (isProGroup(pref) && !proActive) {
                    prefGroup.setEnabled(true);
                    uncheckTwoStatePreferences(prefGroup);
                    disableChildrenOfProGroupExceptActivation(prefGroup, activationKey);

                    if (activationPref == null) {
                        activationPref = new Preference(context);
                        activationPref.setKey(activationKey);
                        String titleHtml = "<b><font color='#8B5CF6'>🔑 Tap here to verify license key & unlock</font></b>";
                        activationPref.setTitle(Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY));
                        activationPref.setSummary("This category is locked. Verify your WaEnhancerX Pro license to unlock all features.");
                        activationPref.setOrder(-1);
                        activationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(@NonNull Preference preference) {
                                try {
                                    Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                                    Intent intent = new Intent(context, clazz);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                } catch (Throwable t) {
                                    android.widget.Toast.makeText(context, "Pro features are not available.", android.widget.Toast.LENGTH_SHORT).show();
                                }
                                return true;
                            }
                        });
                        prefGroup.addPreference(activationPref);
                    }
                } else {
                    if (activationPref != null) {
                        prefGroup.removePreference(activationPref);
                    }
                    if (isProGroup(pref) && proActive) {
                        String key = pref.getKey();
                        if ("customize_status_view_category".equals(key)) {
                            String hookClass = getHookStringSafely("customize_status_control_class");
                            if (hookClass == null || hookClass.trim().isEmpty()) {
                                disableAndUncheckGroupFromServer(prefGroup, "(Disabled by Server)");
                                continue;
                            }
                        }
                    }
                    updatePreferences(context, prefGroup);
                }
            } else {
                if (isProFeature(pref) && !proActive) {
                    boolean limitedFree = isLimitedFreePreferenceEnabled(pref.getKey());

                    if (!limitedFree) {
                        if (pref.getClass().getName().contains("ProSwitchPreference")) {
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                        } else {
                            pref.setEnabled(false);
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                        }
                    }
                } else if (isProFeature(pref) && proActive) {
                    String key = pref.getKey();
                    String hookKey = getHookKeyForPref(key);
                    if (hookKey != null) {
                        String hookClass = getHookStringSafely(hookKey);
                        if (hookClass == null || hookClass.trim().isEmpty()) {
                            pref.setEnabled(false);
                            if (pref instanceof TwoStatePreference) {
                                ((TwoStatePreference) pref).setChecked(false);
                            }
                            CharSequence summary = pref.getSummary();
                            if (summary == null || !summary.toString().contains("Disabled by Server")) {
                                pref.setSummary("Disabled by Server");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void disableChildrenOfProGroupExceptActivation(PreferenceGroup group, String activationKey) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (activationKey.equals(pref.getKey())) {
                pref.setEnabled(true);
                continue;
            }
            if (pref.getClass().getName().contains("ProSwitchPreference")) {
                if (pref instanceof TwoStatePreference) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            } else {
                pref.setEnabled(false);
                if (pref instanceof TwoStatePreference) {
                    ((TwoStatePreference) pref).setChecked(false);
                }
            }
            if (pref instanceof PreferenceGroup) {
                disableChildrenOfProGroupExceptActivation((PreferenceGroup) pref, activationKey);
            }
        }
    }

    private static void disableAndUncheckGroupFromServer(PreferenceGroup group, String suffix) {
        if (group == null) return;
        group.setEnabled(false);
        CharSequence title = group.getTitle();
        if (title != null && !title.toString().contains(suffix)) {
            group.setTitle(title + " " + suffix);
        }
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            pref.setEnabled(false);
            if (pref instanceof TwoStatePreference) {
                ((TwoStatePreference) pref).setChecked(false);
            }
            if (pref instanceof androidx.preference.ListPreference || pref instanceof TwoStatePreference) {
                CharSequence summary = pref.getSummary();
                if (summary == null || !summary.toString().contains("Disabled by Server")) {
                    pref.setSummary("Disabled by Server");
                }
            }
            if (pref instanceof PreferenceGroup) {
                disableAndUncheckGroupFromServer((PreferenceGroup) pref, suffix);
            }
        }
    }

    private static boolean isProGroup(Preference pref) {
        if (pref == null) return false;
        String className = pref.getClass().getName();
        if (className.contains("ProPreferenceCategory")) {
            return true;
        }
        String key = pref.getKey();
        if (key != null) {
            return key.equals("customize_status_view_category");
        }
        return false;
    }

    private static void uncheckTwoStatePreferences(PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof TwoStatePreference) {
                ((TwoStatePreference) pref).setChecked(false);
            }
            if (pref instanceof PreferenceGroup) {
                uncheckTwoStatePreferences((PreferenceGroup) pref);
            }
        }
    }

    private static boolean isProFeature(Preference pref) {
        if (pref == null) return false;

        String className = pref.getClass().getName();
        if (className.contains("ProSwitchPreference")) {
            return true;
        }

        String key = pref.getKey();
        if (key != null) {
            return key.equals("message_bomber") 
                    || key.equals("license_verify") 
                    || key.equals("delete_message_file") 
                    || key.equals("delete_message_file_sent") 
                    || key.equals("pro_status_splitter")
                    || key.equals("remove_status_bottom_tile")
                    || key.equals("remove_status_quick_reactions")
                    || key.equals("remove_status_heart_button")
                    || key.equals("status_bottom_play_pause_button")
                    || key.equals("add_status_reply_menu_item")
                    || key.equals("status_video_fast_gesture")
                    || key.equals("status_video_fast_speed")
                    || key.equals("disable_status_swipe_up")
                    || key.equals("always_typing_global")
                    || key.equals("always_typing_global_target")
                    || key.equals("always_typing_global_mode")
                    || key.equals("always_typing_contacts")
                    || key.equals("always_typing_global_type")
                    || key.equals("send_audio_as_voice_status")
                    || key.equals("file_size_spoofer");
        }
        return false;
    }

    private static String getHookKeyForPref(String key) {
        if (key == null) return null;
        if (key.equals("message_bomber")) {
            return "message_bomber";
        }
        if (key.equals("delete_message_file") || key.equals("delete_message_file_sent")) {
            return "delete_message_file";
        }
        if (key.equals("pro_status_splitter")) {
            return "pro_status_splitter";
        }
        if (key.equals("remove_status_bottom_tile")
                || key.equals("remove_status_quick_reactions")
                || key.equals("remove_status_heart_button")
                || key.equals("status_bottom_play_pause_button")
                || key.equals("add_status_reply_menu_item")
                || key.equals("status_video_fast_gesture")
                || key.equals("status_video_fast_speed")
                || key.equals("disable_status_swipe_up")) {
            return "customize_status_control_class";
        }
        if (key.equals("always_typing_global")
                || key.equals("always_typing_global_target")
                || key.equals("always_typing_global_mode")
                || key.equals("always_typing_contacts")
                || key.equals("always_typing_global_type")) {
            return "always_typing_global";
        }
        if (key.equals("send_audio_as_voice_status")) {
            return "send_audio_as_voice_status";
        }
        if (key.equals("file_size_spoofer")) {
            return "file_size_spoofer";
        }
        return null;
    }

    private static String getHookStringSafely(String hookKey) {
        if (isLimitedFreeHookEnabled(hookKey)) {
            return getLimitedFreeHookString(hookKey);
        }
        if (!isProEnabled()) {
            return null;
        }
        JSONObject config = getDecryptedConfig();
        if (config == null) return null;
        JSONObject hooks = config.optJSONObject("hooks");
        if (hooks == null) return null;
        return hooks.optString(hookKey, null);
    }

    public static java.io.File convertAudioToOpus(Context context, android.net.Uri uri) {
        try {
            ClassLoader pluginLoader = null;
            try {
                pluginLoader = (ClassLoader) Class.forName("com.waenhancer.xposed.core.plugins.PluginLoader")
                        .getMethod("getPluginClassLoader").invoke(null);
            } catch (Throwable ignored) {}

            // Also fall back to companion classloader (companion app context)
            if (pluginLoader == null) {
                pluginLoader = getCompanionPluginClassLoader(context);
            }

            if (pluginLoader != null) {
                Class<?> converterClass = Class.forName("com.waex.pro.utils.AudioToOpusConverter", true, pluginLoader);
                return (java.io.File) converterClass.getMethod("convert", Context.class, android.net.Uri.class).invoke(null, context, uri);
            }
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "convertAudioToOpus failed: " + t.getMessage(), t);
        }
        return null;
    }

    public static void showKeyboxVerificationDialog(androidx.preference.PreferenceFragmentCompat fragment) {
        Context context = fragment.getContext();
        if (context == null) return;
        try {
            android.util.Log.d("WaeX-Helper", "showKeyboxVerificationDialog: starting, context=" + context.getPackageName());
            ClassLoader loader = getCompanionPluginClassLoader(context);
            android.util.Log.d("WaeX-Helper", "showKeyboxVerificationDialog: loader=" + loader);
            if (loader == null) {
                android.util.Log.e("WaeX-Helper", "showKeyboxVerificationDialog: loader is null — pro plugin not found");
                android.widget.Toast.makeText(context, "Verification module not found.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            Class<?> implClass = loader.loadClass("com.waex.pro.utils.KeyboxVerificationImpl");
            implClass.getMethod("showDialog", androidx.preference.PreferenceFragmentCompat.class)
                     .invoke(null, fragment);
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "showKeyboxVerificationDialog failed: " + t.getMessage(), t);
            android.widget.Toast.makeText(context, "Verification module not found.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public static synchronized ClassLoader getCompanionPluginClassLoader(Context context) {
        // 1. When running inside WhatsApp (Xposed context), the pro plugin is already loaded
        //    in-process by PluginLoader. Use that classloader directly to avoid double-loading.
        try {
            ClassLoader xposedLoader = (ClassLoader) Class.forName(
                "com.waenhancer.xposed.core.plugins.PluginLoader")
                .getMethod("getPluginClassLoader").invoke(null);
            if (xposedLoader != null) {
                android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: using Xposed in-process loader");
                return xposedLoader;
            }
        } catch (Throwable ignored) {}

        if (companionPluginClassLoader != null) {
            android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: using cached companion loader");
            return companionPluginClassLoader;
        }

        String apkPath = null;
        try {
            var pm = context.getPackageManager();
            var info = pm.getApplicationInfo("com.waex.pro", 0);
            android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: found com.waex.pro at " + info.sourceDir);
            if (info.sourceDir != null && new File(info.sourceDir).exists()) {
                apkPath = info.sourceDir;
            }
        } catch (Throwable e) {
            android.util.Log.w("WaeX-Helper", "getCompanionPluginClassLoader: com.waex.pro not found via PM: " + e.getMessage());
        }

        if (apkPath == null) {
            try {
                var pref = PreferenceManager.getDefaultSharedPreferences(context);
                String customPath = pref.getString("pro_plugin_path", null);
                android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: fallback pref path=" + customPath);
                if (customPath != null && new File(customPath).exists()) {
                    apkPath = customPath;
                }
            } catch (Throwable ignored) {}
        }

        if (apkPath == null) {
            android.util.Log.e("WaeX-Helper", "getCompanionPluginClassLoader: no APK path found, returning null");
            return null;
        }

        android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: creating DexClassLoader for " + apkPath);

        String libPath = null;
        try {
            var pm = context.getPackageManager();
            var info = pm.getApplicationInfo("com.waex.pro", 0);
            if (info.nativeLibraryDir != null && new File(info.nativeLibraryDir).exists()) {
                libPath = info.nativeLibraryDir;
            }
        } catch (Throwable ignored) {}

        if (libPath == null) {
            try {
                var pref = PreferenceManager.getDefaultSharedPreferences(context);
                libPath = pref.getString("pro_plugin_lib_path", null);
            } catch (Throwable ignored) {}
        }

        try {
            File codeCacheDir = context.getCodeCacheDir();
            ClassLoader hostClassLoader = ProHelper.class.getClassLoader();
            android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: codeCacheDir=" + codeCacheDir + " libPath=" + libPath);
            companionPluginClassLoader = new DexClassLoader(
                apkPath,
                codeCacheDir.getAbsolutePath(),
                libPath,
                hostClassLoader
            );
            android.util.Log.d("WaeX-Helper", "getCompanionPluginClassLoader: DexClassLoader created successfully");
        } catch (Throwable t) {
            android.util.Log.e("WaeX-Helper", "Failed to create companion plugin classloader: " + t.getMessage(), t);
        }
        return companionPluginClassLoader;
    }
}
