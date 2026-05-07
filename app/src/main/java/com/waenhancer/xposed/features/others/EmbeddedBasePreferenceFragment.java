package com.waenhancer.xposed.features.others;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
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
import com.waenhancer.xposed.utils.ThemeUtils;
import com.waenhancer.xposed.utils.Utils;
import com.waenhancer.xposed.utils.XPrefManager;

import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

import rikka.material.preference.MaterialSwitchPreference;

public abstract class EmbeddedBasePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public interface ToolbarTitleProvider {
        CharSequence getToolbarTitle();
    }

    private static final String PREFS_NAME = "wae_embedded_prefs";
    /** Public so {@link EmbeddedSettingsDialogFragment} can read the title. */
    public static final String ARG_TITLE = "embedded_title";

    protected ProviderSharedPreferences mPrefs;
    private boolean suppressRestartBroadcast;
    private final Handler restartBroadcastHandler = new Handler(Looper.getMainLooper());
    private final Runnable restartBroadcastRunnable = () -> {
        if (!isAdded() || getContext() == null) {
            return;
        }
        requireContext().sendBroadcast(new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"));
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        var localPrefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        mPrefs = new ProviderSharedPreferences(requireContext(), localPrefs, getModuleSharedPreferences(requireContext()));

        getPreferenceManager().setPreferenceDataStore(new androidx.preference.PreferenceDataStore() {
            @Override
            public void putString(String key, @Nullable String value) { mPrefs.edit().putString(key, value).apply(); }
            @Override
            @Nullable
            public String getString(String key, @Nullable String defValue) { return mPrefs.getString(key, defValue); }
            @Override
            public void putBoolean(String key, boolean value) { mPrefs.edit().putBoolean(key, value).apply(); }
            @Override
            public boolean getBoolean(String key, boolean defValue) { return mPrefs.getBoolean(key, defValue); }
            @Override
            public void putInt(String key, int value) { mPrefs.edit().putInt(key, value).apply(); }
            @Override
            public int getInt(String key, int defValue) { return mPrefs.getInt(key, defValue); }
            @Override
            public void putFloat(String key, float value) { mPrefs.edit().putFloat(key, value).apply(); }
            @Override
            public float getFloat(String key, float defValue) { return mPrefs.getFloat(key, defValue); }
            @Override
            public void putLong(String key, long value) { mPrefs.edit().putLong(key, value).apply(); }
            @Override
            public long getLong(String key, long defValue) { return mPrefs.getLong(key, defValue); }
            @Override
            public void putStringSet(String key, @Nullable Set<String> values) {
                mPrefs.edit().putStringSet(key, values).apply();
            }
            @Override
            @Nullable
            public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
                Set<String> value = mPrefs.getStringSet(key, defValues);
                return value != null ? value : (defValues != null ? defValues : new LinkedHashSet<>());
            }
        });

    }

    @Override
    public android.content.Context getContext() {
        android.content.Context context = super.getContext();
        if (context == null) return null;
        int themeRes = ThemeUtils.isNightMode(context) ? R.style.Theme : R.style.Theme_Light;
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
        restartBroadcastHandler.removeCallbacks(restartBroadcastRunnable);
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }
        runWithoutRestartBroadcast(() -> applyDynamicStates(null));
        refreshSpecialSummaries();
    }

    @Override
    public void onPause() {
        super.onPause();
        restartBroadcastHandler.removeCallbacks(restartBroadcastRunnable);
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof androidx.preference.ListPreference) {
            androidx.preference.ListPreference listPref = (androidx.preference.ListPreference) preference;
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
        if (preference instanceof androidx.preference.MultiSelectListPreference) {
            androidx.preference.MultiSelectListPreference multiPref = (androidx.preference.MultiSelectListPreference) preference;
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
        if (preference instanceof androidx.preference.EditTextPreference) {
            androidx.preference.EditTextPreference editPref = (androidx.preference.EditTextPreference) preference;
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
        if ("call_recording_settings".equals(preference.getKey())) {
            try {
                Intent intent = new Intent();
                intent.setClassName(BuildConfig.APPLICATION_ID, "com.waenhancer.activities.CallRecordingSettingsActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                requireContext().startActivity(intent);
            } catch (Exception e) {
                Utils.showToast("Failed to open Recording Settings", android.widget.Toast.LENGTH_SHORT);
            }
            return true;
        }
        if ("call_recording_manage".equals(preference.getKey())) {
            try {
                Intent intent = new Intent();
                intent.setClassName(BuildConfig.APPLICATION_ID, "com.waenhancer.activities.RecordingsActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                requireContext().startActivity(intent);
            } catch (Exception e) {
                Utils.showToast("Failed to open Recordings Manager", android.widget.Toast.LENGTH_SHORT);
            }
            return true;
        }
        if (preference.getFragment() != null) {
            navigateToFragment(preference);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        runWithoutRestartBroadcast(() -> applyDynamicStates(key));
        refreshSpecialSummaries();
        if (!suppressRestartBroadcast) {
            scheduleRestartBroadcast();
        }
    }

    private void scheduleRestartBroadcast() {
        if (!isResumed()) {
            return;
        }
        restartBroadcastHandler.removeCallbacks(restartBroadcastRunnable);
        restartBroadcastHandler.postDelayed(restartBroadcastRunnable, 250);
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
            getListView().setPadding(0, Utils.dipToPixels(12), 0, Utils.dipToPixels(24));
        }
        setDivider(null);
    }

    protected void refreshSpecialSummaries() {
        Preference statusRules = findPreference("auto_status_forward_rules_pref");
        if (statusRules instanceof StatusForwardRulesPreference) {
            StatusForwardRulesPreference rulesPreference = (StatusForwardRulesPreference) statusRules;
            rulesPreference.refresh();
        }
    }

    private void navigateToFragment(@NonNull Preference preference) {
        try {
            Class<?> clazz = Class.forName(preference.getFragment());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Fragment)) {
                return;
            }
            Fragment fragment = (Fragment) instance;
            Bundle args = new Bundle();
            args.putAll(preference.getExtras());
            if (preference.getTitle() != null) {
                args.putCharSequence(ARG_TITLE, preference.getTitle());
            }
            fragment.setArguments(args);

            // Walk up the parent chain to find the owning dialog so we use
            // its childFragmentManager and containerId. This keeps all navigation
            // self-contained within the dialog and works in the host process.
            EmbeddedSettingsDialogFragment owningDialog = findOwningDialog();
            if (owningDialog != null) {
                owningDialog.navigateTo(fragment, preference.getTitle());
                return;
            }

            // Fallback: use parent fragment manager with our view's parent as container.
            try {
                int containerId = requireView().getParent() instanceof android.view.View
                        ? ((android.view.View) requireView().getParent()).getId()
                        : android.view.View.NO_ID;
                if (containerId != android.view.View.NO_ID) {
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(containerId, fragment)
                            .addToBackStack("wae_embedded")
                            .commit();
                    return;
                }
            } catch (Throwable ignored) {}

            Utils.showToast("Unable to open settings screen", android.widget.Toast.LENGTH_SHORT);
        } catch (Exception e) {
            Utils.showToast("Unable to open settings screen", android.widget.Toast.LENGTH_SHORT);
        }
    }

    /**
     * Walk the parent fragment chain to find the {@link EmbeddedSettingsDialogFragment}
     * that owns this preference page, if any.
     */
    @Nullable
    private EmbeddedSettingsDialogFragment findOwningDialog() {
        Fragment parent = getParentFragment();
        while (parent != null) {
            if (parent instanceof EmbeddedSettingsDialogFragment) {
                return (EmbeddedSettingsDialogFragment) parent;
            }
            parent = parent.getParentFragment();
        }
        return null;
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
        if (!enabled && pref instanceof MaterialSwitchPreference) {
            MaterialSwitchPreference switchPreference = (MaterialSwitchPreference) pref;
            if (switchPreference.isChecked()) {
                runWithoutRestartBroadcast(() -> switchPreference.setChecked(false));
            }
        }
    }

    private void runWithoutRestartBroadcast(@NonNull Runnable runnable) {
        boolean previous = suppressRestartBroadcast;
        suppressRestartBroadcast = true;
        try {
            runnable.run();
        } finally {
            suppressRestartBroadcast = previous;
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
            try {
                int callType = Integer.parseInt(mPrefs.getString("call_privacy", "0"));
                callBlockContacts.setEnabled(callType == 3);
                callWhiteContacts.setEnabled(callType == 4);
            } catch (Exception ignored) {}
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
        if (mPrefs.getBoolean(key, false)) {
            runWithoutRestartBroadcast(() -> mPrefs.edit().putBoolean(key, false).apply());
        }
        setPreferenceState(key, false);
        pref.setSummary(unsupportedSummary);
    }

    private boolean isSeparateGroupSupported() {
        try {
            var packageInfo = requireContext().getPackageManager().getPackageInfo("com.whatsapp", 0);
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
        if (newValue instanceof Boolean && (Boolean) newValue) {
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

    private SharedPreferences getModuleSharedPreferences(android.content.Context context) {
        return XPrefManager.getPref(context);
    }
}
