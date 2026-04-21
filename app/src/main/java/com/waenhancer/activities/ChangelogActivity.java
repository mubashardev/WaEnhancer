package com.waenhancer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.waenhancer.R;
import com.waenhancer.UpdateDownloader;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.UpdateChecker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.noties.markwon.Markwon;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChangelogActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ChangelogAdapter adapter;
    private static final String RELEASES_API = "https://api.github.com/repos/mubashardev/WaEnhancer/releases";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changelog);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.changelog_recycler);
        progressBar = findViewById(R.id.loading_progress);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChangelogAdapter(getCurrentVersion());
        recyclerView.setAdapter(adapter);

        fetchChangelog();
    }

    private void fetchChangelog() {
        progressBar.setVisibility(View.VISIBLE);
        
        CompletableFuture.runAsync(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WaEnhancer-ChangelogViewer")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonData = response.body().string();
                    JSONArray releases = new JSONArray(jsonData);
                    
                    List<JSONObject> filteredReleases = filterReleases(releases);
                    
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        adapter.setReleases(filteredReleases);
                    });
                } else {
                    throw new IOException("Unexpected code " + response);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load changelog: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private List<JSONObject> filterReleases(JSONArray releases) throws JSONException {
        List<JSONObject> list = new ArrayList<>();
        String userChannel = PreferenceManager.getDefaultSharedPreferences(this).getString("release_channel", "stable");
        boolean wantBeta = "beta".equals(userChannel);

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            String tagName = release.optString("tag_name", "");
            boolean isBeta = tagName.contains("-beta-");

            if (wantBeta) {
                // If user is on Beta channel, show only beta versions
                if (isBeta) list.add(release);
            } else {
                // If user is on Stable channel, show only stable versions
                if (!isBeta) list.add(release);
            }
        }
        return list;
    }

    private class ChangelogAdapter extends RecyclerView.Adapter<ChangelogViewHolder> {
        private final List<JSONObject> releases = new ArrayList<>();
        private final String currentVersion;
        private Markwon markwon;

        public ChangelogAdapter(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public void setReleases(List<JSONObject> newReleases) {
            releases.clear();
            releases.addAll(newReleases);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ChangelogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (markwon == null) markwon = Markwon.create(parent.getContext());
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_changelog_version, parent, false);
            return new ChangelogViewHolder(view, markwon, currentVersion);
        }

        @Override
        public void onBindViewHolder(@NonNull ChangelogViewHolder holder, int position) {
            holder.bind(releases.get(position));
        }

        @Override
        public int getItemCount() {
            return releases.size();
        }
    }

    private static class ChangelogViewHolder extends RecyclerView.ViewHolder {
        private final com.google.android.material.textview.MaterialTextView tvVersion;
        private final com.google.android.material.textview.MaterialTextView tvDate;
        private final com.google.android.material.textview.MaterialTextView tvBadge;
        private final com.google.android.material.textview.MaterialTextView tvInstalledBadge;
        private final com.google.android.material.textview.MaterialTextView tvBody;
        private final com.google.android.material.button.MaterialButton btnUpdate;
        private final Markwon markwon;
        private final String currentVersion;

        public ChangelogViewHolder(@NonNull View itemView, Markwon markwon, String currentVersion) {
            super(itemView);
            this.markwon = markwon;
            this.currentVersion = currentVersion;
            tvVersion = itemView.findViewById(R.id.tv_version_name);
            tvDate = itemView.findViewById(R.id.tv_release_date);
            tvBadge = itemView.findViewById(R.id.tv_release_badge);
            tvInstalledBadge = itemView.findViewById(R.id.tv_installed_badge);
            tvBody = itemView.findViewById(R.id.tv_changelog_body);
            btnUpdate = itemView.findViewById(R.id.btn_update);
        }

        public void bind(JSONObject release) {
            String tagName = release.optString("tag_name", "Unknown");
            String publishedAt = release.optString("published_at", "");
            String body = release.optString("body", "No description available.");
            boolean isBeta = tagName.contains("-beta-");

            String normalizedTagName = normalizeVersion(tagName);
            boolean isInstalled = normalizedTagName.equalsIgnoreCase(currentVersion);
            
            long releaseNum = versionToLong(normalizedTagName);
            long installedNum = versionToLong(currentVersion);
            boolean isNewer = releaseNum > installedNum;

            tvVersion.setText(tagName);
            tvDate.setText(formatDate(publishedAt));
            tvBadge.setVisibility(isBeta ? View.VISIBLE : View.GONE);
            tvBadge.setText(isBeta ? "BETA" : "STABLE");
            tvInstalledBadge.setVisibility(isInstalled ? View.VISIBLE : View.GONE);
            markwon.setMarkdown(tvBody, body.trim());

            btnUpdate.setVisibility(isNewer ? View.VISIBLE : View.GONE);
            btnUpdate.setOnClickListener(v -> {
                String downloadUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.optJSONObject(j);
                        if (asset != null) {
                            String assetName = asset.optString("name", "");
                            if (assetName.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }
                }

                if (downloadUrl != null) {
                    UpdateDownloader.showDownloadDialog(v.getContext(), downloadUrl, tagName);
                } else {
                    try {
                        android.content.Context context = v.getContext();
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, 
                                android.net.Uri.parse("https://github.com/mubashardev/WaEnhancer/releases"));
                        context.startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
        }

        private String formatDate(String isoDate) {
            if (isoDate == null || isoDate.isEmpty()) return "";
            try {
                java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                java.util.Date date = isoFormat.parse(isoDate);
                if (date != null) {
                    java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US);
                    return displayFormat.format(date);
                }
            } catch (Exception ignored) {}
            return isoDate;
        }
    }

    private String getCurrentVersion() {
        try {
            return normalizeVersion(com.waenhancer.BuildConfig.VERSION_NAME);
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int plusIndex = normalized.indexOf('+');
        if (plusIndex >= 0) {
            normalized = normalized.substring(0, plusIndex);
        }
        return normalized.trim();
    }

    private static long versionToLong(String version) {
        if (version == null || version.isEmpty()) return 0;
        
        String normalized = normalizeVersion(version);
        String base = normalized;
        int betaNum = 0;
        boolean isBeta = false;

        int betaIndex = normalized.indexOf("-beta-");
        if (betaIndex > 0) {
            isBeta = true;
            base = normalized.substring(0, betaIndex);
            String betaPart = normalized.substring(betaIndex + 6).trim();
            try {
                betaNum = Integer.parseInt(betaPart);
            } catch (NumberFormatException ignored) {
                betaNum = 1;
            }
        }

        long major = 0, minor = 0, patch = 0;
        try {
            String[] parts = base.split("\\.");
            if (parts.length > 0) major = Long.parseLong(parts[0]);
            if (parts.length > 1) minor = Long.parseLong(parts[1]);
            if (parts.length > 2) patch = Long.parseLong(parts[2]);
        } catch (Exception e) {
            return 0L;
        }

        long baseCode = major * 1_000_000L + minor * 1_000L + patch;
        if (!isBeta) {
            return baseCode * 1_000L + 999L;
        }

        long safeBeta = Math.max(1, Math.min(betaNum, 998));
        return baseCode * 1_000L + safeBeta;
    }
}
