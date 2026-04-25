package com.waenhancer.xposed.features.others;

import static com.waenhancer.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.waenhancer.R;
import com.waenhancer.preference.ContactPickerPreference;
import com.waenhancer.preference.FileSelectPreference;
import com.waenhancer.xposed.features.general.LiteMode;

public class EmbeddedSettingsFragment extends EmbeddedBasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.embedded_settings_root, rootKey);
        setToolbarTitle(R.string.app_name);
    }

    public static class GeneralScreen extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_general, rootKey);
        }
    }

    public static class HomeScreenSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_home_screen, rootKey);
        }
    }

    public static class ConversationSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_conversation, rootKey);
        }
    }

    public static class PrivacySettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_privacy, rootKey);
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
            setPreferencesFromResource(R.xml.embedded_settings_status, rootKey);
        }
    }

    public static class CallsSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_calls, rootKey);
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
    }

    public static class MediaSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_media, rootKey);

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
            setPreferencesFromResource(R.xml.embedded_settings_audio, rootKey);
        }
    }

    public static class AppearanceSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_appearance, rootKey);
        }
    }

    public static class AdvancedSettings extends EmbeddedBasePreferenceFragment {
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.embedded_settings_advanced, rootKey);
        }
    }
}
