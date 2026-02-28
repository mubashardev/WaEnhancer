package com.wmods.wppenhacer.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.databinding.ActivityAboutBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AboutActivity extends BaseActivity {

    private ActivityAboutBinding binding;
    private ContributorAdapter adapter;
    private List<Contributor> contributorList = new ArrayList<>();

    private static final String API_URL = "https://api.github.com/repos/mubashardev/WaEnhancer/contributors";
    private static final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnTelegram.setOnClickListener(v -> openUrl("https://t.me/waenhancer"));
        binding.btnGithub.setOnClickListener(view -> openUrl("https://github.com/mubashardev/WaEnhancer"));

        adapter = new ContributorAdapter();
        binding.rvContributors.setAdapter(adapter);

        fetchContributors();
    }

    private void fetchContributors() {
        android.content.SharedPreferences prefs = getSharedPreferences("github_api_cache", MODE_PRIVATE);
        long lastFetch = prefs.getLong("last_fetch", 0);
        String cachedJson = prefs.getString("contributors_json", null);

        // 1-hour cache
        if (cachedJson != null && (System.currentTimeMillis() - lastFetch < 3600000)) {
            parseContributorsAndRefresh(cachedJson);
            return;
        }

        Request request = new Request.Builder()
                .url(API_URL)
                .header("User-Agent", "WaEnhancer-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    // Fallback to cache if failed
                    if (cachedJson != null) {
                        parseContributorsAndRefresh(cachedJson);
                    } else {
                        Toast.makeText(AboutActivity.this, "Failed to load contributors", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        if (cachedJson != null) {
                            parseContributorsAndRefresh(cachedJson);
                        } else {
                            Toast.makeText(AboutActivity.this, "Error fetching contributors", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    prefs.edit()
                            .putLong("last_fetch", System.currentTimeMillis())
                            .putString("contributors_json", json)
                            .apply();

                    runOnUiThread(() -> parseContributorsAndRefresh(json));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void parseContributorsAndRefresh(String json) {
        try {
            JSONArray jsonArray = new JSONArray(json);
            List<Contributor> fetchList = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                Contributor c = new Contributor();
                c.login = obj.optString("login");
                c.avatarUrl = obj.optString("avatar_url");
                c.htmlUrl = obj.optString("html_url");
                fetchList.add(c);
            }

            contributorList.clear();
            contributorList.addAll(fetchList);
            adapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class Contributor {
        String login;
        String avatarUrl;
        String htmlUrl;
    }

    private class ContributorAdapter extends RecyclerView.Adapter<ContributorAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contributor, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Contributor c = contributorList.get(position);
            holder.tvUsername.setText("@" + c.login);
            holder.ivAvatar.setImageResource(R.drawable.ic_github); // placeholder
            holder.ivAvatar.clearColorFilter(); // Remove the placeholder tint to show the real image colored
                                                // appropriately

            AvatarLoader.loadAvatar(c.avatarUrl, holder.ivAvatar);

            holder.itemView.setOnClickListener(v -> openUrl(c.htmlUrl));
        }

        @Override
        public int getItemCount() {
            return contributorList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvUsername;

            ViewHolder(View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvUsername = itemView.findViewById(R.id.tvUsername);
            }
        }
    }

    // Basic Image Loader to fetch and cache avatars without a large 3rd party
    // library
    private static class AvatarLoader {
        private static final LruCache<String, Bitmap> memoryCache;
        private static final ExecutorService executor = Executors.newFixedThreadPool(4);
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());

        static {
            final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
            final int cacheSize = maxMemory / 8;
            memoryCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };
        }

        static void loadAvatar(String urlString, ImageView imageView) {
            if (urlString == null || urlString.isEmpty())
                return;

            imageView.setTag(urlString);
            Bitmap cachedBitmap = memoryCache.get(urlString);
            if (cachedBitmap != null) {
                imageView.setImageBitmap(cachedBitmap);
                return;
            }

            executor.execute(() -> {
                try {
                    Request request = new Request.Builder()
                            .url(urlString)
                            .header("User-Agent", "WaEnhancer-App")
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                            if (bitmap != null) {
                                memoryCache.put(urlString, bitmap);
                                mainHandler.post(() -> {
                                    if (urlString.equals(imageView.getTag())) {
                                        imageView.setImageBitmap(bitmap);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
