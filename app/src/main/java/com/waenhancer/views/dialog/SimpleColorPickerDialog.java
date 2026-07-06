package com.waenhancer.views.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.SeekBar;

public class SimpleColorPickerDialog {

    private final Context mContext;
    private final OnColorSelectedListener listener;
    private int selectedColor;
    private boolean isUpdating = false;

    private android.app.Dialog dialog;

    private View colorPreview;
    private EditText hexInput;
    private SeekBar hueSeekBar;
    private SeekBar brightnessSeekBar;
    private GridLayout presetsGrid;

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
        this.selectedColor = initialColor != -1 ? initialColor : 0xFFFF0000;
        this.listener = listener;
    }

    public void show() {
        dialog = new android.app.Dialog(mContext);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        View contentView = createContentView(mContext);
        dialog.setContentView(contentView);

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

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int width = (int) (mContext.getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private View createContentView(Context context) {
        boolean isDark = isDarkTheme(context);
        int textColor = isDark ? 0xFFFFFFFF : 0xFF1E1E1E;
        int textSecondary = isDark ? 0xFFB0B0B0 : 0xFF666666;
        int cardBg = isDark ? 0xFF2C2C2C : 0xFFF5F5F5;

        // Root Layout
        android.widget.LinearLayout root = new android.widget.LinearLayout(context);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dpToPx(20), dpToPx(24), dpToPx(20), dpToPx(24));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isDark ? 0xFF1E1E1E : 0xFFFFFFFF);
        bg.setCornerRadius(dpToPx(28));
        root.setBackground(bg);

        // 1. Title
        android.widget.TextView title = new android.widget.TextView(context);
        title.setText("Choose Color");
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setTextColor(textColor);
        android.widget.LinearLayout.LayoutParams titleParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dpToPx(20));
        root.addView(title, titleParams);

        // 2. Presets Label
        android.widget.TextView presetsLabel = new android.widget.TextView(context);
        presetsLabel.setText("Presets");
        presetsLabel.setTextSize(14);
        presetsLabel.setTextColor(textSecondary);
        android.widget.LinearLayout.LayoutParams labelParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 0, 0, dpToPx(10));
        root.addView(presetsLabel, labelParams);

        // 3. Presets Scroll + Grid
        android.widget.HorizontalScrollView presetScroll = new android.widget.HorizontalScrollView(context);
        presetScroll.setHorizontalScrollBarEnabled(false);
        presetScroll.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);

        presetsGrid = new GridLayout(context);
        presetsGrid.setColumnCount(6);
        presetsGrid.setRowCount(2);

        presetScroll.addView(presetsGrid, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.setMargins(0, 0, 0, dpToPx(24));
        root.addView(presetScroll, scrollParams);

        // 4. Custom Color Label
        android.widget.TextView customLabel = new android.widget.TextView(context);
        customLabel.setText("Custom Color");
        customLabel.setTextSize(14);
        customLabel.setTextColor(textSecondary);
        root.addView(customLabel, labelParams);

        // 5. Preview + Hex Row
        android.widget.LinearLayout previewRow = new android.widget.LinearLayout(context);
        previewRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        previewRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        colorPreview = new View(context);
        android.widget.LinearLayout.LayoutParams previewParams = new android.widget.LinearLayout.LayoutParams(dpToPx(52), dpToPx(52));
        previewParams.setMargins(0, 0, dpToPx(16), 0);
        previewRow.addView(colorPreview, previewParams);

        hexInput = new EditText(context);
        hexInput.setHint("Hex Code (#RRGGBB)");
        hexInput.setSingleLine(true);
        hexInput.setTextColor(textColor);
        hexInput.setHintTextColor(textSecondary);
        hexInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
        hexInput.setInputType(android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        
        GradientDrawable editBg = new GradientDrawable();
        editBg.setColor(cardBg);
        editBg.setCornerRadius(dpToPx(8));
        hexInput.setBackground(editBg);
        hexInput.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        
        android.widget.LinearLayout.LayoutParams inputParams = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        previewRow.addView(hexInput, inputParams);

        android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dpToPx(20));
        root.addView(previewRow, rowParams);

        // 6. Hue Label
        android.widget.TextView hueLabel = new android.widget.TextView(context);
        hueLabel.setText("Hue");
        hueLabel.setTextSize(12);
        hueLabel.setTextColor(textSecondary);
        android.widget.LinearLayout.LayoutParams smallLabelParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        smallLabelParams.setMargins(0, 0, 0, dpToPx(4));
        root.addView(hueLabel, smallLabelParams);

        // 7. Hue SeekBar
        hueSeekBar = new SeekBar(context);
        hueSeekBar.setMax(360);
        android.widget.LinearLayout.LayoutParams seekParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        seekParams.setMargins(0, 0, 0, dpToPx(16));
        root.addView(hueSeekBar, seekParams);

        // 8. Brightness Label
        android.widget.TextView brightnessLabel = new android.widget.TextView(context);
        brightnessLabel.setText("Brightness");
        brightnessLabel.setTextSize(12);
        brightnessLabel.setTextColor(textSecondary);
        root.addView(brightnessLabel, smallLabelParams);

        // 9. Brightness SeekBar
        brightnessSeekBar = new SeekBar(context);
        brightnessSeekBar.setMax(100);
        brightnessSeekBar.setProgress(100);
        android.widget.LinearLayout.LayoutParams brightnessParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        brightnessParams.setMargins(0, 0, 0, dpToPx(24));
        root.addView(brightnessSeekBar, brightnessParams);

        // 10. Buttons Row
        android.widget.LinearLayout buttonsRow = new android.widget.LinearLayout(context);
        buttonsRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(android.view.Gravity.END);

        android.widget.Button btnCancel = new android.widget.Button(context);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(textColor);
        btnCancel.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.TRANSPARENT);
        btnCancel.setBackground(cancelBg);
        btnCancel.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        buttonsRow.addView(btnCancel);

        android.widget.Button btnSelect = new android.widget.Button(context);
        btnSelect.setText("Select");
        btnSelect.setTextColor(isDark ? 0xFF000000 : 0xFFFFFFFF);
        btnSelect.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        GradientDrawable selectBg = new GradientDrawable();
        selectBg.setColor(isDark ? 0xFF00E676 : 0xFF00A884); // Bright green for dark theme, teal green for light theme
        selectBg.setCornerRadius(dpToPx(100)); // Fully rounded
        btnSelect.setBackground(selectBg);
        btnSelect.setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8));
        btnSelect.setOnClickListener(v -> {
            if (listener != null) {
                listener.onColorSelected(selectedColor);
            }
            dialog.dismiss();
        });

        android.widget.LinearLayout.LayoutParams selectParams = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        selectParams.setMargins(dpToPx(12), 0, 0, 0);
        buttonsRow.addView(btnSelect, selectParams);

        root.addView(buttonsRow, new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        scrollView.addView(root);
        return scrollView;
    }

    private boolean isDarkTheme(Context context) {
        return (context.getResources().getConfiguration().uiMode & 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
