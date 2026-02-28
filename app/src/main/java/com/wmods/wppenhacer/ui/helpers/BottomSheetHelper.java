package com.wmods.wppenhacer.ui.helpers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.wmods.wppenhacer.R;

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
     * Show a confirmation bottom sheet with a destructive (red) action button.
     */
    public static void showConfirmation(Context context, String title, String message,
            String confirmText, OnConfirmListener onConfirm) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_confirmation, null);
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
            String confirmText, OnConfirmListener onConfirm) {
        BottomSheetDialog dialog = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_confirmation, null);
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
}
