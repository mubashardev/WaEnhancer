package com.wmods.wppenhacer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SupportedVersionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supported_versions);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<VersionItem> allVersions = new ArrayList<>();

        // Add WhatsApp Versions
        String[] wppVersions = getResources().getStringArray(R.array.supported_versions_wpp);
        Arrays.sort(wppVersions, Collections.reverseOrder());
        for (String v : wppVersions) {
            allVersions.add(new VersionItem(v, "WhatsApp Messenger"));
        }

        // Add Business Versions
        String[] bizVersions = getResources().getStringArray(R.array.supported_versions_business);
        Arrays.sort(bizVersions, Collections.reverseOrder());
        for (String v : bizVersions) {
            allVersions.add(new VersionItem(v, "WhatsApp Business"));
        }

        // To properly sort descending, we could sort the entire list together,
        // but grouping by package descending is cleaner for the UI.
        recyclerView.setAdapter(new VersionsAdapter(allVersions));
    }

    private static class VersionItem {
        String version;
        String packageType;

        VersionItem(String version, String packageType) {
            this.version = version;
            this.packageType = packageType;
        }
    }

    private static class VersionsAdapter extends RecyclerView.Adapter<VersionsAdapter.ViewHolder> {
        private final List<VersionItem> items;

        VersionsAdapter(List<VersionItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_supported_version, parent,
                    false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VersionItem item = items.get(position);
            holder.versionName.setText(item.version);
            holder.packageType.setText(item.packageType);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView versionName;
            TextView packageType;

            ViewHolder(View view) {
                super(view);
                versionName = view.findViewById(R.id.version_name);
                packageType = view.findViewById(R.id.package_type);
            }
        }
    }
}
