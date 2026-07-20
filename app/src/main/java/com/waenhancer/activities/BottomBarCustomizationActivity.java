package com.waenhancer.activities;

import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.views.dialog.SimpleColorPickerDialog;
import android.util.TypedValue;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.waenhancer.xposed.utils.ProHelper;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.content.Context;
import android.content.res.Configuration;
import androidx.annotation.Nullable;
import android.graphics.Canvas;
import android.os.Build;
import android.view.ViewParent;

public class BottomBarCustomizationActivity extends BaseActivity {

    private SharedPreferences prefs;
    private MaterialSwitch switchFloating;
    private View layoutCustomizationControls;
    private RadioGroup pillDesignGroup;
    private RadioButton radioDesignRegular;
    private RadioButton radioDesignPro;
    private RadioButton radioDesignIosGlass;
    private MaterialSwitch switchGlass;
    private LinearLayout btnSelectColor;
    private View colorPreviewCircle;
    private Slider sliderRadius;
    private Slider sliderMargin;
    private Slider sliderMarginHorizontal;
    private Slider sliderFab;
    private Slider sliderOpacity;
    private Slider sliderIconSize;
    private Slider sliderTextSize;
    private Slider sliderPaddingVertical;
    private Slider sliderIconLabelSpacing;
    private View layoutGlassOpacity;
    
    private TextView txtRadiusVal;
    private TextView txtMarginVal;
    private TextView txtMarginHorizontalVal;
    private TextView txtFabVal;
    private TextView txtOpacityVal;
    private TextView txtIconSizeVal;
    private TextView txtTextSizeVal;
    private TextView txtPaddingVerticalVal;
    private TextView txtIconLabelSpacingVal;

    // Preview views
    private View previewFab;
    private LinearLayout previewBottomBar;
    private View previewActiveIndicator;
    private ImageView previewChatsIcon;

