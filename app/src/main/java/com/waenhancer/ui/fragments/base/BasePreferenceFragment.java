package com.waenhancer.ui.fragments.base;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import androidx.preference.TwoStatePreference;

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
import androidx.core.text.HtmlCompat;
import com.waenhancer.utils.KeyboxValidator;
import com.waenhancer.ui.helpers.BottomSheetHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;
import com.waenhancer.activities.ChangelogActivity;
import com.waenhancer.preference.ContactPickerPreference;
import com.waenhancer.preference.FileSelectPreference;
import com.waenhancer.preference.SafeSharedPreferences;
import com.waenhancer.utils.KeyboxFetcher;
import com.waenhancer.utils.KeyboxVerification;
import com.waenhancer.xposed.utils.ProHelper;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String RELEASES_URL = "https://github.com/mubashardev/WaEnhancer/releases";
    private static final String LATEST_STABLE_URL = "https://github.com/mubashardev/WaEnhancer/releases/latest";
    protected SharedPreferences mPrefs;
    // Default keybox verify results are persisted in SharedPreferences via KeyboxVerificationImpl (pro module).
    private boolean suppressRestartBroadcast;
    private final Handler restartBroadcastHandler = new Handler(Looper.getMainLooper());
    private final Runnable restartBroadcastRunnable = () -> {
        if (!isAdded() || getContext() == null) {
            return;
        }
        // Collect changed preference titles
        ArrayList<String> titles = null;
        try {
            Set<String> changes = mPrefs.getStringSet("pending_restart_changes", null);
            if (changes != null && !changes.isEmpty()) {
                titles = new ArrayList<>(changes);
            }
        } catch (Exception ignored) {
        }

        // Send to both WhatsApp variants (must target package explicitly)
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
            intent.setPackage(pkg);
            if (titles != null) {
                intent.putStringArrayListExtra("changed_titles", titles);
            }
            App.getInstance().sendBroadcast(intent);
        }
        // Clear after sending so old titles don't accumulate
        mPrefs.edit().remove("pending_restart_changes").apply();
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        var localPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPrefs = new SafeSharedPreferences(localPrefs);

        getPreferenceManager().setPreferenceDataStore(new PreferenceDataStore() {
            @Override
            public void putString(String key, @Nullable String value) {
                mPrefs.edit().putString(key, value).apply();
            }

            @Override
            @Nullable
            public String getString(String key, @Nullable String defValue) {
                return mPrefs.getString(key, defValue);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                mPrefs.edit().putBoolean(key, value).apply();
            }

            @Override
            public boolean getBoolean(String key, boolean defValue) {
                return mPrefs.getBoolean(key, defValue);
            }

            @Override
            public void putInt(String key, int value) {
                mPrefs.edit().putInt(key, value).apply();
            }

            @Override
            public int getInt(String key, int defValue) {
                return mPrefs.getInt(key, defValue);
            }

            @Override
            public void putFloat(String key, float value) {
                mPrefs.edit().putFloat(key, value).apply();
            }

            @Override
            public float getFloat(String key, float defValue) {
                return mPrefs.getFloat(key, defValue);
            }

            @Override
            public void putLong(String key, long value) {
                mPrefs.edit().putLong(key, value).apply();
            }

            @Override
            public long getLong(String key, long defValue) {
                return mPrefs.getLong(key, defValue);
            }

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Add bottom padding so the last preference items are not hidden
        // behind the floating bottom navigation pill.
        try {
            RecyclerView recyclerView = getListView();
            if (recyclerView != null) {
                int bottomPaddingPx = (int) (112 * requireContext().getResources().getDisplayMetrics().density);
                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        bottomPaddingPx
                );
                recyclerView.setClipToPadding(false);
            }
        } catch (Exception ignored) {
        }
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
        updateKeyboxVerifySummary();

        try {
            KeyboxFetcher.syncKeyboxAsync(requireContext());
        } catch (Throwable ignored) {}

        // Lockdown pro preferences dynamically if not verified
        ProHelper.updatePreferences(requireContext(), getPreferenceScreen());
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
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null && data.hasExtra("key")) {
            String key = data.getStringExtra("key");
            Preference preference = findPreference(key);
            if (preference instanceof ContactPickerPreference) {
                ((ContactPickerPreference) preference).handleActivityResult(requestCode, resultCode, data);
            } else if (preference instanceof FileSelectPreference) {
                ((FileSelectPreference) preference).handleActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof ListPreference) {
            ListPreference listPref = (ListPreference) preference;
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
        } else if (preference instanceof MultiSelectListPreference) {
            MultiSelectListPreference multiPref = (MultiSelectListPreference) preference;
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
        } else if (preference instanceof EditTextPreference) {
            EditTextPreference editPref = (EditTextPreference) preference;
            String title = editPref.getDialogTitle() != null ? editPref.getDialogTitle().toString()
                    : editPref.getTitle() != null ? editPref.getTitle().toString() : "";
            BottomSheetHelper.showInput(
                    getContext(),
                    title,
                    editPref.getText(),
                    title,
                    getString(android.R.string.ok),
                    editPref,
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
        if ("bootloader_spoofer_verify".equals(preference.getKey())) {
            ProHelper.showKeyboxVerificationDialog(this);
            return true;
        }
        if (preference.getFragment() != null) {
            try {
                Class<?> clazz = Class.forName(preference.getFragment());
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof Fragment) {
                    Fragment fragment = (Fragment) instance;
                    fragment.setArguments(preference.getExtras());

                    // Scope navigation to the correct fragment manager and container.
                    FragmentManager fm = getParentFragmentManager();
                    int containerId = View.NO_ID;
                    if (getView() != null && getView().getParent() instanceof View) {
                        containerId = ((View) getView().getParent()).getId();
                    }
                    if (containerId == View.NO_ID) {
                        containerId = com.waenhancer.R.id.frag_container; // Fallback container
                    }

                    fm.beginTransaction()
                            .replace(containerId, fragment)
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
            } catch (Exception e) {
                Log.e("WAEX", "Failed to navigate to fragment: " + preference.getFragment(), e);
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
        ;
        if (Objects.equals(s, "release_channel")) {
            String channel = mPrefs.getString("release_channel", "stable");
            WppCore.setPrivString("release_channel", channel);
        }
        if (Objects.equals(s, "bootloader_spoofer_xml") 
                || Objects.equals(s, "bootloader_spoofer_default_xml") 
                || Objects.equals(s, "bootloader_spoofer_custom") 
                || Objects.equals(s, "bootloader_spoofer")) {
            if (Objects.equals(s, "bootloader_spoofer_xml")) {
                mPrefs.edit()
                        .remove("keybox_verify_status")
                        .remove("keybox_verify_time")
                        .apply();
            }
            if (Objects.equals(s, "bootloader_spoofer_default_xml")) {
                mPrefs.edit()
                        .remove("default_kb_status")
                        .remove("default_kb_time")
                        .remove("default_kb_score")
                        .apply();
            }
            updateKeyboxVerifySummary();
        }

        // Flag that a restart is needed for the changes to take effect in WhatsApp
        // Ignore internal/meta keys to avoid synchronization loops
        boolean isInternalKey = s == null
                || s.equals("need_restart")
                || s.equals("release_channel")
                || s.equals("pending_restart_changes")
                || s.equals("ignored_version")
                || s.equals("ignored_timestamp")
                || s.equals("update_alert_frequency")
                || s.equals("last_update_check")
                || s.equals("show_hook_toast")
                || s.equals("open_waex")
                || s.equals("open_settings_mode")
                || s.equals("keybox_verify_status")
                || s.equals("keybox_verify_time");

        if (!isInternalKey) {
            ;

            // Track what changed for the restart dialog
            try {
                Preference pref = findPreference(s);
                if (pref != null && pref.getTitle() != null) {
                    String title = pref.getTitle().toString();
                    Set<String> changes = new HashSet<>(mPrefs.getStringSet("pending_restart_changes", new HashSet<>()));
                    changes.add(title);
                    mPrefs.edit().putStringSet("pending_restart_changes", changes).apply();
                }
            } catch (Exception e) {
                Log.e("WAEX_Manager", "Failed to track change title: " + e.getMessage());
            }

            // Notify the Xposed module that preferences have changed via both ContentProvider and Broadcast
            try {
                String authority = BuildConfig.APPLICATION_ID + ".hookprovider";
                getContext().getContentResolver().notifyChange(
                        Uri.parse("content://" + authority + "/preferences"),
                        null
                );

                Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
                intent.setPackage("com.whatsapp"); // Target WhatsApp if it's running
                getContext().sendBroadcast(intent);

                Intent intent2 = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
                intent2.setPackage("com.whatsapp.w4b"); // Target WhatsApp Business
                getContext().sendBroadcast(intent2);
            } catch (Exception ignored) {
            }

            mPrefs.edit().putBoolean("need_restart", true).commit();
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

    private void enforceBlueOnReplyDependency() {
        var hideReadPreference = (TwoStatePreference) findPreference("hideread");
        if (hideReadPreference == null) {
            return;
        }

        boolean blueOnReply = mPrefs.getBoolean("blueonreply", false);
        if (blueOnReply && !mPrefs.getBoolean("hideread", false)) {
            runWithoutRestartBroadcast(() -> mPrefs.edit().putBoolean("hideread", true).apply());
        }
        if (blueOnReply && !hideReadPreference.isChecked()) {
            runWithoutRestartBroadcast(() -> hideReadPreference.setChecked(true));
        }
        hideReadPreference.setEnabled(!blueOnReply);
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

        var download_video_note = (MaterialSwitchPreference) findPreference("download_video_note");
        if (download_video_note != null) {
            download_video_note.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
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

        var colorMode = mPrefs.getString("waex_color_mode", "preset");
        var useMonet = Objects.equals(colorMode, "monet") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        setPreferenceState("waex_color_preset", !useMonet);

        if (Objects.equals(key, "waex_color_mode") || Objects.equals(key, "waex_color_preset")) {
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

        enforceBlueOnReplyDependency();

        boolean removeBottomTile = mPrefs.getBoolean("remove_status_bottom_tile", false);
        setPreferenceState("remove_status_quick_reactions", !removeBottomTile);
        setPreferenceState("remove_status_heart_button", !removeBottomTile);

        if (mPrefs.getBoolean("filtergroups", false)) {
            runWithoutRestartBroadcast(() -> mPrefs.edit().putBoolean("filtergroups", false).apply());
        }
        setPreferenceState("filtergroups", false); // Forced disabled

        var sepPref = findPreference("separategroups");
        if (sepPref != null) {
            sepPref.setEnabled(true);
            sepPref.setSummary(getString(com.waenhancer.R.string.separate_groups_sum));
        }
        // Fully disable FilterGroups due to technical instability
        setPreferenceState("filtergroups", false);
        var filterGroupsPreference = findPreference("filtergroups");
        if (filterGroupsPreference != null) {
            filterGroupsPreference.setSummary(R.string.new_ui_group_filter_unsupported_sum);
        }

        var callBlockContacts = findPreference("call_block_contacts");
        var callWhiteContacts = findPreference("call_white_contacts");
        if (callBlockContacts != null && callWhiteContacts != null) {
            int callType = 0;
            try {
                callType = Integer.parseInt(mPrefs.getString("call_privacy", "0"));
            } catch (Exception ignored) {
            }
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
        return true;
    }

    private void updateGroupPref(String key, boolean supported, int supportedSummary, int unsupportedSummary) {
        var pref = findPreference(key);
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

    private boolean isVersionAtMost(String versionName, int major, int minor, int patch) {
        if (versionName == null) {
            return true;
        }
        var parts = versionName.split("\\.");
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

    public void setDisplayHomeAsUpEnabled(boolean enabled) {
        if (getActivity() == null) {
            return;
        }
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
        if (versionName == null) {
            return false;
        }
        try {
            String[] parts = versionName.split("[^0-9]+");
            int[] nums = new int[]{0, 0, 0};
            int idx = 0;
            for (String p : parts) {
                if (p == null || p.isEmpty()) {
                    continue;
                }
                if (idx < 3) {
                    nums[idx++] = Integer.parseInt(p);
                } else {
                    break;
                }
            }
            if (nums[0] != major) {
                return nums[0] > major;
            }
            if (nums[1] != minor) {
                return nums[1] > minor;
            }
            return nums[2] >= patch;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Scroll to a specific preference by key. This is called when navigating
     * from search results.
     */
    public void scrollToPreference(String preferenceKey) {
        if (preferenceKey == null) {
            return;
        }

        var rootView = getView();
        if (rootView == null) {
            return;
        }

        // Small delay to ensure preference screen is fully loaded
        if (rootView != null) {
            rootView.postDelayed(() -> {
                if (!isAdded()) {
                    return; // Fragment not attached

                }
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
    private void highlightPreference(Preference preference) {
        var rootView = getView();
        if (rootView == null || preference == null || preference.getKey() == null) {
            return;
        }

        final String targetKey = preference.getKey();

        // Wait for RecyclerView to lay out items after scroll
        rootView.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }

            RecyclerView recyclerView;
            try {
                recyclerView = getListView();
            } catch (IllegalStateException e) {
                return;
            }

            if (recyclerView == null || getPreferenceScreen() == null) {
                return;
            }

            boolean found = false;
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                if (child == null) {
                    continue;
                }

                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof PreferenceViewHolder) {
                    PreferenceViewHolder prefHolder = (PreferenceViewHolder) holder;
                    int position = prefHolder.getBindingAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {
                        try {
                            Preference pref = findPreferenceAtPosition(getPreferenceScreen(), position);
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
        if (!isAdded()) {
            return;
        }
        RecyclerView recyclerView;
        try {
            recyclerView = getListView();
        } catch (IllegalStateException e) {
            return;
        }
        if (recyclerView == null) {
            return;
        }

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);

            // Simple approach: check all text views in the item for matching preference
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                // Get the preference at this position and check key
                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                if (holder instanceof PreferenceViewHolder) {
                    int position = holder.getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        Preference pref = findPreferenceAtPosition(getPreferenceScreen(), position);
                        if (pref != null && pref.getKey() != null && pref.getKey().equals(targetKey)) {
                            animateHighlight(child);
                            break;
                        }
                    }
                }
            }
        }
    }

    private Preference findPreferenceAtPosition(PreferenceGroup group,
            int targetPosition) {
        if (group == null) {
            return null;
        }

        int currentPosition = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref == null) {
                continue;
            }

            if (currentPosition == targetPosition) {
                return pref;
            }
            currentPosition++;

            // Recursively check groups
            if (pref instanceof PreferenceGroup) {
                PreferenceGroup subGroup = (PreferenceGroup) pref;
                int subCount = countPreferences(subGroup);
                if (targetPosition < currentPosition + subCount) {
                    return findPreferenceAtPosition(subGroup, targetPosition - currentPosition);
                }
                currentPosition += subCount;
            }
        }
        return null;
    }

    private int countPreferences(PreferenceGroup group) {
        int count = 0;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                count += countPreferences((PreferenceGroup) pref);
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * Animate a highlight effect on the view.
     */
    private void animateHighlight(View view) {
        if (view == null || getContext() == null) {
            return;
        }

        // Get primary color using android attribute
        TypedValue typedValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;

        // Make it 20% opacity (dim)
        int highlightColor = Color.argb(
                51, // ~20% of 255
                Color.red(primaryColor),
                Color.green(primaryColor),
                Color.blue(primaryColor));

        // Save original background
        Drawable originalBackground = view.getBackground();

        // Set highlight background
        view.setBackgroundColor(highlightColor);

        // Fade out after 1.5 seconds
        view.postDelayed(() -> {
            if (originalBackground != null) {
                view.setBackground(originalBackground);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        }, 1500);
    }

    private void initializeReleaseChannelPreference() {
        try {
            var pref = findPreference("release_channel");
            if (pref == null) {
                return;
            }

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
        if (!(preference instanceof ListPreference)) {
            return;
        }

        ListPreference releaseChannelPreference = (ListPreference) preference;
        releaseChannelPreference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (!(newValue instanceof String)) {
                return false;
            }

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

        new AlertDialog.Builder(requireContext())
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

    private void updateKeyboxVerifySummary() {
        Preference verifyPref = findPreference("bootloader_spoofer_verify");
        if (verifyPref == null) {
            return;
        }
        if (mPrefs == null) {
            return;
        }

        verifyPref.setVisible(true);

        boolean customEnabled = mPrefs.getBoolean("bootloader_spoofer_custom", false);
        String xmlContent = mPrefs.getString("bootloader_spoofer_xml", "");
        boolean hasKeybox = xmlContent != null && !xmlContent.trim().isEmpty();

        boolean isCustom = (customEnabled && hasKeybox);
        String label = isCustom ? "Custom KeyBox" : "Default Spoofer";

        String status;
        long lastCheckTime;
        int score;

        if (isCustom) {
            String targetXml = xmlContent;
            String xmlHash = getXmlMd5(targetXml);
            lastCheckTime = mPrefs.getLong("kb_hash_" + xmlHash + "_time", 0L);
            status = mPrefs.getString("kb_hash_" + xmlHash + "_status", "");
            score = mPrefs.getInt("kb_hash_" + xmlHash + "_score", -1);
        } else {
            lastCheckTime = mPrefs.getLong("default_kb_time", 0L);
            status = mPrefs.getString("default_kb_status", "");
            score = mPrefs.getInt("default_kb_score", -1);
        }

        // Live check for spoofer status
        Context context = getContext();
        boolean hookActive = KeyboxVerification.isBootloaderSpooferActive(context, mPrefs);
        boolean attestationSpoofed = KeyboxVerification.isBootloaderAttestationSpoofed();
        if (context != null) {
            String currentPkg = context.getPackageName();
            boolean isInWhatsApp = "com.whatsapp".equals(currentPkg) || "com.whatsapp.w4b".equals(currentPkg);
            if (!isInWhatsApp && hookActive) {
                attestationSpoofed = true; // Fallback for manager app UI
            }
        }
        boolean bootloaderSpoofEnabled = mPrefs.getBoolean("bootloader_spoofer", false);
        boolean spooferOk = bootloaderSpoofEnabled && hookActive && attestationSpoofed;

        // Force fail status if spoofer is disabled or inactive
        if (!spooferOk) {
            status = "Failed";
        }

        // Add live spooferScore to cached cert score
        int spooferScore = 0;
        if (hookActive) {
            spooferScore += 5;
        }
        if (attestationSpoofed) {
            spooferScore += 5;
        }

        if (score >= 0) {
            score += spooferScore;
        }

        if (context != null) {
            if (lastCheckTime == 0L || status == null || status.isEmpty()) {
                verifyPref.setSummary("Check spoofer trust chain, keys, and expiration");
                Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_round_help_outline_24);
                if (icon != null) {
                    icon = DrawableCompat.wrap(icon);
                    DrawableCompat.setTint(icon, 0xFF8E8E93);
                    verifyPref.setIcon(icon);
                }
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String formattedDate = sdf.format(new Date(lastCheckTime));
                String scoreSuffix = (score >= 0) ? " (Score: " + score + "/100)" : "";

                if ("Pass".equals(status)) {
                    verifyPref.setSummary(label + " Pass" + scoreSuffix + " - " + formattedDate);
                    Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_round_verified_24);
                    if (icon != null) {
                        icon = DrawableCompat.wrap(icon);
                        DrawableCompat.setTint(icon, 0xFF4CAF50);
                        verifyPref.setIcon(icon);
                    }
                } else {
                    verifyPref.setSummary(label + " Failed" + scoreSuffix + " - " + formattedDate);
                    Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_round_error_outline_24);
                    if (icon != null) {
                        icon = DrawableCompat.wrap(icon);
                        DrawableCompat.setTint(icon, 0xFFFF3B30);
                        verifyPref.setIcon(icon);
                    }
                }
            }
        }
    }

    private String getDefaultSpooferXml() {
        if (mPrefs != null) {
            String cached = mPrefs.getString("bootloader_spoofer_default_xml", "");
            if (cached != null && !cached.trim().isEmpty()) {
                return cached;
            }
        }
        return """
                <AndroidAttestation>
                    <NumberOfKeyboxes>1</NumberOfKeyboxes>
                    <Keybox DeviceID="google">
                        <Key algorithm="ecdsa">
                            <PrivateKey format="pem">
                                -----BEGIN EC PRIVATE KEY-----
                                MHcCAQEEICOd8gK7eF5g2diA0hdH8N5/ucVpF3Nto3xuU5yXNGqioAoGCCqGSM49
                                AwEHoUQDQgAE6lXy73P+EknjegJdmuA07/wlu7RPC2CCam0Tiy60PvlOCsWSECTg
                                8BwbTBIzZ2qgSv2nKumUWzrLpWpc0v8PBw==
                                -----END EC PRIVATE KEY-----
                            </PrivateKey>
                            <CertificateChain>
                                <NumberOfCertificates>3</NumberOfCertificates>
                                <Certificate format="pem">
                                    -----BEGIN CERTIFICATE-----
                                    MIIB8zCCAXqgAwIBAgIRAJNp8u+IlBm41dacSVioZEEwCgYIKoZIzj0EAwIwOTEM
                                    MAoGA1UEDAwDVEVFMSkwJwYDVQQFEyAxMWRlZjVlMjVlOTBjZGVhY2ViNjUwYzZj
                                    YzkyNWJmMTAeFw0yMDA0MjgxODUyMDlaFw0zMDA0MjYxODUyMDlaMDkxDDAKBgNV
                                    BAwMA1RFRTEpMCcGA1UEBRMgODA4Y2I4NGYxYmJhYzk2YWFlNTdkMTMzMDVhMDkz
                                    ZTMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATqVfLvc/4SSeN6Al2a4DTv/CW7
                                    tE8LYIJqbROLLrQ++U4KxZIQJODwHBtMEjNnaqBK/acq6ZRbOsulalzS/w8Ho2Mw
                                    YTAdBgNVHQ4EFgQUqarPnmPZ5Z1nkqZBOA1GsSuZe6AwHwYDVR0jBBgwFoAUba4Y
                                    fI7phq7FCcDRVxeT7XrtrxgwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
                                    AgQwCgYIKoZIzj0EAwIDZwAwZAIwdHoak9MU22sLH21950DJnxhkNHmpFPqplB3I
                                    n5D0Mq2ttqWsXdilh/AFoX+Jo7UpAjB2a2siHNqxGJOX2f/+Zo7vCreVBCMtlGt+
                                    xKfNS9swrMrfuWp2br5L2b9TufK/1as=
                                    -----END CERTIFICATE-----
                                </Certificate>
                                <Certificate format="pem">
                                    -----BEGIN CERTIFICATE-----
                                    MIIDkzCCAXugAwIBAgIQbS6dRno28uOL1jMOAJztvzANBgkqhkiG9w0BAQsFADAb
                                    MRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTIwMDQyODE4NTEyNFoXDTMw
                                    MDQyNjE4NTEyNFowOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyAxMWRlZjVlMjVl
                                    OTBjZGVhY2ViNjUwYzZjYzkyNWJmMTB2MBAGByqGSM49AgEGBSuBBAAiA2IABH25
                                    Qj73BqRX+/NPHaqFU9/5ZjM5xiEsXE3EDv2kniqTK9TtM9IOe2bPPdpLHNZk5LhG
                                    XMlFhhsJe2N1KN+goVx1vh3GsfGqWAD3ZEm4nem1l7U3i4m2SXGYFtEQCc/lU6Nj
                                    MGEwHQYDVR0OBBYEFG2uGHyO6YauxQnA0VcXk+167a8YMB8GA1UdIwQYMBaAFDZh
                                    4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQD
                                    AgIEMA0GCSqGSIb3DQEBCwUAA4ICAQCfMm8ozF5fbo1iBldM6WVwkN/n0cd7/T1X
                                    LpfAgmo2XpC+k+jFBDWiWhWhKohpS64aRVGXwsFN8pMmqxqTqhlYhmExJi/BJnhD
                                    IEACLs42bCKzOYJS0qzGDGL14kzlniorD3IFvq+4U4FMBuXWnqljCKA8QMB2Hu4q
                                    HDRRZOyiTXZOfCASRuW6Pkvg14gSqRIJ1KJb2WItCRuwURx+/O3UimGjZorvnQUb
                                    M5nT6yF+Csk8C6lfNgJEXxsnP40wnNK109aEKqKh5DM9vNqHN4Vb2ZDj+UdJB2Ax
                                    BX5g9zyL+SA5ksFZ5bwNx9EH5rqBUSeH+xZULvai/YB0Uqxyc5YcP4oM40lYqa72
                                    wqw+JArMYIiJz83XgjXizEcGIgBDyzpCf8ltnaQ/FO3BvMsSdWLJ3EUOw5sBlQKW
                                    rtvYvt9/9OinjtpnAoRVXbRuXgmhhFt27qonN7XziRli2mH234CHAxcxHG+elqjn
                                    iouW9h6cL6jZpg41pKoCYL4Qa8xnuIYytwfn8WOqW9u0Dfain2CTj2HImqWpCXNC
                                    /Xn84KgGVJgL6+Crdn9IfR3fdVTG0mRT1lPFm2Iz/J3J4uwvvA0+ZzJjE8hto73r
                                    TbK6PdDuAObOU8t7nCt8R+pH6YJBd2GVRlf5soK+iZW7f8NODjy/3Vb52/MfwnBQ
                                    Fv2AQqR3YA==
                                    -----END CERTIFICATE-----
                                </Certificate>
                                <Certificate format="pem">
                                    -----BEGIN CERTIFICATE-----
                                    MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
                                    BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAz
                                    NzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
                                    AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
                                    Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
                                    tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
                                    nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
                                    C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
                                    oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
                                    JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
                                    sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
                                    igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
                                    RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
                                    aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
                                    AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
                                    IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
                                    VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnu
                                    XKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83U
                                    h6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cno
                                    L/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2ok
                                    QBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vA
                                    D32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAI
                                    mMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoW
                                    Fua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91
                                    oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09o
                                    jm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUB
                                    ZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCH
                                    ex0SdDrx+tWUDqG8At2JHA==
                                    -----END CERTIFICATE-----
                                </Certificate>
                            </CertificateChain>
                        </Key>
                        <Key algorithm="rsa">
                            <PrivateKey format="pem">
                                -----BEGIN RSA PRIVATE KEY-----
                                MIIG4wIBAAKCAYEAu+QIhJj7aDTA01fBSB6UX3He4F+I892RZ6sA8SgBubNInrki
                                WDdxOUZZA6O/qddxNuQVlaFWygQwKRabL2t4hueYTHyrvgzJyBWV6gStc1gjqvTo
                                BAThO5Lq2NsQUs0Qe16UXxRwBPwdKbxckG4F/X03BNHVV0rDCep3tovLEgmdp/gt
                                s2YhWuwYEAdhHEU7wH6TWrLGKIe4I9d9rTvMNPzN539LuPgXL18DCuEJUkV4AdFi
                                UnapDjT6uTbQEwekT38R8jprerPUQeQofN1oJBpFXzEBud7y7wukKHNDSoh6jZMj
                                +lWKbE9xiajw9X7ceRAL2ZIaWsUOqSTmg+Y9CDS+azLCjxpYkSvDwgZPNWonbGHr
                                nIzxhlLEYieNsEPt1rNmnxZkTvvwBNkkP/iR2awssZYDWpEoEnyBwraeOwDyE7cl
                                Er3zrYOn4q+ylrpVK3PM8ygUtqBZh0cNuiaFJtrficSepZeaGPt75jLzQRGPGAVr
                                deSFagkcLpvn6C2vAgMBAAECggGABuUeXudSSoetD9RnlmLw5PPDzw4Sc4iM/nXr
                                Ce6C6bKnlpOKrBwUvppTR+vpa60pTW9fT2dlTPKMZeWbekkCWkkDcMMedlH30azh
                                HH5hcxsn6+0i2ornTQ1eKukXF0LJOQ3GehrA5Z3u4Ao2h2JSO/QtYbLllld7AtEk
                                5YEJybaqn3BfFPdJgBGr7GKo8KWlxLGgbLKkzPX2DvKofQP1wXgJglZMjBQmnalp
                                7itF8Uv1VHO/nPEX0RqmnMdjKV+dWuiX34hf5SlCbOCSl2KovKrCSFgRVTDFQTmi
                                rgPj7q00Oblf+qtWcyXAJ9Fy0yOSn0jDB9zT2fIwMIT8fXwTIhIEirhYRK56ca5E
                                zfZ3Ji9+LyMC/xP12lzeZ/hYw5FYoZOWp8bPabjTFauURWwTWiKV650Be4+Rr1EP
                                z4BFJieKV1WP3+6Vhsp46XtkezaQXwwbdhLg4mKD+4LjxZtSJLTCaQ3e/BgZpwxz
                                stTtHbXTlqENJUTOZpbAVKOCBgJRAoHBANz8FFiI6bMfWXTZ3BAE3M4n8+DnCuwR
                                6chvI0L4DfCXj1Je70lT3WBdV9zL/vT1X1ndhFV54vetvDF8WHonbpw2an61Mkpr
                                njYzRQy2Aq9T7aTQMSdiWlER7CUcCCMl+dEvUAOXrT0M2Ozw3jFNd6P4EFNhC6Ew
                                exVc2C/3UIp+fZ8OWEmi+G/o15y1Bvi1kMoZD8JrLxAhpFawQrSMhLsc0vQkxxeP
                                RLI/Et8u1nYD04fGnc+JJsDNaL/+AzgHRwKBwQDZqYwCnycjUa0ihAWJjzDSpa7w
                                5I513c6ACWghxl01M/1VpRGqlgwoIdyfLH9no85axAGKtZKHK/VAttFC2JtQFr63
                                R7QjLeqO1wUEX511hP7ZN9/qr6d1rp0tvkVfwn7wvF97uY8YuJxqi1p+nIsgGhRB
                                Y0H2XXTMSt2QnBuCPTaEouxY3RaOnAQd7GkhlGfQidcJh63YYAM2CAUxxC4wdoF7
                                FvMgtGAi62EeyxBFeK2SF95T3gNKDiaroHvkKlkCgcBuBZ9HmRrpkIEkWVdkLleU
                                2HVmkwFwGVcQ8KxYqlGeaIb11shB9NwyHycgifwtD4Fip5Q8Tkv/TmN1K9iNMNa0
                                Na992E7qmHwTtiD5vCDIE/wsY28lkaUv2cF9lGBEx6KCUJEAyOJ6k8vo499sIoqf
                                e2D9ckKtBQsyzp/f+b0CxwlaSHUSbG5OoVm/7q1C5Hrq8+FRxbWPzYAZnPYJGDD5
                                S9eHsEvjYfQs3pRRw+sIpM0LO4rUig9eTKaLeDc4DP8CgcBXbyoU64W3RFn+IXZv
                                +ZstIu0RS16GrmEDQcQYvSw38Ph07OgZ1Ehx3phXQHK1WTHNeCr+Y03HCrtsEYQi
                                DAznsRtPWHheIVW1p14WkaoYySHuc+l4xrLILSpqc6I+g0ymu6THeJSo44/BpNTn
                                Q08HyDIW8+U9Z/FBF1nFe0/5k0lRInk6gSVMiBOHSa45lPnW5WgCJgSJhJgFnlcn
                                1JyRTylYHrHvk0WDAXZz/jI9FerzYq8mlWpQ1zplewQJdZECgcEA2n4Qpg12Em4P
                                osYJAs0GqMQgjhlTNffwe6kFj46Iy8OtR1mG9OXRmzq6NsEIQaKnn28JWibkkz09
                                2NniF6lt53wtJQWSSXFf3LTawRiS7lSUmqZfULiilUoIMmfeBpaj7ZT6NvXzY9PP
                                mYKWVJ+DP1Is12hcGEU9UByDg4/BXzvGzw9o4FcDnNYCepaOj+hItwjqs2oGEWK9
                                drRPQm72SCNy/AITromUFxTV5Cl8tg1IQS4OtiLQEiyco21YyAUu
                                -----END RSA PRIVATE KEY-----
                            </PrivateKey>
                            <CertificateChain>
                                <NumberOfCertificates>3</NumberOfCertificates>
                                <Certificate format="pem">
                                    -----BEGIN CERTIFICATE-----
                                    MIIE4DCCAsigAwIBAgIRALK/VaUQzuEAZlfWFPGTilIwDQYJKoZIhvcNAQELBQAw
                                    OTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyBkZDFmOGRiM2JmOTdlMGMzYmU4NGQ5
                                    MWI0NjZiZmVmNTAeFw0yMTA2MTYxOTI1MjNaFw0zMTA2MTQxOTI1MjNaMDkxDDAKB
                                    gNVBAwMA1RFRTEpMCcGA1UEBRMgNmZlMmY5MTljMWU5ZDg3NzY2NTU2YjlhOWYw
                                    NzFjNTEwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAwggGKAoIBgQC75AiEmPtoNMDT
                                    V8FIHpRfcd7gX4jz3ZFnqwDxKAG5s0ieuSJYN3E5RlkDo7+p13E25BWVoVbKBDAp
                                    Fpsva3iG55hMfKu+DMnIFZXqBK1zWCOq9OgEBOE7kurY2xBSzRB7XpRfFHAE/B0p
                                    vFyQbgX9fTcE0dVXSsMJ6ne2i8sSCZ2n+C2zZiFa7BgQB2EcRTvAfpNassYoh7gj
                                    132tO8w0/M3nf0u4+BcvXwMK4QlSRXgB0WJSdqkONPq5NtATB6RPfxHyOmt6s9RB
                                    5Ch83WgkGkVfMQG53vLvC6Qoc0NKiHqNkyP6VYpsT3GJqPD1ftx5EAvZkhpaxQ6p
                                    JOaD5j0INL5rMsKPGliRK8PCBk81aidsYeucjPGGUsRiJ42wQ+3Ws2afFmRO+/AE
                                    2SQ/+JHZrCyxlgNakSgSfIHCtp47APITtyUSvfOtg6fir7KWulUrc8zzKBS2oFmH
                                    Rw26JoUm2t+JxJ6ll5oY+3vmMvNBEY8YBWt15IVqCRwum+foLa8CAwEAAaNjMGEw
                                    HQYDVR0OBBYEFLILTMjpvEOZhTE2NO/vxzBoDuchMB8GA1UdIwQYMBaAFDlk3/vK
                                    LASjKdwFd8yIHu5hZ95SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIE
                                    MA0GCSqGSIb3DQEBCwUAA4ICAQA3pRvTnluSLiMVky9j36Q4Zq+8HNDZeRKnODz6
                                    UH1wPxmMXP810zvcNDcZyWziJKgxVYnrrNGcvLCQSviDq9aZyGyUykjLLqVvtNe0
                                    aRTglBprmyPYRlFoj7dznfNylWSZ532XdzHARveLUfApmYZ4TZOOx4mQmZLWPO/F
                                    dYUlBRgzCQ6T4JY75z0WUYWyfYh+i3p8qKaGoq+zhuehlOBlWqy8untmd3LeBZUj
                                    A/fRn9hz4+SYSKV5zAgyq0LU1IrdvFDEhPi6EmnXH0m49zhkAjNY17FYkBjkeB80
                                    H0bzGYdqA2LDvLyxy4ygquFyPKVehEVS9/k0QJJlEzOeCE17Kk5DoO4gw/Gk/jyS
                                    xEacJ+19OxKbmtB46f/CjONBJr7MVVk03dAtDoKS31ypKj6VvQOCDssbly21TbpJ
                                    f5257htiHLm8wKohdi9jmyF+9ne6QyL3zozquOimrLbsNBetPP+ZNRVD/X8oxnfj
                                    HEsYeSPWxHE6KPtCMrR5F/YBRMRLi4sW/MU4cS5L3/YZwfDCVLrmHavix89rfrsT
                                    8g8r6LL6BAFDR59O/66LZuPppM0wR5L4lrEuzBKF7T9baqnZspozFSloBPniakbK
                                    H1ZFORhUdRKi4IJN5pBU7JmHnlZHIfY/5wS5VAJVE1Z2oKmhaHbpU/XkH1mhk3wM
                                    JJF7xQ==
                                    -----END CERTIFICATE-----
                                </Certificate>
                                <Certificate format="pem">
                                    -----BEGIN CERTIFICATE-----
                                    MIIFQjCCAyqgAwIBAgIRALgkQE6xGEXpbXg5aRaqEUswDQYJKoZIhvcNAQELBQAw
                                    GzEZMBcGA1UEBRMQZjkyMDA5ZTg1M2I2YjA0NTAeFw0yMTA2MTYxOTI2MDVaFw0z
                                    MTA2MTQxOTI2MDVaMDkxDDAKBgNVBAwMA1RFRTEpMCcGA1UEBRMgZGQxZjhkYjNi
                                    Zjk3ZTBjM2JlODRkOTFiNDY2YmZlZjUwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAw
                                    ggIKAoICAQC5bMgIOWvC5Ybfkij01Zm9l1AAbZLbu1+g7ZFj6ZCF9T5UcS67qXn/
                                    rHr7jr/6s2I+gdwvefU2y5+zHX8DVw2hmXBh11G5dbxHpGShh6gUpIwYkjPp+8T3
                                    CzW4Dwxop0hEYzjFwuprTmqB1DrgE2dH4Ml/naSSvs97mGPWNuPfAD5HkskyQwS6
                                    gzjR9S4d3dY8PmHSTfeENNvPRqpSbI2/49iBXxBN8tUmyP4Q14eUOMJGlvryD5E1
                                    YbVXGM5m+0kRazzXkrkM7rgTLLGXGeCR3J8hJ/8MqSvIIQ4Vw73fkksS6Xakf9WE
                                    hfDAB9QpRzVioHkw4Bl68T9zVjrcOfCXB8zLTjJzKpQh2ooNeU926m7pqRwXOojx
                                    tP9PY14awAfyA2qzv+RiaMRc0IXDmY93OFMt06mvrowmfwVJZQn75Y6jTTRn+Dw9
                                    jKC/aO2DgcMugp77rU8yhNSi2xqbWiOHd8pSp2/8PCPVLjjZXFSeVl4Hs9EdUKq4
                                    prfWNJekF/rU5JXFz3/VjLRV1DLSx3RrdYQRIc8Bjmr3cxr2H9Y/wnOOVmhG3ABY
                                    EYUKxpLMAInO5xOjRDlCR/iaPLTeCYh9hj2Pf6L5Byo+cldrMe0b0nbqPPGnsQnDJ
                                    HE8f9NrHkcexJ31yxVG59BHVjOJks6TL1W9dsMd1uAHJMd5iSfKFwIDAQABo2Mw
                                    YTAdBgNVHQ4EFgQUOWTf+8osBKMp3AV3zIge7mFn3lIwHwYDVR0jBBgwFoAUNmHh
                                    AHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
                                    AgQwDQYJKoZIhvcNAQELBQADggIBABcHDeZJQh5/WQac8/2rC1hN/sN6W/SQ4K2+
                                    glWPXkKnG5QfRZmGdzOdk0uShOu8YqFYi1ka4sipDLDOrieGPTRJ5MVtvhMre+X6
                                    9Lh7AQnqGGgGfmcRRhAKfZBrU4k+DVcnyM30FkdD1ZEkxXMKQ8eyTzqYfhZxPHj7
                                    ohRSwE/wEBrM/Fiu8k/PgjnqoLtFi/RfLH7qVwcUQ7EagNVlnxDkzofKbxr2Udr4
                                    Gq8FxmHVT+4OOooqgk+qq1wpXD4Dn/14wISVP9wuVIsvYfkntMpGnHu7p0Q4V8z/
                                    DbYob3SIWkgvjeY/5L0zeK38dRvyuVJ7tl950NrWkGnPFimA+rjiE9hwos0mAe9t
                                    AnMcx3nuiV7QZtDgMayURcRUB1Tq/LKA9+3jRZx06aW9gk+WzgfBvX+Ntvu7GVMw
                                    piDamW3F16qsWxc5k2Utca6tzv7Pv0m0+D6TsN2uOxvTMcqsOqMw67pqePdeQydl
                                    8cNgu4k4XQpZPaYYbdhAf3PxBio7Itk5f6XrgSmdJNv1da5bACUzHUBZISqxOpc7
                                    AWIUO4fL8oLM07YT9yV+O4Kf6q7WZ4Oc6Nns5w1T++XUyp3vL5tGZSdHqDbN2q1q
                                    YTEyUQE+/ajaDihGTxZ4oUC2B3YGzD9Wr866/mA2tgwKhjYl3fhJWjnKlFO921PH
                                    emRD3UR3
                                    -----END CERTIFICATE-----
                                </Certificate>
                                <Certificate format="pem">
                                    -----BEGIN CERTIFICATE-----
                                    MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
                                    BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAz
                                    NzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
                                    AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
                                    Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
                                    tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
                                    nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
                                    C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
                                    oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
                                    JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
                                    sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
                                    igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
                                    RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
                                    aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
                                    AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
                                    IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
                                    VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnu
                                    XKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83U
                                    h6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cno
                                    L/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2ok
                                    QBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vA
                                    D32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAI
                                    mMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoW
                                    Fua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91
                                    oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09o
                                    jm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUB
                                    ZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCH
                                    ex0SdDrx+tWUDqG8At2JHA==
                                    -----END CERTIFICATE-----
                                </Certificate>
                            </CertificateChain>
                        </Key>
                    </Keybox>
                </AndroidAttestation>
                """;
    }

    private String getXmlMd5(String xml) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(xml.getBytes("UTF-8"));
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (Exception e) {
            return String.valueOf(xml.hashCode());
        }
    }
}