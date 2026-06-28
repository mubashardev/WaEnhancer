package com.waenhancer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.waenhancer.R;
import com.waenhancer.ui.fragments.base.BaseFragment;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;

public class GeneralFragment extends BaseFragment {

    private TabLayout tabLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_general_tabbed, container, false);
        tabLayout = root.findViewById(R.id.general_tabs);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.general));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.home_screen));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.conversation));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (savedInstanceState == null) {
            switchTab(0);
        }

        return root;
    }

    private void switchTab(int position) {
        Fragment fragment = switch (position) {
            case 1 -> new HomeScreenGeneralPreference();
            case 2 -> new ConversationGeneralPreference();
            default -> new GeneralPreferenceFragment();
        };
        getChildFragmentManager().beginTransaction()
                .replace(R.id.general_frag_container, fragment)
                .commit();
    }

    public void showTab(String parentKey) {
        if (tabLayout == null) return;
        int tabIndex = 0;
        if ("general_home".equals(parentKey) || "general".equals(parentKey)) {
            tabIndex = 0;
        } else if ("homescreen".equals(parentKey)) {
            tabIndex = 1;
        } else if ("conversation".equals(parentKey)) {
            tabIndex = 2;
        }
        TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
        if (tab != null && tabLayout.getSelectedTabPosition() != tabIndex) {
            tab.select();
        }
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
            setupManageVersionsPref();
        }

        private void setupManageVersionsPref() {
            androidx.preference.Preference pref = findPreference("manage_supported_versions");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    android.content.Intent intent = new android.content.Intent(requireContext(),
                            SupportedVersionsActivity.class);
                    startActivity(intent);
                    return true;
                });
            }
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
                            intent.setClassName("com.waex.helper", "com.waex.helper.activities.ProUpdateActivity");
                            var prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                            var colorPreset = prefs.getString("wae_color_preset", "green");
                            intent.putExtra("wae_color_preset", colorPreset);
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

    public static class HomeScreenGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey);
            setDisplayHomeAsUpEnabled(false);
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }
    }

    public static class ConversationGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey);
            setDisplayHomeAsUpEnabled(false);

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

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }
    }
}
