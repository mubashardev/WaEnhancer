package com.waenhancer.ui.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.waenhancer.R;
import com.waenhancer.activities.FilterItemsActivity;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;
import com.waenhancer.activities.BottomBarCustomizationActivity;
import org.json.JSONArray;

public class CustomizationFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_customization, rootKey);

        Preference filterItemsPref = findPreference("filter_items");
        if (filterItemsPref != null) {
            filterItemsPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), FilterItemsActivity.class);
                startActivity(intent);
                return true;
            });
        }

        Preference pillCustomizerPref = findPreference("floating_bottom_bar_customizer");
        if (pillCustomizerPref != null) {
            pillCustomizerPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), BottomBarCustomizationActivity.class);
                startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
        updateFilterItemsSummary();
    }

    private void updateFilterItemsSummary() {
        Preference filterItemsPref = findPreference("filter_items");
        if (filterItemsPref != null) {
            String rawFilters = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("filter_items", "");
            if (rawFilters == null || rawFilters.trim().isEmpty()) {
                filterItemsPref.setSummary(R.string.filters_summary_empty);
            } else {
                int count = 0;
                if (rawFilters.trim().startsWith("[")) {
                    try {
                        count = new JSONArray(rawFilters).length();
                    } catch (Exception ignored) {}
                } else {
                    String[] items = rawFilters.split("\n");
                    for (String item : items) {
                        if (!item.trim().isEmpty()) {
                            count++;
                        }
                    }
                }
                if (count == 0) {
                    filterItemsPref.setSummary(R.string.filters_summary_empty);
                } else {
                    filterItemsPref.setSummary(getString(R.string.filters_summary_count, count));
                }
            }
        }
    }
}