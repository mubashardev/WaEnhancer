package com.waenhancer.ui.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.waenhancer.App;
import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.UpdateChecker;
import com.waenhancer.UpdateDownloader;
import com.waenhancer.activities.MainActivity;
import com.waenhancer.activities.ChangelogActivity;
import com.waenhancer.databinding.FragmentHomeBinding;
import com.waenhancer.ui.fragments.base.BaseFragment;
import com.waenhancer.utils.FilePicker;
import com.waenhancer.utils.ApkMirrorFeedHelper;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

import rikka.core.util.IOUtils;

import java.io.File;
import android.app.ActivityManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.core.text.HtmlCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.ui.helpers.BottomSheetHelper;
import com.waenhancer.utils.ModuleStatus;
import com.waenhancer.xposed.utils.ProHelper;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends BaseFragment {

    private static final String RELEASES_URL = "https://github.com/mubashardev/WaEnhancer/releases";

    /**
     * In-memory flag — reset to false every time the app process starts.
     * Becomes true only when WhatsApp/Business sends a live broadcast response
     * in the current session, proving the Xposed hook is actually running.
     * Using SharedPreferences here caused a false "Module Enabled" status
     * when the module was disabled in LSPosed after a previous active session.
     */
    private static volatile long sLastHeartbeatTime = 0L;

    private FragmentHomeBinding binding;
    private String pendingUpdateUrl;
    private String pendingUpdateVersion;
    private BroadcastReceiver proStatusReceiver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var intentFilter = new IntentFilter(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
        ContextCompat.registerReceiver(requireContext(), new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String pkg = intent.getStringExtra("PKG");
                ;
                try {
                    if (FeatureLoader.PACKAGE_WPP.equals(pkg)) {
                        receiverBroadcastWpp(context, intent);
                    } else {
                        receiverBroadcastBusiness(context, intent);
                    }
                } catch (Exception e) {
                    Log.e("WAE_STATUS", "Error in receiverBroadcast: " + e.getMessage());
                }
            }
        }, intentFilter, ContextCompat.RECEIVER_EXPORTED);

        proStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateProUI();
            }
        };
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Migration: remove the legacy disk-persisted heartbeat key introduced in older builds.
        // That key caused a false "Module Enabled" status when the module was disabled in
        // LSPosed between app restarts. The heartbeat is now an in-memory flag only.
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().remove("module_heartbeat").apply();

        checkStateWpp(requireActivity());

        ApkMirrorFeedHelper.fetchVersionsIfNeeded(requireContext(), () -> {
            if (getActivity() != null && isAdded()) {
                checkStateWpp(getActivity());
            }
        });

        binding.rebootBtn.setOnClickListener(view -> {
            animateClick(view);
            App.getInstance().restartApp(FeatureLoader.PACKAGE_WPP);
            disableWpp(requireActivity());
        });

        binding.rebootBtn2.setOnClickListener(view -> {
            animateClick(view);
            App.getInstance().restartApp(FeatureLoader.PACKAGE_BUSINESS);
            disableBusiness(requireActivity());
        });

        binding.exportBtn.setOnClickListener(view -> {
            animateClick(view);
            saveConfigs(this.getContext());
        });

        binding.importBtn.setOnClickListener(view -> {
            animateClick(view);
            importConfigs(this.getContext());
        });

        binding.resetBtn.setOnClickListener(view -> {
            animateClick(view);
            showResetBottomSheet();
        });

        binding.viewSupportedVersionsBtn.setOnClickListener(view -> {
            animateClick(view);
            startActivity(new Intent(requireContext(), SupportedVersionsActivity.class));
        });

        binding.btnReportIssue.setOnClickListener(view -> {
            animateClick(view);
            try {
                String fwRaw = getXposedFrameworkVersion();
                String fwLabel = "Xposed/LSPosed API";
                String fwValue = fwRaw;
                String[] parts = fwRaw.split("\\|");
                if (parts.length == 2) {
                    fwLabel = parts[0] + " API";
                    fwValue = parts[1];
                }

                String dialogDetailsHtml = "<b>Device:</b> " + Build.MANUFACTURER + " "
                        + Build.MODEL + "<br>"
                        + "<b>Android Version:</b> " + Build.VERSION.RELEASE + " (SDK "
                        + Build.VERSION.SDK_INT + ")<br>"
                        + "<b>" + fwLabel + ":</b> " + fwValue + "<br>"
                        + "<b>Module Version:</b> " + BuildConfig.VERSION_NAME + "<br>";

                String githubDetailsMd = "**Device:** " + Build.MANUFACTURER + " " + Build.MODEL
                        + "\n"
                        + "**Android Version:** " + Build.VERSION.RELEASE + " (SDK "
                        + Build.VERSION.SDK_INT + ")\n"
                        + "**" + fwLabel + ":** " + fwValue + "\n"
                        + "**Module Version:** " + BuildConfig.VERSION_NAME + "\n";

                String tempWaVersion = "Not Installed";
                try {
                    PackageInfo pInfo = requireContext().getPackageManager()
                            .getPackageInfo(FeatureLoader.PACKAGE_WPP, 0);
                    tempWaVersion = pInfo.versionName;
                } catch (Exception e) {
                }
                final String waVersion = tempWaVersion;

                String tempWaBusinessVersion = "Not Installed";
                try {
                    PackageInfo pInfo = requireContext().getPackageManager()
                            .getPackageInfo(FeatureLoader.PACKAGE_BUSINESS, 0);
                    tempWaBusinessVersion = pInfo.versionName;
                } catch (Exception e) {
                }
                final String waBusinessVersion = tempWaBusinessVersion;

                final String finalDialogDetails = dialogDetailsHtml;
                final String finalGithubDetails = githubDetailsMd;

                String dialogMessageHtml = "This will open the WaEnhancer X GitHub Issues page to report a bug.<br><br>"
                        + "The following information about your device and installed apps will be pre-filled in your report:<br><br>"
                        + finalDialogDetails + "<b>WhatsApp Version:</b> " + waVersion + "<br>"
                        + "<b>WhatsApp Business Version:</b> " + waBusinessVersion + "<br>";

                var bottomSheetDialog = new BottomSheetDialog(requireContext());
                var sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_report_issue, null);
                bottomSheetDialog.setContentView(sheetView);

                var bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    bottomSheet.setBackgroundResource(android.R.color.transparent);
                }

                TextView deviceDetailsText = sheetView.findViewById(R.id.device_details);
                deviceDetailsText.setText(HtmlCompat.fromHtml(dialogMessageHtml,
                        HtmlCompat.FROM_HTML_MODE_LEGACY));

                LinearProgressIndicator progressBar = sheetView.findViewById(R.id.progress_bar);
                progressBar.setMax(100);
                progressBar.setProgressCompat(33, true);

                ViewFlipper viewFlipper = sheetView.findViewById(R.id.view_flipper);
                viewFlipper.setInAnimation(requireContext(), android.R.anim.fade_in);
                viewFlipper.setOutAnimation(requireContext(), android.R.anim.fade_out);

                TextInputEditText titleInput = sheetView.findViewById(R.id.title_input);
                TextInputEditText issueInput = sheetView.findViewById(R.id.issue_input);
                TextInputLayout inputLayout = sheetView.findViewById(R.id.input_layout);

                MaterialButton btnCancel = sheetView.findViewById(R.id.btn_cancel);
                MaterialButton btnNext = sheetView.findViewById(R.id.btn_next);

                titleInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (viewFlipper.getDisplayedChild() == 1) {
                            int len = s != null ? s.toString().trim().length() : 0;
                            btnNext.setEnabled(len >= 15 && len <= 50);
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });

                issueInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (viewFlipper.getDisplayedChild() == 2) {
                            btnNext.setEnabled(s != null && s.toString().trim().length() >= 15);
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });

                btnCancel.setOnClickListener(v -> {
                    if (viewFlipper.getDisplayedChild() > 0) {
                        viewFlipper.showPrevious();
                        if (viewFlipper.getDisplayedChild() == 0) {
                            progressBar.setProgressCompat(33, true);
                            btnCancel.setText(R.string.cancel);
                            btnNext.setText("Next");
                            btnNext.setEnabled(true);
                        } else if (viewFlipper.getDisplayedChild() == 1) {
                            progressBar.setProgressCompat(66, true);
                            btnCancel.setText("Back");
                            btnNext.setText("Next");
                            int len = titleInput.getText() != null ? titleInput.getText().toString().trim().length() : 0;
                            btnNext.setEnabled(len >= 15 && len <= 50);
                        }
                    } else {
                        bottomSheetDialog.dismiss();
                    }
                });

                btnNext.setOnClickListener(v -> {
                    if (viewFlipper.getDisplayedChild() == 0) {
                        viewFlipper.showNext();
                        progressBar.setProgressCompat(66, true);
                        btnCancel.setText("Back");
                        int len = titleInput.getText() != null ? titleInput.getText().toString().trim().length() : 0;
                        btnNext.setEnabled(len >= 15 && len <= 50);
                    } else if (viewFlipper.getDisplayedChild() == 1) {
                        viewFlipper.showNext();
                        progressBar.setProgressCompat(100, true);
                        btnNext.setText("Continue");
                        btnCancel.setText("Back");
                        int len = issueInput.getText() != null ? issueInput.getText().toString().trim().length() : 0;
                        btnNext.setEnabled(len >= 15);
                    } else {
                        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "Bug Report";
                        String description = issueInput.getText() != null ? issueInput.getText().toString().trim() : "";
                        try {
                            String body = finalGithubDetails + "**WhatsApp Version:** " + waVersion + "\n"
                                    + "**WhatsApp Business Version:** " + waBusinessVersion + "\n"
                                    + "\n---\n"
                                    + description + "\n";

                            String url = "https://github.com/mubashardev/WaEnhancer/issues/new?title="
                                    + URLEncoder.encode(title, "UTF-8") + "&body="
                                    + URLEncoder.encode(body, "UTF-8");
                            openUrl(requireContext(), url);
                            bottomSheetDialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                bottomSheetDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        binding.telegramBtn.setOnClickListener(view -> {
            animateClick(view);
            openTelegramChannel(requireContext());
        });

        binding.githubBtn.setOnClickListener(view -> {
            animateClick(view);
            openUrl(requireContext(), "https://github.com/mubashardev/WaEnhancer/issues");
        });

        binding.clearCacheBtn.setOnClickListener(view -> {
            animateClick(view);
            showClearCacheConfirmation();
        });

        binding.statusSummary.setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(requireContext(), ChangelogActivity.class);
            startActivity(intent);
        });

        binding.proStatusChip.setOnClickListener(v -> {
            animateClick(v);
            launchLicenseActivity(requireContext());
        });

        setupReleaseChannelSelector();
        setupUpdateBanner();
        startCardAnimations();

        showConsentDialogIfNeeded();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding != null && binding.proStatusChip != null) {
            binding.proStatusChip.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) {
                    launchLicenseActivity(context);
                }
            });
        }
    }

    private void showConsentDialogIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        if (!prefs.contains("consent_crashlytics_asked")) {
            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_action, null);
            dialog.setContentView(view);
            dialog.setCancelable(false);

            ((MaterialTextView) view.findViewById(R.id.bs_title)).setText("Share Anonymous Crash Logs");
            ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(
                    "Help us fix bugs by sharing anonymous crash logs.\n\n" +
                    "No personally identifiable information is collected. " +
                    "You can always change this preference later in Settings → General.");

            MaterialButton acceptBtn = view.findViewById(R.id.bs_confirm_btn);
            acceptBtn.setText("Accept");
            acceptBtn.setOnClickListener(v -> {
                prefs.edit()
                        .putBoolean("consent_crashlytics_asked", true)
                        .putBoolean("enable_crash_analytics", true)
                        .apply();
                // FirebaseInitProvider has already initialised the Firebase App object.
                // We just need to enable collection — no initializeApp call needed.
                if (BuildConfig.FIREBASE_ENABLED && !BuildConfig.DEBUG) {
                    App.applyFirebaseConsent(requireContext(), true);
                }
                dialog.dismiss();
            });

            MaterialButton declineBtn = view.findViewById(R.id.bs_cancel_btn);
            declineBtn.setText("Decline");
            declineBtn.setOnClickListener(v -> {
                prefs.edit()
                        .putBoolean("consent_crashlytics_asked", true)
                        .putBoolean("enable_crash_analytics", false)
                        .apply();
                // Explicitly disable in the current process (not just on next launch).
                if (BuildConfig.FIREBASE_ENABLED && !BuildConfig.DEBUG) {
                    App.applyFirebaseConsent(requireContext(), false);
                }
                dialog.dismiss();
            });

            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }

            dialog.show();
        }
    }


    private void openUrl(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTelegramChannel(Context context) {
        String channelUrl = "https://t.me/WaEnhancerX";
        String installedPackage = Utils.getInstalledTelegramPackage(context);

        if (installedPackage != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl));
            try {
                intent.setPackage(installedPackage);
                context.startActivity(intent);
            } catch (Exception e) {
                // Fallback to implicit intent if explicit one fails
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)));
            }
        } else {
            Toast.makeText(context, "Telegram app is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCardAnimations() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        var slideUp = AnimationUtils.loadAnimation(context, R.anim.slide_up);
        var fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in);

        binding.status.startAnimation(slideUp);

        binding.status2.postDelayed(() -> {
            if (getContext() == null || !isAdded()) {
                return;
            }
            var anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
            binding.status2.startAnimation(anim);
        }, 100);

        binding.status3.postDelayed(() -> {
            if (getContext() == null || !isAdded()) {
                return;
            }
            var anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
            binding.status3.startAnimation(anim);
        }, 100);

        binding.infoCard.postDelayed(() -> {
            if (getContext() == null || !isAdded()) {
                return;
            }
            binding.infoCard.startAnimation(fadeIn);
        }, 200);
    }

    private void animateClick(View view) {
        Context context = getContext();
        if (context != null) {
            var scaleIn = AnimationUtils.loadAnimation(context, R.anim.scale_in);
            view.startAnimation(scaleIn);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
        syncReleaseChannelToInstalled();
        checkForUpdates();
        checkStateWpp(requireActivity());

        if (proStatusReceiver != null && getContext() != null) {
            var proFilter = new IntentFilter(requireContext().getPackageName() + ".ACTION_PRO_STATUS_CHANGED");
            ContextCompat.registerReceiver(requireContext(), proStatusReceiver, proFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        updateProUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (proStatusReceiver != null && getContext() != null) {
            requireContext().unregisterReceiver(proStatusReceiver);
        }
    }

    private void updateProUI() {
        if (binding == null || getContext() == null) {
            return;
        }
        boolean isPro = ProHelper.isProEnabled();
        String planName = ProHelper.getProPlanName();
        String proStatus = ProHelper.getProStatus();

        if (binding.proStatusChip != null) {
            String text;
            boolean packageInstalled = ProHelper.isPluginPackageInstalled(getContext());
            boolean pluginInstalled = ProHelper.isPluginInstalled(getContext());
            boolean isRedDotError = false;

            if (packageInstalled && !pluginInstalled) {
                // Plugin is installed, but unsupported (since pluginInstalled is false)
                int minVersion = ProHelper.getPluginMinWaexVersion(getContext());
                String minVersionName = ProHelper.getVersionNameFromCode(minVersion);
                text = "v" + minVersionName + " Required";
            } else if (!packageInstalled) {
                if (!"FREE".equalsIgnoreCase(proStatus)) {
                    // License activated in module but Pro plugin app is not installed
                    text = planName;
                    isRedDotError = true;
                } else {
                    text = "Free";
                }
            } else if ("ACTIVE".equalsIgnoreCase(proStatus)) {
                text = planName;
            } else if ("EXPIRED".equalsIgnoreCase(proStatus)) {
                text = "License Expired";
            } else {
                text = "Free";
            }
            binding.proStatusChip.setText(text);

            // Dynamically update chip's background tint and text colors based on status
            if ((packageInstalled && !pluginInstalled) || "EXPIRED".equalsIgnoreCase(proStatus) || isRedDotError) {
                // Light red background with dark red text for Expired Pro / Plugin Required / Red Dot Error
                binding.proStatusChip.setBackgroundTintList(ColorStateList.valueOf(0xFFFFEBEE));
                binding.proStatusChip.setTextColor(0xFFC62828);
            } else {
                // Standard default background tint and primary color text for Free status and Active/Pro status
                TypedValue typedValueContainer = new TypedValue();
                TypedValue typedValuePrimary = new TypedValue();
                if (requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValueContainer, true)) {
                    binding.proStatusChip.setBackgroundTintList(ColorStateList.valueOf(typedValueContainer.data));
                } else {
                    binding.proStatusChip.setBackgroundTintList(null); // fallback
                }
                if (requireContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValuePrimary, true)) {
                    binding.proStatusChip.setTextColor(typedValuePrimary.data);
                } else {
                    binding.proStatusChip.setTextColor(0xFF000000); // generic fallback
                }
            }

            if (isRedDotError) {
                binding.proStatusChip.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_dot_inactive, 0, R.drawable.ic_chevron_right, 0);
            } else {
                binding.proStatusChip.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                binding.proStatusChip.setCompoundDrawableTintList(binding.proStatusChip.getTextColors());
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private void receiverBroadcastBusiness(Context context, Intent intent) {
        markModuleActive();
        updateModuleStatusUi(MainActivity.isXposedFrameworkPresent(context), ModuleStatus.isModuleActive(), true);
        binding.statusTitle3.setText(R.string.business_in_background);
        var version = intent.getStringExtra("VERSION");
        var supported_list = Arrays.asList(context.getResources().getStringArray(R.array.supported_versions_business));
        if (version != null && supported_list.stream().anyMatch(s -> version.startsWith(s.replace(".xx", "")))) {
            binding.statusSummary3.setText(getString(R.string.version_s, version));
            binding.statusDotBusiness.setBackgroundResource(R.drawable.status_dot_active);
        } else {
            binding.statusSummary3.setText(getString(R.string.version_s_not_listed, version));
            binding.statusDotBusiness.setBackgroundResource(R.drawable.status_dot_inactive);
        }
        binding.rebootBtn2.setVisibility(View.VISIBLE);
        binding.statusSummary3.setVisibility(View.VISIBLE);
    }

    @SuppressLint("StringFormatInvalid")
    private void receiverBroadcastWpp(Context context, Intent intent) {
        markModuleActive();
        updateModuleStatusUi(MainActivity.isXposedFrameworkPresent(context), ModuleStatus.isModuleActive(), true);
        binding.statusTitle2.setText(R.string.whatsapp_in_background);
        var version = intent.getStringExtra("VERSION");
        var supported_list = Arrays.asList(context.getResources().getStringArray(R.array.supported_versions_wpp));

        if (version != null && supported_list.stream().anyMatch(s -> version.startsWith(s.replace(".xx", "")))) {
            binding.statusSummary1.setText(getString(R.string.version_s, version));
            binding.statusDotWpp.setBackgroundResource(R.drawable.status_dot_active);
        } else {
            binding.statusSummary1.setText(getString(R.string.version_s_not_listed, version));
            binding.statusDotWpp.setBackgroundResource(R.drawable.status_dot_inactive);
        }
        binding.rebootBtn.setVisibility(View.VISIBLE);
        binding.statusSummary1.setVisibility(View.VISIBLE);
    }

    private void showResetBottomSheet() {
        var context = requireContext();
        var bottomSheetDialog = new BottomSheetDialog(context);
        var sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_reset, null);
        bottomSheetDialog.setContentView(sheetView);

        // Make background transparent so our custom shape shows
        var bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        }

        sheetView.findViewById(R.id.confirm_reset_btn).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            resetConfigs(context);
        });

        sheetView.findViewById(R.id.cancel_reset_btn).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void resetConfigs(Context context) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.getAll().forEach((key, value) -> prefs.edit().remove(key).apply());
        App.getInstance().restartApp(FeatureLoader.PACKAGE_WPP);
        App.getInstance().restartApp(FeatureLoader.PACKAGE_BUSINESS);
        Utils.showToast(context.getString(R.string.configs_reset), Toast.LENGTH_SHORT);
        if (getActivity() != null && context.getPackageName().equals(BuildConfig.APPLICATION_ID)) {
            getActivity().recreate();
        }
    }

    private static @NonNull
    JSONObject getJsonObject(SharedPreferences prefs) throws JSONException {
        var entries = prefs.getAll();
        var JSOjsonObject = new JSONObject();
        for (var entry : entries.entrySet()) {
            var type = new JSONObject();
            var keyValue = entry.getValue();
            if (keyValue instanceof HashSet<?> hashSet) {
                keyValue = new JSONArray(new ArrayList<>(hashSet));
            }
            type.put("type", keyValue.getClass().getSimpleName());
            type.put("value", keyValue);
            JSOjsonObject.put(entry.getKey(), type);
        }
        return JSOjsonObject;
    }

    private void saveConfigs(Context context) {
        if (FilePicker.fileSalve == null) {
            Toast.makeText(context, context.getString(R.string.configs_imported) != null ? "Please use the standalone WaEnhancerX app for file operations." : "Please use the standalone WaEnhancerX app for file operations.", Toast.LENGTH_SHORT).show();
            return;
        }
        FilePicker.setOnUriPickedListener((uri) -> {
            try {
                try (var output = context.getContentResolver().openOutputStream(uri)) {
                    var prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    var JSOjsonObject = getJsonObject(prefs);
                    Objects.requireNonNull(output).write(JSOjsonObject.toString(4).getBytes());
                }
                Toast.makeText(context, context.getString(R.string.configs_saved), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        String formattedDate = dateFormat.format(new Date());
        FilePicker.fileSalve.launch("wpp_enhacer_configs_" + formattedDate + ".json");
    }

    private void importConfigs(Context context) {
        if (FilePicker.fileCapture == null) {
            Toast.makeText(context, "Please use the standalone WaEnhancerX app for file operations.", Toast.LENGTH_SHORT).show();
            return;
        }
        FilePicker.setOnUriPickedListener((uri) -> {
            try {
                try (var input = context.getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] dataBuffer = new byte[8192];
                    int totalRead = 0;
                    while ((nRead = input.read(dataBuffer, 0, dataBuffer.length)) != -1) {
                        buffer.write(dataBuffer, 0, nRead);
                        totalRead += nRead;
                        if (totalRead > 5 * 1024 * 1024) { // 5 MB limit
                            throw new RuntimeException("File is too large to be a valid config.");
                        }
                    }
                    var data = buffer.toString("UTF-8");
                    var prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    var jsonObject = new JSONObject(data);
                    prefs.getAll().forEach((key, value) -> prefs.edit().remove(key).apply());
                    var key = jsonObject.keys();
                    while (key.hasNext()) {
                        var keyName = key.next();
                        var value = jsonObject.get(keyName);
                        var type = value.getClass().getSimpleName();
                        if (value instanceof JSONObject valueJson) {
                            value = valueJson.get("value");
                            type = valueJson.getString("type");
                        }

                        if (type.equals(JSONArray.class.getSimpleName())) {
                            var jsonArray = (JSONArray) value;
                            HashSet<String> hashSet = new HashSet<>();
                            for (var i = 0; i < jsonArray.length(); i++) {
                                hashSet.add(jsonArray.getString(i));
                            }
                            prefs.edit().putStringSet(keyName, hashSet).apply();
                        } else if (type.equals(String.class.getSimpleName())) {
                            prefs.edit().putString(keyName, (String) value).apply();
                        } else if (type.equals(Boolean.class.getSimpleName())) {
                            prefs.edit().putBoolean(keyName, (boolean) value).apply();
                        } else if (type.equals(Integer.class.getSimpleName())) {
                            prefs.edit().putInt(keyName, (int) value).apply();
                        } else if (type.equals(Long.class.getSimpleName())) {
                            prefs.edit().putLong(keyName, (long) value).apply();
                        } else if (type.equals(Double.class.getSimpleName())) {
                            prefs.edit().putFloat(keyName, Float.parseFloat(String.valueOf(value))).apply();
                        } else if (type.equals(Float.class.getSimpleName())) {
                            prefs.edit().putFloat(keyName, Float.parseFloat(String.valueOf(value))).apply();
                        }
                    }
                }
                Toast.makeText(context, context.getString(R.string.configs_imported), Toast.LENGTH_SHORT).show();
                App.getInstance().restartApp(FeatureLoader.PACKAGE_WPP);
                App.getInstance().restartApp(FeatureLoader.PACKAGE_BUSINESS);
                if (getActivity() != null && context.getPackageName().equals(BuildConfig.APPLICATION_ID)) {
                    getActivity().recreate();
                }
            } catch (Exception e) {
                Log.e("importConfigs", e.getMessage(), e);
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        FilePicker.fileCapture.launch(new String[]{"application/json"});
    }

    private boolean isInitialCheck = true;

    @SuppressLint("StringFormatInvalid")
    private void checkStateWpp(FragmentActivity activity) {
        boolean frameworkPresent = MainActivity.isXposedFrameworkPresent(requireContext());
        boolean hookEnabled = ModuleStatus.isModuleActive();
        boolean heartbeatEnabled = hasRecentModuleHeartbeat();
        ;

        updateModuleStatusUi(frameworkPresent, hookEnabled, heartbeatEnabled);

        if (isInstalled(FeatureLoader.PACKAGE_WPP) && App.isOriginalPackage()) {
            disableWpp(activity);
        } else {
            binding.status2.setVisibility(View.GONE);
        }

        if (isInstalled(FeatureLoader.PACKAGE_BUSINESS)) {
            disableBusiness(activity);
        } else {
            binding.status3.setVisibility(View.GONE);
        }

        // We still send the check broadcast to keep the heartbeat alive for when WhatsApp IS running,
        // but we no longer wait for its response to determine the basic "Enabled" status.
        checkWpp(activity);

        binding.deviceName.setText(Build.MANUFACTURER);
        binding.sdk.setText(String.valueOf(Build.VERSION.SDK_INT));
        binding.modelName.setText(Build.DEVICE);
        
        String xposedVer = getXposedFrameworkVersion();
        String[] xposedParts = xposedVer.split("\\|");
        if (xposedParts.length == 2) {
            binding.xposedVersionLabel.setText(xposedParts[0] + " API");
            binding.xposedVersion.setText(xposedParts[1]);
        } else {
            binding.xposedVersionLabel.setText("Xposed/LSPosed API");
            binding.xposedVersion.setText(xposedVer);
        }

        if (App.isOriginalPackage()) {
            checkPackageVersion(activity, FeatureLoader.PACKAGE_WPP, binding.wppVersionRow, binding.wppInstalledVersion,
                    binding.wppVersionStatus, binding.wppStatusIcon, binding.wppUnsupportedBtn,
                    R.array.supported_versions_wpp);
        } else {
            // Hide WhatsApp section if not the original package flavor
            View parent = (View) binding.wppInstalledVersion.getParent().getParent().getParent();
            if (parent != null) {
                parent.setVisibility(View.GONE);
            }
            View divider = (View) ((ViewGroup) parent.getParent())
                    .getChildAt(((ViewGroup) parent.getParent()).indexOfChild(parent) + 1);
            if (divider != null) {
                divider.setVisibility(View.GONE);
            }
        }

        checkPackageVersion(activity, FeatureLoader.PACKAGE_BUSINESS, binding.businessVersionRow, binding.businessInstalledVersion,
                binding.businessVersionStatus, binding.businessStatusIcon, binding.businessUnsupportedBtn,
                R.array.supported_versions_business);
    }

    private void updateModuleStatusUi(boolean frameworkPresent, boolean hookEnabled, boolean heartbeatEnabled) {
        // Show version name in the badge
        binding.statusSummary.setText(String.format("v%s", BuildConfig.VERSION_NAME));
        binding.statusSummary.setVisibility(View.VISIBLE);

        if (hookEnabled || heartbeatEnabled) {
            // Module is active (either directly hooked or validated via WhatsApp process heartbeat)
            binding.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24);
            binding.statusIcon.setColorFilter(null);
            binding.statusTitle.setText(R.string.module_enabled);
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.hero_glow_enabled);
        } else if (frameworkPresent) {
            // IDLE STATE: Framework detected but nothing is happening
            binding.statusIcon.setImageResource(R.drawable.ic_round_warning_24);
            binding.statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light));
            binding.statusTitle.setText(R.string.module_disabled);
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.hero_glow_disabled);
        } else {
            // ERROR STATE: No framework at all
            binding.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
            binding.statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
            binding.statusTitle.setText(R.string.framework_not_detected);
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.hero_glow_disabled);
        }
    }

    private boolean isInstalled(String packageWpp) {
        try {
            App.getInstance().getPackageManager().getPackageInfo(packageWpp, 0);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private void checkPackageVersion(FragmentActivity activity, String packageName,
            View rowView,
            MaterialTextView versionView,
            MaterialTextView statusView, ImageView iconView,
            View unsupportedBtnView,
            int supportedArrayResId) {

        int colorError = ContextCompat.getColor(activity, android.R.color.holo_red_light);
        int colorOutline = ContextCompat.getColor(activity, android.R.color.darker_gray);
        int colorSuccess = 0xFF2E7D32; // Premium green color!

        try {
            var packageInfo = App.getInstance().getPackageManager().getPackageInfo(packageName, 0);
            var installedVersion = packageInfo.versionName;
            versionView.setText(installedVersion);

            var supportedList = new ArrayList<>(Arrays.asList(activity.getResources().getStringArray(supportedArrayResId)));
            var prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean isCustomizeEnabled = prefs.getBoolean("customize_supported_versions", false);
            if (isCustomizeEnabled) {
                String customKey = FeatureLoader.PACKAGE_WPP.equals(packageName) ? "custom_versions_wpp" : "custom_versions_business";
                java.util.Set<String> customSet = prefs.getStringSet(customKey, null);
                if (customSet != null) {
                    supportedList.addAll(customSet);
                }
            }

            boolean isSupported = false;
            boolean isCustomSupported = false;
            if (installedVersion != null) {
                isSupported = supportedList.stream().anyMatch(s -> {
                    String target = s.endsWith(".xx") ? s.replace(".xx", "") : s;
                    return installedVersion.startsWith(target);
                });

                if (isSupported && isCustomizeEnabled) {
                    // Check if it's not supported by standard/system array
                    var systemList = Arrays.asList(activity.getResources().getStringArray(supportedArrayResId));
                    boolean systemSupported = systemList.stream().anyMatch(s -> installedVersion.startsWith(s.replace(".xx", "")));
                    if (!systemSupported) {
                        isCustomSupported = true;
                    }
                }
            }

            unsupportedBtnView.setVisibility(isSupported ? View.GONE : View.VISIBLE);
            if (!isSupported) {
                unsupportedBtnView.setOnClickListener(v -> {
                    showUnsupportedVersionDialog(activity, packageName);
                });
            }

            if (isSupported) {
                boolean isBetaModule = BuildConfig.VERSION_NAME.toLowerCase().contains("beta");
                if (!isBetaModule && ApkMirrorFeedHelper.isBetaVersion(activity, packageName, installedVersion)) {
                    statusView.setText("Beta Unsupported");
                    statusView.setTextColor(colorError);
                    iconView.setImageResource(R.drawable.ic_round_error_outline_24);
                    iconView.setColorFilter(colorError);

                    unsupportedBtnView.setVisibility(View.VISIBLE);
                    unsupportedBtnView.setOnClickListener(v -> {
                        showBetaIssueDialog(activity, packageName);
                    });

                    rowView.setOnClickListener(v -> {
                        showBetaIssueDialog(activity, packageName);
                    });
                } else {
                    if (isCustomSupported) {
                        statusView.setText("Custom Supported");
                        TypedValue typedValue = new TypedValue();
                        activity.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
                        statusView.setTextColor(typedValue.data);
                        iconView.setImageResource(R.drawable.ic_round_check_circle_24);
                        iconView.setColorFilter(typedValue.data);
                    } else {
                        statusView.setText("Supported");
                        statusView.setTextColor(colorSuccess);
                        iconView.setImageResource(R.drawable.ic_round_check_circle_24);
                        iconView.setColorFilter(colorSuccess);
                    }
                    rowView.setOnClickListener(null);
                    rowView.setClickable(false);
                }
            } else {
                statusView.setText("Not Supported");
                statusView.setTextColor(colorError);
                iconView.setImageResource(R.drawable.ic_round_error_outline_24);
                iconView.setColorFilter(colorError);
                rowView.setOnClickListener(v -> {
                    showUnsupportedVersionDialog(activity, packageName);
                });
            }
        } catch (Exception e) {
            versionView.setText("Not Installed");
            statusView.setText("-");
            unsupportedBtnView.setVisibility(View.GONE);
            iconView.setImageResource(R.drawable.ic_round_error_outline_24);
            iconView.setColorFilter(colorOutline);
            rowView.setOnClickListener(null);
            rowView.setClickable(false);
        }
    }

    private void showBetaIssueDialog(FragmentActivity activity, String packageName) {
        String appName = FeatureLoader.PACKAGE_WPP.equals(packageName) ? "WhatsApp" : "WhatsApp Business";
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(activity);
            View view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_action, null);
            dialog.setContentView(view);

            ((MaterialTextView) view.findViewById(R.id.bs_title)).setText("WhatsApp Beta Detected");
            ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(
                    "You are using a beta version of " + appName + " while WaEnhancerX is currently set to the Stable update channel.\n\n" +
                    "To ensure full compatibility and stay up-to-date with every new WhatsApp beta update, we highly recommend switching WaEnhancerX to the Beta update channel.");

            MaterialButton okBtn = view.findViewById(R.id.bs_confirm_btn);
            okBtn.setText("Leave WhatsApp Beta");
            okBtn.setOnClickListener(v2 -> {
                try {
                    String url = FeatureLoader.PACKAGE_WPP.equals(packageName)
                            ? "https://play.google.com/apps/testing/com.whatsapp"
                            : "https://play.google.com/apps/testing/com.whatsapp.w4b";
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e("WAE_BETA", "Failed to open beta URL: " + e.getMessage());
                }
                dialog.dismiss();
            });

            MaterialButton cancelBtn = view.findViewById(R.id.bs_cancel_btn);
            cancelBtn.setText("Switch to WAEX Beta");
            cancelBtn.setOnClickListener(v2 -> {
                try {
                    Intent intent = new Intent(activity, ChangelogActivity.class);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e("WAE_BETA", "Failed to open ChangelogActivity: " + e.getMessage());
                }
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            Log.e("WAE_BETA", "Error showing bottom sheet: " + e.getMessage());
        }
    }

    private void showUnsupportedVersionDialog(FragmentActivity activity, String packageName) {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(activity);
            View view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_action, null);
            dialog.setContentView(view);

            ((MaterialTextView) view.findViewById(R.id.bs_title)).setText("Unsupported Version");
            ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(
                    "Your installed version of WhatsApp/Business is not officially supported by your current version of WaEnhancerX.\n\n" +
                    "To resolve this, you can check if a newer update of WaEnhancerX is available, or directly enable customize version support to bypass the check.");

            MaterialButton checkUpdateBtn = view.findViewById(R.id.bs_confirm_btn);
            checkUpdateBtn.setText("Check for Update");
            checkUpdateBtn.setOnClickListener(v2 -> {
                dialog.dismiss();
                runSolveUpdateCheck(activity);
            });

            MaterialButton customizeBtn = view.findViewById(R.id.bs_cancel_btn);
            customizeBtn.setText("Customize Support");
            customizeBtn.setOnClickListener(v2 -> {
                dialog.dismiss();
                enableCustomizeSupportAndRedirect(activity);
            });

            dialog.show();
        } catch (Exception e) {
            Log.e("WAE_UNSUPPORTED", "Error showing unsupported version dialog: " + e.getMessage());
        }
    }

    private void runSolveUpdateCheck(FragmentActivity activity) {
        Toast.makeText(activity, "Checking for latest version...", Toast.LENGTH_SHORT).show();
        var updateChecker = new UpdateChecker(activity);
        updateChecker.setSilent(true);
        updateChecker.setOnUpdateFoundListener((version, tagName, changelog, publishedAt, downloadUrl) -> {
            activity.runOnUiThread(() -> {
                updateChecker.showUpdateDialog(version, tagName, changelog, publishedAt, downloadUrl);
            });
        });
        
        updateChecker.setOnNoUpdateFoundListener(() -> {
            boolean customizeEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean("customize_supported_versions", false);
            activity.runOnUiThread(() -> {
                if (customizeEnabled) {
                    Toast.makeText(activity, "You have the latest version. Redirecting to customize supported versions...", Toast.LENGTH_LONG).show();
                    activity.startActivity(new Intent(activity, SupportedVersionsActivity.class));
                } else {
                    showEnableCustomizePrompt(activity);
                }
            });
        });
        
        CompletableFuture.runAsync(updateChecker);
    }

    private void showEnableCustomizePrompt(FragmentActivity activity) {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(activity);
            View view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_action, null);
            dialog.setContentView(view);

            ((MaterialTextView) view.findViewById(R.id.bs_title)).setText("Latest Version Installed");
            ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(
                    "You are already running the latest version of WaEnhancerX.\n\n" +
                    "To proceed, you can enable Customize Version Support to allow your current WhatsApp version.");

            MaterialButton confirmBtn = view.findViewById(R.id.bs_confirm_btn);
            confirmBtn.setText("Enable Customize Support");
            confirmBtn.setOnClickListener(v -> {
                dialog.dismiss();
                enableCustomizeSupportAndRedirect(activity);
            });

            MaterialButton cancelBtn = view.findViewById(R.id.bs_cancel_btn);
            cancelBtn.setText("Cancel");
            cancelBtn.setOnClickListener(v -> dialog.dismiss());

            dialog.show();
        } catch (Exception e) {
            Log.e("WAE_UNSUPPORTED", "Error showing enable customize prompt: " + e.getMessage());
        }
    }

    private void enableCustomizeSupportAndRedirect(FragmentActivity activity) {
        PreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean("customize_supported_versions", true)
                .apply();
        Toast.makeText(activity, "Customized version support enabled!", Toast.LENGTH_SHORT).show();
        activity.startActivity(new Intent(activity, SupportedVersionsActivity.class));
    }

    private void showBetaWarningDialog(FragmentActivity activity, String packageName, boolean forceShow) {
        String appName = FeatureLoader.PACKAGE_WPP.equals(packageName) ? "WhatsApp" : "WhatsApp Business";
        String prefKey = FeatureLoader.PACKAGE_WPP.equals(packageName) ? "last_beta_warning_dismissed_wpp" : "last_beta_warning_dismissed_business";

        SharedPreferences prefs = activity.getSharedPreferences("ApkMirrorCache", Context.MODE_PRIVATE);
        long lastDismissed = prefs.getLong(prefKey, 0L);
        long now = System.currentTimeMillis();

        if (!forceShow && (now - lastDismissed < TimeUnit.DAYS.toMillis(1))) {
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Beta Version Detected")
                .setMessage("You have installed a beta version of " + appName + ". WaEnhancerX is designed for the stable versions of WhatsApp, if you face any bugs please switch to a stable version of " + appName + ".")
                .setPositiveButton("Dismiss for 1 Day", (dialog, which) -> {
                    prefs.edit().putLong(prefKey, System.currentTimeMillis()).apply();
                    dialog.dismiss();
                })
                .setNegativeButton("Close", (dialog, which) -> {
                    prefs.edit().putLong(prefKey, System.currentTimeMillis()).apply();
                    dialog.dismiss();
                })
                .setOnCancelListener(dialog -> {
                    prefs.edit().putLong(prefKey, System.currentTimeMillis()).apply();
                })
                .show();
    }

    private void disableBusiness(FragmentActivity activity) {
        binding.statusTitle3.setText(R.string.business_is_not_running_or_has_not_been_activated_in_lsposed);
        binding.statusDotBusiness.setBackgroundResource(R.drawable.status_dot_inactive);
        binding.statusSummary3.setVisibility(View.GONE);
        binding.rebootBtn2.setVisibility(View.GONE);
    }

    private void disableWpp(FragmentActivity activity) {
        binding.statusTitle2.setText(R.string.whatsapp_is_not_running_or_has_not_been_activated_in_lsposed);
        binding.statusDotWpp.setBackgroundResource(R.drawable.status_dot_inactive);
        binding.statusSummary1.setVisibility(View.GONE);
        binding.rebootBtn.setVisibility(View.GONE);
    }

    private static void checkWpp(FragmentActivity activity) {
        ;
        Intent checkWpp = new Intent(BuildConfig.APPLICATION_ID + ".CHECK_WPP");
        // Ensure broadcast reaches WhatsApp even if it is in background/stopped state (Android 14+)
        checkWpp.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        activity.sendBroadcast(checkWpp);
    }

    private void setupReleaseChannelSelector() {
        syncReleaseChannelToInstalled();
        binding.releaseChannelGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String selectedChannel = checkedId == R.id.release_channel_beta_btn ? "beta" : "stable";
            String installedChannel = getInstalledReleaseChannel();
            if (!selectedChannel.equals(installedChannel)) {
                updateReleaseChannelUi(installedChannel);
                showReleaseInstallPrompt(selectedChannel);
                return;
            }
            setReleaseChannel(selectedChannel);
        });
    }

    private void syncReleaseChannelToInstalled() {
        String installedChannel = getInstalledReleaseChannel();
        setReleaseChannel(installedChannel);
        updateReleaseChannelUi(installedChannel);
    }

    private String getInstalledReleaseChannel() {
        return BuildConfig.VERSION_NAME != null && BuildConfig.VERSION_NAME.contains("-beta-") ? "beta" : "stable";
    }

    private void setReleaseChannel(String channel) {
        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit().putString("release_channel", channel).apply();
        WppCore.setPrivString("release_channel", channel);
    }

    private void updateReleaseChannelUi(String channel) {
        if ("beta".equals(channel)) {
            binding.releaseChannelGroup.check(R.id.release_channel_beta_btn);
        } else {
            binding.releaseChannelGroup.check(R.id.release_channel_stable_btn);
        }
    }

    private void showReleaseInstallPrompt(String selectedChannel) {
        boolean isBeta = "beta".equals(selectedChannel);
        String title = getString(isBeta ? R.string.release_channel_beta_install_title : R.string.release_channel_stable_install_title);
        String message = getString(isBeta ? R.string.release_channel_beta_install_message : R.string.release_channel_stable_install_message);
        String url = RELEASES_URL;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.download, (dialog, which) -> {
                    Intent intent = new Intent(requireContext(), ChangelogActivity.class);
                    intent.putExtra(ChangelogActivity.EXTRA_TARGET_CHANNEL, selectedChannel);
                    startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    // setModuleActiveState is replaced by updateModuleStatusUi
    private void markModuleActive() {
        sLastHeartbeatTime = System.currentTimeMillis();
    }

    private boolean isWhatsAppRunning(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
        if (runningProcesses != null) {
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                if ("com.whatsapp".equals(processInfo.processName) || "com.whatsapp.w4b".equals(processInfo.processName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRecentModuleHeartbeat() {
        return (System.currentTimeMillis() - sLastHeartbeatTime) < 8000;
    }

    private void showClearCacheConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_obfuscate_cache)
                .setMessage(R.string.clear_cache_confirmation)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    UnobfuscatorCache.init(App.getInstance());
                    UnobfuscatorCache.getInstance().clearCache();

                    // Send broadcast to WhatsApp/Business processes to clear their internal cache
                    Intent clearIntent = new Intent(BuildConfig.APPLICATION_ID + ".CLEAR_OBFUSCATE_CACHE");
                    requireContext().sendBroadcast(clearIntent);

                    Utils.showToast(getString(R.string.obfuscate_cache_cleared), Toast.LENGTH_SHORT);
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void setupUpdateBanner() {
        binding.dismissUpdateBtn.setOnClickListener(v -> {
            animateClick(v);
            binding.updateNotificationCard.setVisibility(View.GONE);
            // Optionally store ignored version in prefs
        });

        binding.viewChangelogBtn.setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(requireContext(), ChangelogActivity.class);
            startActivity(intent);
        });

        binding.updateNowBtn.setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(requireContext(), ChangelogActivity.class);
            startActivity(intent);
        });
    }

    private void checkForUpdates() {
        var updateChecker = new UpdateChecker(requireActivity());
        updateChecker.setSilent(true);
        updateChecker.setOnUpdateFoundListener((version, tagName, changelog, publishedAt, downloadUrl) -> {
            if (binding == null) {
                return;
            }
            this.pendingUpdateUrl = downloadUrl;
            this.pendingUpdateVersion = version;

            boolean isBeta = tagName != null && tagName.contains("-beta-");
            int titleResId = isBeta ? R.string.new_beta_update_available : R.string.new_stable_update_available;
            binding.updateNotificationTitle.setText(getString(titleResId, version));
            binding.updateNotificationChangelog.setText(changelog);
            binding.updateNotificationCard.setVisibility(View.VISIBLE);

            // Animation for the banner
            var anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
            binding.updateNotificationCard.startAnimation(anim);
        });
        CompletableFuture.runAsync(updateChecker);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void launchLicenseActivity(Context context) {
        try {
            Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
            Intent intent = new Intent(context, clazz);
            context.startActivity(intent);
        } catch (ClassNotFoundException e) {
            Toast.makeText(context, "Pro features are not available.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getXposedFrameworkVersion() {
        Context context = getContext();
        if (context == null) return "Unknown";
        
        String apiVal = "";
        try {
            Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge");
            Method getVersion = bridge.getMethod("getXposedVersion");
            int ver = (Integer) getVersion.invoke(null);
            if (ver > 0) {
                apiVal = String.valueOf(ver);
            }
        } catch (Throwable ignored) {}
        
        if (apiVal.isEmpty()) {
            try {
                SharedPreferences localPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                int localApi = localPrefs.getInt("active_xposed_api_version", 0);
                if (localApi > 0) {
                    apiVal = String.valueOf(localApi);
                }
            } catch (Throwable ignored) {}
        }
        
        if (apiVal.isEmpty()) {
            try {
                Class<?> sp = Class.forName("android.os.SystemProperties");
                Method get = sp.getMethod("get", String.class, String.class);
                apiVal = (String) get.invoke(null, "debug.waenhancer.lsposed.api", "");
            } catch (Throwable ignored) {}
        }
        
        boolean isActive = false;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            String val = (String) get.invoke(null, "debug.waenhancer.lsposed", "0");
            if ("1".equals(val)) {
                isActive = true;
            }
        } catch (Throwable ignored) {}
        
        if (!isActive) {
            isActive = ModuleStatus.isModuleActive();
        }

        if (apiVal.isEmpty() && !isActive) {
            return "LSPosed|Not Detected";
        }

        // Determine framework name (LSPosed, EdXposed, or Xposed)
        String frameworkName = "LSPosed";
        PackageManager pm = context.getPackageManager();
        String[] managerPackages = {
                "org.lsposed.manager", 
                "io.github.lsposed.manager",
                "org.meowcat.edxposed.manager", 
                "com.solohsu.android.edxp.manager",
                "de.robv.android.xposed.installer"
        };
        for (String pkg : managerPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                frameworkName = pkg.contains("lsposed") ? "LSPosed" : (pkg.contains("edxposed") ? "EdXposed" : "Xposed");
                break;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        String finalApi = !apiVal.isEmpty() ? apiVal : "93";
        return frameworkName + "|" + finalApi;
    }
}