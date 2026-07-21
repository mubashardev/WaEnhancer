package com.waenhancer.ui.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.waenhancer.BuildConfig;

public class SupportedVersionsActivity extends BaseActivity {

    /**
     * Valid version pattern: major.minor.patch.build
     * build can be a number or "xx" (wildcard).
     * Examples: 2.26.18.xx  or  2.26.18.15
     */
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.(\\d+|xx)$");

    /** SharedPreferences key where user custom versions are stored (Set<String>) */
    private static final String PREF_CUSTOM_WPP = "custom_versions_wpp";
    private static final String PREF_CUSTOM_BIZ = "custom_versions_business";
    private static final String PREF_CUSTOMIZE_ENABLED = "customize_supported_versions";

    private SharedPreferences mPrefs;
    /** True when "Customize Supported Versions" switch is ON */
    private boolean isCustomizeEnabled = false;

    private LinearLayout wppContainer;
    private LinearLayout businessContainer;
    private MaterialTextView totalText;
    private ExtendedFloatingActionButton fabAddVersion;

    // Cached system versions
    private List<String> systemWppVersions;
    private List<String> systemBizVersions;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supported_versions);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        wppContainer = findViewById(R.id.wpp_versions_container);
        businessContainer = findViewById(R.id.business_versions_container);
        totalText = findViewById(R.id.total_versions_text);
        fabAddVersion = findViewById(R.id.fab_add_version);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        isCustomizeEnabled = mPrefs.getBoolean(PREF_CUSTOMIZE_ENABLED, false);

        // Load system versions
        String[] sysWpp = getResources().getStringArray(R.array.supported_versions_wpp);
        String[] sysBiz = getResources().getStringArray(R.array.supported_versions_business);
        Arrays.sort(sysWpp, Collections.reverseOrder());
        Arrays.sort(sysBiz, Collections.reverseOrder());
        systemWppVersions = Arrays.asList(sysWpp);
        systemBizVersions = Arrays.asList(sysBiz);

        // FAB only visible when customize is enabled
        fabAddVersion.setVisibility(isCustomizeEnabled ? View.VISIBLE : View.GONE);
        fabAddVersion.setOnClickListener(v -> showAddVersionDialog(null, null));

        refresh();
    }

    // ── Refresh all lists ──────────────────────────────────────────────────────

    private void refresh() {
        wppContainer.removeAllViews();
        businessContainer.removeAllViews();

        List<String> customWpp = getCustomVersions(PREF_CUSTOM_WPP);
        List<String> customBiz = getCustomVersions(PREF_CUSTOM_BIZ);

        Collections.sort(customWpp, Collections.reverseOrder());
        Collections.sort(customBiz, Collections.reverseOrder());

        populateSection(wppContainer, systemWppVersions, customWpp, PREF_CUSTOM_WPP);
        populateSection(businessContainer, systemBizVersions, customBiz, PREF_CUSTOM_BIZ);

        // Active count = system versions + user versions (only when enabled)
        int activeTotal = systemWppVersions.size() + systemBizVersions.size();
        if (isCustomizeEnabled) {
            activeTotal += customWpp.size() + customBiz.size();
        }
        totalText.setText(activeTotal + " Active Versions");
    }

    private void populateSection(LinearLayout container,
                                  List<String> systemVersions,
                                  List<String> customVersions,
                                  String prefKey) {
        LayoutInflater inflater = LayoutInflater.from(this);

        // System versions always active; user versions active only when customize is enabled
        List<VersionEntry> all = new ArrayList<>();
        for (String v : systemVersions) all.add(new VersionEntry(v, true, true));
        for (String v : customVersions) all.add(new VersionEntry(v, false, isCustomizeEnabled));

        for (int i = 0; i < all.size(); i++) {
            VersionEntry entry = all.get(i);
            View row = inflater.inflate(R.layout.item_supported_version, container, false);

            MaterialTextView versionName = row.findViewById(R.id.version_name);
            MaterialTextView badge = row.findViewById(R.id.version_badge);
            ImageButton deleteBtn = row.findViewById(R.id.version_delete);
            View divider = row.findViewById(R.id.version_divider);

            versionName.setText(entry.version);

            if (entry.isSystem) {
                // System — always active, muted badge
                badge.setText(getString(R.string.system_defined));
                badge.setAlpha(0.6f);
                row.setAlpha(1f);
                versionName.setAlpha(1f);
            } else if (entry.isActive) {
                // User-defined and currently applied
                badge.setText(getString(R.string.user_defined));
                badge.setAlpha(1f);
                row.setAlpha(1f);
                versionName.setAlpha(1f);
            } else {
                // User-defined but NOT applied (customize toggle is off)
                badge.setText(getString(R.string.not_applied));
                badge.setAlpha(0.5f);
                row.setAlpha(0.4f);         // dim the whole row
                versionName.setAlpha(0.6f);
            }

            // Delete button — visible for user versions only when customize is ON
            if (!entry.isSystem && isCustomizeEnabled) {
                deleteBtn.setVisibility(View.VISIBLE);
                deleteBtn.setOnClickListener(v -> confirmDelete(entry.version, prefKey));
            } else {
                deleteBtn.setVisibility(View.GONE);
            }

            // Long-press edit is not needed anymore as the flow is automated
            row.setOnLongClickListener(null);

            // Hide divider on last item
            if (i == all.size() - 1 && divider != null) {
                divider.setVisibility(View.GONE);
            }

            container.addView(row);
        }
    }

    // ── Add Support Flow ───────────────────────────────────────────────────────

    private void showAddVersionDialog(@Nullable String existingVersion, @Nullable String prefKey) {
        String wppVer = getInstalledVersion("com.whatsapp");
        String bizVer = getInstalledVersion("com.whatsapp.w4b");

        boolean isWppInstalled = wppVer != null;
        boolean isBizInstalled = bizVer != null;

        if (!isWppInstalled && !isBizInstalled) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("No WhatsApp Installed")
                    .setMessage("Neither WhatsApp nor WhatsApp Business was found installed on this device.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        if (isWppInstalled && isBizInstalled) {
            String[] options = {
                    "WhatsApp (Installed: " + wppVer + ")",
                    "WhatsApp Business (Installed: " + bizVer + ")"
            };
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Select WhatsApp Application")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            confirmAddSupport(wppVer, PREF_CUSTOM_WPP, "WhatsApp");
                        } else {
                            confirmAddSupport(bizVer, PREF_CUSTOM_BIZ, "WhatsApp Business");
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (isWppInstalled) {
            confirmAddSupport(wppVer, PREF_CUSTOM_WPP, "WhatsApp");
        } else {
            confirmAddSupport(bizVer, PREF_CUSTOM_BIZ, "WhatsApp Business");
        }
    }

    private void confirmAddSupport(String version, String prefKey, String appName) {
        String wildcard = convertToWildcard(version);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Supported Version")
                .setMessage("Installed " + appName + " version: " + version + "\n\n" +
                            "Would you like to add custom support for: " + wildcard + "?")
                .setPositiveButton("Add Support", (dialog, which) -> {
                    saveCustomVersion(wildcard, prefKey, null);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getInstalledVersion(String packageName) {
        try {
            var packageInfo = getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            return null;
        }
    }

    private String convertToWildcard(String version) {
        if (version == null) return "";
        String[] parts = version.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".xx";
        }
        return version;
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    private void confirmDelete(String version, String prefKey) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete))
                .setMessage("Remove \"" + version + "\" from the list?")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Set<String> versions = new LinkedHashSet<>(getCustomVersions(prefKey));
                    versions.remove(version);
                    mPrefs.edit().putStringSet(prefKey, versions).apply();
                    notifyXposed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private void saveCustomVersion(String newVersion, String prefKey, @Nullable String oldVersion) {
        List<String> system = PREF_CUSTOM_WPP.equals(prefKey)
                ? systemWppVersions : systemBizVersions;

        for (String sv : system) {
            if (sv.equals(newVersion)) {
                Toast.makeText(this,
                        getString(R.string.version_exists),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Set<String> versions = new LinkedHashSet<>(getCustomVersions(prefKey));

        if (!newVersion.equals(oldVersion) && versions.contains(newVersion)) {
            Toast.makeText(this,
                    getString(R.string.version_exists),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (oldVersion != null) {
            versions.remove(oldVersion);
        }
        versions.add(newVersion);
        mPrefs.edit().putStringSet(prefKey, versions).apply();
        notifyXposed();
        refresh();
    }

    private List<String> getCustomVersions(String prefKey) {
        Set<String> set = mPrefs.getStringSet(prefKey, new LinkedHashSet<>());
        return new ArrayList<>(set);
    }

    // ── Notify Xposed ──────────────────────────────────────────────────────────

    private void notifyXposed() {
        try {
            for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
                Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
                intent.setPackage(pkg);
                sendBroadcast(intent);
            }
        } catch (Exception ignored) {
        }
    }

    // ── Helper DTO ─────────────────────────────────────────────────────────────

    private static class VersionEntry {
        final String version;
        final boolean isSystem;
        /** True when this version is actually fed into the Xposed hook */
        final boolean isActive;

        VersionEntry(String version, boolean isSystem, boolean isActive) {
            this.version = version;
            this.isSystem = isSystem;
            this.isActive = isActive;
        }
    }
}