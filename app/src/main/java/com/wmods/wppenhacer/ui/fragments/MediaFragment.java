package com.wmods.wppenhacer.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.CallRecordingSettingsActivity;
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment;

public class MediaFragment extends BasePreferenceFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_media, rootKey);

        // Call Recording Settings preference
        var callRecordingSettings = findPreference("call_recording_settings");
        if (callRecordingSettings != null) {
            callRecordingSettings.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), CallRecordingSettingsActivity.class);
                startActivity(intent);
                return true;
            });
        }

        var videoCallScreenRec = findPreference("video_call_screen_rec");
        if (videoCallScreenRec != null) {
            videoCallScreenRec.setEnabled(true);
            videoCallScreenRec.setOnPreferenceClickListener(preference -> {
                try {
                    var intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/mubashardev"));
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            });
            videoCallScreenRec.setOnPreferenceChangeListener((preference, newValue) -> false); // Prevent toggling
        }
        var useRootPref = findPreference("call_recording_use_root");
        if (useRootPref != null) {
            useRootPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue) {
                    new Thread(() -> {
                        try {
                            // Request root immediately to prompt Magisk, and grant to both possible WA
                            // packages
                            Process process = Runtime.getRuntime().exec(new String[] { "su", "-c", "echo root_test" });
                            int exitCode = process.waitFor();
                            if (exitCode == 0) {
                                String[] cmds = {
                                        "pm grant com.whatsapp android.permission.CAPTURE_AUDIO_OUTPUT",
                                        "pm grant com.whatsapp.w4b android.permission.CAPTURE_AUDIO_OUTPUT",
                                        "appops set com.whatsapp RECORD_AUDIO allow",
                                        "appops set com.whatsapp.w4b RECORD_AUDIO allow"
                                };
                                for (String cmd : cmds) {
                                    Runtime.getRuntime().exec(new String[] { "su", "-c", cmd }).waitFor();
                                }
                            } else {
                                requireActivity().runOnUiThread(() -> android.widget.Toast.makeText(requireContext(),
                                        "Root Access Denied!", android.widget.Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
                return true;
            });
        }
    }
}