    private int selectedColor = 0; // 0 represents default
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bottom_bar_customization);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.pill_customization_title);
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        density = getResources().getDisplayMetrics().density;

        // Initialize UI Elements
        switchFloating = findViewById(R.id.switch_floating);
        layoutCustomizationControls = findViewById(R.id.layout_customization_controls);
        pillDesignGroup = findViewById(R.id.pill_design_group);
        radioDesignRegular = findViewById(R.id.radio_design_regular);
        radioDesignPro = findViewById(R.id.radio_design_pro);
        radioDesignIosGlass = findViewById(R.id.radio_design_ios_glass);
        switchGlass = findViewById(R.id.switch_glass);
        btnSelectColor = findViewById(R.id.btn_select_color);
        colorPreviewCircle = findViewById(R.id.color_preview_circle);
        sliderRadius = findViewById(R.id.slider_radius);
        sliderMargin = findViewById(R.id.slider_margin);
        sliderMarginHorizontal = findViewById(R.id.slider_margin_horizontal);
        sliderFab = findViewById(R.id.slider_fab);
        sliderOpacity = findViewById(R.id.slider_opacity);
        sliderIconSize = findViewById(R.id.slider_icon_size);
        sliderTextSize = findViewById(R.id.slider_text_size);
        sliderPaddingVertical = findViewById(R.id.slider_padding_vertical);
        sliderIconLabelSpacing = findViewById(R.id.slider_icon_label_spacing);
        layoutGlassOpacity = findViewById(R.id.layout_glass_opacity);

        txtRadiusVal = findViewById(R.id.txt_radius_val);
        txtMarginVal = findViewById(R.id.txt_margin_val);
        txtMarginHorizontalVal = findViewById(R.id.txt_margin_horizontal_val);
        txtFabVal = findViewById(R.id.txt_fab_val);
        txtOpacityVal = findViewById(R.id.txt_opacity_val);
        txtIconSizeVal = findViewById(R.id.txt_icon_size_val);
        txtTextSizeVal = findViewById(R.id.txt_text_size_val);
        txtPaddingVerticalVal = findViewById(R.id.txt_padding_vertical_val);
        txtIconLabelSpacingVal = findViewById(R.id.txt_icon_label_spacing_val);

        previewFab = findViewById(R.id.preview_fab);
        previewBottomBar = findViewById(R.id.preview_bottom_bar);
        previewActiveIndicator = findViewById(R.id.preview_active_indicator);
        previewChatsIcon = findViewById(R.id.preview_chats_icon);

        // Load values from Preferences
        boolean floatingEnabled = prefs.getBoolean("floating_bottom_bar", false);
        switchFloating.setChecked(floatingEnabled);
        layoutCustomizationControls.setVisibility(floatingEnabled ? View.VISIBLE : View.GONE);

        boolean isProActive = ProHelper.isProEnabled();
        if (!isProActive) {
            radioDesignPro.setEnabled(false);
            radioDesignIosGlass.setEnabled(false);
            radioDesignRegular.setChecked(true);
        } else {
            String pillDesign = prefs.getString("floating_bottom_bar_pill_design", "regular");
            if ("pro".equals(pillDesign)) {
                radioDesignPro.setChecked(true);
            } else if ("ios_glass".equals(pillDesign)) {
                radioDesignIosGlass.setChecked(true);
            } else {
                radioDesignRegular.setChecked(true);
            }
        }

        boolean glassEnabled = prefs.getBoolean("floating_bottom_bar_glass", true);
        switchGlass.setChecked(glassEnabled);
        layoutGlassOpacity.setVisibility(glassEnabled ? View.VISIBLE : View.GONE);

        selectedColor = prefs.getInt("floating_bottom_bar_fill_color", 0);
        updateColorPreviewCircle();

        int radius = prefs.getInt("floating_bottom_bar_radius", 28);
        sliderRadius.setValue(radius);
        txtRadiusVal.setText(radius + "dp");

        int marginBottom = prefs.getInt("floating_bottom_bar_margin_bottom", 22);
        sliderMargin.setValue(marginBottom);
        txtMarginVal.setText(marginBottom + "dp");

        int marginHorizontal = prefs.getInt("floating_bottom_bar_margin_horizontal", 16);
        sliderMarginHorizontal.setValue(marginHorizontal);
        txtMarginHorizontalVal.setText(marginHorizontal + "dp");

        int fabOffset = prefs.getInt("floating_bottom_bar_fab_offset", 80);
        sliderFab.setValue(fabOffset);
        txtFabVal.setText(fabOffset + "dp");

        int opacity = (int) prefs.getFloat("floating_bottom_bar_glass_opacity", 35f);
        sliderOpacity.setValue(opacity);
        txtOpacityVal.setText(opacity + "%");

        int iconSize = prefs.getInt("floating_bottom_bar_icon_size", 24);
        sliderIconSize.setValue(iconSize);
        txtIconSizeVal.setText(iconSize + "dp");

        int textSize = prefs.getInt("floating_bottom_bar_text_size", 12);
        sliderTextSize.setValue(textSize);
        txtTextSizeVal.setText(textSize + "sp");

        int paddingVertical = prefs.getInt("floating_bottom_bar_padding_vertical", 6);
        sliderPaddingVertical.setValue(paddingVertical);
        txtPaddingVerticalVal.setText(paddingVertical + "dp");

        int iconLabelSpacing = prefs.getInt("floating_bottom_bar_icon_label_spacing", 2);
        sliderIconLabelSpacing.setValue(iconLabelSpacing);
        txtIconLabelSpacingVal.setText(iconLabelSpacing + "dp");

        // Set Listeners & Bind to Preview Real-Time Updates
        switchFloating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutCustomizationControls.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateLivePreview();
        });

        pillDesignGroup.setOnCheckedChangeListener((group, checkedId) -> updateLivePreview());

        switchGlass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutGlassOpacity.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateLivePreview();
        });

        btnSelectColor.setOnClickListener(v -> {
            int initialColor = selectedColor != 0 ? selectedColor : 0xFF1F2C34;
            new SimpleColorPickerDialog(this, initialColor, color -> {
                selectedColor = color;
                updateColorPreviewCircle();
                updateLivePreview();
            }).show();
        });

        sliderRadius.addOnChangeListener((slider, value, fromUser) -> {
            txtRadiusVal.setText((int) value + "dp");
            updateLivePreview();
        });

        sliderMargin.addOnChangeListener((slider, value, fromUser) -> {
            txtMarginVal.setText((int) value + "dp");
            updateLivePreview();
        });

        sliderMarginHorizontal.addOnChangeListener((slider, value, fromUser) -> {
            txtMarginHorizontalVal.setText((int) value + "dp");
            updateLivePreview();
        });

        sliderFab.addOnChangeListener((slider, value, fromUser) -> {
            txtFabVal.setText((int) value + "dp");
            updateLivePreview();
        });

        sliderOpacity.addOnChangeListener((slider, value, fromUser) -> {
            txtOpacityVal.setText((int) value + "%");
            updateLivePreview();
        });

        sliderIconSize.addOnChangeListener((slider, value, fromUser) -> {
            txtIconSizeVal.setText((int) value + "dp");
            updateLivePreview();
        });

        sliderTextSize.addOnChangeListener((slider, value, fromUser) -> {
            txtTextSizeVal.setText((int) value + "sp");
            updateLivePreview();
        });

        sliderPaddingVertical.addOnChangeListener((slider, value, fromUser) -> {
            txtPaddingVerticalVal.setText((int) value + "dp");
            updateLivePreview();
        });

        sliderIconLabelSpacing.addOnChangeListener((slider, value, fromUser) -> {
            txtIconLabelSpacingVal.setText((int) value + "dp");
            updateLivePreview();
        });

        // Perform initial preview layout
        updateLivePreview();
    }

    private void updateColorPreviewCircle() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        if (selectedColor != 0) {
            shape.setColor(selectedColor);
        } else {
            shape.setColor(0x00000000); // transparent (default)
        }
        shape.setStroke((int) (1 * density), 0x44FFFFFF);
        colorPreviewCircle.setBackground(shape);
    }

    private void updateLivePreview() {
        try {
            boolean isFloating = switchFloating.isChecked();
            boolean isPro = radioDesignPro.isChecked();
            boolean isIosGlass = radioDesignIosGlass.isChecked();
            boolean isGlass = switchGlass.isChecked();
            int radius = (int) sliderRadius.getValue();
            int marginBottom = (int) sliderMargin.getValue();
            int marginHorizontal = (int) sliderMarginHorizontal.getValue();
            int fabOffset = (int) sliderFab.getValue();
            int opacity = (int) sliderOpacity.getValue();
            int iconLabelSpacing = (int) sliderIconLabelSpacing.getValue();

            // 1. Update Preview Pill Layout Params & Margins
            ViewGroup.LayoutParams lp = previewBottomBar.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                if (isFloating) {
                    mlp.leftMargin = (int) (marginHorizontal * density);
                    mlp.rightMargin = (int) (marginHorizontal * density);
                    mlp.bottomMargin = (int) (marginBottom * density);
                    mlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                } else {
                    mlp.leftMargin = 0;
                    mlp.rightMargin = 0;
                    mlp.bottomMargin = 0;
                    mlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                previewBottomBar.setLayoutParams(mlp);
            }

            // 2. Update Preview Pill Background & Corners
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);

            // Disable clipping so the glow/oval can render outside the pill boundaries
            previewBottomBar.setClipChildren(false);
            previewBottomBar.setClipToPadding(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                previewBottomBar.setClipToOutline(false);
            }
            ViewParent previewParent = previewBottomBar.getParent();
            if (previewParent instanceof ViewGroup) {
                ViewGroup vgParent = (ViewGroup) previewParent;
                vgParent.setClipChildren(false);
                vgParent.setClipToPadding(false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    vgParent.setClipToOutline(false);
                }
            }

            if (isFloating) {
                shape.setCornerRadius(radius * density);
                int baseBgColor = selectedColor != 0 ? selectedColor : 0xFF1F2C34;
                if (isGlass || isIosGlass) {
                    int alpha = Math.round((opacity / 100f) * 255f);
                    if (isIosGlass) {
                        alpha = Math.round(0.18f * 255f); // Beautiful frosted translucency for iOS Glass
                    }
                    int rgb = baseBgColor & 0x00FFFFFF;
                    shape.setColor((alpha << 24) | rgb);
                    shape.setStroke((int) (0.8f * density), isIosGlass ? 0x48FFFFFF : 0x18FFFFFF);
                } else {
                    shape.setColor(baseBgColor);
                    shape.setStroke((int) (0.6f * density), 0x18FFFFFF);
                }
                
                if (isPro || isIosGlass) {
                    previewActiveIndicator.setBackground(null);
                    
                    View chatsTab = previewBottomBar.getChildAt(0);
                    if (chatsTab != null) {
                        if (isIosGlass) {
                            chatsTab.setBackground(new IosLiquidGlassDrawable(this, density));
                        } else {
                            chatsTab.setBackground(new LiquidOvalDrawable(this, density));
                        }
                        chatsTab.setPadding(0, 0, 0, 0);
                    }
                    previewChatsIcon.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
                } else {
                    previewActiveIndicator.setBackgroundResource(R.drawable.wa_active_indicator);
                    previewChatsIcon.setImageTintList(ColorStateList.valueOf(0xFF00A884));
                    View chatsTab = previewBottomBar.getChildAt(0);
                    if (chatsTab != null) {
                        chatsTab.setBackground(null);
                        chatsTab.setPadding(0, 0, 0, 0);
                    }
                }
                
                // Adjust vertical padding inside the pill
                int verticalPadding = (int) (sliderPaddingVertical.getValue() * density);
                previewBottomBar.setPadding(
                        previewBottomBar.getPaddingLeft(),
                        verticalPadding,
                        previewBottomBar.getPaddingRight(),
                        verticalPadding
                );
            } else {
                shape.setCornerRadius(0);
                shape.setColor(0xFF121B22); // WhatsApp Dark Mode standard bottom bar color
                shape.setStroke(0, 0);
                
                // WhatsApp shows the active indicator in its stock bar too
                previewActiveIndicator.setBackgroundResource(R.drawable.wa_active_indicator);
                previewChatsIcon.setImageTintList(ColorStateList.valueOf(0xFF00A884));
                
                // Reset padding to normal
                int verticalPadding = (int) (12 * density);
                previewBottomBar.setPadding(
                        previewBottomBar.getPaddingLeft(),
                        verticalPadding,
                        previewBottomBar.getPaddingRight(),
                        verticalPadding
                );
            }
            previewBottomBar.setBackground(shape);

            // Translate mock tab views to match the Pro floating bar layout structure
            for (int i = 0; i < previewBottomBar.getChildCount(); i++) {
                View tab = previewBottomBar.getChildAt(i);
                if (tab instanceof ViewGroup) {
                    ViewGroup tabGroup = (ViewGroup) tab;
                    
                    tabGroup.setClipChildren(false);
                    tabGroup.setClipToPadding(false);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tabGroup.setClipToOutline(false);
                    }
                    
                    if (!isPro || !isFloating || i != 0) {
                        tabGroup.setBackground(null);
                        tabGroup.setPadding(0, 0, 0, 0);
                    }
                    
                    if (tabGroup.getChildCount() >= 2) {
                        View iconContainer = tabGroup.getChildAt(0);
                        View labelView = tabGroup.getChildAt(1);
                        // Reset translationY — we use padding/margins now, not translation hacks
                        if (iconContainer != null) iconContainer.setTranslationY(0);
                        if (labelView != null) {
                            labelView.setTranslationY(0);
                            // Apply icon-label spacing as marginTop on the label
                            ViewGroup.LayoutParams labelLp = labelView.getLayoutParams();
                            if (labelLp instanceof ViewGroup.MarginLayoutParams) {
                                ViewGroup.MarginLayoutParams labelMlp = (ViewGroup.MarginLayoutParams) labelLp;
                                labelMlp.topMargin = (int) (iconLabelSpacing * density);
                                labelView.setLayoutParams(labelMlp);
                            }
                        }
                    }
                }
            }

            // 3. Update Preview FAB margins
            ViewGroup.LayoutParams fabLp = previewFab.getLayoutParams();
            if (fabLp instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) fabLp;
                if (isFloating) {
                    // Position is default padding + user offset (matches WhatsApp exactly)
                    rlp.bottomMargin = (int) ((16 + fabOffset) * density);
                } else {
                    // Classic position: classic bottom bar (80dp) + FAB padding (16dp) = 96dp
                    rlp.bottomMargin = (int) (96 * density);
                }
                previewFab.setLayoutParams(rlp);
            }

            // 4. Update Preview Label Text Size & Icon Size dynamically
            int iconSize = (int) sliderIconSize.getValue();
            int textSize = (int) sliderTextSize.getValue();
            applyCustomSizesToPreview(previewBottomBar, iconSize, textSize);

        } catch (Throwable ignored) {}
    }

    private void applyCustomSizesToPreview(ViewGroup viewGroup, int iconSizeDp, int textSizeSp) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ImageView) {
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                lp.width = (int) (iconSizeDp * density);
                lp.height = (int) (iconSizeDp * density);
                child.setLayoutParams(lp);
            } else if (child instanceof TextView) {
                ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            } else if (child instanceof ViewGroup) {
                applyCustomSizesToPreview((ViewGroup) child, iconSizeDp, textSizeSp);
            }
        }
    }

    private void savePreferencesAndExit() {
        boolean floatingEnabled = switchFloating.isChecked();
        String pillDesign = "regular";
        if (radioDesignPro.isChecked()) {
            pillDesign = "pro";
        } else if (radioDesignIosGlass.isChecked()) {
            pillDesign = "ios_glass";
        }
        boolean glassEnabled = switchGlass.isChecked();
        int radius = (int) sliderRadius.getValue();
        int marginBottom = (int) sliderMargin.getValue();
        int marginHorizontal = (int) sliderMarginHorizontal.getValue();
        int fabOffset = (int) sliderFab.getValue();
        float opacity = sliderOpacity.getValue();
        int iconSize = (int) sliderIconSize.getValue();
        int textSize = (int) sliderTextSize.getValue();
        int paddingVertical = (int) sliderPaddingVertical.getValue();
        int iconLabelSpacing = (int) sliderIconLabelSpacing.getValue();

        prefs.edit()
                .putBoolean("floating_bottom_bar", floatingEnabled)
                .putString("floating_bottom_bar_pill_design", pillDesign)
                .putBoolean("floating_bottom_bar_glass", glassEnabled)
                .putInt("floating_bottom_bar_radius", radius)
                .putInt("floating_bottom_bar_margin_bottom", marginBottom)
                .putInt("floating_bottom_bar_margin_horizontal", marginHorizontal)
                .putInt("floating_bottom_bar_fab_offset", fabOffset)
                .putFloat("floating_bottom_bar_glass_opacity", opacity)
                .putInt("floating_bottom_bar_fill_color", selectedColor)
                .putInt("floating_bottom_bar_icon_size", iconSize)
                .putInt("floating_bottom_bar_text_size", textSize)
                .putInt("floating_bottom_bar_padding_vertical", paddingVertical)
                .putInt("floating_bottom_bar_icon_label_spacing", iconLabelSpacing)
                .apply();

        Toast.makeText(this, R.string.configs_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pill_customization, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_reset) {
            resetToDefaultValues();
            return true;
        } else if (id == R.id.action_save) {
            savePreferencesAndExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void resetToDefaultValues() {
        switchFloating.setChecked(false);
        radioDesignRegular.setChecked(true);
        switchGlass.setChecked(true);
        selectedColor = 0;
        updateColorPreviewCircle();
        
        sliderRadius.setValue(28);
        txtRadiusVal.setText("28dp");
        
        sliderMargin.setValue(22);
        txtMarginVal.setText("22dp");
        
        sliderMarginHorizontal.setValue(16);
        txtMarginHorizontalVal.setText("16dp");
        
        sliderFab.setValue(80);
        txtFabVal.setText("80dp");
        
        sliderOpacity.setValue(35);
        txtOpacityVal.setText("35%");
        
        sliderIconSize.setValue(24);
        txtIconSizeVal.setText("24dp");
        
        sliderTextSize.setValue(12);
        txtTextSizeVal.setText("12sp");
        
        sliderPaddingVertical.setValue(6);
        txtPaddingVerticalVal.setText("6dp");
        
        sliderIconLabelSpacing.setValue(2);
        txtIconLabelSpacingVal.setText("2dp");
        
        updateLivePreview();
        Toast.makeText(this, "Reset to default values", Toast.LENGTH_SHORT).show();
    }

    private static class LiquidOvalDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topRainbow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomRainbow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean isNight;
        private final int accentColor;
        private final float density;

        public LiquidOvalDrawable(Context ctx, float density) {
            this.density = density;
            this.isNight = isNightMode(ctx);
            this.accentColor = getThemeAccentColor(ctx);
            
            fillPaint.setStyle(Paint.Style.FILL);
            
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.0f * density);
            strokePaint.setColor(isNight ? 0x45FFFFFF : 0x25000000);
            
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(1.2f * density);
            
            topRainbow.setStyle(Paint.Style.STROKE);
            topRainbow.setStrokeWidth(0.8f * density);
            
            bottomRainbow.setStyle(Paint.Style.STROKE);
            bottomRainbow.setStrokeWidth(0.8f * density);

            shadowPaint.setStyle(Paint.Style.FILL);
            shadowPaint.setColor(isNight ? 0x66000000 : 0x2C000000);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();

            float ovalWidth = bounds.width() * 0.78f;
            float ovalHeight = bounds.height() + 16 * density;

            float left = cx - ovalWidth / 2f;
            float right = cx + ovalWidth / 2f;
            float top = cy - ovalHeight / 2f;
            float bottom = cy + ovalHeight / 2f;

            RectF rectF = new RectF(left, top, right, bottom);
            float cornerRadius = ovalWidth / 2f;

            canvas.drawRoundRect(new RectF(left, top + 1.5f * density, right, bottom + 1.5f * density), cornerRadius, cornerRadius, shadowPaint);

            int startColor = isNight ? 0x2DFFFFFF : 0x70FFFFFF;
            int midColor = isNight ? 0x15FFFFFF : 0x40FFFFFF;
            int endColor = isNight ? 0x22FFFFFF : 0x55FFFFFF;
            
            LinearGradient fillGradient = new LinearGradient(
                    cx, top, cx, bottom,
                    new int[]{startColor, midColor, endColor},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillGradient);
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint);

            canvas.save();
            Path clipPath = new Path();
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);

            LinearGradient glowGradient = new LinearGradient(
                    left, top, right, top + 10 * density,
                    new int[]{0x00FFFFFF, isNight ? 0x77FFFFFF : 0x55FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            glowPaint.setShader(glowGradient);
            canvas.drawArc(new RectF(left + 1f, top + 1f, right - 1f, top + 15 * density), 200, 140, false, glowPaint);

            LinearGradient topGrad = new LinearGradient(
                    left + 8 * density, top, right - 8 * density, top,
                    new int[]{0x00FFFFFF, 0xB8FFFFFF, 0xEEFFFFFF, 0xB8FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
                    Shader.TileMode.CLAMP
            );
            topRainbow.setShader(topGrad);
            canvas.drawArc(new RectF(left, top, right, top + 16 * density), 210, 120, false, topRainbow);

            LinearGradient bottomGrad = new LinearGradient(
                    left + 8 * density, bottom, right - 8 * density, bottom,
                    new int[]{0x00FFFFFF, 0x68FFFFFF, 0x9EFFFFFF, 0x68FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
                    Shader.TileMode.CLAMP
            );
            bottomRainbow.setShader(bottomGrad);
            canvas.drawArc(new RectF(left, bottom - 16 * density, right, bottom), 30, 120, false, bottomRainbow);

            canvas.restore();

            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            glowPaint.setAlpha(alpha);
            topRainbow.setAlpha(alpha);
            bottomRainbow.setAlpha(alpha);
            shadowPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        private static boolean isNightMode(Context context) {
            try {
                if (context == null) return false;
                int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return uiMode == Configuration.UI_MODE_NIGHT_YES;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static int getThemeAccentColor(Context context) {
            try {
                TypedValue outValue = new TypedValue();
                if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, outValue, true)) {
                    if (outValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && outValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        return outValue.data;
                    } else {
                        return context.getResources().getColor(outValue.resourceId);
                    }
                }
            } catch (Throwable ignored) {}
            return 0xff25d366; // WhatsApp Green
        }
    }

    private static class IosLiquidGlassDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean isNight;
        private final float density;

        public IosLiquidGlassDrawable(Context ctx, float density) {
            this.density = density;
            this.isNight = isNightMode(ctx);

            fillPaint.setStyle(Paint.Style.FILL);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.0f * density);
            strokePaint.setColor(isNight ? 0x55FFFFFF : 0x28000000);

            specularPaint.setStyle(Paint.Style.FILL);

            shadowPaint.setStyle(Paint.Style.FILL);
            shadowPaint.setColor(isNight ? 0x33000000 : 0x14000000);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;

            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();

            float w = bounds.width() * 0.82f;
            float h = bounds.height() - 8 * density;

            float left = cx - w / 2f;
            float right = cx + w / 2f;
            float top = cy - h / 2f;
            float bottom = cy + h / 2f;

            RectF rectF = new RectF(left, top, right, bottom);
            float rx = h / 2f;

            // Subtle drop shadow
            canvas.drawRoundRect(new RectF(left, top + 1.5f * density, right, bottom + 1.5f * density), rx, rx, shadowPaint);

            // Frosted glass fill gradient
            int startColor = isNight ? 0x22FFFFFF : 0x80FFFFFF;
            int endColor = isNight ? 0x06FFFFFF : 0x2AFFFFFF;
            LinearGradient fillGrad = new LinearGradient(cx, top, cx, bottom, startColor, endColor, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillGrad);
            canvas.drawRoundRect(rectF, rx, rx, fillPaint);

            // Specular reflection at the top
            RectF specRect = new RectF(left + 2 * density, top + 1 * density, right - 2 * density, top + h * 0.38f);
            LinearGradient specGrad = new LinearGradient(cx, top, cx, top + h * 0.38f, 0x65FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            specularPaint.setShader(specGrad);
            canvas.drawRoundRect(specRect, rx * 0.8f, rx * 0.8f, specularPaint);

            // Stroke border
            canvas.drawRoundRect(rectF, rx, rx, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            specularPaint.setAlpha(alpha);
            shadowPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        private static boolean isNightMode(Context context) {
            try {
                if (context == null) return false;
                int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return uiMode == Configuration.UI_MODE_NIGHT_YES;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}