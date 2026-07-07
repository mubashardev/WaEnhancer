package com.waenhancer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.waenhancer.xposed.utils.XPrefManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KeyboxFetcher {
    private static final String TAG = "WAEX-KeyboxFetcher";
    private static final String PRIMARY_URL = "https://waex.mubashar.dev/keybox.json";
    private static final String FALLBACK_URL = "https://waex.pages.dev/keybox.json";
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final OkHttpClient client = new OkHttpClient();

    public static void syncKeyboxAsync(final Context context) {
        executor.execute(() -> syncKeybox(context));
    }

    private static void syncKeybox(Context context) {
        try {
            fetchFromUrl(context, PRIMARY_URL);
        } catch (Exception e) {
            Log.w(TAG, "Primary URL failed, trying fallback: " + e.getMessage());
            try {
                fetchFromUrl(context, FALLBACK_URL);
            } catch (Exception fallbackEx) {
                Log.e(TAG, "Fallback URL also failed: " + fallbackEx.getMessage(), fallbackEx);
            }
        }
    }

    private static void fetchFromUrl(Context context, String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            if (response.body() == null) {
                throw new IOException("Response body is null");
            }
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            String keyboxXml = json.getString("keybox");
            String lastUpdated = json.optString("last_updated", "");
            if (keyboxXml != null && !keyboxXml.trim().isEmpty()) {
                SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("bootloader_spoofer_default_xml", keyboxXml);
                if (!lastUpdated.isEmpty()) {
                    editor.putString("default_kb_last_updated", lastUpdated);
                }
                editor.apply();
            }
        }
    }
}
