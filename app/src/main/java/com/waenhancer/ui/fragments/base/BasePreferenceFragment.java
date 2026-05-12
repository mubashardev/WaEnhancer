package com.waenhancer.ui.fragments.base;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.waenhancer.App;
import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.utils.Utils;

import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

import rikka.material.preference.MaterialSwitchPreference;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String RELEASES_URL = "https://github.com/mubashardev/WaEnhancerX/releases";
    private static final String LATEST_STABLE_URL = "https://github.com/mubashardev/WaEnhancerX/releases/latest";
    protected SharedPreferences mPrefs;
    private boolean suppressRestartBroadcast;
    private final Handler restartBroadcastHandler = new Handler(Looper.getMainLooper());
    private final Runnable restartBroadcastRunnable = () -> {
        if (!isAdded() || getContext() == null) {
            return;
        }
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
        App.getInstance().sendBroadcast(intent);
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        var localPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPrefs = new com.waenhancer.preference.SafeSharedPreferences(localPrefs);

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

        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    requireActivity().finish();
                }
            }
        });
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        runWithoutRestartBroadcast(() -> chanceStates(null));
        monitorPreference();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPrefs != null) {
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }
        setDisplayHomeAsUpEnabled(true);
        initializeReleaseChannelPreference();
        setupReleaseChannelPreference();
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
    public void onDestroy() {
        super.onDestroy();
        restartBroadcastHandler.removeCallbacks(restartBroadcastRunnable);
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onDisplayPreferenceDialog(androidx.preference.Preference preference) {
        if (preference instanceof androidx.preference.ListPreference) {
            androidx.preference.ListPreference listPref = (androidx.preference.ListPreference) preference;
            com.waenhancer.ui.helpers.BottomSheetHelper.showSingleChoice(
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
        } else if (preference instanceof androidx.preference.MultiSelectListPreference) {
            androidx.preference.MultiSelectListPreference multiPref = (androidx.preference.MultiSelectListPreference) preference;
            com.waenhancer.ui.helpers.BottomSheetHelper.showMultiChoice(
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
        } else if (preference instanceof androidx.preference.EditTextPreference) {
            androidx.preference.EditTextPreference editPref = (androidx.preference.EditTextPreference) preference;
            com.waenhancer.ui.helpers.BottomSheetHelper.showInput(
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        ;
        if (Objects.equals(s, "release_channel")) {
            String channel = mPrefs.getString("release_channel", "stable");
            WppCore.setPrivString("release_channel", channel);
        }
        
        // Flag that a restart is needed for the changes to take effect in WhatsApp
        // Ignore internal/meta keys to avoid synchronization loops
        boolean isInternalKey = s == null || 
                s.equals("need_restart") || 
                s.equals("release_channel") || 
                s.equals("pending_restart_changes") || 
                s.equals("ignored_version") || 
                s.equals("ignored_timestamp") || 
                s.equals("update_alert_frequency") || 
                s.equals("last_update_check") || 
                s.equals("show_hook_toast") || 
                s.equals("open_wae");

        if (!isInternalKey) {
            ;
            
            // Track what changed for the restart dialog
            try {
                androidx.preference.Preference pref = findPreference(s);
                if (pref != null && pref.getTitle() != null) {
                    String title = pref.getTitle().toString();
                    java.util.Set<String> changes = new java.util.HashSet<>(mPrefs.getStringSet("pending_restart_changes", new java.util.HashSet<>()));
                    changes.add(title);
                    mPrefs.edit().putStringSet("pending_restart_changes", changes).apply();
                }
            } catch (Exception e) {
                android.util.Log.e("WAE_Manager", "Failed to track change title: " + e.getMessage());
            }

            mPrefs.edit().putBoolean("need_restart", true).apply();
        }

        runWithoutRestartBroadcast(() -> chanceStates(s));
        if (!suppressRestartBroadcast) {
            scheduleRestartBroadcast();
        }
    }

    private void scheduleRestartBroadcast() {
        if (!isResumed()) {
            ;
            return;
        }
        ;
        restartBroadcastHandler.removeCallbacks(restartBroadcastRunnable);
        restartBroadcastHandler.postDelayed(restartBroadcastRunnable, 250);
    }

    private void setPreferenceState(String key, boolean enabled) {
        var pref = findPreference(key);
        if (pref != null) {
            pref.setEnabled(enabled);
            if (pref instanceof MaterialSwitchPreference && !enabled) {
                var switchPreference = (MaterialSwitchPreference) pref;
                if (switchPreference.isChecked()) {
                    runWithoutRestartBroadcast(() -> switchPreference.setChecked(false));
                }
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

    private void monitorPreference() {
        var downloadstatus = (MaterialSwitchPreference) findPreference("downloadstatus");

        if (downloadstatus != null) {
            downloadstatus.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
        }

        var downloadviewonce = (MaterialSwitchPreference) findPreference("downloadviewonce");
        if (downloadviewonce != null) {
            downloadviewonce.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
        }
    }

    private boolean checkStoragePermission(Object newValue) {
        if (newValue instanceof Boolean && (Boolean) newValue) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager())
                    || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                            && ContextCompat.checkSelfPermission(requireContext(),
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                App.showRequestStoragePermission(requireActivity());
                return false;
            }
        }
        return true;
    }

    @SuppressLint("ApplySharedPref")
    private void chanceStates(String key) {

        var lite_mode = mPrefs.getBoolean("lite_mode", false);

        if (lite_mode) {
            setPreferenceState("wallpaper", false);
            setPreferenceState("custom_filters", false);
        }

        var changeColorEnabled = mPrefs.getBoolean("changecolor", false);
        var changeColorMode = mPrefs.getString("changecolor_mode", "manual");
        var monetAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        var useMonetColors = changeColorEnabled && monetAvailable && Objects.equals(changeColorMode, "monet");

        setPreferenceState("changecolor_mode", changeColorEnabled && monetAvailable);
        setPreferenceState("primary_color", changeColorEnabled && !useMonetColors);
        setPreferenceState("background_color", changeColorEnabled && !useMonetColors);
        setPreferenceState("text_color", changeColorEnabled && !useMonetColors);

        if (Objects.equals(key, "thememode")) {
            var mode = Integer.parseInt(mPrefs.getString("thememode", "0"));
            App.setThemeMode(mode);
        }

        var colorMode = mPrefs.getString("wae_color_mode", "preset");
        var useMonet = Objects.equals(colorMode, "monet") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        setPreferenceState("wae_color_preset", !useMonet);

        if (Objects.equals(key, "wae_color_mode") || Objects.equals(key, "wae_color_preset")) {
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }

        if (Objects.equals(key, "force_english")) {
            mPrefs.edit().commit();
            Utils.doRestart(requireContext());
        }

        var igstatus = mPrefs.getBoolean("igstatus", false);
        setPreferenceState("oldstatus", !igstatus);

        var oldstatus = mPrefs.getBoolean("oldstatus", false);
        setPreferenceState("verticalstatus", !oldstatus);
        setPreferenceState("channels", !oldstatus);
        setPreferenceState("removechannel_rec", !oldstatus);
        setPreferenceState("status_style", !oldstatus);
        setPreferenceState("igstatus", !oldstatus);

        var channels = mPrefs.getBoolean("channels", false);
        setPreferenceState("removechannel_rec", !channels && !oldstatus);

        var freezelastseen = mPrefs.getBoolean("freezelastseen", false);
        setPreferenceState("show_freezeLastSeen", !freezelastseen);
        setPreferenceState("showonlinetext", !freezelastseen);
        setPreferenceState("dotonline", !freezelastseen);

        boolean separategroups = mPrefs.getBoolean("separategroups", false);
        setPreferenceState("filtergroups", false); // Forced disabled

        var filtergroups = mPrefs.getBoolean("filtergroups", false);

        // Temporarily disable Separate Groups feature in settings and show explanation
        boolean disableSeparateGroups = true;
        var sepPref = findPreference("separategroups");
        if (sepPref != null) {
            setPreferenceState("separategroups", false);
            sepPref.setSummary(getString(com.waenhancer.R.string.separate_groups_sum) + "\n\n" + getString(com.waenhancer.R.string.separate_groups_disabled_wa_update));
        } else {
            setPreferenceState("separategroups", false);
        }

        var supported = isSeparateGroupSupported();
        updateGroupPref("separategroups", supported, R.string.separate_groups_sum, R.string.separate_groups_unsupported_sum);
        // Fully disable FilterGroups due to technical instability
        setPreferenceState("filtergroups", false);
        var filterGroupsPreference = findPreference("filtergroups");
        if (filterGroupsPreference != null) {
            filterGroupsPreference.setSummary(R.string.new_ui_group_filter_sum);
        }

        var callBlockContacts = findPreference("call_block_contacts");
        var callWhiteContacts = findPreference("call_white_contacts");
        if (callBlockContacts != null && callWhiteContacts != null) {
            int callType = 0;
            try {
                callType = Integer.parseInt(mPrefs.getString("call_privacy", "0"));
            } catch (Exception ignored) {}
            switch (callType) {
                case 3:
                    callBlockContacts.setEnabled(true);
                    callWhiteContacts.setEnabled(false);
                    break;
                case 4:
                    callWhiteContacts.setEnabled(true);
                    callBlockContacts.setEnabled(false);
                    break;
                default:
                    callWhiteContacts.setEnabled(false);
                    callBlockContacts.setEnabled(false);
                    break;
            }

        }
    }

    private boolean isSeparateGroupSupported() {
        try {
            var packageInfo = requireContext().getPackageManager().getPackageInfo(FeatureLoader.PACKAGE_WPP, 0);
            return isVersionAtMost(packageInfo.versionName, 2, 26, 12);
        } catch (Exception ignored) {
            return true;
        }
    }

    private void updateGroupPref(String key, boolean supported, int supportedSummary, int unsupportedSummary) {
        var pref = findPreference(key);
        if (pref == null) return;
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

    private boolean isVersionAtMost(String versionName, int major, int minor, int patch) {
        if (versionName == null) return true;
        var parts = versionName.split("\\.");
        if (parts.length < 3) return true;
        try {
            int vMajor = Integer.parseInt(parts[0]);
            int vMinor = Integer.parseInt(parts[1]);
            int vPatch = Integer.parseInt(parts[2]);
            if (vMajor != major) return vMajor < major;
            if (vMinor != minor) return vMinor < minor;
            return vPatch <= patch;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    public void setDisplayHomeAsUpEnabled(boolean enabled) {
        if (getActivity() == null)
            return;
        var actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
        // Toggle action buttons visibility — hide when back button shows
        var actionButtons = getActivity().findViewById(com.waenhancer.R.id.action_buttons);
        if (actionButtons != null) {
            actionButtons.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isVersionAtLeast(String versionName, int major, int minor, int patch) {
        if (versionName == null) return false;
        try {
            String[] parts = versionName.split("[^0-9]+");
            int[] nums = new int[]{0, 0, 0};
            int idx = 0;
            for (String p : parts) {
                if (p == null || p.isEmpty()) continue;
                if (idx < 3) nums[idx++] = Integer.parseInt(p);
                else break;
            }
            if (nums[0] != major) return nums[0] > major;
            if (nums[1] != minor) return nums[1] > minor;
            return nums[2] >= patch;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Scroll to a specific preference by key.
     * This is called when navigating from search results.
     */
    public void scrollToPreference(String preferenceKey) {
        if (preferenceKey == null)
            return;

        var rootView = getView();
        if (rootView == null) {
            return;
        }

        // Small delay to ensure preference screen is fully loaded
        if (rootView != null) {
            rootView.postDelayed(() -> {
                if (!isAdded()) return; // Fragment not attached
                var preference = findPreference(preferenceKey);
                if (preference != null) {
                    scrollToPreference(preference);
                    // Highlight the preference for visibility
                    highlightPreference(preference);
                }
            }, 100);
        }
    }

    /**
     * Highlight a preference with a temporary background color.
     */
    private void highlightPreference(androidx.preference.Preference preference) {
        var rootView = getView();
        if (rootView == null || preference == null || preference.getKey() == null) return;

        final String targetKey = preference.getKey();

        // Wait for RecyclerView to lay out items after scroll
        rootView.postDelayed(() -> {
            if (!isAdded()) return;

            androidx.recyclerview.widget.RecyclerView recyclerView;
            try {
                recyclerView = getListView();
            } catch (IllegalStateException e) {
                return;
            }

            if (recyclerView == null || getPreferenceScreen() == null) return;

            boolean found = false;
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                android.view.View child = recyclerView.getChildAt(i);
                if (child == null) continue;

                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof androidx.preference.PreferenceViewHolder) {
                    androidx.preference.PreferenceViewHolder prefHolder = (androidx.preference.PreferenceViewHolder) holder;
                    int position = prefHolder.getBindingAdapterPosition();

                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        try {
                            androidx.preference.Preference pref = findPreferenceAtPosition(getPreferenceScreen(), position);
                            if (pref != null && targetKey.equals(pref.getKey())) {
                                animateHighlight(prefHolder.itemView);
                                found = true;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (!found) {
                View currentView = getView();
                if (currentView != null) {
                    currentView.postDelayed(() -> tryHighlightAgain(targetKey), 500);
                }
            }
        }, 500);
    }

    private void tryHighlightAgain(String targetKey) {
        if (!isAdded()) return;
        androidx.recyclerview.widget.RecyclerView recyclerView;
        try {
            recyclerView = getListView();
        } catch (IllegalStateException e) {
            return;
        }
        if (recyclerView == null) return;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            android.view.View child = recyclerView.getChildAt(i);

            // Simple approach: check all text views in the item for matching preference
            if (child instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) child;
                // Get the preference at this position and check key
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof androidx.preference.PreferenceViewHolder) {
                    int position = holder.getBindingAdapterPosition();
                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        androidx.preference.Preference pref = findPreferenceAtPosition(getPreferenceScreen(), position);
                        if (pref != null && pref.getKey() != null && pref.getKey().equals(targetKey)) {
                            animateHighlight(child);
                            break;
                        }
                    }
                }
            }
        }
    }

    private androidx.preference.Preference findPreferenceAtPosition(androidx.preference.PreferenceGroup group,
            int targetPosition) {
        if (group == null)
            return null;

        int currentPosition = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            androidx.preference.Preference pref = group.getPreference(i);
            if (pref == null)
                continue;

            if (currentPosition == targetPosition) {
                return pref;
            }
            currentPosition++;

            // Recursively check groups
            if (pref instanceof androidx.preference.PreferenceGroup) {
                androidx.preference.PreferenceGroup subGroup = (androidx.preference.PreferenceGroup) pref;
                int subCount = countPreferences(subGroup);
                if (targetPosition < currentPosition + subCount) {
                    return findPreferenceAtPosition(subGroup, targetPosition - currentPosition);
                }
                currentPosition += subCount;
            }
        }
        return null;
    }

    private int countPreferences(androidx.preference.PreferenceGroup group) {
        int count = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            androidx.preference.Preference pref = group.getPreference(i);
            if (pref instanceof androidx.preference.PreferenceGroup) {
                count += countPreferences((androidx.preference.PreferenceGroup) pref);
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * Animate a highlight effect on the view.
     */
    private void animateHighlight(android.view.View view) {
        if (view == null || getContext() == null)
            return;

        // Get primary color using android attribute
        android.util.TypedValue typedValue = new android.util.TypedValue();
        view.getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;

        // Make it 20% opacity (dim)
        int highlightColor = android.graphics.Color.argb(
                51, // ~20% of 255
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor));

        // Save original background
        android.graphics.drawable.Drawable originalBackground = view.getBackground();

        // Set highlight background
        view.setBackgroundColor(highlightColor);

        // Fade out after 1.5 seconds
        view.postDelayed(() -> {
            if (originalBackground != null) {
                view.setBackground(originalBackground);
            } else {
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }, 1500);
    }

    private void initializeReleaseChannelPreference() {
        try {
            var pref = findPreference("release_channel");
            if (pref == null) return;

            String installedVersion = "";
            try {
                var pkgInfo = requireContext().getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
                installedVersion = pkgInfo.versionName != null ? pkgInfo.versionName : "";
            } catch (Exception ignored) {
            }

            boolean installedIsBeta = installedVersion.contains("-beta-");
            String installedChannel = installedIsBeta ? "beta" : "stable";

            runWithoutRestartBroadcast(() -> {
                if (pref instanceof ListPreference && !installedChannel.equals(((ListPreference) pref).getValue())) {
                    ((ListPreference) pref).setValue(installedChannel);
                }
                if (!installedChannel.equals(mPrefs.getString("release_channel", "stable"))) {
                    mPrefs.edit().putString("release_channel", installedChannel).apply();
                }
            });
            WppCore.setPrivString("release_channel", installedChannel);
        } catch (Exception ignored) {
        }
    }

    private void setupReleaseChannelPreference() {
        var preference = findPreference("release_channel");
        if (!(preference instanceof ListPreference)) return;

        ListPreference releaseChannelPreference = (ListPreference) preference;
        releaseChannelPreference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (!(newValue instanceof String)) return false;

            String selectedChannel = (String) newValue;
            String installedChannel = getInstalledReleaseChannel();
            if (selectedChannel.equals(installedChannel)) {
                return true;
            }

            showReleaseInstallPrompt(selectedChannel);
            return false;
        });
    }

    private String getInstalledReleaseChannel() {
        try {
            var pkgInfo = requireContext().getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            String installedVersion = pkgInfo.versionName != null ? pkgInfo.versionName : "";
            return installedVersion.contains("-beta-") ? "beta" : "stable";
        } catch (Exception ignored) {
            return "stable";
        }
    }

    private void showReleaseInstallPrompt(String selectedChannel) {
        boolean isBeta = "beta".equals(selectedChannel);
        String title = getString(isBeta ? R.string.release_channel_beta_install_title : R.string.release_channel_stable_install_title);
        String message = getString(isBeta ? R.string.release_channel_beta_install_message : R.string.release_channel_stable_install_message);
        String url = isBeta ? RELEASES_URL : LATEST_STABLE_URL;

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.download, (dialog, which) -> {
                    Intent intent = new Intent(requireContext(), com.waenhancer.activities.ChangelogActivity.class);
                    intent.putExtra(com.waenhancer.activities.ChangelogActivity.EXTRA_TARGET_CHANNEL, selectedChannel);
                    startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }
}
