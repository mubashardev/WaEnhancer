package com.waenhancer.xposed.features.others;

import static com.waenhancer.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.waenhancer.R;
import com.waenhancer.activities.CallRecordingSettingsActivity;
import com.waenhancer.activities.RecordingsActivity;
import com.waenhancer.preference.ContactPickerPreference;
import com.waenhancer.preference.FileSelectPreference;
import com.waenhancer.xposed.features.general.LiteMode;
import com.waenhancer.xposed.utils.ResId;

public class EmbeddedSettingsFragment extends EmbeddedBasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(ResId.xml.embedded_settings_root, rootKey);
        setToolbarTitle(ResId.string.app_name);
    }

    public static class GeneralScreen extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_general, rootKey);
        }
    }

    public static class HomeScreenSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_home_screen, rootKey);
        }
    }

    public static class ConversationSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_conversation, rootKey);
        }
    }

    public static class PrivacySettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_privacy, rootKey);
            var deletedMessages = findPreference("open_deleted_messages");
            if (deletedMessages != null) {
                deletedMessages.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(requireContext(), com.waenhancer.activities.DeletedMessagesActivity.class));
                    return true;
                });
            }
        }
    }

    public static class StatusSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_status, rootKey);
        }
    }

    public static class CallsSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_calls, rootKey);

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
        }

        @Override
        public void onSharedPreferenceChanged(android.content.SharedPreferences sharedPreferences, @Nullable String key) {
            super.onSharedPreferenceChanged(sharedPreferences, key);
            if (key == null || "call_recording_enable".equals(key) || "call_recording_mode".equals(key)) {
                updateCallRecordingPreferenceState();
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (data == null) {
                return;
            }
            if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
                ContactPickerPreference contactPickerPref = findPreference(data.getStringExtra("key"));
                if (contactPickerPref != null) {
                    contactPickerPref.handleActivityResult(requestCode, resultCode, data);
                }
            } else if (requestCode == LiteMode.REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
                FileSelectPreference fileSelectPreference = findPreference(data.getStringExtra("key"));
                if (fileSelectPreference != null) {
                    fileSelectPreference.handleActivityResult(requestCode, resultCode, data);
                }
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
    }

    public static class MediaSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_media, rootKey);

            var downloadStatus = findPreference("downloadstatus");
            if (downloadStatus != null) {
                downloadStatus.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
            }
            var downloadViewOnce = findPreference("downloadviewonce");
            if (downloadViewOnce != null) {
                downloadViewOnce.setOnPreferenceChangeListener((preference, newValue) -> checkStoragePermission(newValue));
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (data == null) {
                return;
            }
            if (requestCode == LiteMode.REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
                FileSelectPreference fileSelectPreference = findPreference(data.getStringExtra("key"));
                if (fileSelectPreference != null) {
                    fileSelectPreference.handleActivityResult(requestCode, resultCode, data);
                }
            }
        }
    }

    public static class AudioSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_audio, rootKey);
        }
    }

    public static class AppearanceSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_appearance, rootKey);
        }
    }

    public static class AdvancedSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(ResId.xml.embedded_settings_advanced, rootKey);
        }
    }
}
