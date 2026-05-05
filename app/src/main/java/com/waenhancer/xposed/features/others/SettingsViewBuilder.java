package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.waenhancer.xposed.utils.ThemeUtils;

public final class SettingsViewBuilder {

    private SettingsViewBuilder() {
    }

    public static final class Host {
        public final View root;
        public final FrameLayout container;
        public final ImageView backButton;
        public final TextView titleView;

        private Host(View root, FrameLayout container, ImageView backButton, TextView titleView) {
            this.root = root;
            this.container = container;
            this.backButton = backButton;
            this.titleView = titleView;
        }
    }

    public static Host buildHost(Context context) {
        boolean isDark = ThemeUtils.isNightMode(context);
        int colorPrimary = ThemeUtils.getThemeAccentColor(context);
        int windowBg = ThemeUtils.getThemeBackgroundColor(context, isDark);
        int cardBg = ThemeUtils.getThemeCardColor(context, isDark);
        int textColor = ThemeUtils.getThemeTextColorPrimary(context, isDark);
        int textSecondary = ThemeUtils.getThemeTextColorSecondary(context, isDark);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(windowBg);

        // Header Section
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 20));
        
        // Toolbar with Back Button
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 56)));

        ImageView backButton = new ImageView(context);
        backButton.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));
        backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backButton.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        backButton.setImageDrawable(resolveBackDrawable(context, textColor));
        
        TypedValue rippleValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, rippleValue, true);
        try {
            backButton.setBackgroundResource(rippleValue.resourceId);
        } catch (Throwable ignored) {}

        // Spacer to push things right if needed, but here we want back button on left
        toolbar.addView(backButton);
        
        // Add "Manager" badge style text if we want, or just the logo
        header.addView(toolbar);

        // Large Title - Modern look
        TextView titleView = new TextView(context);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(dp(context, 4), dp(context, 8), 0, 0);
        titleView.setLayoutParams(titleParams);
        titleView.setTextColor(textColor);
        titleView.setTextSize(32); // Slightly larger
        titleView.setLetterSpacing(-0.02f);
        titleView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        titleView.setText("Settings");

        TextView subtitleView = new TextView(context);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(dp(context, 4), dp(context, 2), 0, 0);
        subtitleView.setLayoutParams(subtitleParams);
        subtitleView.setTextColor(textSecondary);
        subtitleView.setTextSize(14);
        subtitleView.setText("WaEnhancer X Premium");
        subtitleView.setAlpha(0.7f);

        header.addView(titleView);
        header.addView(subtitleView);
        root.addView(header);

        // Settings Container
        FrameLayout container = new FrameLayout(context);
        container.setId(com.waenhancer.R.id.container);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        container.setLayoutParams(containerParams);
        
        // Add a subtle top radius to the settings container if in dark mode for "sheet" look
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            container.setElevation(dp(context, 2));
        }
        
        root.addView(container);

        return new Host(root, container, backButton, titleView);
    }

    private static Drawable resolveBackDrawable(Context context, int tint) {
        Drawable backArrow = getHostDrawable(context, "abc_ic_ab_back_material");
        if (backArrow == null) {
            backArrow = getHostDrawable(context, "ic_ab_back_white");
        }
        if (backArrow == null) {
            try {
                TypedValue tv = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.homeAsUpIndicator, tv, true);
                backArrow = context.getResources().getDrawable(tv.resourceId, context.getTheme());
            } catch (Throwable ignored) {
            }
        }
        if (backArrow != null) {
            backArrow = backArrow.mutate();
            backArrow.setTint(tint);
        }
        return backArrow;
    }

    private static int getHostColor(Context context, String colorName, int fallback) {
        try {
            Resources res = context.getResources();
            int id = res.getIdentifier(colorName, "color", context.getPackageName());
            if (id != 0) {
                return res.getColor(id, context.getTheme());
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static Drawable getHostDrawable(Context context, String drawableName) {
        try {
            Resources res = context.getResources();
            int id = res.getIdentifier(drawableName, "drawable", context.getPackageName());
            if (id != 0) {
                return res.getDrawable(id, context.getTheme());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int getHostDimen(Context context, String dimenName, int fallbackDp) {
        try {
            Resources res = context.getResources();
            int id = res.getIdentifier(dimenName, "dimen", context.getPackageName());
            if (id != 0) {
                return (int) res.getDimension(id);
            }
        } catch (Throwable ignored) {
        }
        return dp(context, fallbackDp);
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics());
    }
}
