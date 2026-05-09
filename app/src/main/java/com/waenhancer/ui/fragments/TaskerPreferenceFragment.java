package com.waenhancer.ui.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.waenhancer.R;
import com.waenhancer.activities.TaskerGuideActivity;
import com.waenhancer.activities.TaskerHistoryActivity;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;

/**
 * Hosts the Tasker/Automation preference screen
 * ({@code preference_tasker.xml}) and routes the two "Resources"
 * items to their respective activities.
 */
public class TaskerPreferenceFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preference_tasker, rootKey);

        // "Automation Guide & Samples" → TaskerGuideActivity
        Preference guide = findPreference("tasker_guide");
        if (guide != null) {
            guide.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), TaskerGuideActivity.class));
                return true;
            });
        }

        // "Automation History" → TaskerHistoryActivity
        Preference history = findPreference("tasker_history");
        if (history != null) {
            history.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), TaskerHistoryActivity.class));
                return true;
            });
        }
    }
}
