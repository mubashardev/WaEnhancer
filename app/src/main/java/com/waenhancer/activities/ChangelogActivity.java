package com.waenhancer.activities;

import com.waenhancer.BuildConfig;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
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
import com.google.android.material.tabs.TabLayout;
// import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.loadingindicator.LoadingIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChangelogActivity extends BaseActivity {

    public static final String EXTRA_TARGET_CHANNEL = "target_channel";

    private RecyclerView recyclerView;
    // private ShimmerFrameLayout shimmerFrameLayout;
    private LoadingIndicator progressIndicator;
    private TabLayout tabLayout;
    private ChangelogAdapter adapter;
    private final List<JSONObject> stableReleases = new ArrayList<>();
    private final List<JSONObject> betaReleases = new ArrayList<>();
    private boolean downgradesEnabled = false;
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
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerView = findViewById(R.id.changelog_recycler);
        // shimmerFrameLayout = findViewById(R.id.shimmer_view_container);
        progressIndicator = findViewById(R.id.expressive_loading_progress);
        tabLayout = findViewById(R.id.tabs);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.release_channel_stable));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.release_channel_beta));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateList(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChangelogAdapter(getCurrentVersion());
        // Load initial state
        downgradesEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("downgrades_enabled", false);
        adapter.setDowngradesEnabled(downgradesEnabled);

        recyclerView.setAdapter(adapter);

        fetchChangelog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh settings in case they were changed in UpdateSettingsActivity
        boolean newDowngradeState = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("downgrades_enabled", false);
        if (newDowngradeState != downgradesEnabled) {
            downgradesEnabled = newDowngradeState;
            adapter.setDowngradesEnabled(downgradesEnabled);
        }
    }

    // @Override
    // public void onBackPressed() {
    //     navigateToHome();
    // }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_changelog, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // navigateToHome();
            onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_update_settings) {
            startActivity(new Intent(this, UpdateSettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void updateList(int position) {
        if (position == 0) {
            adapter.setReleases(stableReleases);
        } else {
            adapter.setReleases(betaReleases);
        }
    }

    private void fetchChangelog() {
        // shimmerFrameLayout.setVisibility(View.VISIBLE);
        // shimmerFrameLayout.startShimmer();
        if (progressIndicator != null) {
            progressIndicator.setVisibility(View.VISIBLE);
        }
        tabLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        CompletableFuture.runAsync(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();

            var requestBuilder = new Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            if (BuildConfig.GH_PUBLIC_TOKEN != null && !BuildConfig.GH_PUBLIC_TOKEN.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + BuildConfig.GH_PUBLIC_TOKEN);
            }

            Request request = requestBuilder.build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonData = response.body().string();
                    JSONArray releases = new JSONArray(jsonData);

                    categorizeReleases(releases);

                    runOnUiThread(() -> {
                        // shimmerFrameLayout.stopShimmer();
                        // shimmerFrameLayout.setVisibility(View.GONE);
                        if (progressIndicator != null) {
                            progressIndicator.setVisibility(View.GONE);
                        }
                        tabLayout.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.VISIBLE);

                        String userChannel = getIntent().getStringExtra(EXTRA_TARGET_CHANNEL);
                        if (userChannel == null) {
                            userChannel = PreferenceManager.getDefaultSharedPreferences(this).getString("release_channel", "stable");
                        }

                        int defaultTab = "beta".equals(userChannel) ? 1 : 0;
                        if (tabLayout.getTabAt(defaultTab) != null) {
                            tabLayout.getTabAt(defaultTab).select();
                        }
                        updateList(defaultTab);
                    });
                } else {
                    if (response.code() == 403) {
                        throw new IOException("GitHub rate limit exceeded. Please try again in 1 hour.");
                    }
                    throw new IOException("Unexpected code " + response);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    // shimmerFrameLayout.stopShimmer();
                    // shimmerFrameLayout.setVisibility(View.GONE);
                    if (progressIndicator != null) {
                        progressIndicator.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, "Failed to load changelog: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void categorizeReleases(JSONArray releases) throws JSONException {
        stableReleases.clear();
        betaReleases.clear();

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            String tagName = release.optString("tag_name", "");
            boolean isBeta = tagName.contains("-beta-");

            if (isBeta) {
                betaReleases.add(release);
            } else {
                stableReleases.add(release);
            }
        }
    }

    private class ChangelogAdapter extends RecyclerView.Adapter<ChangelogViewHolder> {

        private final List<JSONObject> releases = new ArrayList<>();
        private final String currentVersion;
        private boolean downgradesEnabled = false;
        private Markwon markwon;
        private final java.util.Set<String> expandedTags = new java.util.HashSet<>();

        public ChangelogAdapter(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public void setDowngradesEnabled(boolean enabled) {
            this.downgradesEnabled = enabled;
            notifyDataSetChanged();
        }

        public void setReleases(List<JSONObject> newReleases) {
            releases.clear();
            releases.addAll(newReleases);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ChangelogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (markwon == null) {
                markwon = Markwon.builder(parent.getContext())
                        .usePlugin(HtmlPlugin.create())
                        .build();
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_changelog_version, parent, false);
            return new ChangelogViewHolder(view, markwon, currentVersion);
        }

        @Override
        public void onBindViewHolder(@NonNull ChangelogViewHolder holder, int position) {
            holder.bind(releases.get(position), downgradesEnabled, expandedTags);
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
        private final com.google.android.material.button.MaterialButton btnGithub;
        private final android.view.View btnUpdateSpacer;
        private final android.widget.ImageView ivExpandArrow;
        private final android.view.View layoutCollapsible;
        private final android.widget.LinearLayout changelogItemsContainer;
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
            btnGithub = itemView.findViewById(R.id.btn_github);
            btnUpdateSpacer = itemView.findViewById(R.id.btn_update_spacer);
            ivExpandArrow = itemView.findViewById(R.id.iv_expand_arrow);
            layoutCollapsible = itemView.findViewById(R.id.layout_collapsible_changelog);
            changelogItemsContainer = itemView.findViewById(R.id.changelog_items_container);
        }

        private static class ParsedCategory {

            final String name;
            final List<String> items = new java.util.ArrayList<>();

            ParsedCategory(String name) {
                this.name = name;
            }
        }

        private static List<ParsedCategory> parseBody(String body) {
            List<ParsedCategory> categories = new java.util.ArrayList<>();
            if (body == null || body.trim().isEmpty()) {
                return categories;
            }
            String[] lines = body.split("\n");
            ParsedCategory currentCategory = null;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Check if the line is a category header, e.g., [Added], [Fixes], [Improvements]
                if (line.startsWith("[") && line.endsWith("]")) {
                    String catName = line.substring(1, line.length() - 1).trim();
                    currentCategory = findOrCreateCategory(categories, catName);
                    continue;
                }

                String text = line;
                if (text.startsWith("-") || text.startsWith("*")) {
                    text = text.substring(1).trim();
                }

                String itemCategoryName = (currentCategory != null) ? currentCategory.name : "Added";
                if (text.startsWith("[")) {
                    int closeBracket = text.indexOf(']');
                    if (closeBracket > 0) {
                        itemCategoryName = text.substring(1, closeBracket).trim();
                        text = text.substring(closeBracket + 1).trim();
                    }
                }

                if (text.startsWith("-") || text.startsWith("*")) {
                    text = text.substring(1).trim();
                }

                if (!text.isEmpty()) {
                    ParsedCategory cat = findOrCreateCategory(categories, itemCategoryName);
                    cat.items.add(text);
                }
            }
            return categories;
        }

        private static ParsedCategory findOrCreateCategory(List<ParsedCategory> categories, String name) {
            for (ParsedCategory cat : categories) {
                if (cat.name.equalsIgnoreCase(name)) {
                    return cat;
                }
            }
            ParsedCategory newCat = new ParsedCategory(name);
            categories.add(newCat);
            return newCat;
        }

        private static int dpToPx(android.content.Context context, int dp) {
            return (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP,
                    dp,
                    context.getResources().getDisplayMetrics()
            );
        }

        public void bind(JSONObject release) {
            bind(release, false, new java.util.HashSet<>());
        }

        public void bind(JSONObject release, boolean downgradesEnabled, java.util.Set<String> expandedTags) {
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
            tvBadge.setVisibility(View.GONE); // No need of mentioning Stable/Beta chip on each item

            tvInstalledBadge.setVisibility(isInstalled ? View.VISIBLE : View.GONE);

            // Handle Expand/Collapse State
            boolean isExpanded = expandedTags.contains(tagName);
            layoutCollapsible.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            ivExpandArrow.setRotation(isExpanded ? 270 : 90);

            itemView.setOnClickListener(v -> {
                boolean nextExpanded = !expandedTags.contains(tagName);
                if (nextExpanded) {
                    expandedTags.add(tagName);
                    layoutCollapsible.setVisibility(View.VISIBLE);
                    ivExpandArrow.animate().rotation(270).setDuration(200).start();
                } else {
                    expandedTags.remove(tagName);
                    layoutCollapsible.setVisibility(View.GONE);
                    ivExpandArrow.animate().rotation(90).setDuration(200).start();
                }
            });

            // Populate Changelog Items
            changelogItemsContainer.removeAllViews();
            List<ParsedCategory> parsedCategories = parseBody(body);
            if (parsedCategories.isEmpty()) {
                tvBody.setVisibility(View.VISIBLE);
                markwon.setMarkdown(tvBody, body.trim());
            } else {
                tvBody.setVisibility(View.GONE);
                android.view.LayoutInflater inflater = android.view.LayoutInflater.from(itemView.getContext());
                for (ParsedCategory category : parsedCategories) {
                    // 1. Inflate Category Header Row
                    android.view.View headerRow = inflater.inflate(R.layout.item_changelog_row, changelogItemsContainer, false);
                    com.google.android.material.textview.MaterialTextView tvCatBadge = headerRow.findViewById(R.id.tv_item_badge);
                    com.google.android.material.textview.MaterialTextView tvCatText = headerRow.findViewById(R.id.tv_item_text);

                    tvCatText.setVisibility(View.GONE);
                    tvCatBadge.setText(category.name.toUpperCase(java.util.Locale.US));

                    // Style the category badge based on category name
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    gd.setCornerRadius(dpToPx(itemView.getContext(), 6));

                    int bgColor;
                    if ("added".equalsIgnoreCase(category.name)) {
                        bgColor = 0xFF10B981; // Emerald Green
                    } else if ("improvements".equalsIgnoreCase(category.name)) {
                        bgColor = 0xFF3B82F6; // Vibrant Blue
                    } else if ("fixes".equalsIgnoreCase(category.name)) {
                        bgColor = 0xFFEF4444; // Coral Red
                    } else {
                        bgColor = 0xFF64748B; // Slate Gray
                    }
                    gd.setColor(bgColor);
                    tvCatBadge.setBackground(gd);
                    tvCatBadge.setTextColor(0xFFFFFFFF);

                    changelogItemsContainer.addView(headerRow);

                    // 2. Inflate Category Bullet Point Rows
                    for (String itemText : category.items) {
                        android.view.View itemRow = inflater.inflate(R.layout.item_changelog_row, changelogItemsContainer, false);
                        com.google.android.material.textview.MaterialTextView tvItemBadge = itemRow.findViewById(R.id.tv_item_badge);
                        com.google.android.material.textview.MaterialTextView tvItemText = itemRow.findViewById(R.id.tv_item_text);

                        tvItemBadge.setVisibility(View.GONE);
                        markwon.setMarkdown(tvItemText, "•  " + itemText);

                        android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) tvItemText.getLayoutParams();
                        lp.leftMargin = dpToPx(itemView.getContext(), 16);
                        tvItemText.setLayoutParams(lp);

                        changelogItemsContainer.addView(itemRow);
                    }
                }
            }

            boolean showUpdateButton = isNewer || (downgradesEnabled && !isInstalled);
            btnUpdate.setVisibility(showUpdateButton ? View.VISIBLE : View.GONE);
            btnUpdateSpacer.setVisibility(showUpdateButton ? View.VISIBLE : View.GONE);

            if (showUpdateButton && !isNewer) {
                btnUpdate.setText(R.string.downgrade);
            } else {
                btnUpdate.setText(R.string.update);
            }

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
                    UpdateDownloader.showDownloadDialog(v.getContext(), downloadUrl, tagName, downgradesEnabled);
                } else {
                    try {
                        android.content.Context context = v.getContext();
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/mubashardev/WaEnhancer/releases"));
                        context.startActivity(intent);
                    } catch (Exception ignored) {
                    }
                }
            });

            String htmlUrl = release.optString("html_url", "https://github.com/mubashardev/WaEnhancer/releases");
            btnGithub.setOnClickListener(v -> {
                try {
                    android.content.Context context = v.getContext();
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(htmlUrl));
                    context.startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }

        private String formatDate(String isoDate) {
            if (isoDate == null || isoDate.isEmpty()) {
                return "";
            }
            try {
                java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                java.util.Date date = isoFormat.parse(isoDate);
                if (date != null) {
                    java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US);
                    return displayFormat.format(date);
                }
            } catch (Exception ignored) {
            }
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
        if (version == null) {
            return "";
        }
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
        if (version == null || version.isEmpty()) {
            return 0;
        }

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
            if (parts.length > 0) {
                major = Long.parseLong(parts[0]);
            }
            if (parts.length > 1) {
                minor = Long.parseLong(parts[1]);
            }
            if (parts.length > 2) {
                patch = Long.parseLong(parts[2]);
            }
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
