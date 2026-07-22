package com.waenhancer.ui.helpers;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.R;
import com.google.android.material.loadingindicator.LoadingIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.waenhancer.xposed.utils.DesignUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Global helper for showing professional bottom sheets throughout the app.
 * Replaces AlertDialogs with consistent EU-aesthetic bottom sheets.
 */
public class BottomSheetHelper {

    public interface OnConfirmListener {

        void onConfirm();
    }

    public interface OnInputConfirmListener {

        void onConfirm(String input);
    }

    /**
     * Show a confirmation bottom sheet. If isDestructive is true, the action
     * button is red.
     */
    public static void showConfirmation(Context context, String title, String message,
            String confirmText, boolean isDestructive, OnConfirmListener onConfirm) {
        BottomSheetDialog dialog = createDialog(context);
        int layoutId = isDestructive ? R.layout.bottom_sheet_confirmation : R.layout.bottom_sheet_action;
        View view = LayoutInflater.from(context).inflate(layoutId, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);
        ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(message);

        MaterialButton confirmBtn = view.findViewById(R.id.bs_confirm_btn);
        confirmBtn.setText(confirmText);
        confirmBtn.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.onConfirm();
        });

        view.findViewById(R.id.bs_cancel_btn).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Show a confirmation with CharSequence message (for styled text).
     */
    public static void showConfirmation(Context context, String title, CharSequence message,
            String confirmText, boolean isDestructive, OnConfirmListener onConfirm) {
        BottomSheetDialog dialog = createDialog(context);
        int layoutId = isDestructive ? R.layout.bottom_sheet_confirmation : R.layout.bottom_sheet_action;
        View view = LayoutInflater.from(context).inflate(layoutId, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);
        ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(message);

        MaterialButton confirmBtn = view.findViewById(R.id.bs_confirm_btn);
        confirmBtn.setText(confirmText);
        confirmBtn.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirm.onConfirm();
        });

        view.findViewById(R.id.bs_cancel_btn).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Show an informational bottom sheet with a single OK button.
     */
    public static void showInfo(Context context, String title, String message) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_info, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);
        ((MaterialTextView) view.findViewById(R.id.bs_message)).setText(message);

        view.findViewById(R.id.bs_ok_btn).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Show an input bottom sheet with a text field.
     */
    public static void showInput(Context context, String title, String hint,
            String confirmText, OnInputConfirmListener onConfirm) {
        showInput(context, title, null, hint, confirmText, null, onConfirm);
    }

    public static void showInput(Context context, String title, String defaultValue, String hint,
            String confirmText, OnInputConfirmListener onConfirm) {
        showInput(context, title, defaultValue, hint, confirmText, null, onConfirm);
    }

    public static void showInput(Context context, String title, String defaultValue, String hint,
            String confirmText, EditTextPreference editPref, OnInputConfirmListener onConfirm) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_input, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);

        TextInputLayout inputLayout = view.findViewById(R.id.bs_input_layout);
        if (hint != null) {
            inputLayout.setHint(hint);
        } else {
            inputLayout.setHint("Enter value");
        }

        TextInputEditText input = view.findViewById(R.id.bs_input);

        // Pre-fill the previous saved value
        if (defaultValue != null) {
            input.setText(defaultValue);
            input.setSelection(defaultValue.length());
        }

        // Dynamically apply input type and bindings from the preference
        if (editPref != null) {
            EditTextPreference.OnBindEditTextListener bindListener = null;
            try {
                Method getListener = EditTextPreference.class.getDeclaredMethod("getOnBindEditTextListener");
                getListener.setAccessible(true);
                bindListener = (EditTextPreference.OnBindEditTextListener) getListener.invoke(editPref);
            } catch (Exception ignored) {}

            if (bindListener != null) {
                bindListener.onBindEditText(input);
            } else {
                String key = editPref.getKey();
                if (key != null && (key.equals("change_dpi") || key.equals("customforwardlimit"))) {
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    input.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
                } else {
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                }
            }
        } else {
            // Safe fallback: check if title or hint suggests numeric input
            if (title != null && (title.toLowerCase().contains("dpi") || title.toLowerCase().contains("limit") || title.toLowerCase().contains("number"))) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
            } else {
                input.setInputType(InputType.TYPE_CLASS_TEXT);
            }
        }

        MaterialButton confirmBtn = view.findViewById(R.id.bs_confirm_btn);
        confirmBtn.setText(confirmText);
        confirmBtn.setOnClickListener(v -> {
            String text = input.getText() != null ? input.getText().toString() : "";
            dialog.dismiss();
            onConfirm.onConfirm(text);
        });

        view.findViewById(R.id.bs_cancel_btn).setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Auto-focus the input and show keyboard
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    public interface OnSingleChoiceListener {

        void onChoice(int index, String value);
    }

    public interface OnMultiChoiceListener {

        void onChoices(Set<String> values);
    }

    public static void showSingleChoice(Context context, String title, CharSequence[] entries,
            CharSequence[] entryValues, String currentValue, OnSingleChoiceListener listener) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_options, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);
        LinearLayout container = view.findViewById(R.id.bs_options_container);

        // Hide buttons for single choice, it acts immediately on click
        view.findViewById(R.id.bs_buttons_container).setVisibility(View.GONE);

        List<MaterialRadioButton> radioButtons = new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < entries.length; i++) {
            MaterialRadioButton rb = (MaterialRadioButton) inflater.inflate(R.layout.item_bs_single_choice, container,
                    false);
            rb.setText(entries[i]);
            String val = entryValues[i].toString();
            if (val.equals(currentValue)) {
                rb.setChecked(true);
            }
            final int index = i;
            rb.setOnClickListener(v -> {
                for (MaterialRadioButton btn : radioButtons) {
                    btn.setChecked(false);
                }
                rb.setChecked(true);
                dialog.dismiss();
                listener.onChoice(index, val);
            });
            radioButtons.add(rb);
            container.addView(rb);
        }

        dialog.show();
    }

    public static void showMultiChoice(Context context, String title, CharSequence[] entries,
            CharSequence[] entryValues, Set<String> currentValues, OnMultiChoiceListener listener) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_options, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);
        LinearLayout container = view.findViewById(R.id.bs_options_container);

        LayoutInflater inflater = LayoutInflater.from(context);
        List<MaterialCheckBox> checkBoxes = new ArrayList<>();

        for (int i = 0; i < entries.length; i++) {
            MaterialCheckBox cb = (MaterialCheckBox) inflater.inflate(R.layout.item_bs_multi_choice, container, false);
            cb.setText(entries[i]);
            String val = entryValues[i].toString();
            if (currentValues != null && currentValues.contains(val)) {
                cb.setChecked(true);
            }
            checkBoxes.add(cb);
            container.addView(cb);
        }

        MaterialButton confirmBtn = view.findViewById(R.id.bs_confirm_btn);
        confirmBtn.setText(android.R.string.ok);
        confirmBtn.setOnClickListener(v -> {
            Set<String> selected = new HashSet<>();
            for (int i = 0; i < checkBoxes.size(); i++) {
                if (checkBoxes.get(i).isChecked()) {
                    selected.add(entryValues[i].toString());
                }
            }
            dialog.dismiss();
            listener.onChoices(selected);
        });

        view.findViewById(R.id.bs_cancel_btn).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Create a styled BottomSheetDialog with transparent background.
     */
    public static BottomSheetDialog createStyledDialog(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    private static BottomSheetDialog createDialog(Context context) {
        return createStyledDialog(context);
    }

    private static final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

    public static void showUserProfile(
            Context context,
            String username,
            String avatarUrl,
            String htmlUrl,
            int contributions) {

        BottomSheetDialog bottomSheet = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_user_profile, null);
        bottomSheet.setContentView(view);

        ShapeableImageView ivAvatar = view.findViewById(R.id.bsAvatar);
        MaterialTextView tvName = view.findViewById(R.id.bsName);
        MaterialTextView tvUsername = view.findViewById(R.id.bsUsername);

        LoadingIndicator progressIndicator = view.findViewById(R.id.expressive_loading_progress);
        View contentLayout = view.findViewById(R.id.bsContentLayout);

        com.bumptech.glide.Glide.with(context)
                .load(new com.bumptech.glide.load.model.GlideUrl(avatarUrl,
                        new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                .addHeader("User-Agent", "WaEnhancerX-App")
                                .build()))
                .placeholder(R.drawable.ic_github)
                .into(ivAvatar);

        tvName.setText(username);
        tvUsername.setText("@" + username);

        if (progressIndicator != null) {
            progressIndicator.setVisibility(View.VISIBLE);
        }
        contentLayout.setVisibility(View.GONE);

        bottomSheet.show();

        SharedPreferences prefs = context.getSharedPreferences("github_user_cache",
                Context.MODE_PRIVATE);
        long lastFetch = prefs.getLong(username + "_time", 0);
        String cachedJson = prefs.getString(username + "_json", null);

        if (cachedJson != null && (System.currentTimeMillis() - lastFetch < 3600000)) {
            parseAndPopulateProfile(context, view, bottomSheet, cachedJson, htmlUrl, avatarUrl, contributions);
            return;
        }

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.github.com/users/" + username)
                .header("User-Agent", "WaEnhancerX-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call,
                    @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (cachedJson != null) {
                        parseAndPopulateProfile(context, view, bottomSheet, cachedJson, htmlUrl, avatarUrl,
                                contributions);
                    } else {
                        Toast
                                .makeText(context, "Failed to load user profile", Toast.LENGTH_SHORT)
                                .show();
                        bottomSheet.dismiss();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call,
                    @NonNull okhttp3.Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (cachedJson != null) {
                            parseAndPopulateProfile(context, view, bottomSheet, cachedJson, htmlUrl, avatarUrl,
                                    contributions);
                        } else {
                            Toast
                                    .makeText(context, "Error fetching user", Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    prefs.edit()
                            .putLong(username + "_time", System.currentTimeMillis())
                            .putString(username + "_json", json)
                            .apply();

                    new Handler(Looper.getMainLooper())
                            .post(() -> parseAndPopulateProfile(context, view, bottomSheet, json, htmlUrl, avatarUrl,
                            contributions));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void parseAndPopulateProfile(Context context, View view, BottomSheetDialog bottomSheet, String json,
            String fallbackHtmlUrl, String avatarUrl, int contributions) {
        try {
            JSONObject obj = new JSONObject(json);
            String name = obj.optString("name", "");
            String login = obj.optString("login", "");
            String location = obj.optString("location", "");
            String bio = obj.optString("bio", "");
            boolean hireable = obj.optBoolean("hireable", false);
            int followers = obj.optInt("followers", 0);
            String twitter = obj.optString("twitter_username", "");
            String blog = obj.optString("blog", "");
            String htmlUrl = obj.optString("html_url", fallbackHtmlUrl);

            if (name.isEmpty() || name.equals("null")) {
                name = login;
            }

            MaterialTextView tvName = view.findViewById(R.id.bsName);
            MaterialTextView tvLocation = view.findViewById(R.id.bsLocation);
            MaterialTextView tvFollowers = view.findViewById(R.id.bsFollowers);
            MaterialTextView tvBio = view.findViewById(R.id.bsBio);
            MaterialTextView tvContributions = view
                    .findViewById(R.id.bsContributions);

            ImageView ivLocationIcon = view.findViewById(R.id.bsLocationIcon);
            View locationContainer = view.findViewById(R.id.bsLocationContainer);
            View chipsScroll = view.findViewById(R.id.bsChipsScroll);

            Chip chipHireable = view.findViewById(R.id.chipHireable);
            Chip chipTwitter = view.findViewById(R.id.chipTwitter);

            MaterialButton btnGithub = view.findViewById(R.id.bsGithubBtn);
            MaterialButton btnWebsite = view.findViewById(R.id.bsWebsiteBtn);
            MaterialButton btnContributions = view
                    .findViewById(R.id.bsContributionsBtn);

            tvName.setText(name);

            boolean hasLocation = location != null && !location.isEmpty() && !location.equals("null");
            if (hasLocation || followers > 0 || contributions > 0) {
                locationContainer.setVisibility(View.VISIBLE);
                if (hasLocation) {
                    ivLocationIcon.setVisibility(View.VISIBLE);
                    tvLocation.setVisibility(View.VISIBLE);
                    tvLocation.setText(location);
                } else {
                    ivLocationIcon.setVisibility(View.GONE);
                    tvLocation.setVisibility(View.GONE);
                }
                if (followers > 0) {
                    tvFollowers.setVisibility(View.VISIBLE);
                    tvFollowers.setText(hasLocation ? "• " + followers + " followers" : followers + " followers");
                } else {
                    tvFollowers.setVisibility(View.GONE);
                }
                if (contributions > 0) {
                    tvContributions.setVisibility(View.VISIBLE);
                    boolean hasPrev = hasLocation || followers > 0;
                    tvContributions.setText(hasPrev ? "• " + contributions + " commits" : contributions + " commits");

                    btnContributions.setVisibility(View.VISIBLE);
                    btnContributions.setVisibility(View.VISIBLE);
                    final String finalName = name;
                    btnContributions.setOnClickListener(v -> {
                        bottomSheet.dismiss();
                        showContributions(context, login, finalName, avatarUrl, htmlUrl, contributions);
                    });
                } else {
                    tvContributions.setVisibility(View.GONE);
                    btnContributions.setVisibility(View.GONE);
                }
            }

            if (bio != null && !bio.isEmpty() && !bio.equals("null")) {
                tvBio.setVisibility(View.VISIBLE);
                tvBio.setText(bio);
            }

            boolean hasChips = false;
            if (hireable) {
                hasChips = true;
                chipHireable.setVisibility(View.VISIBLE);
                chipHireable.setText("Open to hire");
            }

            if (twitter != null && !twitter.isEmpty() && !twitter.equals("null")) {
                hasChips = true;
                chipTwitter.setVisibility(View.VISIBLE);
                chipTwitter.setText("@" + twitter);
                chipTwitter.setOnClickListener(v -> {
                    try {
                        context.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://x.com/" + twitter)));
                    } catch (Exception ignored) {
                    }
                });
            }

            if (blog != null && !blog.isEmpty() && !blog.equals("null")) {
                btnWebsite.setVisibility(View.VISIBLE);
                btnWebsite.setOnClickListener(v -> {
                    String url = blog;
                    if (!url.startsWith("http")) {
                        url = "https://" + url;
                    }
                    try {
                        context.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                });
            }

            if (hasChips) {
                chipsScroll.setVisibility(View.VISIBLE);
            }

            btnGithub.setOnClickListener(v -> {
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(htmlUrl)));
                    bottomSheet.dismiss();
                } catch (Exception ignored) {
                }
            });

            // com.facebook.shimmer.ShimmerFrameLayout shimmerLayout = view.findViewById(R.id.bsShimmerLayout);
            LoadingIndicator progressIndicator = view.findViewById(R.id.expressive_loading_progress);
            View contentLayout = view.findViewById(R.id.bsContentLayout);

            // shimmerLayout.stopShimmer();
            // shimmerLayout.setVisibility(View.GONE);
            if (progressIndicator != null) {
                progressIndicator.setVisibility(View.GONE);
            }
            contentLayout.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to parse profile", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        }
    }

    private static void showContributions(Context context, String login, String displayName, String avatarUrl,
            String htmlUrl, int contributions) {
        BottomSheetDialog bottomSheet = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_contributions, null);
        bottomSheet.setContentView(view);

        ImageButton btnBack = view.findViewById(R.id.bsContribBackBtn);
        btnBack.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showUserProfile(context, login, avatarUrl, htmlUrl, contributions);
        });

        MaterialTextView tvTitle = view.findViewById(R.id.bsContribTitle);
        tvTitle.setText(displayName + "'s Contributions");

        bottomSheet.show();

        LoadingIndicator progressIndicator = view.findViewById(R.id.expressive_loading_progress);
        View contentLayout = view.findViewById(R.id.bsContribContent);
        if (progressIndicator != null) {
            progressIndicator.setVisibility(View.VISIBLE);
        }
        contentLayout.setVisibility(View.GONE);

        SharedPreferences prefs = context.getSharedPreferences("github_user_cache",
                Context.MODE_PRIVATE);
        long lastFetch = prefs.getLong("repo_stats_time", 0);
        String cachedJson = prefs.getString("repo_stats_json", null);

        if (cachedJson != null && (System.currentTimeMillis() - lastFetch < 3600000)) {
            parseAndPopulateContributions(context, view, bottomSheet, cachedJson, login);
            return;
        }

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.github.com/repos/mubashardev/WaEnhancer/stats/contributors")
                .header("User-Agent", "WaEnhancerX-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call,
                    @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (cachedJson != null) {
                        parseAndPopulateContributions(context, view, bottomSheet, cachedJson, login);
                    } else {
                        Toast
                                .makeText(context, "Failed to load contributions", Toast.LENGTH_SHORT)
                                .show();
                        bottomSheet.dismiss();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call,
                    @NonNull okhttp3.Response response) throws IOException {

                // GitHub stats API can return 202 Accepted if calculating
                if (!response.isSuccessful() || response.body() == null || response.code() == 202) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (cachedJson != null) {
                            parseAndPopulateContributions(context, view, bottomSheet, cachedJson, login);
                        } else {
                            Toast.makeText(context, "GitHub is calculating stats, try again later.",
                                    Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    prefs.edit()
                            .putLong("repo_stats_time", System.currentTimeMillis())
                            .putString("repo_stats_json", json)
                            .apply();

                    new Handler(Looper.getMainLooper())
                            .post(() -> parseAndPopulateContributions(context, view, bottomSheet, json, login));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void parseAndPopulateContributions(Context context, View view, BottomSheetDialog bottomSheet,
            String json, String login) {
        try {
            JSONArray jsonArray = new JSONArray(json);
            int totalCommits = 0;
            long linesAdded = 0;
            long linesDeleted = 0;

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                JSONObject author = obj.optJSONObject("author");
                if (author != null) {
                    String authorLogin = author.optString("login");
                    if (authorLogin.equalsIgnoreCase(login)) {
                        totalCommits = obj.optInt("total", 0);
                        JSONArray weeks = obj.optJSONArray("weeks");
                        if (weeks != null) {
                            for (int j = 0; j < weeks.length(); j++) {
                                JSONObject week = weeks.getJSONObject(j);
                                linesAdded += week.optLong("a", 0);
                                linesDeleted += week.optLong("d", 0);
                            }
                        }
                        break;
                    }
                }
            }

            MaterialTextView tvTotal = view.findViewById(R.id.bsContribTotal);
            MaterialTextView tvAdded = view.findViewById(R.id.bsContribAdded);
            MaterialTextView tvDeleted = view.findViewById(R.id.bsContribDeleted);
            // com.facebook.shimmer.ShimmerFrameLayout shimmerLayout = view.findViewById(R.id.bsContribShimmer);
            LoadingIndicator progressIndicator = view.findViewById(R.id.expressive_loading_progress);
            View contentLayout = view.findViewById(R.id.bsContribContent);

            NumberFormat format = NumberFormat.getInstance();
            tvTotal.setText(format.format(totalCommits));
            tvAdded.setText("+ " + format.format(linesAdded));
            tvDeleted.setText("~ " + format.format(linesDeleted));

            // shimmerLayout.stopShimmer();
            // shimmerLayout.setVisibility(View.GONE);
            if (progressIndicator != null) {
                progressIndicator.setVisibility(View.GONE);
            }
            contentLayout.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to parse contributions", Toast.LENGTH_SHORT)
                    .show();
            bottomSheet.dismiss();
        }
    }

    public static void showPillDesignChoice(Context context, String title, CharSequence[] entries,
            CharSequence[] entryValues, String currentValue, OnSingleChoiceListener listener) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_options, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);
        LinearLayout container = view.findViewById(R.id.bs_options_container);

        view.findViewById(R.id.bs_buttons_container).setVisibility(View.GONE);

        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout previewContainer = new LinearLayout(context);
        previewContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int margin16 = (int) (16 * density);
        containerLp.setMargins(margin16, 0, margin16, margin16);
        previewContainer.setLayoutParams(containerLp);

        LinearLayout classicCard = createPreviewCard(context, "Classic", false, density, currentValue.equals("regular"));
        LinearLayout refinedCard = createPreviewCard(context, "Refined (Pro)", true, density, currentValue.equals("pro"));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        cardLp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
        classicCard.setLayoutParams(cardLp);
        refinedCard.setLayoutParams(cardLp);

        previewContainer.addView(classicCard);
        previewContainer.addView(refinedCard);

        container.addView(previewContainer);

        List<MaterialRadioButton> radioButtons = new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < entries.length; i++) {
            MaterialRadioButton rb = (MaterialRadioButton) inflater.inflate(R.layout.item_bs_single_choice, container,
                    false);
            rb.setText(entries[i]);
            String val = entryValues[i].toString();
            if (val.equals(currentValue)) {
                rb.setChecked(true);
            }
            final int index = i;
            rb.setOnClickListener(v -> {
                for (MaterialRadioButton btn : radioButtons) {
                    btn.setChecked(false);
                }
                rb.setChecked(true);
                dialog.dismiss();
                listener.onChoice(index, val);
            });
            radioButtons.add(rb);
            container.addView(rb);
        }

        classicCard.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onChoice(0, "regular");
        });
        refinedCard.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onChoice(1, "pro");
        });

        dialog.show();
    }

    private static LinearLayout createPreviewCard(Context context, String label, boolean isPro, float density, boolean isSelected) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(12 * density);

        boolean isNight = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        int strokeColor;
        if (isSelected) {
            int resolved = DesignUtils.resolveColorAttr(context, android.R.attr.colorPrimary);
            strokeColor = resolved != 0 ? resolved : (isNight ? 0xFF25D366 : 0xFF008069);
        } else {
            strokeColor = isNight ? 0x22FFFFFF : 0x15000000;
        }
        int strokeWidth = isSelected ? (int) (2 * density) : (int) (1 * density);
        int bgColor = isNight ? 0xFF2D2D30 : 0xFFFFFFFF;

        gd.setColor(bgColor);
        gd.setStroke(strokeWidth, strokeColor);
        card.setBackground(gd);

        int pad = (int) (8 * density);
        card.setPadding(pad, pad, pad, pad);

        MaterialTextView title = new MaterialTextView(context);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(isNight ? 0xFFFFFFFF : 0xFF000000);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleLp.setMargins(0, (int) (4 * density), 0, (int) (8 * density));
        title.setLayoutParams(titleLp);
        card.addView(title);

        ImageView gifView = new ImageView(context);
        gifView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        gifView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams gifLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (110 * density)
        );
        gifView.setLayoutParams(gifLp);
        card.addView(gifView);

        String url = isPro
                ? "https://cdn.jsdelivr.net/gh/mubashardev/WaEnhancer@master/demo/pill_pro.gif"
                : "https://cdn.jsdelivr.net/gh/mubashardev/WaEnhancer@master/demo/pill_regular.gif";

        com.bumptech.glide.Glide.with(context)
                .load(url)
                .placeholder(R.drawable.ic_image)
                .error(android.R.drawable.stat_notify_error)
                .into(gifView);

        return card;
    }
}