package com.wmods.wppenhacer.notices;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.helpers.BottomSheetHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class NoticeCenter {

    private static final String NOTICES_URL = BuildConfig.NOTICES_URL;

    private static final String PREFS_NAME = "wae_notices";
    private static final String KEY_CACHE_JSON = "cache_json";
    private static final String KEY_CACHE_ETAG = "cache_etag";
    private static final String KEY_CACHE_LAST_MODIFIED = "cache_last_modified";
    private static final String KEY_CACHE_FETCHED_AT = "cache_fetched_at";

    private static final long MIN_FETCH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);

    private static final AtomicBoolean sCheckInFlight = new AtomicBoolean(false);
    private static final AtomicBoolean sDialogShowing = new AtomicBoolean(false);
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private static OkHttpClient sHttpClient;

    private NoticeCenter() {
    }

    public static void checkAndShow(@NonNull Activity activity) {
        if (!sCheckInFlight.compareAndSet(false, true)) return;
        fetchNotices(activity.getApplicationContext(), new FetchCallback() {
            @Override
            public void onResult(@Nullable NoticePayload payload) {
                sCheckInFlight.set(false);
                if (payload == null || payload.notices.isEmpty()) return;
                sMainHandler.post(() -> showFirstEligible(activity, payload.notices));
            }
        });
    }

    private static void showFirstEligible(@NonNull Activity activity, @NonNull ArrayList<Notice> notices) {
        if (activity.isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;
        if (!sDialogShowing.compareAndSet(false, true)) return;

        try {
            String channel = getChannel();
            int versionCode = BuildConfig.VERSION_CODE;
            String commitId = getCommitIdFromVersionName(BuildConfig.VERSION_NAME);

            // Higher severity first.
            notices.sort(Comparator.comparingInt(NoticeCenter::severityRank).reversed());

            Notice selected = null;
            for (Notice n : notices) {
                if (!n.enabled) continue;
                if (!matchesTargets(n, channel, versionCode, commitId)) continue;
                selected = n;
                break;
            }

            if (selected == null) {
                sDialogShowing.set(false);
                return;
            }

            showNoticeDialog(activity, selected);
        } catch (Throwable t) {
            sDialogShowing.set(false);
        }
    }

    private static void showNoticeDialog(@NonNull Activity activity, @NonNull Notice notice) {
        BottomSheetDialog dialog = BottomSheetHelper.createStyledDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_notice, null);
        dialog.setContentView(view);
        dialog.setCancelable(notice.dismissible);
        dialog.setCanceledOnTouchOutside(notice.dismissible);
        dialog.setOnDismissListener(d -> sDialogShowing.set(false));

        MaterialTextView tvTitle = view.findViewById(R.id.bs_title);
        MaterialTextView tvMessage = view.findViewById(R.id.bs_message);
        MaterialButton btnPrimary = view.findViewById(R.id.bs_primary_btn);
        MaterialButton btnSecondary = view.findViewById(R.id.bs_secondary_btn);
        MaterialButton btnDismiss = view.findViewById(R.id.bs_dismiss_btn);

        tvTitle.setText(notice.title);

        if ("markdown".equalsIgnoreCase(notice.format)) {
            try {
                Markwon.create(activity).setMarkdown(tvMessage, notice.message);
            } catch (Throwable t) {
                tvMessage.setText(notice.message);
            }
        } else {
            tvMessage.setText(notice.message);
        }

        NoticeAction primary = notice.getPrimaryAction();
        NoticeAction secondary = notice.getSecondaryAction();
        NoticeAction dismiss = notice.getDismissAction();

        if (primary != null && !TextUtils.isEmpty(primary.label)) {
            btnPrimary.setText(primary.label);
            btnPrimary.setOnClickListener(v -> {
                dialog.dismiss();
                openAction(activity, primary);
            });
            btnPrimary.setVisibility(View.VISIBLE);
        } else {
            btnPrimary.setVisibility(View.GONE);
        }

        if (secondary != null && !TextUtils.isEmpty(secondary.label)) {
            btnSecondary.setText(secondary.label);
            btnSecondary.setOnClickListener(v -> {
                dialog.dismiss();
                openAction(activity, secondary);
            });
            btnSecondary.setVisibility(View.VISIBLE);
        } else {
            btnSecondary.setVisibility(View.GONE);
        }

        // Always keep a dismiss action, unless overridden by explicit JSON action with style "dismiss".
        String dismissLabel = (dismiss != null && !TextUtils.isEmpty(dismiss.label)) ? dismiss.label : "Dismiss";
        btnDismiss.setText(dismissLabel);
        btnDismiss.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static void openAction(@NonNull Activity activity, @NonNull NoticeAction action) {
        if (!"url".equalsIgnoreCase(action.type)) return;
        if (TextUtils.isEmpty(action.url)) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(action.url)));
        } catch (Throwable ignored) {
        }
    }

    private static boolean matchesTargets(@NonNull Notice n, @NonNull String channel, int versionCode,
                                         @Nullable String commitId) {
        if (n.targets == null) return true;

        if (!n.targets.channels.isEmpty() && !n.targets.channels.contains(channel)) return false;

        if (n.targets.versionMin != null && versionCode < n.targets.versionMin) return false;
        if (n.targets.versionMax != null && versionCode > n.targets.versionMax) return false;

        if (!n.targets.commitIds.isEmpty()) {
            if (commitId == null) return false;
            String normalized = commitId.toUpperCase(Locale.US);
            boolean ok = false;
            for (String c : n.targets.commitIds) {
                if (normalized.equalsIgnoreCase(c)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }

        return true;
    }

    private static int severityRank(@NonNull Notice n) {
        String s = n.severity == null ? "" : n.severity.toLowerCase(Locale.US);
        return switch (s) {
            case "critical", "error" -> 3;
            case "warning" -> 2;
            default -> 1;
        };
    }

    @NonNull
    private static String getChannel() {
        String name = BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME;
        return name.toUpperCase(Locale.US).contains("DEV") ? "dev" : "release";
    }

    @Nullable
    private static String getCommitIdFromVersionName(@Nullable String versionName) {
        if (TextUtils.isEmpty(versionName)) return null;
        int start = versionName.lastIndexOf('(');
        int end = versionName.lastIndexOf(')');
        if (start >= 0 && end > start + 1) {
            String hash = versionName.substring(start + 1, end).trim();
            if (!hash.isEmpty() && !"unknown".equalsIgnoreCase(hash)) return hash;
        }
        return null;
    }

    private static synchronized OkHttpClient getHttpClient() {
        if (sHttpClient == null) {
            sHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        return sHttpClient;
    }

    private static void fetchNotices(@NonNull Context context, @NonNull FetchCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastFetch = prefs.getLong(KEY_CACHE_FETCHED_AT, 0);
        String cached = prefs.getString(KEY_CACHE_JSON, null);

        boolean canUseCacheOnly = cached != null && (System.currentTimeMillis() - lastFetch) < MIN_FETCH_INTERVAL_MS;
        if (canUseCacheOnly) {
            callback.onResult(parsePayload(cached));
            return;
        }

        Request.Builder req = new Request.Builder()
                .url(NOTICES_URL)
                .header("User-Agent", "WaEnhancer-App");

        String etag = prefs.getString(KEY_CACHE_ETAG, null);
        String lastModified = prefs.getString(KEY_CACHE_LAST_MODIFIED, null);
        if (!TextUtils.isEmpty(etag)) req.header("If-None-Match", etag);
        if (!TextUtils.isEmpty(lastModified)) req.header("If-Modified-Since", lastModified);

        getHttpClient().newCall(req.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                callback.onResult(cached == null ? null : parsePayload(cached));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    if (response.code() == 304 && cached != null) {
                        prefs.edit().putLong(KEY_CACHE_FETCHED_AT, System.currentTimeMillis()).apply();
                        callback.onResult(parsePayload(cached));
                        return;
                    }

                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onResult(cached == null ? null : parsePayload(cached));
                        return;
                    }

                    String body = response.body().string();
                    String newEtag = response.header("ETag");
                    String newLastModified = response.header("Last-Modified");

                    prefs.edit()
                            .putString(KEY_CACHE_JSON, body)
                            .putLong(KEY_CACHE_FETCHED_AT, System.currentTimeMillis())
                            .putString(KEY_CACHE_ETAG, newEtag)
                            .putString(KEY_CACHE_LAST_MODIFIED, newLastModified)
                            .apply();

                    callback.onResult(parsePayload(body));
                } catch (Throwable t) {
                    callback.onResult(cached == null ? null : parsePayload(cached));
                }
            }
        });
    }

    @Nullable
    private static NoticePayload parsePayload(@NonNull String json) {
        try {
            JSONObject root = new JSONObject(json);
            int schema = root.optInt("schemaVersion", 0);
            if (schema != 1) return null;

            JSONArray array = root.optJSONArray("notices");
            if (array == null) return null;

            ArrayList<Notice> notices = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                Notice notice = Notice.fromJson(obj);
                if (notice == null) continue;
                notices.add(notice);
            }
            return new NoticePayload(notices);
        } catch (Throwable t) {
            return null;
        }
    }

    private interface FetchCallback {
        void onResult(@Nullable NoticePayload payload);
    }

    private static final class NoticePayload {
        final ArrayList<Notice> notices;

        NoticePayload(ArrayList<Notice> notices) {
            this.notices = Objects.requireNonNullElseGet(notices, ArrayList::new);
        }
    }

    private static final class Notice {
        final String id;
        final int revision;
        final boolean enabled;
        final String severity;
        final String title;
        final String message;
        final String format;
        final boolean dismissible;
        final NoticeTargets targets;
        final ArrayList<NoticeAction> actions;

        private Notice(String id, int revision, boolean enabled, String severity, String title, String message,
                       String format, boolean dismissible, NoticeTargets targets, ArrayList<NoticeAction> actions) {
            this.id = id;
            this.revision = revision;
            this.enabled = enabled;
            this.severity = severity;
            this.title = title;
            this.message = message;
            this.format = format;
            this.dismissible = dismissible;
            this.targets = targets;
            this.actions = actions;
        }

        @Nullable
        static Notice fromJson(@NonNull JSONObject obj) {
            String id = obj.optString("id", "");
            if (TextUtils.isEmpty(id)) return null;
            int revision = Math.max(0, obj.optInt("revision", 0));
            boolean enabled = obj.optBoolean("enabled", true);
            String severity = obj.optString("severity", "info");
            String title = obj.optString("title", "Notice");
            String message = obj.optString("message", "");
            String format = obj.optString("format", "plain");
            boolean dismissible = obj.optBoolean("dismissible", true);

            NoticeTargets targets = NoticeTargets.fromJson(obj.optJSONObject("targets"));

            ArrayList<NoticeAction> actions = new ArrayList<>();
            JSONArray actionsArray = obj.optJSONArray("actions");
            if (actionsArray != null) {
                for (int i = 0; i < actionsArray.length(); i++) {
                    JSONObject a = actionsArray.optJSONObject(i);
                    if (a == null) continue;
                    NoticeAction action = NoticeAction.fromJson(a);
                    if (action != null) actions.add(action);
                }
            }

            return new Notice(id, revision, enabled, severity, title, message, format, dismissible, targets, actions);
        }

        @Nullable
        NoticeAction getPrimaryAction() {
            for (NoticeAction a : actions) {
                if ("primary".equalsIgnoreCase(a.style)) return a;
            }
            for (NoticeAction a : actions) {
                if ("dismiss".equalsIgnoreCase(a.style)) continue;
                return a;
            }
            return null;
        }

        @Nullable
        NoticeAction getSecondaryAction() {
            for (NoticeAction a : actions) {
                if ("secondary".equalsIgnoreCase(a.style)) return a;
            }
            NoticeAction primary = getPrimaryAction();
            for (NoticeAction a : actions) {
                if (a == primary) continue;
                if ("dismiss".equalsIgnoreCase(a.style)) continue;
                return a;
            }
            return null;
        }

        @Nullable
        NoticeAction getDismissAction() {
            for (NoticeAction a : actions) {
                if ("dismiss".equalsIgnoreCase(a.style)) return a;
            }
            return null;
        }
    }

    private static final class NoticeTargets {
        final Set<String> channels;
        final Integer versionMin;
        final Integer versionMax;
        final ArrayList<String> commitIds;

        private NoticeTargets(Set<String> channels, Integer versionMin, Integer versionMax, ArrayList<String> commitIds) {
            this.channels = channels;
            this.versionMin = versionMin;
            this.versionMax = versionMax;
            this.commitIds = commitIds;
        }

        @Nullable
        static NoticeTargets fromJson(@Nullable JSONObject obj) {
            if (obj == null) return null;

            Set<String> channels = new HashSet<>();
            JSONArray ch = obj.optJSONArray("channels");
            if (ch != null) {
                for (int i = 0; i < ch.length(); i++) {
                    String s = ch.optString(i, "");
                    if (!TextUtils.isEmpty(s)) channels.add(s.toLowerCase(Locale.US));
                }
            }

            Integer min = null;
            Integer max = null;
            JSONObject versionCodes = obj.optJSONObject("versionCodes");
            if (versionCodes != null) {
                if (versionCodes.has("min")) min = versionCodes.optInt("min");
                if (versionCodes.has("max")) max = versionCodes.optInt("max");
            }

            ArrayList<String> commitIds = new ArrayList<>();
            JSONArray commits = obj.optJSONArray("commitIds");
            if (commits != null) {
                for (int i = 0; i < commits.length(); i++) {
                    String s = commits.optString(i, "");
                    if (!TextUtils.isEmpty(s)) commitIds.add(s);
                }
            }

            return new NoticeTargets(channels, min, max, commitIds);
        }
    }

    private static final class NoticeAction {
        final String label;
        final String type;
        final String url;
        final String style;

        private NoticeAction(String label, String type, String url, String style) {
            this.label = label;
            this.type = type;
            this.url = url;
            this.style = style;
        }

        @Nullable
        static NoticeAction fromJson(@NonNull JSONObject obj) {
            String label = obj.optString("label", "");
            String type = obj.optString("type", "url");
            String url = obj.optString("url", "");
            String style = obj.optString("style", "");
            if (TextUtils.isEmpty(label)) return null;
            boolean isDismiss = "dismiss".equalsIgnoreCase(style) || "dismiss".equalsIgnoreCase(type);
            if (!isDismiss && "url".equalsIgnoreCase(type) && TextUtils.isEmpty(url)) return null;
            return new NoticeAction(label, type, url, style);
        }
    }
}
