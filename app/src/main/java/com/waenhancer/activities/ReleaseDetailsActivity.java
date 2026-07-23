package com.waenhancer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.UpdateDownloader;
import com.waenhancer.activities.base.BaseActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ReleaseDetailsActivity extends BaseActivity {

    private LoadingIndicator progressIndicator;
    private LinearLayout errorLayout;
    private MaterialTextView tvErrorTitle;
    private MaterialTextView tvErrorMessage;
    private MaterialButton btnRetry;
    private NestedScrollView contentLayout;

    private MaterialTextView tvVersionName;
    private MaterialTextView tvReleaseDate;
    private MaterialTextView tvReleaseBody;
    private LinearLayout changelogItemsContainer;
    private MaterialButton btnDownload;

    private String tagName;
    private Markwon markwon;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_release_details);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        progressIndicator = findViewById(R.id.progress_indicator);
        errorLayout = findViewById(R.id.error_layout);
        tvErrorTitle = findViewById(R.id.tv_error_title);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        btnRetry = findViewById(R.id.btn_retry);
        contentLayout = findViewById(R.id.content_layout);

        tvVersionName = findViewById(R.id.tv_version_name);
        tvReleaseDate = findViewById(R.id.tv_release_date);
        tvReleaseBody = findViewById(R.id.tv_release_body);
        changelogItemsContainer = findViewById(R.id.changelog_items_container);
        btnDownload = findViewById(R.id.btn_download);

        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .build();

        // Retrieve the tag name from deep link or Intent extra
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            String path = data.getPath();
            if (path != null && path.startsWith("/releases/")) {
                tagName = path.substring("/releases/".length()).trim();
                if (tagName.endsWith("/")) {
                    tagName = tagName.substring(0, tagName.length() - 1).trim();
                }
            }
        }

        if (tagName == null || tagName.trim().isEmpty()) {
            tagName = intent.getStringExtra("tag_name");
        }

        if (tagName == null || tagName.trim().isEmpty()) {
            showError("Invalid Link", "The link is incorrect or does not contain a valid release version.");
            return;
        }

        btnRetry.setOnClickListener(v -> fetchReleaseDetails());

        fetchReleaseDetails();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Go back to the changelog page
        Intent intent = new Intent(this, ChangelogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void fetchReleaseDetails() {
        showLoading();

        CompletableFuture.runAsync(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();

            // Fetch from mubashardev/WaEnhancer as requested
            String url = "https://api.github.com/repos/mubashardev/WaEnhancer/releases/tags/" + tagName;

            var requestBuilder = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            if (BuildConfig.GH_PUBLIC_TOKEN != null && !BuildConfig.GH_PUBLIC_TOKEN.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + BuildConfig.GH_PUBLIC_TOKEN);
            }

            Request request = requestBuilder.build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonData = response.body().string();
                    JSONObject releaseJson = new JSONObject(jsonData);
                    runOnUiThread(() -> bindReleaseDetails(releaseJson));
                } else {
                    final int code = response.code();
                    runOnUiThread(() -> {
                        if (code == 404) {
                            showError("Release Not Found", "The requested release details could not be found. The link might be incorrect.");
                        } else if (code == 403) {
                            showError("Temporarily Unavailable", "The release details are currently inaccessible due to GitHub rate limits. Please try again in an hour.");
                        } else {
                            showError("Release Inaccessible", "The release details are currently inaccessible. Please try again later.");
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> showError("Release Inaccessible", "Unable to load details. Please check your internet connection and try again."));
            }
        });
    }

    private void bindReleaseDetails(JSONObject release) {
        try {
            String tagName = release.optString("tag_name", this.tagName);
            String publishedAt = release.optString("published_at", "");
            String body = release.optString("body", "No description available.");

            tvVersionName.setText(tagName);
            tvReleaseDate.setText(formatDate(publishedAt));

            // Populate Changelog Items like ChangelogActivity does
            changelogItemsContainer.removeAllViews();
            List<ParsedCategory> parsedCategories = parseBody(body);
            if (parsedCategories.isEmpty()) {
                tvReleaseBody.setVisibility(View.VISIBLE);
                changelogItemsContainer.setVisibility(View.GONE);
                markwon.setMarkdown(tvReleaseBody, body.trim());
            } else {
                tvReleaseBody.setVisibility(View.GONE);
                changelogItemsContainer.setVisibility(View.VISIBLE);
                LayoutInflater inflater = LayoutInflater.from(this);
                for (ParsedCategory category : parsedCategories) {
                    // 1. Inflate Category Header Row
                    View headerRow = inflater.inflate(R.layout.item_changelog_row, changelogItemsContainer, false);
                    MaterialTextView tvCatBadge = headerRow.findViewById(R.id.tv_item_badge);
                    MaterialTextView tvCatText = headerRow.findViewById(R.id.tv_item_text);
                    
                    tvCatText.setVisibility(View.GONE);
                    tvCatBadge.setText(category.name.toUpperCase(Locale.US));
                    
                    // Style the category badge based on category name
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.RECTANGLE);
                    gd.setCornerRadius(dpToPx(this, 6));
                    
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
                        View itemRow = inflater.inflate(R.layout.item_changelog_row, changelogItemsContainer, false);
                        MaterialTextView tvItemBadge = itemRow.findViewById(R.id.tv_item_badge);
                        MaterialTextView tvItemText = itemRow.findViewById(R.id.tv_item_text);
                        
                        tvItemBadge.setVisibility(View.GONE);
                        markwon.setMarkdown(tvItemText, "•  " + itemText);
                        
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tvItemText.getLayoutParams();
                        lp.leftMargin = dpToPx(this, 16);
                        tvItemText.setLayoutParams(lp);
                        
                        changelogItemsContainer.addView(itemRow);
                    }
                }
            }

            // Parse assets to find apk
            String downloadUrl = null;
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null) {
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
            }

            final String finalDownloadUrl = downloadUrl;
            if (finalDownloadUrl != null) {
                btnDownload.setVisibility(View.VISIBLE);
                btnDownload.setOnClickListener(v -> {
                    boolean useRoot = PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean("downgrades_enabled", false);
                    UpdateDownloader.showDownloadDialog(this, finalDownloadUrl, tagName, useRoot);
                });
            } else {
                btnDownload.setVisibility(View.GONE);
            }

            showContent();
        } catch (Exception e) {
            showError("Parse Error", "Failed to process release details properly.");
        }
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            Date date = isoFormat.parse(isoDate);
            if (date != null) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
                return displayFormat.format(date);
            }
        } catch (Exception ignored) {}
        return isoDate;
    }

    private void showLoading() {
        progressIndicator.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        contentLayout.setVisibility(View.GONE);
    }

    private void showContent() {
        progressIndicator.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
    }

    private void showError(String title, String message) {
        progressIndicator.setVisibility(View.GONE);
        contentLayout.setVisibility(View.GONE);
        if (tvErrorTitle != null) {
            tvErrorTitle.setText(title);
        }
        if (tvErrorMessage != null) {
            tvErrorMessage.setText(message);
        }
        errorLayout.setVisibility(View.VISIBLE);
    }

    private static class ParsedCategory {
        final String name;
        final List<String> items = new ArrayList<>();

        ParsedCategory(String name) {
            this.name = name;
        }
    }

    private static List<ParsedCategory> parseBody(String body) {
        List<ParsedCategory> categories = new ArrayList<>();
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

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
        );
    }
}