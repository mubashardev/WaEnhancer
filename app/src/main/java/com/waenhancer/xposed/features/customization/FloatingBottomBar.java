package com.waenhancer.xposed.features.customization;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.WeakHashMap;

import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.waenhancer.xposed.utils.ProHelper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FloatingBottomBar extends Feature {

    private static final int PILL_SIDE_MARGIN_DP = 16;
    private static final int PILL_BOTTOM_MARGIN_DP = 22;
    private static final int SCROLL_BOTTOM_PADDING_DP = 126;
    private static final int FAB_VISIBLE_OFFSET_DP = 80;
    private static final float PILL_ELEVATION_DP = 12f;
    private static final float PILL_TRANSLATION_Z_DP = 8f;
    private static final WeakHashMap<View, Boolean> styledBottomBars = new WeakHashMap<>();
    private static final Set<Class<?>> hookedItemClasses = new HashSet<>();
    private static final WeakHashMap<View, Boolean> registeredScrollListeners = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> targetTranslations = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> originalBottomPaddings = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> fabListeners = new WeakHashMap<>();
    private static final WeakHashMap<View, FrameLayout> glassHosts = new WeakHashMap<>();
    private static final WeakHashMap<View, BlurView> glassBlurViews = new WeakHashMap<>();
    private static boolean scrollHideEnabled = true;
    private static boolean glassEnabled = false;
    private static float glassOpacity = 35f;
    private static int glassFillColor = 0;
    private static boolean pillDesignPro = true;
    private static int userBottomMarginDp = 22;
    private static int userSideMarginDp = 16;
    private static int userFabOffsetDp = 80;
    private static int userIconSizeDp = 24;
    private static int userTextSizeSp = 12;
    private static int userVerticalPaddingDp = 6;

    public FloatingBottomBar(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("floating_bottom_bar", false)) return;

        scrollHideEnabled = prefs.getBoolean("floating_bottom_bar_scroll_hide", true);
        glassEnabled = prefs.getBoolean("floating_bottom_bar_glass", true);
        glassOpacity = getPrefFloat(prefs, "floating_bottom_bar_glass_opacity", 35f);
        glassFillColor = getPrefColor(prefs, "floating_bottom_bar_fill_color", 0);
        // Read pref — default to "regular" so new installs/free users get Classic
        boolean prefWantsPro = "pro".equals(prefs.getString("floating_bottom_bar_pill_design", "regular"));
        // Runtime Pro gate: override to false if the license is not active regardless of saved pref
        pillDesignPro = prefWantsPro && ProHelper.isPillDesignProEnabled();
        userBottomMarginDp = prefs.getInt("floating_bottom_bar_margin_bottom", 22);
        userSideMarginDp = prefs.getInt("floating_bottom_bar_margin_horizontal", 16);
        userFabOffsetDp = prefs.getInt("floating_bottom_bar_fab_offset", 80);
        userIconSizeDp = prefs.getInt("floating_bottom_bar_icon_size", 24);
        userTextSizeSp = prefs.getInt("floating_bottom_bar_text_size", 12);
        userVerticalPaddingDp = prefs.getInt("floating_bottom_bar_padding_vertical", 6);


        // Hook the tab frame container
        Class<?> loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        XposedBridge.hookAllMethods(loadTabFrameClass, "setVisibility", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    View view = (View) param.thisObject;
                    if (view == null) return;
                    int visibility = (int) param.args[0];
                    View animTarget = getBarAnimationTarget(view);
                    if (animTarget != view && animTarget.getVisibility() != visibility) {
                        animTarget.setVisibility(visibility);
                    }
                } catch (Throwable ignored) {}
            }
        });

        XposedBridge.hookAllMethods(loadTabFrameClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final View view = (View) param.thisObject;
                if (view == null) return;

                view.post(() -> {
                    try {
                        // Prevent duplicate initializations
                        if (styledBottomBars.containsKey(view)) return;
                        styledBottomBars.put(view, true);

                        // Hook the click listener on the BottomNavigationItemView class dynamically
                        ViewGroup menuView = findMenuView(view);
                        if (menuView != null && menuView.getChildCount() > 0) {
                            View child = menuView.getChildAt(0);
                            Class<?> itemViewClass = child.getClass();
                            
                            // Wrap existing listeners immediately
                            for (int i = 0; i < menuView.getChildCount(); i++) {
                                wrapOnClickListener(view, menuView.getChildAt(i));
                            }

                            synchronized (hookedItemClasses) {
                                if (!hookedItemClasses.contains(itemViewClass)) {
                                    hookedItemClasses.add(itemViewClass);
                                    XposedBridge.hookAllMethods(itemViewClass, "setOnClickListener", new XC_MethodHook() {
                                        @Override
                                        protected void beforeHookedMethod(MethodHookParam param2) throws Throwable {
                                            try {
                                                final View.OnClickListener originalListener = (View.OnClickListener) param2.args[0];
                                                if (originalListener == null) return;
                                                if (originalListener.getClass().getName().contains("FloatingBottomBar")) return;

                                                View itemView = (View) param2.thisObject;
                                                View.OnClickListener proxyListener = new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        try {
                                                            int tabId = v.getId();
                                                            View parentBottomNav = findBottomNavForView(v);
                                                            if (parentBottomNav != null) {
                                                                handleTabSelectionChanged(parentBottomNav, tabId);
                                                            }
                                                        } catch (Throwable t) {
                                                            XposedBridge.log("[WAEX-FBB] Error in proxy onClick: " + t);
                                                        }
                                                        originalListener.onClick(v);
                                                    }
                                                };
                                                param2.args[0] = proxyListener;
                                            } catch (Throwable t) {
                                                XposedBridge.log("[WAEX-FBB] Error wrapping setOnClickListener: " + t);
                                            }
                                        }
                                    });
                                }
                            }
                        }

                        float density = view.getContext().getResources().getDisplayMetrics().density;
                        int userRadius = prefs.getInt("floating_bottom_bar_radius", 28);
                        float finalRadius = userRadius * density;

                        // Create rounded shape background matching the theme surface/card color
                        GradientDrawable shape = new GradientDrawable();
                        shape.setShape(GradientDrawable.RECTANGLE);
                        shape.setCornerRadius(finalRadius);

                        boolean isNight = DesignUtils.isNightMode(view.getContext());
                        int bgColor = isNight ? 0xff1f2c34 : 0xffffffff;
                        if (glassFillColor != 0) {
                            bgColor = glassFillColor;
                        } else if (prefs.getBoolean("changecolor", false)) {
                            int customBg = DesignUtils.getPrimarySurfaceColor();
                            if (customBg != 0 && customBg != -1) {
                                  bgColor = customBg;
                            }
                        }
                        
                        shape.setColor(bgColor);
                        // Subtle premium stroke matching host app's design
                        shape.setStroke(Math.max(1, (int) (0.6f * density)), isNight ? 0x18FFFFFF : 0x22000000);

                        // Prevent system tints from overriding our custom background shape
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            view.setBackgroundTintList(null);
                        }

                        if (view instanceof ViewGroup) {
                            ((ViewGroup) view).setClipChildren(false);
                            ((ViewGroup) view).setClipToPadding(false);
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            view.setClipToOutline(false);
                        }

                        if (glassEnabled) {
                            applyGlassmorphism(view, density);
                        } else {
                            view.setBackground(shape);
                            applyPillShadow(view, density);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                            }
                        }

                        // Clear backgrounds of immediate children to prevent solid white rectangular overlays
                        makeChildrenTransparent(view);

                        if (pillDesignPro) {
                            try {
                                ClassLoader pluginLoader = (ClassLoader) System.getProperties().get("com.waex.helper.classloader");
                                if (pluginLoader != null) {
                                    Class<?> pillProClass = Class.forName("com.waex.helper.PillDesignPro", true, pluginLoader);
                                    pillProClass.getMethod("applyProDesign", View.class, float.class).invoke(null, view, density);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("Failed to load PillDesignPro: " + t.getMessage());
                            }
                        }

                        // Adjust padding dynamically to center items within floating pill
                        int paddingVertical = (int) (userVerticalPaddingDp * density);
                        view.setPadding(view.getPaddingLeft(), paddingVertical, view.getPaddingRight(), paddingVertical);

                        // Attach LayoutChangeListener to enforce margins, overriding parent-forced layout passes
                        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                            private boolean isUpdating = false;

                            @Override
                            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                if (isUpdating) return;
                                isUpdating = true;
                                try {
                                    if (pillDesignPro && v.getMinimumHeight() != 0) {
                                        v.setMinimumHeight(0);
                                    }
                                    View targetLayoutView = v;
                                    FrameLayout host = glassHosts.get(v);
                                    if (host != null) {
                                        targetLayoutView = host;
                                    }
                                    ViewGroup.LayoutParams lp = targetLayoutView.getLayoutParams();
                                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                        int marginSide = dp(density, userSideMarginDp);
                                        
                                        int systemInsetBottom = getSystemInsetBottomSafely(v);
                                        int marginBottom = dp(density, userBottomMarginDp) + systemInsetBottom;
                                        
                                        int targetHeight = (int) (64 * density);
                                        if (mlp.leftMargin != marginSide || mlp.rightMargin != marginSide || mlp.bottomMargin != marginBottom || mlp.height != targetHeight) {
                                            mlp.leftMargin = marginSide;
                                            mlp.rightMargin = marginSide;
                                            mlp.bottomMargin = marginBottom;
                                            mlp.height = targetHeight;
                                            targetLayoutView.setLayoutParams(mlp);
                                        }
                                        applyCustomSizesAndPaddings(findMenuView(v), density);
                                    }
                                } finally {
                                    isUpdating = false;
                                }
                            }
                        });

                        // Initial layout params update to force immediate layout
                        View targetLayoutView = view;
                        FrameLayout host = glassHosts.get(view);
                        if (host != null) {
                            targetLayoutView = host;
                        }
                        ViewGroup.LayoutParams lp = targetLayoutView.getLayoutParams();
                        if (lp instanceof ViewGroup.MarginLayoutParams) {
                            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                            int marginSide = dp(density, userSideMarginDp);
                            int systemInsetBottom = getSystemInsetBottomSafely(view);
                            int marginBottom = dp(density, userBottomMarginDp) + systemInsetBottom;
                            int targetHeight = (int) (64 * density);
                            mlp.leftMargin = marginSide;
                            mlp.rightMargin = marginSide;
                            mlp.bottomMargin = marginBottom;
                            mlp.height = targetHeight;
                            targetLayoutView.setLayoutParams(mlp);
                        }

                        applyCustomSizesAndPaddings(findMenuView(view), density);

                        // Make layouts overlap and hide adjacent divider lines
                        adjustLayoutOverlap(view, density);

                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                });
            }
        });

        // Hook RecyclerView's onAttachedToWindow to dynamically hook scroll events/padding on pages
        try {
            Class<?> recyclerViewClass = XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView", classLoader);
            
            XposedBridge.hookAllMethods(recyclerViewClass, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final View rv = (View) param.thisObject;
                    if (rv == null) return;
                    rv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                        private boolean hasSetPadding = false;
                        @Override
                        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
                            try {
                                if (hasSetPadding) return;
                                if (v.getHeight() > 0 && v.getWidth() > 0) {
                                    if (isMainTabScrollable(v)) {
                                        View bottomNav = findBottomNavForScrollable(v);
                                        if (bottomNav != null) {
                                            float density = v.getContext().getResources().getDisplayMetrics().density;
                                            int paddingBottom = dp(density, SCROLL_BOTTOM_PADDING_DP);
                                            prepareScrollableBottomPadding(v, paddingBottom);
                                            hasSetPadding = true;
                                        }
                                    } else {
                                        restoreOriginalBottomPadding(v);
                                        hasSetPadding = true;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            });

            // Hook dispatchOnScrolled candidate methods in RecyclerView
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
            XposedBridge.log(t);
        }

        // Hook WDSFab constructors to add attach state change and layout change listeners.
        // This guarantees shifting the FABs up by 80dp to float cleanly above the bottom bar,
        // and overrides CoordinatorLayout behaviors that try to reset the translation.
        try {
            Class<?> wdsFabClass = XposedHelpers.findClass("com.whatsapp.ui.wds.components.fab.WDSFab", classLoader);
            XposedBridge.hookAllConstructors(wdsFabClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final View fab = (View) param.thisObject;
                    if (fab == null) return;
                    fab.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            if (fabListeners.containsKey(v)) return;
                            fabListeners.put(v, true);
                            v.post(() -> {
                                try {
                                    float density = v.getContext().getResources().getDisplayMetrics().density;
                                    ViewGroup rootLayout = getRootLayout(v);
                                    v.setTranslationY(getFabVisibleTranslation(density));
                                    
                                    v.setElevation(20 * density);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        v.setTranslationZ(12 * density);
                                    }
                                    
                                    // Add layout listener to keep translation and elevation on layout passes
                                    v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                        @Override
                                        public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                            try {
                                                float d = view.getContext().getResources().getDisplayMetrics().density;
                                                view.setTranslationY(getFabVisibleTranslation(d));
                                                
                                                view.setElevation(20 * d);
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                    view.setTranslationZ(12 * d);
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                    });
                                } catch (Throwable ignored) {}
                            });
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {}
                    });
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        // Hook ViewPager page dispatch events directly to intercept page changes
        try {
            Class<?> viewPagerClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", classLoader);
            XposedBridge.hookAllMethods(viewPagerClass, "dispatchOnPageSelected", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        int position = (int) param.args[0];
                        View viewPager = (View) param.thisObject;
                        ViewGroup root = getRootLayout(viewPager);
                        View bottomNav = findBottomNavInRoot(root);
                        if (bottomNav != null) {
                            int tabId = getTabIdForIndex(bottomNav, position);
                            handleTabSelectionChanged(bottomNav, tabId);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[WAEX-FBB] Error in dispatchOnPageSelected: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error hooking dispatchOnPageSelected: " + t);
        }

        // Hook tab selection calls on BottomNavigationView directly to intercept clicks and manual selections
        try {
            XposedBridge.hookAllMethods(loadTabFrameClass, "setSelectedItemId", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        int tabId = (int) param.args[0];
                        View bottomNav = (View) param.thisObject;
                        handleTabSelectionChanged(bottomNav, tabId);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error hooking setSelectedItemId: " + t);
        }

        // Hook the tab item selection listener of BottomNavigationView to capture tab taps/clicks
        try {
            Method targetSetListenerMethod = null;
            for (Method m : loadTabFrameClass.getMethods()) {
                String name = m.getName();
                if ((name.equals("setOnItemSelectedListener") || name.equals("setOnNavigationItemSelectedListener")) 
                    && m.getParameterCount() == 1) {
                    targetSetListenerMethod = m;
                    break;
                }
            }

            if (targetSetListenerMethod != null) {
                final Class<?> listenerInterface = targetSetListenerMethod.getParameterTypes()[0];
                
                XposedBridge.hookMethod(targetSetListenerMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final Object originalListener = param.args[0];
                            if (originalListener == null) return;
                            if (Proxy.isProxyClass(originalListener.getClass())) return;
                            
                            final View bottomNav = (View) param.thisObject;
                            
                            Object listenerProxy = Proxy.newProxyInstance(
                                listenerInterface.getClassLoader(),
                                new Class<?>[]{listenerInterface},
                                new InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                        String methodName = method.getName();
                                        if ("equals".equals(methodName)) {
                                            return proxy == args[0];
                                        }
                                        if ("hashCode".equals(methodName)) {
                                            return System.identityHashCode(proxy);
                                        }
                                        if ("toString".equals(methodName)) {
                                            return "NavigationBarOnItemSelectedListenerProxy@" + Integer.toHexString(System.identityHashCode(proxy));
                                        }
                                        
                                        if (args != null && args.length == 1 && args[0] instanceof MenuItem) {
                                            MenuItem item = (MenuItem) args[0];
                                            int tabId = item.getItemId();
                                            handleTabSelectionChanged(bottomNav, tabId);
                                        }
                                        
                                        return method.invoke(originalListener, args);
                                    }
                                }
                            );
                            
                            param.args[0] = listenerProxy;
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX-FBB] Error wrapping tab selection listener: " + t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error hooking item selection listener: " + t);
        }
    }

    private void makeChildrenTransparent(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setBackground(null);
                child.setBackgroundColor(0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    child.setBackgroundTintList(null);
                }
                
                // Clear background drawables recursively for intermediate layouts/wrappers
                String className = child.getClass().getName();
                if (child instanceof ViewGroup && (className.contains("Frame") || className.contains("Linear") || className.contains("Relative") || className.contains("Menu"))) {
                    makeChildrenTransparent(child);
                }
            }
        }
    }

    private void adjustLayoutOverlap(final View bottomNav, final float density) {
        try {
            final ViewParent parent = bottomNav.getParent();
            if (!(parent instanceof ViewGroup)) return;
            final ViewGroup parentGroup = (ViewGroup) parent;

            makeContainerTransparent(parentGroup);

            // Walk up the hierarchy to find the root layout container (preferring the nested FrameLayout)
            ViewGroup root = null;
            if (parentGroup != null) {
                ViewParent grandparent = parentGroup.getParent();
                if (grandparent instanceof ViewGroup) {
                    ViewGroup conversationListViewHost = (ViewGroup) grandparent;
                    View nestedContent = conversationListViewHost.findViewById(android.R.id.content);
                    if (nestedContent instanceof ViewGroup) {
                        root = (ViewGroup) nestedContent;
                    }
                }
            }
            if (root == null) {
                root = getRootLayout(bottomNav);
            }
            if (root == null) {
                root = parentGroup; // Fallback
            }
            final ViewGroup rootLayout = root;

            // Find the ViewPager or ViewPager2 sibling/ancestor in rootLayout
            final View viewPager = findViewPager(rootLayout);
            if (viewPager == null) {
                return;
            }

            // 1. Hide dividers in the original parent before we move bottomNav
            hideAdjacentDividers(bottomNav, parentGroup, density);

            // 2. Ensure viewPager bottom margin is 0
            viewPager.post(() -> {
                try {
                    ViewGroup.LayoutParams lp = viewPager.getLayoutParams();
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                        if (mlp.bottomMargin != 0) {
                            mlp.bottomMargin = 0;
                            viewPager.setLayoutParams(mlp);
                        }
                    }
                } catch (Throwable ignored) {}
            });

            // 3. Move bottomNav out of WhatsApp's full-width bottom_nav_container.
            // That container owns the solid rectangle; the pill itself must be the overlay.
            parentGroup.post(() -> {
                try {
                    ViewGroup targetRoot = findBottomOverlayRoot(bottomNav, parentGroup, rootLayout);
                    if (targetRoot == null) targetRoot = rootLayout;
                    if (targetRoot == null) return;
                    ViewParent currentParent = bottomNav.getParent();
                    if (currentParent instanceof ViewGroup) {
                        ((ViewGroup) currentParent).removeView(bottomNav);
                        if (currentParent != targetRoot) {
                            collapseOldBottomNavContainer((ViewGroup) currentParent);
                        }
                    }

                    int marginSide = dp(density, userSideMarginDp);
                    int systemInsetBottom = 0;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        WindowInsets insets = bottomNav.getRootWindowInsets();
                        if (insets != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                systemInsetBottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
                            } else {
                                systemInsetBottom = insets.getSystemWindowInsetBottom();
                            }
                        }
                    }
                    int marginBottom = dp(density, userBottomMarginDp) + systemInsetBottom;
                    ViewGroup.LayoutParams newLp = null;

                        if (targetRoot instanceof FrameLayout) {
                            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                            flp.gravity = Gravity.BOTTOM;
                            newLp = flp;
                        } else if (targetRoot.getClass().getName().contains("CoordinatorLayout")) {
                            try {
                                Class<?> clLpClass = Class.forName("androidx.coordinatorlayout.widget.CoordinatorLayout$LayoutParams");
                                Constructor<?> ctor = clLpClass.getConstructor(int.class, int.class);
                                Object clLp = ctor.newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                                Field gravityField = clLpClass.getField("gravity");
                                gravityField.set(clLp, Gravity.BOTTOM);
                                newLp = (ViewGroup.LayoutParams) clLp;
                            } catch (Throwable ignored) {}
                        } else if (targetRoot instanceof RelativeLayout) {
                            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                            rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                            newLp = rlp;
                        } else if (targetRoot.getClass().getName().contains("ConstraintLayout")) {
                            try {
                                Class<?> clLpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams");
                                Constructor<?> ctor = clLpClass.getConstructor(int.class, int.class);
                                Object clLp = ctor.newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                                Field bottomToBottomField = clLpClass.getField("bottomToBottom");
                                bottomToBottomField.set(clLp, 0); // 0 is PARENT_ID
                                Field leftToLeftField = clLpClass.getField("leftToLeft");
                                leftToLeftField.set(clLp, 0);
                                Field rightToRightField = clLpClass.getField("rightToRight");
                                rightToRightField.set(clLp, 0);
                                newLp = (ViewGroup.LayoutParams) clLp;
                            } catch (Throwable ignored) {}
                        }

                        if (newLp == null) {
                            newLp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        }

                        if (newLp instanceof ViewGroup.MarginLayoutParams) {
                            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) newLp;
                            int targetHeight = (int) (64 * density);
                            mlp.leftMargin = marginSide;
                            mlp.rightMargin = marginSide;
                            mlp.bottomMargin = marginBottom;
                            mlp.height = targetHeight;
                        }

                    View barOverlay;
                    if (glassEnabled) {
                        barOverlay = installGlassHost(targetRoot, rootLayout, bottomNav, newLp, density);
                    } else {
                        targetRoot.addView(bottomNav, newLp);
                        barOverlay = bottomNav;
                    }

                    targetRoot.setClipChildren(false);
                    targetRoot.setClipToPadding(false);
                    ViewParent gp = targetRoot.getParent();
                    if (gp instanceof ViewGroup) {
                        ((ViewGroup) gp).setClipChildren(false);
                        ((ViewGroup) gp).setClipToPadding(false);
                    }

                    barOverlay.bringToFront();
                    applyPillShadow(barOverlay, density);
                    bottomNav.bringToFront();

                    final View animTarget = getBarAnimationTarget(bottomNav);
                    final ViewGroup finalParentGroup = parentGroup;
                    bottomNav.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            try {
                                int targetVisibility = View.GONE;
                                if (bottomNav.getVisibility() == View.VISIBLE && finalParentGroup.isShown()) {
                                    targetVisibility = View.VISIBLE;
                                    View convHost = findConversationViewHost(bottomNav);
                                    if (convHost != null && convHost.isShown()) {
                                        targetVisibility = View.GONE;
                                    }
                                }
                                if (animTarget.getVisibility() != targetVisibility) {
                                    animTarget.setVisibility(targetVisibility);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            });

            // 4. Update inner scroll paddings and add scroll-to-hide listeners
            viewPager.post(() -> {
                try {
                    int paddingBottom = dp(density, SCROLL_BOTTOM_PADDING_DP);
                    setBottomPaddingAndScrollListeners(viewPager, paddingBottom, bottomNav);
                    
                    int currentItem = (int) XposedHelpers.callMethod(viewPager, "getCurrentItem");
                    int tabId = getTabIdForIndex(bottomNav, currentItem);
                    if (tabId == 1000 || tabId == 1100) {
                        handleTabSelectionChanged(bottomNav, tabId);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            });

            // 5. Adjust initial position of FABs to float above the pill
            rootLayout.postDelayed(() -> {
                try {
                    List<View> fabs = findFabs(rootLayout);
                    for (View fab : fabs) {
                        fab.setTranslationY(getFabVisibleTranslation(density));
                    }
                } catch (Throwable ignored) {}
            }, 500);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static View findViewPager(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            String name = child.getClass().getName();
            // Match custom TabsPager or traditional ViewPager
            if (name.toLowerCase().contains("pager") || name.equals("androidx.viewpager.widget.ViewPager")) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View vp = findViewPager((ViewGroup) child);
                if (vp != null) return vp;
            }
        }
        return null;
    }

    private void setBottomPaddingAndScrollListeners(View view, int paddingBottom, final View bottomNav) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (isScrollableClass(child.getClass())) {
                    final View scrollView = child;
                    scrollView.post(() -> {
                        try {
                            if (!isMainTabScrollable(scrollView)) {
                                restoreOriginalBottomPadding(scrollView);
                                return;
                            }
                            prepareScrollableBottomPadding(scrollView, paddingBottom);

                            String scrollClassName = scrollView.getClass().getName();
                            if (scrollClassName.contains("ScrollView") && scrollView instanceof ViewGroup) {
                                ViewGroup vg = (ViewGroup) scrollView;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    if (!registeredScrollListeners.containsKey(vg)) {
                                        vg.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                                            onViewScrolled(bottomNav, scrollY - oldScrollY);
                                        });
                                        registeredScrollListeners.put(vg, true);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    });
                } else if (child instanceof ViewGroup) {
                    setBottomPaddingAndScrollListeners(child, paddingBottom, bottomNav);
                }
            }
        }
    }

    private static void prepareScrollableBottomPadding(View scrollView, int paddingBottom) {
        if (!originalBottomPaddings.containsKey(scrollView)) {
            originalBottomPaddings.put(scrollView, scrollView.getPaddingBottom());
        }
        scrollView.setPadding(
            scrollView.getPaddingLeft(),
            scrollView.getPaddingTop(),
            scrollView.getPaddingRight(),
            paddingBottom
        );
        if (scrollView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) scrollView;
            vg.setClipToPadding(false);
        }
    }

    private static void restoreOriginalBottomPadding(View scrollView) {
        Integer originalBottom = originalBottomPaddings.get(scrollView);
        if (originalBottom == null) return;
        if (scrollView.getPaddingBottom() == originalBottom) return;
        scrollView.setPadding(
            scrollView.getPaddingLeft(),
            scrollView.getPaddingTop(),
            scrollView.getPaddingRight(),
            originalBottom
        );
    }

    private static void handleRecyclerViewScrolled(View rv, int dy) {
        try {
            if (Math.abs(dy) > 50000) return; // Ignore layout measureSpec triggers (e.g. 1073743008)
            if (Math.abs(dy) < 5) return;
            if (!rv.isShown()) return; // Ignore background scrolls
            if (!isMainTabScrollable(rv)) return;

            View bottomNav = findBottomNavForScrollable(rv);
            if (bottomNav == null) return;
            onViewScrolled(bottomNav, dy);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void onViewScrolled(View bottomNav, int dy) {
        if (bottomNav == null) return;
        if (isMetaAiTabActive(bottomNav)) return;
        
        View barTarget = getBarAnimationTarget(bottomNav);
        if (!barTarget.isShown()) return; // Do not animate if bottom bar is hidden on screen
        
        float density = bottomNav.getContext().getResources().getDisplayMetrics().density;
        
        // Use cached preference to prevent disk I/O in hot scroll path
        if (!scrollHideEnabled) {
            Float lastTarget = targetTranslations.get(barTarget);
            if (lastTarget == null || lastTarget != 0f) {
                targetTranslations.put(barTarget, 0f);
                barTarget.animate().translationY(0).setDuration(200).start();
                animateFabs(bottomNav, false, density);
            }
            return;
        }

        if (Math.abs(dy) < 5) return; // Skip minor/jitter scroll actions

        float targetTranslationY;
        int height = barTarget.getHeight();
        if (height <= 0) {
            height = bottomNav.getHeight();
        }
        if (height <= 0) {
            height = (int) (80 * density);
        }

        if (dy > 0) {
            targetTranslationY = height + (24 * density);
        } else {
            targetTranslationY = 0;
        }

        Float lastTarget = targetTranslations.get(barTarget);
        if (lastTarget != null && lastTarget == targetTranslationY) {
            // Already animating to this target, avoid thrashing
            return;
        }
        targetTranslations.put(barTarget, targetTranslationY);

        barTarget.animate()
            .translationY(targetTranslationY)
            .setDuration(250)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
        // Animate FABs in sync
        animateFabs(bottomNav, dy > 0, density);
    }

    private static void animateFabs(View bottomNav, boolean hide, float density) {
        try {
            ViewGroup root = getRootLayout(bottomNav);
            if (root == null) return;
            
            List<View> fabs = findFabs(root);
            for (View fab : fabs) {
                float targetTranslationY = hide ? 0f : getFabVisibleTranslation(density);
                Float lastFabTarget = targetTranslations.get(fab);
                if (lastFabTarget != null && lastFabTarget == targetTranslationY) {
                    continue;
                }
                targetTranslations.put(fab, targetTranslationY);
                fab.animate()
                    .translationY(targetTranslationY)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            }
        } catch (Throwable ignored) {}
    }

    private static List<View> findFabs(ViewGroup root) {
        List<View> fabs = new ArrayList<>();
        findFabsRecursive(root, fabs);
        return fabs;
    }

    private static void findFabsRecursive(View view, List<View> fabs) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                String name = child.getClass().getName();
                if (name.contains("WDSFab") || name.contains("FloatingActionButton")) {
                    fabs.add(child);
                } else if (child instanceof ViewGroup) {
                    findFabsRecursive(child, fabs);
                }
            }
        }
    }

    private void hideAdjacentDividers(View bottomNav, ViewGroup originalParent, float density) {
        try {
            // Hide dividers inside the tab frame container itself
            if (bottomNav instanceof ViewGroup) {
                ViewGroup container = (ViewGroup) bottomNav;
                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    int height = child.getHeight() > 0 ? child.getHeight() : (child.getLayoutParams() != null ? child.getLayoutParams().height : 0);
                    if (height > 0 && height <= (int) (4 * density)) {
                        child.setVisibility(View.GONE);
                    }
                }
            }
            
            // Hide dividers inside the original parent layout
            if (originalParent != null) {
                originalParent.setBackground(null);
                originalParent.setBackgroundColor(0);
                
                for (int i = 0; i < originalParent.getChildCount(); i++) {
                    View child = originalParent.getChildAt(i);
                    if (child != bottomNav) {
                        int height = child.getHeight() > 0 ? child.getHeight() : (child.getLayoutParams() != null ? child.getLayoutParams().height : 0);
                        if (height > 0 && height <= (int) (4 * density)) {
                            child.setVisibility(View.GONE);
                        }
                    }
                }
                
                // Hide dividers inside the original grandparent layout
                ViewParent grandparent = originalParent.getParent();
                if (grandparent instanceof ViewGroup) {
                    ViewGroup grandGroup = (ViewGroup) grandparent;
                    for (int i = 0; i < grandGroup.getChildCount(); i++) {
                        View child = grandGroup.getChildAt(i);
                        if (child != originalParent) {
                            int height = child.getHeight() > 0 ? child.getHeight() : (child.getLayoutParams() != null ? child.getLayoutParams().height : 0);
                            if (height > 0 && height <= (int) (4 * density)) {
                                child.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
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

    private static boolean isMainTabScrollable(View view) {
        if (view == null) return false;

        if (!isScrollableClass(view.getClass())) return false;
        if (isInsideConversation(view)) return false;

        boolean isDescendant = isDescendantOfTabsPager(view);
        boolean isLarge = isLargeVerticalScrollable(view);
        
        // If it's a descendant of TabsPager and it is large vertical scrollable, it is a main tab list!
        if (isDescendant && isLarge) {
            return true;
        }

        // Fallback checks
        try {
            if (view.getId() != View.NO_ID) {
                String entryName = view.getResources().getResourceEntryName(view.getId());
                if ("list".equalsIgnoreCase(entryName) && isLarge) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        String className = view.getClass().getName();
        if ((className.contains("WDSList") || className.contains("ObservableRecyclerView") || className.contains("CallsHistory")) && isLarge) {
            return true;
        }

        return false;
    }

    private static boolean isLargeVerticalScrollable(View view) {
        int height = view.getHeight();
        int width = view.getWidth();
        float density = view.getContext().getResources().getDisplayMetrics().density;

        // If view is already measured, use its measured size
        if (height > 0 && width > 0) {
            return height >= dp(density, 280) && width >= dp(density, 240);
        }

        // If not measured yet (e.g. during onAttachedToWindow), check layout parameters
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            boolean heightOk = (lp.height == ViewGroup.LayoutParams.MATCH_PARENT || 
                               lp.height >= dp(density, 280));
            boolean widthOk = (lp.width == ViewGroup.LayoutParams.MATCH_PARENT || 
                              lp.width >= dp(density, 240));
            return heightOk && widthOk;
        }

        return false;
    }

    private static int dp(float density, int value) {
        return (int) (value * density + 0.5f);
    }

    private static float getFabVisibleTranslation(float density) {
        return -dp(density, userFabOffsetDp);
    }

    private static View getBarAnimationTarget(View bottomNav) {
        FrameLayout host = glassHosts.get(bottomNav);
        return host != null ? host : bottomNav;
    }

    private static void makeContainerTransparent(ViewGroup group) {
        if (group == null) return;
        group.setBackground(null);
        group.setBackgroundColor(0);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            group.setBackgroundTintList(null);
        }
    }

    private static void collapseOldBottomNavContainer(ViewGroup group) {
        makeContainerTransparent(group);
        group.setPadding(0, 0, 0, 0);
        group.setMinimumHeight(0);
        ViewGroup.LayoutParams lp = group.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            group.setLayoutParams(lp);
        }
    }

    private static ViewGroup findBottomOverlayRoot(View bottomNav, ViewGroup parentGroup, ViewGroup fallbackRoot) {
        ViewParent parent = parentGroup.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup directParent = (ViewGroup) parent;
            if (!(directParent instanceof LinearLayout)) {
                return directParent;
            }
        }
        ViewGroup root = getRootLayout(bottomNav);
        return root != null ? root : fallbackRoot;
    }

    private View installGlassHost(ViewGroup targetRoot, ViewGroup blurRoot, View bottomNav,
                                  ViewGroup.LayoutParams hostLp, float density) {
        try {
            removeGlassHost(bottomNav);

            Context ctx = bottomNav.getContext();
            FrameLayout host = new FrameLayout(ctx);
            host.setClipChildren(false);
            host.setClipToPadding(false);
            host.setBackground(createGlassOutlineShape(ctx, density));
            applyPillShadow(host, density);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                host.setClipToOutline(false);
                host.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }

            BlurView blurView = new BlurView(ctx);
            blurView.setBackground(createGlassShape(ctx, density, true));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                blurView.setClipToOutline(true);
                blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }

            FrameLayout.LayoutParams blurLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            host.addView(blurView, blurLp);

            bottomNav.setBackground(createGlassShape(ctx, density, false));
            if (bottomNav instanceof ViewGroup) {
                ((ViewGroup) bottomNav).setClipChildren(false);
                ((ViewGroup) bottomNav).setClipToPadding(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bottomNav.setBackgroundTintList(null);
                bottomNav.setClipToOutline(false);
                bottomNav.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }

            FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (64 * density),
                    Gravity.BOTTOM
            );
            glassHosts.put(bottomNav, host);
            glassBlurViews.put(bottomNav, blurView);
            host.addView(bottomNav, navLp);

            setupBlurView(blurView, blurRoot != null ? blurRoot : targetRoot, bottomNav);
            targetRoot.addView(host, hostLp);
            return host;
        } catch (Throwable t) {
            XposedBridge.log(t);
            glassHosts.remove(bottomNav);
            glassBlurViews.remove(bottomNav);
            targetRoot.addView(bottomNav, hostLp);
            bottomNav.setBackground(createGlassShape(bottomNav.getContext(), density, true));
            return bottomNav;
        }
    }

    private void setupBlurView(BlurView blurView, ViewGroup blurRoot, View bottomNav) {
        try {
            Context ctx = bottomNav.getContext();
            BlurAlgorithm algorithm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? new RenderEffectBlur()
                    : new RenderScriptBlur(ctx);
            Drawable windowBg = null;
            View rootView = bottomNav.getRootView();
            if (rootView != null) {
                windowBg = rootView.getBackground();
            }
            blurView.setupWith(blurRoot, algorithm)
                    .setFrameClearDrawable(windowBg)
                    .setBlurRadius(18f)
                    .setOverlayColor(getGlassOverlayColor(ctx));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void removeGlassHost(View bottomNav) {
        try {
            FrameLayout host = glassHosts.remove(bottomNav);
            glassBlurViews.remove(bottomNav);
            if (host != null) {
                ViewParent parent = host.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(host);
                }
                host.removeView(bottomNav);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Applies a glassmorphism effect to the floating bottom bar.
     *
     * Uses a translucent theme-aware fill. In WhatsApp's hooked hierarchy a separate blur layer
     * is too easy to desync during tab/page transitions, which causes transparent or resizing pills.
     */
    private void applyGlassmorphism(final View bottomNav, final float density) {
        try {
            final Context ctx = bottomNav.getContext();
            if (ctx == null) return;

            bottomNav.setBackground(createGlassShape(ctx, density, true));
            if (bottomNav instanceof ViewGroup) {
                ((ViewGroup) bottomNav).setClipChildren(false);
                ((ViewGroup) bottomNav).setClipToPadding(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bottomNav.setBackgroundTintList(null);
                bottomNav.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                bottomNav.setClipToOutline(false);
            }
            applyPillShadow(bottomNav, density);

        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    private static void applyPillShadow(View view, float density) {
        view.setElevation(PILL_ELEVATION_DP * density);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setTranslationZ(PILL_TRANSLATION_Z_DP * density);
        }
    }

    private static View findConversationViewHost(View bottomNav) {
        try {
            ViewGroup root = getRootLayout(bottomNav);
            if (root != null) {
                int resId = bottomNav.getContext().getResources().getIdentifier("conversation_view_host", "id", bottomNav.getContext().getPackageName());
                if (resId != 0 && resId != View.NO_ID) {
                    return root.findViewById(resId);
                }
            }
        } catch (Throwable ignored) {}
        return null;
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

    private GradientDrawable createGlassShape(Context ctx, float density, boolean includeFill) {
        int userRadius = prefs.getInt("floating_bottom_bar_radius", 28);
        float finalRadius = userRadius * density;
        GradientDrawable glassShape = new GradientDrawable();
        glassShape.setShape(GradientDrawable.RECTANGLE);
        glassShape.setCornerRadius(finalRadius);
        glassShape.setColor(includeFill ? getGlassOverlayColor(ctx) : 0x00000000);
        glassShape.setStroke(Math.max(1, (int) (0.6f * density)), getGlassStrokeColor(ctx));
        return glassShape;
    }

    private GradientDrawable createGlassOutlineShape(Context ctx, float density) {
        int userRadius = prefs.getInt("floating_bottom_bar_radius", 28);
        float finalRadius = userRadius * density;
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(finalRadius);
        shape.setColor(0x00000000);
        return shape;
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

    private static ViewGroup findMenuView(View tabFrame) {
        if (!(tabFrame instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) tabFrame;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup childGroup = (ViewGroup) child;
                if (childGroup.getChildCount() > 0) {
                    return childGroup;
                }
            }
        }
        return null;
    }


    private static void handleTabSelectionChanged(View bottomNav, int tabId) {
        try {
            View barTarget = getBarAnimationTarget(bottomNav);
            float density = bottomNav.getContext().getResources().getDisplayMetrics().density;
            int height = barTarget.getHeight();
            if (height <= 0) height = bottomNav.getHeight();
            if (height <= 0) height = (int) (80 * density);

            boolean isMetaAiActive = isMetaAiTabActive(bottomNav);

            if (tabId == 1000 || tabId == 1100 || isMetaAiActive) {
                float targetTranslationY = height + (24 * density);
                targetTranslations.put(barTarget, targetTranslationY);
                barTarget.animate()
                    .translationY(targetTranslationY)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
                animateFabs(bottomNav, true, density);
            } else {
                targetTranslations.put(barTarget, 0f);
                barTarget.animate()
                    .translationY(0)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
                animateFabs(bottomNav, false, density);
            }
            applyCustomSizesAndPaddings(findMenuView(bottomNav), density);
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error handling tab selection: " + t);
        }
    }

    private static int getTabIdForIndex(View bottomNav, int index) {
        try {
            Object menu = XposedHelpers.callMethod(bottomNav, "getMenu");
            if (menu != null) {
                int size = (int) XposedHelpers.callMethod(menu, "size");
                List<Integer> visibleIds = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    Object menuItem = XposedHelpers.callMethod(menu, "getItem", i);
                    if (menuItem != null && (boolean) XposedHelpers.callMethod(menuItem, "isVisible")) {
                        visibleIds.add((int) XposedHelpers.callMethod(menuItem, "getItemId"));
                    }
                }
                int resolvedId = -1;
                if (index >= 0 && index < visibleIds.size()) {
                    resolvedId = visibleIds.get(index);
                }
                return resolvedId;
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error in getTabIdForIndex: " + t);
        }
        return -1;
    }

    private static boolean isMetaAiTabSelected(View bottomNav) {
        try {
            int selectedId = (int) XposedHelpers.callMethod(bottomNav, "getSelectedItemId");
            return selectedId == 1000 || selectedId == 1100;
        } catch (Throwable ignored) {}
        return false;
    }

    private static View findBottomNavForView(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof View) {
                View parentView = (View) parent;
                if (styledBottomBars.containsKey(parentView)) {
                    return parentView;
                }
                parent = parentView.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    private static View.OnClickListener getOnClickListener(View view) {
        try {
            Field listenerInfoField = View.class.getDeclaredField("mListenerInfo");
            listenerInfoField.setAccessible(true);
            Object listenerInfo = listenerInfoField.get(view);
            if (listenerInfo != null) {
                Field onClickListenerField = listenerInfo.getClass().getDeclaredField("mOnClickListener");
                onClickListenerField.setAccessible(true);
                return (View.OnClickListener) onClickListenerField.get(listenerInfo);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void wrapOnClickListener(final View bottomNav, final View itemView) {
        try {
            final View.OnClickListener originalListener = getOnClickListener(itemView);
            if (originalListener != null && !originalListener.getClass().getName().contains("FloatingBottomBar")) {
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            int tabId = v.getId();
                            handleTabSelectionChanged(bottomNav, tabId);
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX-FBB] Error in wrapped onClick: " + t);
                        }
                        originalListener.onClick(v);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error wrapping listener: " + t);
        }
    }

    private static boolean isMetaAiTabActive(View bottomNav) {
        try {
            if (isMetaAiTabSelected(bottomNav)) {
                return true;
            }
            ViewGroup root = getRootLayout(bottomNav);
            View viewPager = findViewPager(root);
            if (viewPager != null) {
                int currentItem = (int) XposedHelpers.callMethod(viewPager, "getCurrentItem");
                int tabId = getTabIdForIndex(bottomNav, currentItem);
                return tabId == 1000 || tabId == 1100;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int iconContainerId = -1;
    private static int labelsGroupId = -1;
    private static int iconViewId = -1;
    private static boolean idsFetched = false;

    private static void fetchIds(Context ctx) {
        if (!idsFetched && ctx != null) {
            iconContainerId = ctx.getResources().getIdentifier("navigation_bar_item_icon_container", "id", ctx.getPackageName());
            labelsGroupId = ctx.getResources().getIdentifier("navigation_bar_item_labels_group", "id", ctx.getPackageName());
            iconViewId = ctx.getResources().getIdentifier("navigation_bar_item_icon_view", "id", ctx.getPackageName());
            idsFetched = true;
        }
    }

    private static void applyCustomSizesAndPaddings(ViewGroup menuView, float density) {
        if (menuView == null) return;
        try {
            for (int i = 0; i < menuView.getChildCount(); i++) {
                View itemView = menuView.getChildAt(i);
                if (itemView instanceof ViewGroup) {
                    adjustIconAndText((ViewGroup) itemView, density);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAEX-FBB] Error styling custom sizes: " + t);
        }
    }

    private static void adjustIconAndText(ViewGroup group, float density) {
        fetchIds(group.getContext());
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            
            if (iconContainerId != 0 && child.getId() == iconContainerId) {
                child.setTranslationY(-8f * density);
            } else if (labelsGroupId != 0 && child.getId() == labelsGroupId) {
                child.setTranslationY(8f * density);
            }

            if (child instanceof ImageView) {
                if (userIconSizeDp != 24 && iconViewId != 0 && child.getId() == iconViewId) {
                    ImageView iv = (ImageView) child;
                    ViewGroup.LayoutParams lp = iv.getLayoutParams();
                    int targetSize = (int) (userIconSizeDp * density);
                    if (lp.width != targetSize || lp.height != targetSize) {
                        lp.width = targetSize;
                        lp.height = targetSize;
                        iv.setLayoutParams(lp);
                    }
                }
            } else if (child instanceof TextView) {
                if (userTextSizeSp != 12) {
                    TextView tv = (TextView) child;
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, userTextSizeSp);
                }
            } else if (child instanceof ViewGroup) {
                adjustIconAndText((ViewGroup) child, density);
            }
        }
    }

    private static int getSystemInsetBottomSafely(View view) {
        int systemInsetBottom = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = view.getRootWindowInsets();
            if (insets != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    systemInsetBottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
                } else {
                    systemInsetBottom = insets.getSystemWindowInsetBottom();
                }
            }
            if (systemInsetBottom == 0) {
                // Try from the Window DecorView
                try {
                    Context context = view.getContext();
                    while (context instanceof ContextWrapper) {
                        if (context instanceof Activity) {
                            break;
                        }
                        context = ((ContextWrapper) context).getBaseContext();
                    }
                    if (context instanceof Activity) {
                        Window window = ((Activity) context).getWindow();
                        if (window != null) {
                            View decorView = window.getDecorView();
                            if (decorView != null) {
                                WindowInsets decInsets = decorView.getRootWindowInsets();
                                if (decInsets != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        systemInsetBottom = decInsets.getInsets(WindowInsets.Type.systemBars()).bottom;
                                    } else {
                                        systemInsetBottom = decInsets.getSystemWindowInsetBottom();
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (systemInsetBottom == 0) {
            try {
                int resourceId = view.getContext().getResources().getIdentifier("navigation_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    systemInsetBottom = view.getContext().getResources().getDimensionPixelSize(resourceId);
                }
            } catch (Throwable ignored) {}
        }
        return systemInsetBottom;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Floating Bottom Bar";
    }
}