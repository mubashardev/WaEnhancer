package com.waenhancer.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.databinding.ActivityCallRecordingSettingsBinding;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class CallRecordingSettingsActivity extends BaseActivity {

    private static final String TAG = "WaEnhancer";

    private ActivityCallRecordingSettingsBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallRecordingSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.call_recording_settings);
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useRoot = prefs.getBoolean("call_recording_use_root", false);
        setModeSelection(useRoot);

        binding.radioRoot.setOnClickListener(v -> {
            setModeSelection(true);
            Toast.makeText(this, R.string.call_recording_root_checking, Toast.LENGTH_SHORT).show();
            checkRootAccess();
        });

        binding.rootModeCard.setOnClickListener(v -> binding.radioRoot.performClick());
        binding.radioNonRoot.setOnClickListener(v -> enableNonRootMode());
        binding.nonRootModeCard.setOnClickListener(v -> binding.radioNonRoot.performClick());
    }

    private void setModeSelection(boolean useRoot) {
        binding.radioRoot.setChecked(useRoot);
        binding.radioNonRoot.setChecked(!useRoot);
    }

    private void enableNonRootMode() {
        setModeSelection(false);
        prefs.edit().putBoolean("call_recording_use_root", false).apply();
        Toast.makeText(this, R.string.non_root_mode_enabled, Toast.LENGTH_SHORT).show();
    }

    private void checkRootAccess() {
        new Thread(() -> {
            boolean hasRoot = false;
            String rootOutput = "";

            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                os.writeBytes("id\n");
                os.writeBytes("exit\n");
                os.flush();

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                rootOutput = sb.toString();
                int exitCode = process.waitFor();
                hasRoot = exitCode == 0 && rootOutput.contains("uid=0");
            } catch (Exception e) {
                Log.e(TAG, "Root check exception: " + e.getMessage());
            }

            boolean rootGranted = hasRoot;
            String output = rootOutput;
            runOnUiThread(() -> {
                if (rootGranted) {
                    prefs.edit().putBoolean("call_recording_use_root", true).apply();
                    setModeSelection(true);
                    Toast.makeText(this, R.string.root_access_granted, Toast.LENGTH_SHORT).show();
                } else {
                    prefs.edit().putBoolean("call_recording_use_root", false).apply();
                    setModeSelection(false);
                    ;
                    Toast.makeText(this, R.string.root_access_denied, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
