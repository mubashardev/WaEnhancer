package com.waenhancer.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.databinding.ActivityRecordingsBinding;
import com.waenhancer.ui.fragments.RecordingsFragment;

public class RecordingsActivity extends BaseActivity {

    private ActivityRecordingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.recordings_manager);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.recordings_container, new RecordingsFragment())
                    .commit();
        }
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
