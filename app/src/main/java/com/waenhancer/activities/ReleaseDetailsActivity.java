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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
        btnDownload = findViewById(R.id.btn_download);

        markwon = Markwon.create(this);

        // Retrieve the tag name from deep link or Intent extra
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            String path = data.getPath();
            if (path != null && path.startsWith("/releases/")) {
                tagName = path.substring("/releases/".length());
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
            markwon.setMarkdown(tvReleaseBody, body.trim());

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
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            java.util.Date date = isoFormat.parse(isoDate);
            if (date != null) {
                java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US);
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
}
