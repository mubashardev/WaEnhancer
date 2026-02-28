package com.wmods.wppenhacer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.google.android.material.textview.MaterialTextView;
import com.wmods.wppenhacer.R;

import java.util.Arrays;
import java.util.Collections;

public class SupportedVersionsActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supported_versions);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout wppContainer = findViewById(R.id.wpp_versions_container);
        LinearLayout businessContainer = findViewById(R.id.business_versions_container);
        MaterialTextView totalText = findViewById(R.id.total_versions_text);

        // WhatsApp Versions
        String[] wppVersions = getResources().getStringArray(R.array.supported_versions_wpp);
        Arrays.sort(wppVersions, Collections.reverseOrder());
        populateVersions(wppContainer, wppVersions);

        // Business Versions
        String[] bizVersions = getResources().getStringArray(R.array.supported_versions_business);
        Arrays.sort(bizVersions, Collections.reverseOrder());
        populateVersions(businessContainer, bizVersions);

        // Summary
        int total = wppVersions.length + bizVersions.length;
        totalText.setText(total + " Versions");
    }

    private void populateVersions(LinearLayout container, String[] versions) {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < versions.length; i++) {
            View row = inflater.inflate(R.layout.item_supported_version, container, false);

            MaterialTextView versionName = row.findViewById(R.id.version_name);
            View divider = row.findViewById(R.id.version_divider);

            versionName.setText(versions[i]);

            // Hide divider on the last item
            if (i == versions.length - 1 && divider != null) {
                divider.setVisibility(View.GONE);
            }

            container.addView(row);
        }
    }
}
