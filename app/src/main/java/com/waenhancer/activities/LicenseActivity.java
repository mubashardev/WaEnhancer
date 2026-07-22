package com.waenhancer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.waenhancer.adapter.ProFeatureAdapter;
import com.waenhancer.model.SearchableFeature;
import com.waenhancer.utils.FeatureCatalog;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import com.waenhancer.activities.base.BaseActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.xposed.utils.LicenseManager;
import com.waenhancer.xposed.utils.ProHelper;
import com.waenhancer.xposed.utils.SafeSharedPreferences;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.waenhancer.App;
import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.utils.DesignUtils;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Modern LicenseActivity implementing hardware-locked licensing verification.
 * Adheres completely to the application's XML layouts and theme attributes.
 * Integrates with the official Telegram Bot (@waenhancerx_bot) to retrieve license keys.
 */
public class LicenseActivity extends BaseActivity {

    // State 1: Active Plan Views
    private MaterialCardView activePlanContainer;
    private MaterialTextView tvStatus;
    private MaterialTextView tvPlanName;
    private MaterialTextView tvExpiryDate;
    private MaterialTextView tvTgUsername;
    private MaterialTextView tvLicenseKeyMasked;
    private MaterialButton btnUnlink;

    // State 3: Warning Card
    private MaterialCardView helperWarningCard;
    private MaterialTextView tvHelperWarning;

    // State 2: Activation Views
    private LinearLayout activationContainer;
    private TextInputLayout tilLicenseKey;
    private TextInputEditText etLicenseKey;
    private MaterialButton btnVerify;
    private ProgressBar progressBar;
    private MaterialButton btnOpenTelegram;

    private BroadcastReceiver proStatusReceiver;

    private LinearLayout plansContainer;
    private View btnShowFeatures;

    private View loadingOverlay;
    private MaterialTextView tvLoadingStatus;

    private int getResId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private static String getProStatus() {
        return ProHelper.getProStatus();
    }

    private static String getProPlanName() {
        return ProHelper.getProPlanName();
    }

    private static void setForceFree(boolean forceFree) {
        ProHelper.setForceFree(forceFree);
    }



