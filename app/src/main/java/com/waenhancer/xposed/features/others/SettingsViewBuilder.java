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

        // Try to resolve from theme attributes directly
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            android.content.res.Resources.Theme theme = context.getTheme();
            
            // Resolve primary
            int primaryAttr = context.getResources().getIdentifier("colorPrimary", "attr", context.getPackageName());
            if (primaryAttr != 0 && theme.resolveAttribute(primaryAttr, tv, true)) {
                colorPrimary = tv.data;
            }
            
            // Resolve background
            int bgAttr = android.R.attr.windowBackground;
            if (theme.resolveAttribute(bgAttr, tv, true)) {
                if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                    windowBg = tv.data;
                }
            }
        } catch (Throwable ignored) {}
        int toolbarTextColor = getHostColor(context, "toolbar_primary_text_color", 0xffffffff);
        int actionBarHeight = getHostDimen(context, "action_bar_size", 56);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(windowBg);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                actionBarHeight));
        toolbar.setBackgroundColor(colorPrimary);
        toolbar.setPadding(dp(context, 4), 0, dp(context, 16), 0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(dp(context, 4));
        }

        ImageView backButton = new ImageView(context);
        backButton.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backButton.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        backButton.setImageDrawable(resolveBackDrawable(context, toolbarTextColor));
        TypedValue rippleValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, rippleValue, true);
        try {
            backButton.setBackgroundResource(rippleValue.resourceId);
        } catch (Throwable ignored) {
        }

        TextView titleView = new TextView(context);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        titleView.setTextColor(toolbarTextColor);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        titleView.setText("WaEnhancer");

        toolbar.addView(backButton);
        toolbar.addView(titleView);
        root.addView(toolbar);

        FrameLayout container = new FrameLayout(context);
        container.setId(View.generateViewId());
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        container.setBackgroundColor(windowBg);
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
