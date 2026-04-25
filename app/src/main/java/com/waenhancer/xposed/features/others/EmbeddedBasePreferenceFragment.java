package com.waenhancer.xposed.features.others;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.preference.StatusForwardRulesPreference;
import com.waenhancer.ui.helpers.BottomSheetHelper;
import com.waenhancer.xposed.bridge.client.ProviderSharedPreferences;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ResId;
import com.waenhancer.xposed.utils.Utils;

import java.util.Objects;

import rikka.material.preference.MaterialSwitchPreference;

public abstract class EmbeddedBasePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public interface ToolbarTitleProvider {
        CharSequence getToolbarTitle();
    }

    private static final String PREFS_NAME = "wae_embedded_prefs";
    private static final String ARG_TITLE = "embedded_title";

    protected ProviderSharedPreferences mPrefs;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        var localPrefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        mPrefs = new ProviderSharedPreferences(requireContext(), localPrefs);
        try {
            var field = androidx.preference.PreferenceManager.class.getDeclaredField("mSharedPreferences");
            field.setAccessible(true);
            field.set(getPreferenceManager(), mPrefs);
        } catch (Exception ignored) {
        }
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public android.content.Context getContext() {
        android.content.Context context = super.getContext();
        if (context == null) return null;
        int themeRes = DesignUtils.isNightMode() ? com.waenhancer.xposed.utils.ResId.style.Theme : com.waenhancer.xposed.utils.ResId.style.Theme_Light;
        return new android.view.ContextThemeWrapper(context, themeRes) {
            @Override
            public android.content.res.Resources getResources() {
                if (com.waenhancer.xposed.utils.XResManager.moduleResources != null) {
                    return com.waenhancer.xposed.utils.XResManager.moduleResources;
                }
                return super.getResources();
            }

            @Override
            public Object getSystemService(String name) {
                if (android.content.Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                    return android.view.LayoutInflater.from(getBaseContext()).cloneInContext(this);
                }
                return super.getSystemService(name);
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyDynamicStates(null);
        refreshSpecialSummaries();
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof androidx.preference.ListPreference listPref) {
            BottomSheetHelper.showSingleChoice(
                    getContext(),
                    listPref.getDialogTitle() != null ? listPref.getDialogTitle().toString()
                            : listPref.getTitle() != null ? listPref.getTitle().toString() : "",
                    listPref.getEntries(),
                    listPref.getEntryValues(),
                    listPref.getValue(),
                    (index, value) -> {
                        if (listPref.callChangeListener(value)) {
                            listPref.setValue(value);
                        }
                    });
            return;
        }
        if (preference instanceof androidx.preference.MultiSelectListPreference multiPref) {
            BottomSheetHelper.showMultiChoice(
                    getContext(),
                    multiPref.getDialogTitle() != null ? multiPref.getDialogTitle().toString()
                            : multiPref.getTitle() != null ? multiPref.getTitle().toString() : "",
                    multiPref.getEntries(),
                    multiPref.getEntryValues(),
                    multiPref.getValues(),
                    values -> {
                        if (multiPref.callChangeListener(values)) {
                            multiPref.setValues(values);
                        }
                    });
            return;
        }
        if (preference instanceof androidx.preference.EditTextPreference editPref) {
            BottomSheetHelper.showInput(
                    getContext(),
                    editPref.getDialogTitle() != null ? editPref.getDialogTitle().toString()
                            : editPref.getTitle() != null ? editPref.getTitle().toString() : "",
                    editPref.getText(),
                    getString(android.R.string.ok),
                    value -> {
                        if (editPref.callChangeListener(value)) {
                            editPref.setText(value);
                        }
                    });
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        if (preference.getFragment() != null) {
            navigateToFragment(preference);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        applyDynamicStates(key);
        refreshSpecialSummaries();
        try {
            requireContext().sendBroadcast(new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"));
        } catch (Exception ignored) {
        }
    }

    protected void setToolbarTitle(CharSequence title) {
        Bundle args = getArguments();
        if (args == null) {
            args = new Bundle();
            setArguments(args);
        }
        args.putCharSequence(ARG_TITLE, title);
    }

    protected void setToolbarTitle(int titleRes) {
        setToolbarTitle(getString(titleRes));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getListView() != null) {
            getListView().setVerticalScrollBarEnabled(false);
            getListView().setClipToPadding(false);
            getListView().setPadding(0, 0, 0, Utils.dipToPixels(16));
        }
    }

    protected void refreshSpecialSummaries() {
        Preference statusRules = findPreference("auto_status_forward_rules_pref");
        if (statusRules instanceof StatusForwardRulesPreference rulesPreference) {
            rulesPreference.refresh();
        }
    }

    private void navigateToFragment(@NonNull Preference preference) {
        try {
            Class<?> clazz = Class.forName(preference.getFragment());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Fragment fragment)) {
                return;
            }
            Bundle args = new Bundle();
            args.putAll(preference.getExtras());
            if (preference.getTitle() != null) {
                args.putCharSequence(ARG_TITLE, preference.getTitle());
            }
            fragment.setArguments(args);
            int containerId = ((View) requireView().getParent()).getId();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(containerId, fragment)
                    .addToBackStack("wae_embedded")
                    .commit();
        } catch (Exception e) {
            Utils.showToast("Unable to open settings screen", android.widget.Toast.LENGTH_SHORT);
        }
    }

    public CharSequence getToolbarTitle() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_TITLE)) {
            return args.getCharSequence(ARG_TITLE);
        }
        return getString(R.string.app_name);
    }

    private void setPreferenceState(String key, boolean enabled) {
        Preference pref = findPreference(key);
        if (pref == null) {
            return;
        }
        pref.setEnabled(enabled);
        if (!enabled && pref instanceof MaterialSwitchPreference switchPreference) {
            switchPreference.setChecked(false);
        }
    }

    private void applyDynamicStates(@Nullable String key) {
        if (mPrefs == null) {
            return;
        }
        boolean liteMode = mPrefs.getBoolean("lite_mode", false);
        if (liteMode) {
            setPreferenceState("wallpaper", false);
            setPreferenceState("custom_filters", false);
        }

        boolean changeColorEnabled = mPrefs.getBoolean("changecolor", false);
        String changeColorMode = mPrefs.getString("changecolor_mode", "manual");
        boolean monetAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        boolean useMonetColors = changeColorEnabled && monetAvailable && Objects.equals(changeColorMode, "monet");
        setPreferenceState("changecolor_mode", changeColorEnabled && monetAvailable);
        setPreferenceState("primary_color", changeColorEnabled && !useMonetColors);
        setPreferenceState("background_color", changeColorEnabled && !useMonetColors);
        setPreferenceState("text_color", changeColorEnabled && !useMonetColors);

        boolean igstatus = mPrefs.getBoolean("igstatus", false);
        setPreferenceState("oldstatus", !igstatus);

        boolean oldstatus = mPrefs.getBoolean("oldstatus", false);
        setPreferenceState("channels", !oldstatus);
        setPreferenceState("removechannel_rec", !oldstatus);
        setPreferenceState("status_style", !oldstatus);
        setPreferenceState("igstatus", !oldstatus);

        boolean channels = mPrefs.getBoolean("channels", false);
        setPreferenceState("removechannel_rec", !channels && !oldstatus);

        boolean freezelastseen = mPrefs.getBoolean("freezelastseen", false);
        setPreferenceState("show_freezeLastSeen", !freezelastseen);
        setPreferenceState("showonlinetext", !freezelastseen);
        setPreferenceState("dotonline", !freezelastseen);

        boolean separategroups = mPrefs.getBoolean("separategroups", false);
        setPreferenceState("filtergroups", !separategroups);

        updateGroupPref("separategroups", isSeparateGroupSupported(),
                R.string.separate_groups_sum,
                R.string.separate_groups_unsupported_sum);

        Preference callBlockContacts = findPreference("call_block_contacts");
        Preference callWhiteContacts = findPreference("call_white_contacts");
        if (callBlockContacts != null && callWhiteContacts != null) {
            int callType = Integer.parseInt(mPrefs.getString("call_privacy", "0"));
            callBlockContacts.setEnabled(callType == 3);
            callWhiteContacts.setEnabled(callType == 4);
        }

        if (Objects.equals(key, "force_english")) {
            mPrefs.edit().commit();
            Utils.doRestart(requireContext());
        }
    }

    private void updateGroupPref(String key, boolean supported, int supportedSummary, int unsupportedSummary) {
        Preference pref = findPreference(key);
        if (pref == null) {
            return;
        }
        if (supported) {
            pref.setEnabled(true);
            pref.setSummary(supportedSummary);
            return;
        }
        mPrefs.edit().putBoolean(key, false).apply();
        setPreferenceState(key, false);
        pref.setSummary(unsupportedSummary);
    }

    private boolean isSeparateGroupSupported() {
        try {
            var packageInfo = requireContext().getPackageManager().getPackageInfo(FeatureLoader.PACKAGE_WPP, 0);
            return isVersionAtMost(packageInfo.versionName, 2, 26, 12);
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isVersionAtMost(String versionName, int major, int minor, int patch) {
        if (versionName == null) {
            return true;
        }
        String[] parts = versionName.split("\\.");
        if (parts.length < 3) {
            return true;
        }
        try {
            int vMajor = Integer.parseInt(parts[0]);
            int vMinor = Integer.parseInt(parts[1]);
            int vPatch = Integer.parseInt(parts[2]);
            if (vMajor != major) {
                return vMajor < major;
            }
            if (vMinor != minor) {
                return vMinor < minor;
            }
            return vPatch <= patch;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    protected boolean checkStoragePermission(Object newValue) {
        if (newValue instanceof Boolean enabled && enabled) {
            boolean denied =
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager())
                            || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                            && ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED);
            if (denied) {
                com.waenhancer.App.showRequestStoragePermission(requireActivity());
                return false;
            }
        }
        return true;
    }
}
