package com.wmods.wppenhacer.ui.helpers;

import android.content.Context;
import android.content.DialogInterface;
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
import com.wmods.wppenhacer.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Show a confirmation bottom sheet. If isDestructive is true, the action button
     * is red.
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
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_input, null);
        dialog.setContentView(view);

        ((MaterialTextView) view.findViewById(R.id.bs_title)).setText(title);

        TextInputLayout inputLayout = view.findViewById(R.id.bs_input_layout);
        inputLayout.setHint(hint);

        TextInputEditText input = view.findViewById(R.id.bs_input);

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
    private static BottomSheetDialog createDialog(Context context) {
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

    public static void showUserProfile(
            Context context,
            String avatarUrl,
            String name,
            String username,
            String location,
            String bio,
            String twitter,
            String blog,
            boolean hireable,
            int followers,
            String htmlUrl) {

        BottomSheetDialog bottomSheet = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_user_profile, null);
        bottomSheet.setContentView(view);

        com.google.android.material.imageview.ShapeableImageView ivAvatar = view.findViewById(R.id.bsAvatar);
        com.google.android.material.textview.MaterialTextView tvName = view.findViewById(R.id.bsName);
        com.google.android.material.textview.MaterialTextView tvUsername = view.findViewById(R.id.bsUsername);
        com.google.android.material.textview.MaterialTextView tvLocation = view.findViewById(R.id.bsLocation);
        com.google.android.material.textview.MaterialTextView tvFollowers = view.findViewById(R.id.bsFollowers);
        com.google.android.material.textview.MaterialTextView tvBio = view.findViewById(R.id.bsBio);

        android.widget.ImageView ivLocationIcon = view.findViewById(R.id.bsLocationIcon);
        View locationContainer = view.findViewById(R.id.bsLocationContainer);
        View chipsScroll = view.findViewById(R.id.bsChipsScroll);

        com.google.android.material.chip.Chip chipHireable = view.findViewById(R.id.chipHireable);
        com.google.android.material.chip.Chip chipTwitter = view.findViewById(R.id.chipTwitter);

        com.google.android.material.button.MaterialButton btnGithub = view.findViewById(R.id.bsGithubBtn);
        com.google.android.material.button.MaterialButton btnWebsite = view.findViewById(R.id.bsWebsiteBtn);

        com.bumptech.glide.Glide.with(context)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_github)
                .into(ivAvatar);

        tvName.setText(name);
        tvUsername.setText("@" + username);

        boolean hasLocation = location != null && !location.isEmpty() && !location.equals("null");
        if (hasLocation || followers > 0) {
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
                tvFollowers.setText(followers + " followers");
            } else {
                tvFollowers.setVisibility(View.GONE);
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
                    context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://twitter.com/" + twitter)));
                } catch (Exception ignored) {
                }
            });
        }

        if (blog != null && !blog.isEmpty() && !blog.equals("null")) {
            btnWebsite.setVisibility(View.VISIBLE);
            btnWebsite.setOnClickListener(v -> {
                String url = blog;
                if (!url.startsWith("http"))
                    url = "https://" + url;
                try {
                    context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)));
                } catch (Exception ignored) {
                }
            });
        }

        if (hasChips) {
            chipsScroll.setVisibility(View.VISIBLE);
        }

        btnGithub.setOnClickListener(v -> {
            try {
                context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(htmlUrl)));
                bottomSheet.dismiss();
            } catch (Exception ignored) {
            }
        });

        bottomSheet.show();
    }
}
