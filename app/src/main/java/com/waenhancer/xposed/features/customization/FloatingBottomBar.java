package com.waenhancer.xposed.features.customization;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ProHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public class FloatingBottomBar extends Feature {

    private static final float CORNER_RADIUS_DP = 28f;
    private static final float SIDE_MARGIN_DP = 16f;
    private static final float BOTTOM_MARGIN_DP = 22f;
    private static final float PILL_ELEVATION_DP = 12f;
    private static final float PILL_TRANSLATION_Z_DP = 8f;
    private static final float FAB_GAP_DP = 12f;
    private static final String[] FAB_RESOURCE_NAMES = new String[]{"fab", "fab_second", "extended_mini_fab"};

    private static final WeakHashMap<ViewGroup, Boolean> processedBars = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Integer> setupAttempts = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> styledBottomBars = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> targetHideStates = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> targetTranslations = new WeakHashMap<>();

    private static boolean scrollHideEnabled = true;
    private static String scrollHideMode = "downward";
    private static boolean glassEnabled = false;
    private static float glassOpacity = 35f;
    private static int glassFillColor = 0;
    private static boolean pillDesignPro = false;
    private static boolean pillDesignIos = false;

    private static int userBottomMarginDp = 22;
    private static int userSideMarginDp = 16;
    private static int userRadiusDp = 28;
    private static int userVerticalPaddingDp = 6;
    private static int userIconSizeDp = 24;
    private static int userTextSizeSp = 12;
    private static int userIconLabelSpacingDp = 2;

    public FloatingBottomBar(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Floating Bottom Bar";
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("floating_bottom_bar", true)) return;

        scrollHideEnabled = prefs.getBoolean("floating_bottom_bar_scroll_hide", true);
        scrollHideMode = prefs.getString("floating_bottom_bar_scroll_hide_mode", "downward");
        glassEnabled = prefs.getBoolean("floating_bottom_bar_glass", false);
        glassOpacity = getPrefFloat(prefs, "floating_bottom_bar_glass_opacity", 35f);
        glassFillColor = getPrefColor(prefs, "floating_bottom_bar_fill_color", 0);

        String designPref = prefs.getString("floating_bottom_bar_pill_design", "regular");
        pillDesignPro = "pro".equals(designPref) && ProHelper.isPillDesignProEnabled();
        pillDesignIos = "ios_glass".equals(designPref) && ProHelper.isPillDesignProEnabled();

        userBottomMarginDp = prefs.getInt("floating_bottom_bar_margin_bottom", (int) BOTTOM_MARGIN_DP);
        userSideMarginDp = prefs.getInt("floating_bottom_bar_margin_horizontal", (int) SIDE_MARGIN_DP);
        userRadiusDp = prefs.getInt("floating_bottom_bar_radius", (int) CORNER_RADIUS_DP);
        userVerticalPaddingDp = prefs.getInt("floating_bottom_bar_padding_vertical", 6);
        userIconSizeDp = prefs.getInt("floating_bottom_bar_icon_size", 24);
        userTextSizeSp = prefs.getInt("floating_bottom_bar_text_size", 12);
        userIconLabelSpacingDp = prefs.getInt("floating_bottom_bar_icon_label_spacing", 2);

        XposedHelpers.findAndHookMethod(
                View.class,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (view.getId() == View.NO_ID) return;
                        try {
                            String entryName = view.getResources().getResourceEntryName(view.getId());
                            if ("bottom_nav".equals(entryName) || "navigation_bar".equals(entryName) || "tab_layout".equals(entryName)) {
                                if (view instanceof ViewGroup) {
                                    scheduleSetup((ViewGroup) view);
                                }
                                return;
                            }
                            for (String fabName : FAB_RESOURCE_NAMES) {
                                if (fabName.equals(entryName)) {
                                    view.post(() -> positionFabAboveCurrentBar(view));
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                View.class,
                "onDetachedFromWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (view.getId() == View.NO_ID) return;
                        try {
                            String entryName = view.getResources().getResourceEntryName(view.getId());
                            if ("bottom_nav".equals(entryName) || "navigation_bar".equals(entryName) || "tab_layout".equals(entryName)) {
                                if (view instanceof ViewGroup) {
                                    ViewGroup bar = (ViewGroup) view;
                                    setupAttempts.remove(bar);
                                    processedBars.remove(bar);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
        );

        Class<?> tabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        if (tabFrameClass != null) {
            XposedBridge.hookAllMethods(tabFrameClass, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view instanceof ViewGroup) {
                        scheduleSetup((ViewGroup) view);
                    }
                }
            });
        }

        hookRecyclerViewScrollListeners();
    }

    private void hookRecyclerViewScrollListeners() {
        try {
            Class<?> recyclerViewClass = XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView", classLoader);
            List<Method> candidateMethods = new ArrayList<>();
            for (Method m : recyclerViewClass.getDeclaredMethods()) {
                Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes.length == 2 && paramTypes[0] == int.class && paramTypes[1] == int.class && m.getReturnType() == void.class) {
                    String name = m.getName();
                    if (Modifier.isStatic(m.getModifiers())) continue;
                    if (name.equals("scrollBy") || name.equals("scrollTo") || name.equals("onMeasure") || name.equals("onSizeChanged") || name.equals("onLayout") || name.equals("setMeasuredDimension")) {
                        continue;
                    }
                    candidateMethods.add(m);
                }
            }

            for (Method m : candidateMethods) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args == null || param.args.length < 2) return;
                        View rv = (View) param.thisObject;
                        int dy = (int) param.args[1];
                        handleRecyclerViewScrolled(rv, dy);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Failed to hook RecyclerView scroll listeners: " + t);
        }
    }

    private static void handleRecyclerViewScrolled(View rv, int dy) {
        try {
            if (Math.abs(dy) > 50000) return;
            if (Math.abs(dy) < 5) return;
            if (!rv.isShown()) return;
            if (!isMainTabScrollable(rv)) return;

            View bottomNav = findBottomNavForScrollable(rv);
            if (bottomNav == null) return;
            onViewScrolled(bottomNav, dy);
        } catch (Throwable ignored) {}
    }

    private static void onViewScrolled(View bottomNav, int dy) {
        if (bottomNav == null) return;
        View barTarget = getBarAnimationTarget(bottomNav);
        float density = bottomNav.getContext().getResources().getDisplayMetrics().density;

        if (!scrollHideEnabled) {
            Boolean lastHide = targetHideStates.get(barTarget);
            Float lastTarget = targetTranslations.get(barTarget);
            if ((lastHide != null && lastHide) || (lastTarget != null && lastTarget != 0f) || barTarget.getVisibility() != View.VISIBLE) {
                targetHideStates.put(barTarget, false);
                targetTranslations.put(barTarget, 0f);
                barTarget.setVisibility(View.VISIBLE);
                barTarget.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
                animateFabs(bottomNav, false, density);
            }
            return;
        }

        if (Math.abs(dy) < 5) return;

        boolean hide = dy > 0;

        if ("invisible".equalsIgnoreCase(scrollHideMode)) {
            Boolean lastHide = targetHideStates.get(barTarget);
            if (lastHide != null && lastHide == hide) return;
            targetHideStates.put(barTarget, hide);

            if (hide) {
                barTarget.animate()
                        .alpha(0f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> {
                            Boolean currentHide = targetHideStates.get(barTarget);
                            if (currentHide != null && currentHide) {
                                barTarget.setVisibility(View.GONE);
                            }
                        })
                        .start();
            } else {
                barTarget.setVisibility(View.VISIBLE);
                barTarget.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(null)
                        .start();
            }
            animateFabs(bottomNav, hide, density);
        } else {
            int height = barTarget.getHeight();
            if (height <= 0) height = bottomNav.getHeight();
            if (height <= 0) height = (int) (80 * density);

            float targetTranslationY = hide ? (height + (userBottomMarginDp * density) + (24 * density)) : 0f;

            Float currentTarget = targetTranslations.get(barTarget);
            if (currentTarget != null && currentTarget == targetTranslationY) return;
            targetTranslations.put(barTarget, targetTranslationY);

            barTarget.animate()
                    .translationY(targetTranslationY)
                    .setDuration(220)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();

            animateFabs(bottomNav, hide, density);
        }
    }

    private static void animateFabs(View bottomNav, boolean hide, float density) {
        FrameLayout rootView = findRootViewStatic(bottomNav);
        if (rootView == null) return;

        ViewGroup container = null;
        if (bottomNav.getParent() instanceof ViewGroup) {
            container = (ViewGroup) bottomNav.getParent();
        }

        int barHeight = container != null ? container.getHeight() : bottomNav.getHeight();
        int bottomMargin = (int) (userBottomMarginDp * density);

        float baseOffset = -(barHeight + bottomMargin + (FAB_GAP_DP * density));
        float targetOffset = hide ? 0f : baseOffset;

        for (String name : FAB_RESOURCE_NAMES) {
            int id = rootView.getContext().getResources().getIdentifier(name, "id", rootView.getContext().getPackageName());
            if (id <= 0) continue;
            View fab = rootView.findViewById(id);
            if (fab != null) {
                fab.animate()
                        .translationY(targetOffset)
                        .setDuration(220)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        }
    }

    private static View getBarAnimationTarget(View bottomNav) {
        if (bottomNav.getParent() instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) bottomNav.getParent();
            if (container.getParent() instanceof FrameLayout) {
                return container;
            }
        }
        return bottomNav;
    }

    private void scheduleSetup(final ViewGroup bar) {
        if (processedBars.containsKey(bar)) {
            ensureBarOverlay(bar);
            return;
        }

        bar.post(() -> {
            if (setupFloatingBar(bar)) {
                processedBars.put(bar, true);
                styledBottomBars.put(bar, true);
                setupAttempts.remove(bar);
                return;
            }
            retrySetup(bar);
        });
    }

    private void retrySetup(final ViewGroup bar) {
        int attempt = setupAttempts.containsKey(bar) ? setupAttempts.get(bar) : 0;
        if (attempt >= 3) return;
        setupAttempts.put(bar, attempt + 1);
        bar.postDelayed(() -> {
            if (processedBars.containsKey(bar)) return;
            if (setupFloatingBar(bar)) {
                processedBars.put(bar, true);
                styledBottomBars.put(bar, true);
                setupAttempts.remove(bar);
            } else {
                retrySetup(bar);
            }
        }, 100L);
    }

    private void ensureBarOverlay(final ViewGroup bar) {
        if (bar.getParent() instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) bar.getParent();
            if (!(container.getParent() instanceof FrameLayout)) {
                bar.post(() -> setupFloatingBar(bar));
            }
        }
    }

    private boolean setupFloatingBar(ViewGroup bar) {
        try {
            ViewParent parent = bar.getParent();
            if (!(parent instanceof ViewGroup)) return false;
            ViewGroup container = (ViewGroup) parent;

            FrameLayout rootView = findRootView(bar);
            if (rootView == null) return false;

            if (container.getParent() == rootView) {
                updateOverlayLayout(rootView, container, bar);
                applyPillStyle(container, bar);
                positionFabsAboveBar(rootView, container);
                return true;
            }

            if (container.getParent() instanceof ViewGroup) {
                ViewGroup originalParent = (ViewGroup) container.getParent();
                originalParent.setOnApplyWindowInsetsListener((v, insets) -> {
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 0);
                    return insets;
                });
                originalParent.removeView(container);
            }

            container.setOnApplyWindowInsetsListener((v, insets) -> insets);
            bar.setOnApplyWindowInsetsListener((v, insets) -> insets);

            float density = bar.getContext().getResources().getDisplayMetrics().density;
            int padV = (int) (userVerticalPaddingDp * density);
            int pillHeight = (int) ((56 + (userVerticalPaddingDp * 2)) * density);
            if (pillHeight < (int) (48 * density)) {
                pillHeight = (int) (48 * density);
            }

            bar.setPadding(0, 0, 0, 0);

            int bottomMargin = navigationBarInset(rootView) + (int) (userBottomMarginDp * density);

            FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    pillHeight
            );
            rootParams.gravity = Gravity.BOTTOM;
            rootParams.bottomMargin = bottomMargin;
            rootView.addView(container, rootParams);

            ViewGroup.LayoutParams containerLp = container.getLayoutParams();
            if (containerLp != null) {
                containerLp.height = pillHeight;
                container.setLayoutParams(containerLp);
            }

            ViewGroup.LayoutParams barLp = bar.getLayoutParams();
            if (barLp != null) {
                barLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                barLp.height = pillHeight;
                bar.setLayoutParams(barLp);
            }

            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                if (child instanceof ViewGroup) {
                    ViewGroup.LayoutParams childLp = child.getLayoutParams();
                    if (childLp != null) {
                        childLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        child.setLayoutParams(childLp);
                    }
                }
            }

            applyVerticalPaddingToTabItems(bar, padV);

            applyPillStyle(container, bar);
            positionFabsAboveBar(rootView, container);
            return true;
        } catch (Throwable e) {
            XposedBridge.log("[WAEX-FBB] Error setting up floating bar: " + e);
            return false;
        }
    }

    private void updateOverlayLayout(FrameLayout rootView, ViewGroup container, ViewGroup bar) {
        float density = bar.getContext().getResources().getDisplayMetrics().density;
        int padV = (int) (userVerticalPaddingDp * density);
        int pillHeight = (int) ((56 + (userVerticalPaddingDp * 2)) * density);
        if (pillHeight < (int) (48 * density)) {
            pillHeight = (int) (48 * density);
        }

        bar.setPadding(0, 0, 0, 0);

        int bottomMargin = navigationBarInset(rootView) + (int) (userBottomMarginDp * density);

        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            flp.gravity = Gravity.BOTTOM;
            flp.bottomMargin = bottomMargin;
            flp.height = pillHeight;
            container.setLayoutParams(flp);
        }

        ViewGroup.LayoutParams barLp = bar.getLayoutParams();
        if (barLp != null) {
            barLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            barLp.height = pillHeight;
            bar.setLayoutParams(barLp);
        }

        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup.LayoutParams childLp = child.getLayoutParams();
                if (childLp != null) {
                    childLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    child.setLayoutParams(childLp);
                }
            }
        }

        applyVerticalPaddingToTabItems(bar, padV);
    }

    private static void applyVerticalPaddingToTabItems(ViewGroup bar, final int padV) {
        if (bar == null) return;
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup menuView = (ViewGroup) child;
                for (int j = 0; j < menuView.getChildCount(); j++) {
                    View tabItem = menuView.getChildAt(j);
                    if (tabItem instanceof ViewGroup) {
                        ViewGroup itemGroup = (ViewGroup) tabItem;
                        styleAndLayoutTabItem(itemGroup, padV);
                        itemGroup.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                            styleAndLayoutTabItem((ViewGroup) v, padV);
                        });
                    }
                }
            }
        }
    }

    private static void styleAndLayoutTabItem(ViewGroup tabItem, int padV) {
        if (tabItem == null) return;
        float density = tabItem.getContext().getResources().getDisplayMetrics().density;

        View iconContainer = null;
        View labelsGroup = null;

        for (int i = 0; i < tabItem.getChildCount(); i++) {
            View child = tabItem.getChildAt(i);
            if (child == null) continue;
            if (child.getId() != View.NO_ID) {
                try {
                    String entryName = child.getResources().getResourceEntryName(child.getId());
                    if (entryName != null) {
                        if (entryName.contains("icon_container") || entryName.contains("icon")) {
                            iconContainer = child;
                        } else if (entryName.contains("labels_group") || entryName.contains("label")) {
                            labelsGroup = child;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (iconContainer == null && child instanceof FrameLayout) {
                iconContainer = child;
            }
            if (labelsGroup == null && (child.getClass().getName().contains("BaselineLayout") || (child instanceof ViewGroup && !(child instanceof FrameLayout)))) {
                labelsGroup = child;
            }
        }

        if (iconContainer == null && tabItem.getChildCount() >= 1) {
            iconContainer = tabItem.getChildAt(0);
        }
        if (labelsGroup == null && tabItem.getChildCount() >= 2) {
            labelsGroup = tabItem.getChildAt(1);
        }

        applyCustomSizesToTabItem(tabItem, userIconSizeDp, userTextSizeSp, density);

        int itemHeight = tabItem.getHeight();
        if (itemHeight <= 0) {
            itemHeight = (int) ((56 + (userVerticalPaddingDp * 2)) * density);
        }

        int iconHeight = (iconContainer != null && iconContainer.getHeight() > 0)
                ? iconContainer.getHeight()
                : (int) (32 * density);

        int labelHeight = 0;
        if (labelsGroup != null && labelsGroup.getVisibility() != View.GONE) {
            labelHeight = labelsGroup.getHeight();
            if (labelHeight <= 0) {
                labelHeight = (int) (16 * density);
            }
        }

        int spacing = (int) (userIconLabelSpacingDp * density);
        int totalContentHeight = (labelHeight > 0) ? (iconHeight + spacing + labelHeight) : iconHeight;

        int targetIconTop = Math.max((int) (2 * density), (itemHeight - totalContentHeight) / 2);

        if (iconContainer != null) {
            int currentIconTop = iconContainer.getTop();
            int iconOffsetY = targetIconTop - currentIconTop;
            iconContainer.setTranslationY(iconOffsetY);
        }

        if (labelsGroup != null && labelHeight > 0) {
            int targetLabelTop = targetIconTop + iconHeight + spacing;
            int currentLabelTop = labelsGroup.getTop();
            int labelOffsetY = targetLabelTop - currentLabelTop;
            labelsGroup.setTranslationY(labelOffsetY);
        }

        formatBadgeViews(tabItem, iconContainer, density);
    }

    private static void formatBadgeViews(ViewGroup tabItem, View iconContainer, float density) {
        if (tabItem == null) return;
        List<View> badgeViews = new ArrayList<>();
        findBadgeViewsRecursive(tabItem, badgeViews);

        if (badgeViews.isEmpty()) return;

        int dotBadgeSizePx = (int) (10 * density);

        for (View badge : badgeViews) {
            if (badge == null || badge.getVisibility() != View.VISIBLE) continue;

            boolean hasTextNumber = false;
            if (badge instanceof TextView) {
                TextView tv = (TextView) badge;
                CharSequence text = tv.getText();
                if (text != null && text.length() > 0) {
                    hasTextNumber = true;
                }
            }

            if (hasTextNumber) {
                if (iconContainer != null) {
                    alignBadgeToIconTopRight(badge, iconContainer, density, false);
                }
                continue;
            }

            // Dot badge without number (e.g. bottom_nav_indicator_badge on Updates)
            ViewGroup.LayoutParams lp = badge.getLayoutParams();
            if (lp != null) {
                if (lp.width != dotBadgeSizePx || lp.height != dotBadgeSizePx) {
                    lp.width = dotBadgeSizePx;
                    lp.height = dotBadgeSizePx;
                    badge.setLayoutParams(lp);
                }
            }

            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(0xFF00A884); // WhatsApp green badge accent
            badge.setBackground(dotDrawable);

            if (badge instanceof ImageView) {
                ImageView iv = (ImageView) badge;
                iv.setImageDrawable(null);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }

            if (iconContainer != null) {
                alignBadgeToIconTopRight(badge, iconContainer, density, true);
            }
        }
    }

    private static void alignBadgeToIconTopRight(View badge, View iconContainer, float density, boolean isDot) {
        if (badge == null || iconContainer == null) return;

        View iconView = findIconView(iconContainer);

        int iconRightInTab;
        int iconTopInTab;

        if (iconView != null && iconView != iconContainer && iconView.getWidth() > 0) {
            iconRightInTab = iconContainer.getLeft() + iconView.getRight();
            iconTopInTab = iconContainer.getTop() + iconView.getTop();
        } else {
            int containerW = iconContainer.getWidth();
            int iconW = (int) (userIconSizeDp * density);
            int paddingH = Math.max(0, (containerW - iconW) / 2);
            iconRightInTab = iconContainer.getRight() - paddingH;
            iconTopInTab = iconContainer.getTop() + (int) (4 * density);
        }

        float iconTranslationY = iconContainer.getTranslationY();

        int targetLeft = isDot
                ? (iconRightInTab - (int) (5 * density))
                : (iconRightInTab - (int) (10 * density));

        int targetTop = isDot
                ? ((int) (iconTopInTab + iconTranslationY - (2 * density)))
                : ((int) (iconTopInTab + iconTranslationY - (4 * density)));

        int currentLeft = badge.getLeft();
        int currentTop = badge.getTop();

        if (currentLeft != 0 || currentTop != 0) {
            badge.setTranslationX(targetLeft - currentLeft);
            badge.setTranslationY(targetTop - currentTop);
        } else {
            ViewGroup.LayoutParams lp = badge.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                flp.gravity = Gravity.TOP | Gravity.START;
                flp.leftMargin = targetLeft;
                flp.topMargin = targetTop;
                badge.setLayoutParams(flp);
            }
        }
    }

    private static View findIconView(View iconContainer) {
        if (iconContainer == null) return null;
        if (iconContainer instanceof ImageView) return iconContainer;
        if (iconContainer instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) iconContainer;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ImageView) {
                    if (child.getId() != View.NO_ID) {
                        try {
                            String entryName = child.getResources().getResourceEntryName(child.getId());
                            if (entryName != null && (entryName.contains("icon") || entryName.contains("image"))) {
                                return child;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ImageView) {
                    return child;
                }
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                View nested = findIconView(group.getChildAt(i));
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void findBadgeViewsRecursive(View view, List<View> outBadges) {
        if (view == null) return;
        if (view.getId() != View.NO_ID) {
            try {
                String entryName = view.getResources().getResourceEntryName(view.getId());
                if (entryName != null) {
                    if (entryName.contains("active_indicator")) {
                        return; // Ignore active tab background indicator shape
                    }
                    if (entryName.contains("badge") || entryName.contains("indicator")) {
                        outBadges.add(view);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }
        String className = view.getClass().getName();
        if ((className.contains("Badge") || className.endsWith("BadgeView")) && !className.contains("ActiveIndicator")) {
            outBadges.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findBadgeViewsRecursive(group.getChildAt(i), outBadges);
            }
        }
    }

    private static void applyCustomSizesToTabItem(ViewGroup viewGroup, int iconSizeDp, int textSizeSp, float density) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ImageView) {
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp != null) {
                    int sizePx = (int) (iconSizeDp * density);
                    if (lp.width != sizePx || lp.height != sizePx) {
                        lp.width = sizePx;
                        lp.height = sizePx;
                        child.setLayoutParams(lp);
                    }
                }
            } else if (child instanceof TextView) {
                TextView tv = (TextView) child;
                float targetPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, tv.getResources().getDisplayMetrics());
                if (Math.abs(tv.getTextSize() - targetPx) > 0.5f) {
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                }
            } else if (child instanceof ViewGroup) {
                applyCustomSizesToTabItem((ViewGroup) child, iconSizeDp, textSizeSp, density);
            }
        }
    }

    private int navigationBarInset(View view) {
        try {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
            if (insets != null) {
                return insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private FrameLayout findRootView(View startView) {
        return findRootViewStatic(startView);
    }

    private static FrameLayout findRootViewStatic(View startView) {
        View current = startView;
        FrameLayout lastFrameLayout = null;
        while (current != null) {
            if (current instanceof FrameLayout) {
                lastFrameLayout = (FrameLayout) current;
            }
            ViewParent p = current.getParent();
            current = (p instanceof View) ? (View) p : null;
        }
        return lastFrameLayout;
    }

    private void applyPillStyle(ViewGroup container, ViewGroup bar) {
        container.setBackgroundColor(Color.TRANSPARENT);

        if (container.getParent() instanceof ViewGroup) {
            ((ViewGroup) container.getParent()).setClipChildren(false);
            ((ViewGroup) container.getParent()).setClipToPadding(false);
        }
        container.setClipChildren(false);
        container.setClipToPadding(false);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);

        int dividerId = bar.getContext().getResources().getIdentifier("bottom_nav_divider", "id", bar.getContext().getPackageName());
        if (dividerId > 0) {
            View divider = container.findViewById(dividerId);
            if (divider != null) {
                divider.setVisibility(View.GONE);
            }
        }

        float density = bar.getContext().getResources().getDisplayMetrics().density;
        Context ctx = bar.getContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bar.setBackgroundTintList(null);
        }

        if (glassEnabled) {
            bar.setBackground(createGlassShape(ctx, density, true));
            applyPillShadow(bar, density);
        } else {
            int userRadius = prefs.getInt("floating_bottom_bar_radius", userRadiusDp);
            float radius = userRadius * density;

            boolean isNight = DesignUtils.isNightMode(ctx);
            int bgColor = isNight ? 0xff1f2c34 : 0xffffffff;
            if (glassFillColor != 0) {
                bgColor = glassFillColor;
            } else if (prefs.getBoolean("changecolor", false)) {
                int customBg = DesignUtils.getPrimarySurfaceColor();
                if (customBg != 0 && customBg != -1) {
                    bgColor = customBg;
                }
            }

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(radius);
            background.setColor(bgColor);
            background.setStroke(Math.max(1, (int) (0.6f * density)), isNight ? 0x18FFFFFF : 0x22000000);

            bar.setBackground(background);
            applyPillShadow(bar, density);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }

        int sideMargin = (int) (userSideMarginDp * density);
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
            mlp.leftMargin = sideMargin;
            mlp.rightMargin = sideMargin;
            bar.setLayoutParams(mlp);
        }

        if (pillDesignPro || pillDesignIos) {
            try {
                ClassLoader pluginLoader = FeatureLoader.getProClassLoader();
                if (pluginLoader != null) {
                    Class<?> pillProClass = Class.forName("com.waex.helper.PillDesignPro", true, pluginLoader);
                    String style = pillDesignIos ? "ios_glass" : "pro";
                    pillProClass.getMethod("applyProDesign", View.class, float.class, String.class).invoke(null, bar, density, style);
                }
            } catch (Throwable t) {
                XposedBridge.log("[WAEX-FBB] Failed to load PillDesignPro: " + t.getMessage());
            }
        }
    }

    private GradientDrawable createGlassShape(Context ctx, float density, boolean includeFill) {
        int userRadius = prefs.getInt("floating_bottom_bar_radius", userRadiusDp);
        float finalRadius = userRadius * density;
        GradientDrawable glassShape = new GradientDrawable();
        glassShape.setShape(GradientDrawable.RECTANGLE);
        glassShape.setCornerRadius(finalRadius);
        glassShape.setColor(includeFill ? getGlassOverlayColor(ctx) : 0x00000000);
        glassShape.setStroke(Math.max(1, (int) (0.6f * density)), getGlassStrokeColor(ctx));
        return glassShape;
    }

    private static int getGlassOverlayColor(Context ctx) {
        int alpha = Math.max(0, Math.min(255, Math.round((glassOpacity / 100f) * 255f)));
        int rgb = resolveGlassFillColor(ctx) & 0x00FFFFFF;
        return (alpha << 24) | rgb;
    }

    private static int resolveGlassFillColor(Context ctx) {
        if (glassFillColor != 0) {
            return glassFillColor;
        }
        return DesignUtils.isNightMode(ctx) ? 0xff1f2c34 : 0xffffffff;
    }

    private static int getGlassStrokeColor(Context ctx) {
        return DesignUtils.isNightMode(ctx) ? 0x22FFFFFF : 0x26000000;
    }

    private static void applyPillShadow(View view, float density) {
        view.setElevation(PILL_ELEVATION_DP * density);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setTranslationZ(PILL_TRANSLATION_Z_DP * density);
        }
    }

    private void positionFabAboveCurrentBar(View fab) {
        FrameLayout rootView = findRootView(fab);
        if (rootView == null) return;
        positionFabsAboveBar(rootView, null);
    }

    private static void positionFabsAboveBar(ViewGroup rootView, ViewGroup barContainer) {
        ViewGroup container = barContainer;
        if (container == null) {
            int navId = rootView.getContext().getResources().getIdentifier("bottom_nav", "id", rootView.getContext().getPackageName());
            if (navId <= 0) {
                navId = rootView.getContext().getResources().getIdentifier("navigation_bar", "id", rootView.getContext().getPackageName());
            }
            if (navId > 0) {
                View navView = rootView.findViewById(navId);
                if (navView != null && navView.getParent() instanceof ViewGroup) {
                    container = (ViewGroup) navView.getParent();
                }
            }
        }

        if (container == null) return;

        int barHeight = container.getHeight();
        if (barHeight <= 0) {
            final ViewGroup targetContainer = container;
            rootView.postDelayed(() -> positionFabsAboveBar(rootView, targetContainer), 100L);
            return;
        }

        int bottomMargin = 0;
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            bottomMargin = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        }

        float density = container.getContext().getResources().getDisplayMetrics().density;
        float totalOffset = -(barHeight + bottomMargin + (FAB_GAP_DP * density));

        for (String name : FAB_RESOURCE_NAMES) {
            int id = container.getContext().getResources().getIdentifier(name, "id", container.getContext().getPackageName());
            if (id <= 0) continue;
            View fab = rootView.findViewById(id);
            if (fab != null) {
                fab.setTranslationY(totalOffset);
                fab.bringToFront();
            }
        }
    }

    private static View findBottomNavForScrollable(View scrollable) {
        ViewGroup rootLayout = getRootLayout(scrollable);
        View bottomNav = findBottomNavInRoot(rootLayout);
        if (bottomNav != null) return bottomNav;

        View rootView = scrollable != null ? scrollable.getRootView() : null;
        if (rootView instanceof ViewGroup) {
            bottomNav = findBottomNavInRoot((ViewGroup) rootView);
            if (bottomNav != null) return bottomNav;
        }

        return null;
    }

    private static ViewGroup getRootLayout(View view) {
        View current = view;
        ViewGroup root = null;
        while (current != null) {
            if (current instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) current;
                String name = vg.getClass().getName();
                if (name.contains("CoordinatorLayout") ||
                        name.contains("ConstraintLayout") ||
                        name.contains("RelativeLayout") ||
                        vg.getId() == android.R.id.content ||
                        name.endsWith("DecorView")) {
                    root = vg;
                    if (vg.getId() == android.R.id.content || name.endsWith("DecorView")) {
                        break;
                    }
                }
            }
            ViewParent next = current.getParent();
            if (next instanceof View) {
                current = (View) next;
            } else {
                break;
            }
        }
        return root;
    }

    private static View findBottomNavInRoot(ViewGroup root) {
        if (root == null) return null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (styledBottomBars.containsKey(child)) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View nested = findBottomNavInRoot((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean isMainTabScrollable(View view) {
        if (view == null) return false;
        if (!isScrollableClass(view.getClass())) return false;
        if (isInsideConversation(view)) return false;

        boolean isDescendant = isDescendantOfTabsPager(view);
        boolean isLarge = isLargeVerticalScrollable(view);

        if (isDescendant && isLarge) {
            return true;
        }

        try {
            if (view.getId() != View.NO_ID) {
                String entryName = view.getResources().getResourceEntryName(view.getId());
                if ("list".equalsIgnoreCase(entryName) && isLarge) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        String className = view.getClass().getName();
        return (className.contains("WDSList") || className.contains("ObservableRecyclerView") || className.contains("CallsHistory")) && isLarge;
    }

    private static boolean isScrollableClass(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            String name = clazz.getName();
            if (name.equals("androidx.recyclerview.widget.RecyclerView") ||
                    name.equals("android.widget.ScrollView") ||
                    name.contains("RecyclerView") ||
                    name.contains("ScrollView") ||
                    name.equals("android.widget.AbsListView") ||
                    name.contains("ListView")) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private static boolean isInsideConversation(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof View) {
                View v = (View) parent;
                if (v.getId() != View.NO_ID) {
                    try {
                        String entryName = v.getResources().getResourceEntryName(v.getId());
                        if ("conversation_view_host".equals(entryName)) {
                            return true;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (parent instanceof View) {
                parent = ((View) parent).getParent();
            } else {
                break;
            }
        }
        return false;
    }

    private static boolean isDescendantOfTabsPager(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            String name = parent.getClass().getName();
            if (name.contains("TabsPager") || name.toLowerCase().contains("pager") || name.equals("androidx.viewpager.widget.ViewPager")) {
                return true;
            }
            if (parent instanceof View) {
                parent = ((View) parent).getParent();
            } else {
                break;
            }
        }
        return false;
    }

    private static boolean isLargeVerticalScrollable(View view) {
        int height = view.getHeight();
        int width = view.getWidth();
        float density = view.getContext().getResources().getDisplayMetrics().density;
        if (height > 0 && width > 0) {
            return height >= (int) (250 * density);
        }
        return true;
    }

    private static float getPrefFloat(SharedPreferences prefs, String key, float defaultValue) {
        try {
            return prefs.getFloat(key, defaultValue);
        } catch (Throwable ignored) {
            try {
                return (float) prefs.getInt(key, (int) defaultValue);
            } catch (Throwable ignoredToo) {
                return defaultValue;
            }
        }
    }

    private static int getPrefColor(SharedPreferences prefs, String key, int defaultValue) {
        try {
            if (!prefs.contains(key)) return defaultValue;
            return prefs.getInt(key, defaultValue);
        } catch (Throwable ignored) {
            try {
                String value = prefs.getString(key, null);
                return value != null ? Color.parseColor(value) : defaultValue;
            } catch (Throwable ignoredToo) {
                return defaultValue;
            }
        }
    }
}