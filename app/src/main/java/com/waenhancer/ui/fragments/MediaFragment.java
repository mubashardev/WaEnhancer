package com.waenhancer.ui.fragments;

import static com.waenhancer.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.R;
import com.waenhancer.activities.CallRecordingSettingsActivity;
import com.waenhancer.activities.RecordingsActivity;
import com.waenhancer.preference.ContactPickerPreference;
import com.waenhancer.preference.FileSelectPreference;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;
import com.waenhancer.xposed.features.general.LiteMode;

import androidx.preference.Preference;

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

        var callRecordingSettings = findPreference("call_recording_settings");
        if (callRecordingSettings != null) {
            callRecordingSettings.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), CallRecordingSettingsActivity.class));
                return true;
            });
        }

        var callRecordingManager = findPreference("call_recording_manage");
        if (callRecordingManager != null) {
            callRecordingManager.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), RecordingsActivity.class));
                return true;
            });
        }

        updateCallRecordingPreferenceState();

        var videoCallScreenRec = findPreference("video_call_screen_rec");
        if (videoCallScreenRec != null) {
            videoCallScreenRec.setEnabled(false);
            videoCallScreenRec.setOnPreferenceClickListener(preference -> {
                return true;
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(android.content.SharedPreferences sharedPreferences, @Nullable String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key == null || "call_recording_enable".equals(key) || "call_recording_mode".equals(key)) {
            updateCallRecordingPreferenceState();
        }
    }

    private void updateCallRecordingPreferenceState() {
        boolean enabled = mPrefs.getBoolean("call_recording_enable", false);
        String mode = mPrefs.getString("call_recording_mode", "0");

        Preference includeContacts = findPreference("call_recording_whitelist");
        Preference excludeContacts = findPreference("call_recording_blacklist");
        Preference settings = findPreference("call_recording_settings");
        Preference manager = findPreference("call_recording_manage");

        if (includeContacts != null) {
            includeContacts.setVisible(enabled && "3".equals(mode));
            includeContacts.setEnabled(enabled && "3".equals(mode));
        }
        if (excludeContacts != null) {
            excludeContacts.setVisible(enabled && "2".equals(mode));
            excludeContacts.setEnabled(enabled && "2".equals(mode));
        }
        if (settings != null) {
            settings.setEnabled(enabled);
        }
        if (manager != null) {
            manager.setEnabled(enabled);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }

        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            ContactPickerPreference contactPickerPreference = findPreference(data.getStringExtra("key"));
            if (contactPickerPreference != null) {
                contactPickerPreference.handleActivityResult(requestCode, resultCode, data);
            }
        } else if (requestCode == LiteMode.REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
            FileSelectPreference fileSelectPreference = findPreference(data.getStringExtra("key"));
            if (fileSelectPreference != null) {
                fileSelectPreference.handleActivityResult(requestCode, resultCode, data);
            }
        }
    }
}