    private BottomSheetDialog createStyledDialog(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    private void showInfoDialog(String title, String message) {
        try {
            BottomSheetDialog dialog = createStyledDialog(this);
            int layoutId = getResId("bottom_sheet_info", "layout");
            View view = LayoutInflater.from(this).inflate(layoutId, null);
            dialog.setContentView(view);

            MaterialTextView tvTitle = view.findViewById(getResId("bs_title", "id"));
            MaterialTextView tvMessage = view.findViewById(getResId("bs_message", "id"));
            View okBtn = view.findViewById(getResId("bs_ok_btn", "id"));

            if (tvTitle != null) tvTitle.setText(title);
            if (tvMessage != null) tvMessage.setText(message);
            if (okBtn != null) {
                okBtn.setOnClickListener(v -> dialog.dismiss());
            }
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, title + ": " + message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResId("activity_license", "layout"));

        // Bind Toolbar and setup navigation
        Toolbar toolbar = findViewById(getResId("toolbar", "id"));
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // Bind State 1: Active Plan Container Views
        activePlanContainer = findViewById(getResId("active_plan_container", "id"));
        tvStatus = findViewById(getResId("tv_status", "id"));
        tvPlanName = findViewById(getResId("tv_plan_name", "id"));
        tvExpiryDate = findViewById(getResId("tv_expiry_date", "id"));
        tvTgUsername = findViewById(getResId("tv_tg_username", "id"));
        tvLicenseKeyMasked = findViewById(getResId("tv_license_key_masked", "id"));
        btnUnlink = findViewById(getResId("btn_unlink", "id"));

        // Bind State 3: Warning Card
        helperWarningCard = findViewById(getResId("helper_warning_card", "id"));
        tvHelperWarning = findViewById(getResId("tv_helper_warning", "id"));
        if (helperWarningCard != null) {
            helperWarningCard.setOnClickListener(v -> {
                ProHelper.navigateToPluginPack(LicenseActivity.this);
            });
        }

        // Bind State 2: Activation Container Views
        activationContainer = findViewById(getResId("activation_container", "id"));
        tilLicenseKey = findViewById(getResId("til_license_key", "id"));
        etLicenseKey = findViewById(getResId("et_license_key", "id"));
        btnVerify = findViewById(getResId("btn_verify", "id"));
        progressBar = findViewById(getResId("progress_bar", "id"));
        btnOpenTelegram = findViewById(getResId("btn_open_telegram", "id"));

        plansContainer = findViewById(getResId("plans_container", "id"));
        btnShowFeatures = findViewById(getResId("btn_show_features", "id"));
        
        if (btnShowFeatures != null) {
            btnShowFeatures.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(LicenseActivity.this, ProFeaturesActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(LicenseActivity.this, "Failed to launch features screen", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        loadPlans();

        // Setup listeners
        if (btnVerify != null) {
            btnVerify.setOnClickListener(v -> performActivation());
        }
        if (btnOpenTelegram != null) {
            btnOpenTelegram.setOnClickListener(v -> openTelegramBot());
        }
        if (btnUnlink != null) {
            btnUnlink.setOnClickListener(v -> showUnlinkBottomSheet());
        }

        if (etLicenseKey != null) {
            etLicenseKey.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (tilLicenseKey != null) {
                        tilLicenseKey.setError(null);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        proStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkStatus();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (proStatusReceiver != null) {
            IntentFilter proFilter = new IntentFilter(getPackageName() + ".ACTION_PRO_STATUS_CHANGED");
            ContextCompat.registerReceiver(this, proStatusReceiver, proFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        checkStatus();

        if ("ACTIVE".equalsIgnoreCase(getProStatus())) {
            LicenseManager.silentCheck(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (proStatusReceiver != null) {
            unregisterReceiver(proStatusReceiver);
        }
    }

    /**
     * Checks the current licensing status and updates visibility containers accordingly.
     * Handles ACTIVE, EXPIRED, and FREE states with distinct UI presentations.
     */
    private void checkStatus() {
        String proStatus = getProStatus();
        String planName = getProPlanName();

        SafeSharedPreferences safePrefs = new SafeSharedPreferences(
                PreferenceManager.getDefaultSharedPreferences(this));
        long expiresAt = safePrefs.getLong("expires_at", 0);
        String tgUsername = safePrefs.getString("tg_username", "");
        String licenseKey = safePrefs.getString("license_key", "");

        boolean isPro = "ACTIVE".equalsIgnoreCase(proStatus);

        // Features lists and adapters removed (moved to ProFeaturesActivity)

        boolean packageInstalled = ProHelper.isPluginPackageInstalled(this);
        boolean pluginInstalled = ProHelper.isPluginInstalled(this);
        
        if (helperWarningCard != null) {
            if ((isPro || "EXPIRED".equalsIgnoreCase(proStatus)) && !pluginInstalled) {
                helperWarningCard.setVisibility(View.VISIBLE);
                if (packageInstalled) {
                    // Installed but min version not satisfying
                    int minVersion = ProHelper.getPluginMinWaexVersion(this);
                    String minVersionName = ProHelper.getVersionNameFromCode(minVersion);
                    if (tvHelperWarning != null) {
                        tvHelperWarning.setText("Plugin requires a newer version of the main app (v" + minVersionName + " required). Tap to view updates.");
                    }
                    helperWarningCard.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(LicenseActivity.this, ChangelogActivity.class);
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    });
                } else {
                    // Not installed at all
                    if (tvHelperWarning != null) {
                        tvHelperWarning.setText("The companion Pro plugin app is required to make the Pro features work. Tap here to download and install the plugin pack.");
                    }
                    helperWarningCard.setOnClickListener(v -> {
                        ProHelper.navigateToPluginPack(LicenseActivity.this);
                    });
                }
            } else {
                helperWarningCard.setVisibility(View.GONE);
            }
        }

        if (isPro) {
            // ─── ACTIVE STATE ───
            if (activePlanContainer != null) activePlanContainer.setVisibility(View.VISIBLE);
            if (activationContainer != null) activationContainer.setVisibility(View.GONE);

            if (tvStatus != null) {
                tvStatus.setText("Status: Active");
                tvStatus.setTextColor(0xFF2E7D32);
            }
            if (tvPlanName != null) tvPlanName.setText("Plan: " + planName);
            if (tvExpiryDate != null) {
                if (expiresAt > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "dd MMM yyyy", Locale.getDefault());
                    tvExpiryDate.setText("Valid until: " + sdf.format(new Date(expiresAt)));
                } else {
                    tvExpiryDate.setText("Valid until: Lifetime Access");
                }
            }
            if (tvTgUsername != null) {
                if (tgUsername != null && !tgUsername.isEmpty()) {
                    tvTgUsername.setText("Linked to: @" + tgUsername);
                    tvTgUsername.setVisibility(View.VISIBLE);
                } else {
                    tvTgUsername.setVisibility(View.GONE);
                }
            }
            if (tvLicenseKeyMasked != null) {
                if (licenseKey != null && !licenseKey.isEmpty()) {
                    tvLicenseKeyMasked.setText("Key: " + maskKey(licenseKey));
                    tvLicenseKeyMasked.setVisibility(View.VISIBLE);
                } else {
                    tvLicenseKeyMasked.setVisibility(View.GONE);
                }
            }

        } else if ("EXPIRED".equalsIgnoreCase(proStatus)) {
            // ─── EXPIRED STATE ───
            if (activePlanContainer != null) activePlanContainer.setVisibility(View.VISIBLE);
            if (activationContainer != null) activationContainer.setVisibility(View.GONE);

            if (tvStatus != null) {
                tvStatus.setText("Status: License Expired");
                tvStatus.setTextColor(0xFFC62828);
            }
            if (tvPlanName != null) {
                String storedPlan = safePrefs.getString("plan_name", "");
                tvPlanName.setText("Plan: " + (storedPlan.isEmpty() ? "Expired Plan" : storedPlan));
            }
            if (tvExpiryDate != null) {
                if (expiresAt > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "dd MMM yyyy", Locale.getDefault());
                    tvExpiryDate.setText("Expired on: " + sdf.format(new Date(expiresAt)));
                    tvExpiryDate.setTextColor(0xFFC62828);
                } else {
                    tvExpiryDate.setText("Expired");
                    tvExpiryDate.setTextColor(0xFFC62828);
                }
            }
            if (tvTgUsername != null) {
                if (tgUsername != null && !tgUsername.isEmpty()) {
                    tvTgUsername.setText("Linked to: @" + tgUsername);
                    tvTgUsername.setVisibility(View.VISIBLE);
                } else {
                    tvTgUsername.setVisibility(View.GONE);
                }
            }
            if (tvLicenseKeyMasked != null) {
                if (licenseKey != null && !licenseKey.isEmpty()) {
                    tvLicenseKeyMasked.setText("Key: " + maskKey(licenseKey));
                    tvLicenseKeyMasked.setVisibility(View.VISIBLE);
                } else {
                    tvLicenseKeyMasked.setVisibility(View.GONE);
                }
            }

            // Auto-disable Pro features in preferences if they are still on
            if (safePrefs.getBoolean("is_pro_verified", false)
                    || safePrefs.getBoolean("message_bomber", false)
                    || safePrefs.getBoolean("delete_message_file", false)
                    || safePrefs.getBoolean("delete_message_file_sent", false)
                    || safePrefs.getBoolean("pro_status_splitter", false)
                    || safePrefs.getBoolean("send_audio_as_voice_status", false)) {
                safePrefs.edit()
                        .putBoolean("is_pro_verified", false)
                        .remove("encrypted_config")
                        .putBoolean("message_bomber", false)
                        .putBoolean("delete_message_file", false)
                        .putBoolean("delete_message_file_sent", false)
                        .putBoolean("pro_status_splitter", false)
                        .putBoolean("send_audio_as_voice_status", false)
                        .commit();
                
                setForceFree(true);
                
                try {
                    LicenseManager.makePrefsWorldReadable(this);
                } catch (Exception ignored) {}

                try {
                    App.getInstance().restartApp("com.whatsapp");
                } catch (Exception ignored) {}
                try {
                    App.getInstance().restartApp("com.whatsapp.w4b");
                } catch (Exception ignored) {}
            }

        } else {
            // ─── FREE STATE ───
            if (activePlanContainer != null) activePlanContainer.setVisibility(View.GONE);
            if (activationContainer != null) activationContainer.setVisibility(View.VISIBLE);

            // Auto-disable Pro features in preferences if they are still on
            if (safePrefs.getBoolean("is_pro_verified", false)
                    || safePrefs.getBoolean("message_bomber", false)
                    || safePrefs.getBoolean("delete_message_file", false)
                    || safePrefs.getBoolean("delete_message_file_sent", false)
                    || safePrefs.getBoolean("pro_status_splitter", false)
                    || safePrefs.getBoolean("send_audio_as_voice_status", false)) {
                safePrefs.edit()
                        .putBoolean("is_pro_verified", false)
                        .remove("encrypted_config")
                        .putBoolean("message_bomber", false)
                        .putBoolean("delete_message_file", false)
                        .putBoolean("delete_message_file_sent", false)
                        .putBoolean("pro_status_splitter", false)
                        .putBoolean("send_audio_as_voice_status", false)
                        .commit();
                
                setForceFree(true);
                
                try {
                    LicenseManager.makePrefsWorldReadable(this);
                } catch (Exception ignored) {}

                try {
                    App.getInstance().restartApp("com.whatsapp");
                } catch (Exception ignored) {}
                try {
                    App.getInstance().restartApp("com.whatsapp.w4b");
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Masks all characters of the license key except the last 4.
     * Example: "ABCD-EFGH-IJKL-1A2B" → "****-****-****-1A2B"
     */
    private String maskKey(String key) {
        if (key == null || key.length() <= 4) {
            return key != null ? key : "";
        }
        int visibleCount = 4;
        StringBuilder masked = new StringBuilder();
        int maskEnd = key.length() - visibleCount;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i < maskEnd) {
                // Preserve hyphens and dashes as structural separators
                masked.append(c == '-' ? '-' : '*');
            } else {
                masked.append(c);
            }
        }
        return masked.toString();
    }

    /**
     * Shows a BottomSheet with two options:
     * 1. "Confirm Unlink" — calls the backend API to unlink, wipes local data, and restarts.
     * 2. "Cancel" — dismisses.
     */
    private void showUnlinkBottomSheet() {
        BottomSheetDialog dialog = createStyledDialog(this);
        int layoutRes = getResId("bottom_sheet_action", "layout");
        View view = LayoutInflater.from(this).inflate(layoutRes, null);
        dialog.setContentView(view);

        MaterialTextView bsTitle = view.findViewById(getResId("bs_title", "id"));
        MaterialTextView bsMessage = view.findViewById(getResId("bs_message", "id"));
        MaterialButton bsConfirmBtn = view.findViewById(getResId("bs_confirm_btn", "id"));
        MaterialButton bsCancelBtn = view.findViewById(getResId("bs_cancel_btn", "id"));

        if (bsTitle != null) {
            bsTitle.setText("Unlink Device");
        }
        if (bsMessage != null) {
            bsMessage.setText("This will unlink your device from this license key. " +
                    "You can re-link it later or use a different key.\n\n" +
                    "WhatsApp and WaEnhancerX will restart after unlinking.");
        }
        if (bsConfirmBtn != null) {
            bsConfirmBtn.setText("Confirm Unlink");
            bsConfirmBtn.setOnClickListener(v -> {
                // Dismiss action bottom sheet so it doesn't overlap loading overlay
                dialog.dismiss();

                // Close soft keyboard
                hideKeyboard(etLicenseKey);

                // Disable inputs programmatically
                setInputsEnabled(false);

                // Show premium interactive loading overlay with unlinking message
                if (tvLoadingStatus != null) {
                    tvLoadingStatus.setText("Unlinking Device...");
                }
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.VISIBLE);
                }

                LicenseManager.unlinkDevice(this, new LicenseManager.UnlinkCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(LicenseActivity.this, "Device unlinked successfully.", Toast.LENGTH_SHORT).show();

                        // Restart WhatsApp processes
                        try {
                            App.getInstance().restartApp("com.whatsapp");
                        } catch (Exception ignored) {}
                        try {
                            App.getInstance().restartApp("com.whatsapp.w4b");
                        } catch (Exception ignored) {}

                        // Clean self-restart of WaEnhancer after a short delay
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent restartIntent = getPackageManager()
                                    .getLaunchIntentForPackage(getPackageName());
                            if (restartIntent != null) {
                                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(restartIntent);
                            }
                            System.exit(0);
                        }, 500);
                    }

                    @Override
                    public void onError(String message) {
                        // Restore inputs and hide loading overlay
                        setInputsEnabled(true);
                        if (loadingOverlay != null) {
                            loadingOverlay.setVisibility(View.GONE);
                        }
                        showInfoDialog("Unlink Failed", message);
                    }
                });
            });
        }
        if (bsCancelBtn != null) {
            bsCancelBtn.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Opens the official Telegram bot to retrieve licensing details.
     */
    private void openTelegramBot() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/waenhancerx_bot"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No browser or app found to open link.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Triggers remote verification using the LicenseManager.
     */
    private void performActivation() {
        if (etLicenseKey == null || etLicenseKey.getText() == null) return;

        if (tilLicenseKey != null) {
            tilLicenseKey.setError(null);
        }

        String key = etLicenseKey.getText().toString().trim();
        if (key.isEmpty()) {
            if (tilLicenseKey != null) {
                tilLicenseKey.setError("Please enter your license key.");
            } else {
                Toast.makeText(this, "Please enter your license key.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!LicenseManager.isValidLicensePattern(key)) {
            if (tilLicenseKey != null) {
                tilLicenseKey.setError("Invalid license key format. Expected format: WAEX-XXXX-XXXX-XXXX");
            } else {
                Toast.makeText(this, "Invalid license key format. Expected format: WAEX-XXXX-XXXX-XXXX", Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Close Soft Keyboard and disable inputs
        hideKeyboard(etLicenseKey);
        setInputsEnabled(false);

        // Show premium interactive loading overlay with verification message
        if (tvLoadingStatus != null) {
            tvLoadingStatus.setText("Verifying Key...");
        }
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }

        LicenseManager.verifyLicense(this, key, new LicenseManager.LicenseCallback() {
            @Override
            public void onSuccess(String encryptedConfig) {
                // Restore interactive states and hide loading overlay
                setInputsEnabled(true);
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }

                Toast.makeText(LicenseActivity.this, "Activation Successful! 🎉", Toast.LENGTH_LONG).show();

                // Immediately refresh visual state to present active pro tier card
                checkStatus();

                // Broadcast status change to update observers immediately
                try {
                    Intent broadcastIntent = new Intent(getPackageName() + ".ACTION_PRO_STATUS_CHANGED");
                    broadcastIntent.setPackage(getPackageName());
                    sendBroadcast(broadcastIntent);
                } catch (Exception ignored) {}

                // Pro hooks are installed at WhatsApp startup — restart WhatsApp
                // so they pick up the now-active license.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        new MaterialAlertDialogBuilder(LicenseActivity.this)
                                .setTitle("Restart WhatsApp")
                                .setMessage("Pro features are installed when WhatsApp starts. Please restart WhatsApp to activate all Pro features.")
                                .setPositiveButton("Restart Now", (dialog, which) -> {
                                    dialog.dismiss();
                                    // Kill WhatsApp and relaunch it
                                    try {
                                        ActivityManager am = (ActivityManager)
                                                getSystemService(Context.ACTIVITY_SERVICE);
                                        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
                                            if ("com.whatsapp".equals(info.processName) || "com.whatsapp.w4b".equals(info.processName)) {
                                                Process.killProcess(info.pid);
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                    // Also send restart broadcast to WhatsApp process
                                    try {
                                        Intent restartIntent = new Intent(getPackageName() + ".WHATSAPP.RESTART");
                                        restartIntent.putExtra("PKG", "com.whatsapp");
                                        sendBroadcast(restartIntent);
                                    } catch (Throwable ignored) {}
                                    // Launch WhatsApp fresh
                                    try {
                                        Intent wppLaunch = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                                        if (wppLaunch == null) {
                                            wppLaunch = getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
                                        }
                                        if (wppLaunch != null) {
                                            wppLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            startActivity(wppLaunch);
                                        }
                                    } catch (Throwable ignored) {}
                                })
                                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                                .setCancelable(true)
                                .show();
                    } catch (Throwable ignored) {}
                }, 800);

                if (!ProHelper.isPluginInstalled(LicenseActivity.this)) {
                    ProHelper.checkRootAndInstallPlugin(LicenseActivity.this, null);
                }

                // Perform native channel allowance check
                String versionName = BuildConfig.VERSION_NAME != null ? BuildConfig.VERSION_NAME : "";
                
                SafeSharedPreferences safePrefs = new SafeSharedPreferences(
                        PreferenceManager.getDefaultSharedPreferences(LicenseActivity.this));
                String whitelist = safePrefs.getString("whitelist_channels", "");
                String price = safePrefs.getString("plan_price", "");
                String planName = safePrefs.getString("plan_name", "Pro");

                boolean isAllowed = true;
                try {
                    ClassLoader loader = ProHelper.getPluginClassLoader(LicenseActivity.this);
                    Class<?> secClazz = loader != null ? Class.forName("com.waex.helper.utils.SecurityNative", true, loader) : Class.forName("com.waex.helper.utils.SecurityNative");
                    isAllowed = (Boolean) secClazz.getMethod("isChannelAllowed", String.class, String.class).invoke(null, versionName, whitelist);
                } catch (Throwable t) {
                    // Fallback
                }
                isAllowed = true; // Unconditionally bypass for single channel architecture

                if (!isAllowed) {
                    showBetaTestingBottomSheet(planName, price.isEmpty() ? "our standard price" : price, whitelist);
                }
            }

            @Override
            public void onError(String message) {
                // Restore interactive states and hide loading overlay
                setInputsEnabled(true);
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }

                showInfoDialog("Verification Failed", message);
            }
        });
    }

    /**
     * Navigates back to MainActivity and highlights the target preference screen/item,
     * matching the SearchActivity's click behavior exactly.
     */
    private int resolveColorAttrByName(String attrName, int fallbackColor) {
        try {
            int attrId = getResources().getIdentifier(attrName, "attr", getPackageName());
            if (attrId != 0) {
                TypedValue typedValue = new TypedValue();
                if (getTheme().resolveAttribute(attrId, typedValue, true)) {
                    return typedValue.data;
                }
            }
        } catch (Exception ignored) {}
        return fallbackColor;
    }

    private void clearPlansContainer(LinearLayout container) {
        if (container == null) return;
        int childCount = container.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            if (child != null) {
                child.clearAnimation();
            }
        }
        container.removeAllViews();
        container.invalidate();
        container.requestLayout();
    }

    private void showShimmer(LinearLayout container, float density, int cardBg, int strokeColor) {
        clearPlansContainer(container);
        for (int i = 0; i < 2; i++) {
            LinearLayout shimmerCard = new LinearLayout(this);
            shimmerCard.setOrientation(LinearLayout.VERTICAL);
            shimmerCard.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(16 * density));
            
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(12 * density);
            gd.setColor(cardBg);
            gd.setStroke((int) (1 * density), strokeColor);
            shimmerCard.setBackground(gd);
            shimmerCard.setElevation(5 * density);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (96 * density));
            int marginHoriz = (int) (6 * density);
            lp.setMargins(marginHoriz, (int)(2 * density), marginHoriz, (int)(14 * density));
            shimmerCard.setLayoutParams(lp);

            View titlePlaceholder = new View(this);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    (int) (120 * density), (int) (16 * density));
            titleLp.bottomMargin = (int) (12 * density);
            titlePlaceholder.setLayoutParams(titleLp);
            
            GradientDrawable titleGd = new GradientDrawable();
            titleGd.setCornerRadius(4 * density);
            titleGd.setColor(strokeColor);
            titlePlaceholder.setBackground(titleGd);
            shimmerCard.addView(titlePlaceholder);

            View pricePlaceholder = new View(this);
            LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                    (int) (80 * density), (int) (24 * density));
            priceLp.bottomMargin = (int) (8 * density);
            pricePlaceholder.setLayoutParams(priceLp);
            
            GradientDrawable priceGd = new GradientDrawable();
            priceGd.setCornerRadius(4 * density);
            priceGd.setColor(strokeColor);
            pricePlaceholder.setBackground(priceGd);
            shimmerCard.addView(pricePlaceholder);
            
            container.addView(shimmerCard);

            AlphaAnimation pulse = new AlphaAnimation(0.4f, 0.9f);
            pulse.setDuration(800);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            shimmerCard.startAnimation(pulse);
        }
    }

