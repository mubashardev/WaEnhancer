package com.waenhancer.views.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.SeekBar;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.waenhancer.R;

public class SimpleColorPickerDialog {

    private final Context mContext;
    private final OnColorSelectedListener listener;
    private int selectedColor;
    private boolean isUpdating = false;

    private BottomSheetDialog dialog;

    private View colorPreview;
    private EditText hexInput;
    private SeekBar hueSeekBar;
    private SeekBar brightnessSeekBar;

    private static final int[] PRESETS = {
        0xFFE53935, // Red
        0xFFD81B60, // Pink
        0xFF8E24AA, // Purple
        0xFF5E35B1, // Deep Purple
        0xFF1E88E5, // Blue
        0xFF039BE5, // Light Blue
        0xFF00897B, // Teal
        0xFF43A047, // Green
        0xFFFFB300, // Amber
        0xFFFB8C00, // Orange
        0xFFFFFFFF, // White
        0xFF000000  // Black
    };

    public SimpleColorPickerDialog(Context context, OnColorSelectedListener listener) {
        this.mContext = context;
        this.selectedColor = 0xFFFF0000;
        this.listener = listener;
    }

    public SimpleColorPickerDialog(Context context, int initialColor, OnColorSelectedListener listener) {
        this.mContext = context;
        this.selectedColor = initialColor;
        this.listener = listener;
    }

    public void show() {
        dialog = new BottomSheetDialog(mContext);

        View contentView = LayoutInflater.from(mContext).inflate(R.layout.dialog_color_picker, null);
        dialog.setContentView(contentView);

        // Force full expand immediately
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View sheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        // Bind views
        colorPreview = contentView.findViewById(R.id.color_preview);
        hexInput = contentView.findViewById(R.id.hex_input);
        hueSeekBar = contentView.findViewById(R.id.hue_seekbar);
        brightnessSeekBar = contentView.findViewById(R.id.brightness_seekbar);
        GridLayout presetsGrid = contentView.findViewById(R.id.presets_grid);

        // Build presets
        presetsGrid.removeAllViews();
        for (int color : PRESETS) {
            View circle = new View(mContext);
            int size = dpToPx(40);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5));
            circle.setLayoutParams(params);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            // White gets a stroke so it's visible
            if (color == 0xFFFFFFFF) {
                gd.setStroke(dpToPx(1), 0xFFCCCCCC);
            }
            circle.setBackground(gd);

            final int clickedColor = color;
            circle.setOnClickListener(v -> {
                selectedColor = clickedColor;
                updateUIFromColor();
            });
            presetsGrid.addView(circle);
        }

        // Rainbow hue gradient
        int[] rainbowColors = {
            0xFFFF0000, 0xFFFF00FF, 0xFF0000FF,
            0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000
        };
        GradientDrawable rainbowGd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, rainbowColors);
        rainbowGd.setCornerRadius(dpToPx(8));
        hueSeekBar.setBackground(rainbowGd);
        hueSeekBar.setProgressDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        // Brightness bar
        brightnessSeekBar.setProgressDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        // Hue listener
        hueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !isUpdating) updateColorFromSliders();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Brightness listener
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !isUpdating) updateColorFromSliders();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Hex text watcher
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isUpdating && s.length() == 7 && s.charAt(0) == '#') {
                    try {
                        isUpdating = true;
                        selectedColor = Color.parseColor(s.toString());
                        updateSlidersFromColor();
                        updatePreview();
                        isUpdating = false;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Initialize everything from current color
        updateUIFromColor();

        // Buttons
        contentView.findViewById(R.id.btn_cancel_color).setOnClickListener(v -> dialog.dismiss());
        contentView.findViewById(R.id.btn_select_color).setOnClickListener(v -> {
            if (listener != null) {
                listener.onColorSelected(selectedColor);
            }
            dialog.dismiss();
        });

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void updateUIFromColor() {
        isUpdating = true;
        updateSlidersFromColor();
        updatePreview();
        hexInput.setText(String.format("#%06X", (0xFFFFFF & selectedColor)));
        isUpdating = false;
    }

    private void updateSlidersFromColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(selectedColor, hsv);
        hueSeekBar.setProgress((int) hsv[0]);
        brightnessSeekBar.setProgress((int) (hsv[2] * 100));
        updateBrightnessBarGradient(hsv[0]);
    }

    private void updateColorFromSliders() {
        float hue = hueSeekBar.getProgress();
        float brightness = brightnessSeekBar.getProgress() / 100f;
        selectedColor = Color.HSVToColor(new float[]{hue, 1.0f, brightness});
        updatePreview();
        updateBrightnessBarGradient(hue);

        isUpdating = true;
        hexInput.setText(String.format("#%06X", (0xFFFFFF & selectedColor)));
        isUpdating = false;
    }

    private void updatePreview() {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(selectedColor);
        gd.setStroke(dpToPx(1), 0x33000000);
        colorPreview.setBackground(gd);
    }

    private void updateBrightnessBarGradient(float hue) {
        int colorEnd = Color.HSVToColor(new float[]{hue, 1.0f, 1.0f});
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.BLACK, colorEnd});
        gd.setCornerRadius(dpToPx(8));
        brightnessSeekBar.setBackground(gd);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * mContext.getResources().getDisplayMetrics().density);
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}
