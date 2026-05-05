package com.waenhancer.activities;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.utils.LogManager;

public class UpdateSettingsActivity extends BaseActivity {

    private static final String PREF_DOWNGRADES = "downgrades_enabled";
    private static final String PREF_UPDATE_ALERTS = "update_alert_pref";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);

        MaterialSwitch switchRoot = findViewById(R.id.switch_root_install);
        switchRoot.setChecked(prefs.getBoolean(PREF_DOWNGRADES, false));
        switchRoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new Thread(() -> {
                    boolean hasRoot = LogManager.hasRootAccess();
                    runOnUiThread(() -> {
                        if (hasRoot) {
                            prefs.edit().putBoolean(PREF_DOWNGRADES, true).apply();
                            com.waenhancer.xposed.core.WppCore.setPrivBoolean(PREF_DOWNGRADES, true);
                        } else {
                            switchRoot.setChecked(false);
                            Toast.makeText(this, R.string.root_required_downgrade, Toast.LENGTH_LONG).show();
                        }
                    });
                }).start();
            } else {
                prefs.edit().putBoolean(PREF_DOWNGRADES, false).apply();
                com.waenhancer.xposed.core.WppCore.setPrivBoolean(PREF_DOWNGRADES, false);
            }
        });

        RadioGroup radioGroup = findViewById(R.id.radio_group_update_alerts);
        String currentAlertPref = prefs.getString(PREF_UPDATE_ALERTS, "both");
        
        switch (currentAlertPref) {
            case "stable":
                radioGroup.check(R.id.radio_stable);
                break;
            case "beta":
                radioGroup.check(R.id.radio_beta);
                break;
            case "both":
            default:
                radioGroup.check(R.id.radio_both);
                break;
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String val = "both";
            if (checkedId == R.id.radio_stable) val = "stable";
            else if (checkedId == R.id.radio_beta) val = "beta";
            
            prefs.edit().putString(PREF_UPDATE_ALERTS, val).apply();
            com.waenhancer.xposed.core.WppCore.setPrivString(PREF_UPDATE_ALERTS, val);
        });
    }
}