    private void loadPlans() {
        if (plansContainer == null) return;
        
        float density = getResources().getDisplayMetrics().density;
        int pad16 = (int) (16 * density);
        
        int cardBg = resolveColorAttrByName("colorSurfaceVariant", 0xFFF0F2F5);
        int strokeColor = resolveColorAttrByName("colorOutline", 0xFFE1E3E6);
        int primaryText = resolveColorAttrByName("colorOnSurface", 0xFF111B21);
        int secondaryText = resolveColorAttrByName("colorOnSurfaceVariant", 0xFF667781);
        int accentG = resolveColorAttrByName("colorPrimary", 0xFF008069);

        SharedPreferences cachePrefs = getSharedPreferences("waex_plans_cache", Context.MODE_PRIVATE);
        long cacheTime = cachePrefs.getLong("plans_cache_time", 0);
        String cachedData = cachePrefs.getString("plans_cache_data", null);
        long currentTime = System.currentTimeMillis();

        if (cachedData != null && (currentTime - cacheTime) < 900000) {
            try {
                JSONArray plansArray = new JSONArray(cachedData);
                clearPlansContainer(plansContainer);
                for (int i = 0; i < plansArray.length(); i++) {
                    JSONObject planObj = plansArray.getJSONObject(i);
                    buildPlanCard(plansContainer, density, pad16, cardBg, strokeColor, primaryText, secondaryText, accentG, planObj);
                }
            } catch (Throwable t) {
                showShimmer(plansContainer, density, cardBg, strokeColor);
                fetchPlansFromNetwork(plansContainer, density, pad16, cardBg, strokeColor, primaryText, secondaryText, accentG, cachePrefs);
            }
        } else {
            showShimmer(plansContainer, density, cardBg, strokeColor);
            fetchPlansFromNetwork(plansContainer, density, pad16, cardBg, strokeColor, primaryText, secondaryText, accentG, cachePrefs);
        }
    }

