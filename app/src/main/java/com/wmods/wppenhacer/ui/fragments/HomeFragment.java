package com.wmods.wppenhacer.ui.fragments;

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

import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.MainActivity;
import com.wmods.wppenhacer.databinding.FragmentHomeBinding;
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment;
import com.wmods.wppenhacer.utils.FilePicker;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.utils.Utils;

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

public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var intentFilter = new IntentFilter(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
        ContextCompat.registerReceiver(requireContext(), new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    if (FeatureLoader.PACKAGE_WPP.equals(intent.getStringExtra("PKG")))
                        receiverBroadcastWpp(context, intent);
                    else
                        receiverBroadcastBusiness(context, intent);
                } catch (Exception ignored) {
                }
            }
        }, intentFilter, ContextCompat.RECEIVER_EXPORTED);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        checkStateWpp(requireActivity());

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

        startCardAnimations();

        return binding.getRoot();
    }

    private void startCardAnimations() {
        var slideUp = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
        var fadeIn = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);

        binding.status.startAnimation(slideUp);

        binding.status2.postDelayed(() -> {
            var anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
            binding.status2.startAnimation(anim);
        }, 100);

        binding.status3.postDelayed(() -> {
            var anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
            binding.status3.startAnimation(anim);
        }, 100);

        binding.infoCard.postDelayed(() -> {
            binding.infoCard.startAnimation(fadeIn);
        }, 200);
    }

    private void animateClick(View view) {
        var scaleIn = AnimationUtils.loadAnimation(getContext(), R.anim.scale_in);
        view.startAnimation(scaleIn);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }

    @SuppressLint("StringFormatInvalid")
    private void receiverBroadcastBusiness(Context context, Intent intent) {
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
        var bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(context);
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
    }

    private static @NonNull JSONObject getJsonObject(SharedPreferences prefs) throws JSONException {
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
        FilePicker.setOnUriPickedListener((uri) -> {
            try {
                try (var input = context.getContentResolver().openInputStream(uri)) {
                    var data = IOUtils.toString(input);
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
            } catch (Exception e) {
                Log.e("importConfigs", e.getMessage(), e);
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        FilePicker.fileCapture.launch(new String[] { "application/json" });
    }

    @SuppressLint("StringFormatInvalid")
    private void checkStateWpp(FragmentActivity activity) {

        if (MainActivity.isXposedEnabled()) {
            binding.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24);
            binding.statusTitle.setText(R.string.module_enabled);
            binding.statusSummary.setText(String.format(getString(R.string.version_s), BuildConfig.VERSION_NAME));
            // Use hero glow enabled drawable
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.hero_glow_enabled);
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
            binding.statusTitle.setText(R.string.module_disabled);
            // Use hero glow disabled drawable
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.hero_glow_disabled);
            binding.statusSummary.setVisibility(View.GONE);
        }
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
        checkWpp(activity);
        binding.deviceName.setText(Build.MANUFACTURER);
        binding.sdk.setText(String.valueOf(Build.VERSION.SDK_INT));
        binding.modelName.setText(Build.DEVICE);

        if (App.isOriginalPackage()) {
            checkPackageVersion(activity, FeatureLoader.PACKAGE_WPP, binding.wppInstalledVersion,
                    binding.wppVersionStatus, binding.wppStatusIcon, R.array.supported_versions_wpp);
        } else {
            // Hide WhatsApp section if not the original package flavor
            View parent = (View) binding.wppInstalledVersion.getParent().getParent();
            if (parent != null)
                parent.setVisibility(View.GONE);
            View divider = (View) ((ViewGroup) parent.getParent())
                    .getChildAt(((ViewGroup) parent.getParent()).indexOfChild(parent) + 1);
            if (divider != null)
                divider.setVisibility(View.GONE);
        }

        checkPackageVersion(activity, FeatureLoader.PACKAGE_BUSINESS, binding.businessInstalledVersion,
                binding.businessVersionStatus, binding.businessStatusIcon, R.array.supported_versions_business);
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
            com.google.android.material.textview.MaterialTextView versionView,
            com.google.android.material.textview.MaterialTextView statusView, android.widget.ImageView iconView,
            int supportedArrayResId) {
        // Fallback to standard android colors to bypass custom theme attribute mapping
        // issues
        int colorPrimary = androidx.core.content.ContextCompat.getColor(activity, android.R.color.holo_blue_light);
        int colorError = androidx.core.content.ContextCompat.getColor(activity, android.R.color.holo_red_light);
        int colorOutline = androidx.core.content.ContextCompat.getColor(activity, android.R.color.darker_gray);

        try {
            var packageInfo = App.getInstance().getPackageManager().getPackageInfo(packageName, 0);
            var installedVersion = packageInfo.versionName;
            versionView.setText(installedVersion);

            var supportedList = Arrays.asList(activity.getResources().getStringArray(supportedArrayResId));
            boolean isSupported = false;
            if (installedVersion != null) {
                isSupported = supportedList.stream().anyMatch(s -> installedVersion.startsWith(s.replace(".xx", "")));
            }

            if (isSupported) {
                statusView.setText("Supported");
                statusView.setTextColor(colorPrimary);
                iconView.setImageResource(R.drawable.ic_round_check_circle_24);
                iconView.setColorFilter(colorPrimary);
            } else {
                statusView.setText("Not Supported");
                statusView.setTextColor(colorError);
                iconView.setImageResource(R.drawable.ic_round_error_outline_24);
                iconView.setColorFilter(colorError);
            }
        } catch (Exception e) {
            versionView.setText("Not Installed");
            statusView.setText("-");
            iconView.setImageResource(R.drawable.ic_round_error_outline_24);
            iconView.setColorFilter(colorOutline);
        }
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
        Intent checkWpp = new Intent(BuildConfig.APPLICATION_ID + ".CHECK_WPP");
        activity.sendBroadcast(checkWpp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}