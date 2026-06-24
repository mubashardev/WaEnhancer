package com.waenhancer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waenhancer.R;
import com.waenhancer.ui.fragments.base.BaseFragment;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;

public class GeneralFragment extends BaseFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        var root = super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction().add(R.id.frag_container, new GeneralPreferenceFragment()).commitNow();
        }
        
        
        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (androidx.fragment.app.Fragment fragment : getChildFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    public static class GeneralPreferenceFragment extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.fragment_general, rootKey);
            updatePluginPreference();
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
            updatePluginPreference();
        }

        private void updatePluginPreference() {
            android.content.Context context = getContext();
            if (context == null) return;
            androidx.preference.Preference pref = findPreference("unlock_limited_free");
            androidx.preference.Preference updatesPref = findPreference("pro_plugin_updates");
            androidx.preference.PreferenceCategory category = findPreference("plugin_pack_category");
            if (category == null) return;

            boolean isInstalled = com.waenhancer.xposed.utils.ProHelper.isPluginInstalled(context);
            if (isInstalled) {
                category.setVisible(true);
                if (pref != null) pref.setVisible(false);
                if (updatesPref != null) {
                    updatesPref.setVisible(true);
                    updatesPref.setOnPreferenceClickListener(preference -> {
                        try {
                            android.content.Intent intent = new android.content.Intent();
                            intent.setClassName("com.waex.pro", "com.waex.pro.activities.ProUpdateActivity");
                            startActivity(intent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(context, "Failed to launch update activity: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }
            } else {
                category.setVisible(true);
                if (pref != null) {
                    pref.setVisible(true);
                    pref.setOnPreferenceClickListener(preference -> {
                        com.waenhancer.xposed.utils.ProHelper.checkRootAndInstallPlugin(getActivity(), null);
                        return true;
                    });
                }
                if (updatesPref != null) updatesPref.setVisible(false);
            }
        }
    }

    public static class HomeGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_home, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class HomeScreenGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey);
            setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class ConversationGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey);
            setDisplayHomeAsUpEnabled(true);

            androidx.preference.EditTextPreference customLimitPref = findPreference("customforwardlimit");
            if (customLimitPref != null) {
                customLimitPref.setSummaryProvider(preference -> {
                    String val = customLimitPref.getText();
                    boolean hasKey = customLimitPref.getSharedPreferences() != null && customLimitPref.getSharedPreferences().contains("customforwardlimit");
                    if (!hasKey || android.text.TextUtils.isEmpty(val)) {
                        return getString(R.string.customforwardlimit_sum);
                    }
                    return val;
                });
                customLimitPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789"));
                    editText.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
                });
            }
        }
    }

}