    private void fetchPlansFromNetwork(
            LinearLayout plansContainer,
            float density,
            int pad16,
            int cardBg,
            int strokeColor,
            int primaryText,
            int secondaryText,
            int accentG,
            SharedPreferences cachePrefs
    ) {
        new Thread(() -> {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL("https://waex.mubashar.dev/api/v1/plans");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                
                String responseStr = sb.toString();
                JSONArray plansArray = new JSONArray(responseStr);
                
                cachePrefs.edit()
                        .putString("plans_cache_data", responseStr)
                        .putLong("plans_cache_time", System.currentTimeMillis())
                        .apply();
                
                runOnUiThread(() -> {
                    clearPlansContainer(plansContainer);
                    try {
                        for (int i = 0; i < plansArray.length(); i++) {
                            JSONObject planObj = plansArray.getJSONObject(i);
                            buildPlanCard(plansContainer, density, pad16, cardBg, strokeColor, primaryText, secondaryText, accentG, planObj);
                        }
                    } catch (Throwable t) {
                        Toast.makeText(LicenseActivity.this, "Error rendering plans: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    clearPlansContainer(plansContainer);
                    try {
                        JSONObject monthlyFallback = new JSONObject();
                        monthlyFallback.put("id", 2);
                        monthlyFallback.put("name", "Pro Monthly");
                        monthlyFallback.put("type", "offer");
                        monthlyFallback.put("original_price", "3.50");
                        monthlyFallback.put("offer_price", "2.30");
                        monthlyFallback.put("badge", JSONObject.NULL);

                        JSONObject yearlyFallback = new JSONObject();
                        yearlyFallback.put("id", 3);
                        yearlyFallback.put("name", "Pro Yearly");
                        yearlyFallback.put("type", "offer");
                        yearlyFallback.put("original_price", "28.50");
                        yearlyFallback.put("offer_price", "18.99");
                        yearlyFallback.put("badge", "Best Value");

                        buildPlanCard(plansContainer, density, pad16, cardBg, strokeColor, primaryText, secondaryText, accentG, monthlyFallback);
                        buildPlanCard(plansContainer, density, pad16, cardBg, strokeColor, primaryText, secondaryText, accentG, yearlyFallback);
                    } catch (Throwable ignored) {}
                });
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }).start();
    }

    private void buildPlanCard(
            LinearLayout plansContainer,
            float density,
            int pad16,
            int cardBg,
            int strokeColor,
            int primaryText,
            int secondaryText,
            int accentG,
            JSONObject planObj
    ) {
        try {
            final String name = planObj.getString("name");
            final String originalPrice = planObj.getString("original_price");
            final String offerPrice = planObj.getString("offer_price");
            final String badge = planObj.isNull("badge") ? null : planObj.getString("badge");
            
            LinearLayout planCard = new LinearLayout(this);
            planCard.setOrientation(LinearLayout.VERTICAL);
            planCard.setPadding(pad16, pad16, pad16, pad16);
            
            GradientDrawable pcGd = new GradientDrawable();
            pcGd.setCornerRadius(12 * density);
            pcGd.setColor(cardBg);
            pcGd.setStroke((int) (1 * density), strokeColor);
            planCard.setBackground(pcGd);
            
            planCard.setElevation(5 * density);
            
            LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int marginHoriz = (int) (6 * density);
            pcLp.setMargins(marginHoriz, (int)(2 * density), marginHoriz, (int)(14 * density));
            planCard.setLayoutParams(pcLp);

            try {
                TypedValue outValue = new TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                planCard.setForeground(getDrawable(outValue.resourceId));
            } catch (Throwable ignored) {}

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topLp.bottomMargin = (int) (8 * density);
            topRow.setLayoutParams(topLp);

            TextView pct = new TextView(this);
            pct.setText(name);
            pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            pct.setTextColor(primaryText);
            pct.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            pct.setLayoutParams(nameLp);
            topRow.addView(pct);

            if (badge != null && !badge.trim().isEmpty()) {
                TextView pcb = new TextView(this);
                pcb.setText(badge.toUpperCase());
                pcb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                boolean isNight = DesignUtils.isNightMode();
                pcb.setTextColor(isNight ? 0xFF111B21 : 0xFFFFFFFF);
                pcb.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                pcb.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
                
                GradientDrawable badgeGd = new GradientDrawable();
                badgeGd.setCornerRadius(8 * density);
                badgeGd.setColor(accentG);
                pcb.setBackground(badgeGd);

                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                pcb.setLayoutParams(badgeLp);
                topRow.addView(pcb);
            }
            planCard.addView(topRow);

            LinearLayout priceRow = new LinearLayout(this);
            priceRow.setOrientation(LinearLayout.HORIZONTAL);
            priceRow.setGravity(Gravity.BOTTOM);
            LinearLayout.LayoutParams priceRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            priceRow.setLayoutParams(priceRowLp);

            boolean hasOffer = !originalPrice.equals(offerPrice);
            if (hasOffer) {
                TextView originalPriceTv = new TextView(this);
                originalPriceTv.setText("$" + originalPrice);
                originalPriceTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                originalPriceTv.setTextColor(secondaryText);
                originalPriceTv.setPaintFlags(originalPriceTv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                LinearLayout.LayoutParams origLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                origLp.rightMargin = (int) (8 * density);
                originalPriceTv.setLayoutParams(origLp);
                priceRow.addView(originalPriceTv);
            }

            TextView offerPriceTv = new TextView(this);
            offerPriceTv.setText("$" + offerPrice);
            offerPriceTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            offerPriceTv.setTextColor(accentG);
            offerPriceTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            priceRow.addView(offerPriceTv);

            String billingPeriod = "";
            if (name.toLowerCase().contains("monthly")) {
                billingPeriod = " / Month";
            } else if (name.toLowerCase().contains("yearly")) {
                billingPeriod = " / Year";
            }
            if (!billingPeriod.isEmpty()) {
                TextView periodTv = new TextView(this);
                periodTv.setText(billingPeriod);
                periodTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                periodTv.setTextColor(secondaryText);
                priceRow.addView(periodTv);
            }
            planCard.addView(priceRow);

            String featureText = "";
            if (name.toLowerCase().contains("monthly")) {
                featureText = "Full access to all Pro features for 30 days";
            } else if (name.toLowerCase().contains("yearly")) {
                featureText = "Save more with full Pro access for 365 days";
            } else {
                featureText = "Unlock all premium Pro capabilities";
            }
            TextView descTv = new TextView(this);
            descTv.setText(featureText);
            descTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            descTv.setTextColor(secondaryText);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = (int) (6 * density);
            descTv.setLayoutParams(descLp);
            planCard.addView(descTv);

            planCard.setClickable(true);
            planCard.setFocusable(true);
            planCard.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/waenhancerx_bot?start=subscribe"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                } catch (Throwable t) {
                    Toast.makeText(LicenseActivity.this, "Could not open Telegram", Toast.LENGTH_SHORT).show();
                }
            });

            plansContainer.addView(planCard);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void hideKeyboard(View view) {
        if (view != null) {
            try {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            } catch (Exception ignored) {}
        }
    }

    private void setInputsEnabled(boolean enabled) {
        if (etLicenseKey != null) etLicenseKey.setEnabled(enabled);
        if (tilLicenseKey != null) tilLicenseKey.setEnabled(enabled);
        if (btnVerify != null) {
            btnVerify.setEnabled(enabled);
            btnVerify.setText(enabled ? "Activate" : "Verifying...");
        }
        if (btnOpenTelegram != null) btnOpenTelegram.setEnabled(enabled);
    }

    private void showBetaTestingBottomSheet(String planName, String price, String whitelist) {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            int layoutId = getResId("bottom_sheet_action", "layout");
            View view = LayoutInflater.from(this).inflate(layoutId, null);
            dialog.setContentView(view);
            dialog.setCancelable(true);

            String channelName = whitelist.isEmpty() ? "Beta" : whitelist;

            ((MaterialTextView) view.findViewById(getResId("bs_title", "id"))).setText("Pro Features in " + channelName);
            ((MaterialTextView) view.findViewById(getResId("bs_message", "id"))).setText(
                    "Pro trial (" + planName + ") features are in " + channelName + " builds for now. If you want to try for " + price + ", please install a whitelisted build: " + channelName);

            MaterialButton joinBtn = view.findViewById(getResId("bs_confirm_btn", "id"));
            joinBtn.setText("Join " + channelName);
            joinBtn.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent();
                intent.setClassName(this, "com.waenhancer.activities.ChangelogActivity");
                intent.putExtra("target_channel", whitelist.isEmpty() ? "beta" : whitelist);
                startActivity(intent);
            });

            MaterialButton dismissBtn = view.findViewById(getResId("bs_cancel_btn", "id"));
            dismissBtn.setText("Dismiss");
            dismissBtn.setOnClickListener(v -> dialog.dismiss());

            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
            dialog.show();
        } catch (Exception ignored) {}
    }
}
