package com.waenhancer.xposed.core.components;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;

import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AlertDialogWpp {


    private static Method getAlertDialog;
    private static Method setItemsMethod;
    private static boolean isAvailable;
    private static Method setMessageMethod;
    private static Method setNegativeButtonMethod;
    private static Method setNeutralButtonMethod;
    private static Method setPositiveButtonMethod;
    private static Method setMultiChoiceItemsMethod;
    private final Context mContext;
    private AlertDialog.Builder mAlertDialog;
    private Object mAlertDialogWpp;
    private Dialog mCreate;
    private boolean mIsUsingSystem = false;
    private CharSequence mTitleText;
    private CharSequence mMessageText;
    private CharSequence mPositiveButtonText;
    private DialogInterface.OnClickListener mPositiveListener;
    private CharSequence mNegativeButtonText;
    private DialogInterface.OnClickListener mNegativeListener;
    private CharSequence mNeutralButtonText;
    private DialogInterface.OnClickListener mNeutralListener;
    private Dialog mBottomSheetDialog;
    private View mCustomView;
    private CharSequence[] mItems;
    private DialogInterface.OnClickListener mItemsListener;
    private CharSequence[] mMultiChoiceItems;
    private boolean[] mCheckedItems;
    private DialogInterface.OnMultiChoiceClickListener mMultiChoiceListener;
    private boolean mIsFullHeight = false;

    public AlertDialogWpp setFullHeight(boolean fullHeight) {
        mIsFullHeight = fullHeight;
        return this;
    }

    public static void initDialog(ClassLoader loader) {
        try {
            getAlertDialog = Unobfuscator.loadMaterialAlertDialog(loader);
            if (getAlertDialog == null) {
                isAvailable = false;
                return;
            }
            Class<?> alertDialogClass = getAlertDialog.getReturnType();
            ;

            // Try to find methods by name first (more reliable if not obfuscated)
            setMessageMethod = null;
            try {
                setMessageMethod = alertDialogClass.getMethod("setMessage", CharSequence.class);
            } catch (NoSuchMethodException e) {
                // Fallback to signature search
                setMessageMethod = ReflectionUtils.findMethodUsingFilterIfExists(alertDialogClass,
                    method -> method.getParameterCount() == 1 && 
                    method.getParameterTypes()[0].equals(CharSequence.class));
            }
            if (setMessageMethod != null) ;

            setItemsMethod = ReflectionUtils.findMethodUsingFilterIfExists(alertDialogClass,
                    method -> method.getParameterCount() == 2 &&
                    ((method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && CharSequence[].class.isAssignableFrom(method.getParameterTypes()[1])) ||
                     (CharSequence[].class.isAssignableFrom(method.getParameterTypes()[0]) && method.getParameterTypes()[1].equals(DialogInterface.OnClickListener.class))));
            if (setItemsMethod != null) ;

            setMultiChoiceItemsMethod = ReflectionUtils.findMethodUsingFilterIfExists(alertDialogClass,
                    method -> method.getParameterCount() == 3 &&
                    ((method.getParameterTypes()[0].equals(DialogInterface.OnMultiChoiceClickListener.class) && CharSequence[].class.isAssignableFrom(method.getParameterTypes()[1])) ||
                     (CharSequence[].class.isAssignableFrom(method.getParameterTypes()[0]) && method.getParameterTypes()[1].equals(boolean[].class) && method.getParameterTypes()[2].equals(DialogInterface.OnMultiChoiceClickListener.class))));
            
            // Robust button discovery
            java.lang.reflect.Method[] buttons = new java.lang.reflect.Method[0];
            try {
                buttons = ReflectionUtils.findAllMethodsUsingFilter(alertDialogClass, method -> 
                    method.getParameterCount() == 2 && 
                    ((method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && CharSequence.class.isAssignableFrom(method.getParameterTypes()[1])) ||
                     (CharSequence.class.isAssignableFrom(method.getParameterTypes()[0]) && method.getParameterTypes()[1].equals(DialogInterface.OnClickListener.class))));
                ;
            } catch (Exception ignored) {}

            setNegativeButtonMethod = null;
            setNeutralButtonMethod = null;
            setPositiveButtonMethod = null;

            for (java.lang.reflect.Method m : buttons) {
                XposedBridge.log("[WAE] AlertDialogWpp: Button candidate: " + m.getName() + " (" + Arrays.toString(m.getParameterTypes()) + ")");
                if (m.getName().equals("setNegativeButton")) setNegativeButtonMethod = m;
                else if (m.getName().equals("setNeutralButton")) setNeutralButtonMethod = m;
                else if (m.getName().equals("setPositiveButton")) setPositiveButtonMethod = m;
            }

            if (setPositiveButtonMethod == null && buttons.length > 0) {
                // Heuristic for MaterialAlertDialogBuilder button order
                // Often it's Positive, Negative, Neutral OR Negative, Neutral, Positive
                // We'll try to find them by name if possible, else fallback to indices
                setPositiveButtonMethod = buttons[0]; 
                if (buttons.length > 1) setNegativeButtonMethod = buttons[1];
                if (buttons.length > 2) setNeutralButtonMethod = buttons[2];
                
                // If there are 3+ buttons, sometimes Positive is the last one in some obfuscations
                if (buttons.length >= 3) {
                    setPositiveButtonMethod = buttons[2];
                    setNegativeButtonMethod = buttons[0];
                    setNeutralButtonMethod = buttons[1];
                }
                
                ;
            }

            isAvailable = true;
            ;
            logClassMethods(alertDialogClass);
        } catch (Throwable e) {
            isAvailable = false;
            XposedBridge.log("[WAE] AlertDialogWpp init failed: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private static void logClassMethods(Class<?> clazz) {
        XposedBridge.log("[WAE] --- Methods for " + clazz.getName() + " ---");
        for (Method m : clazz.getDeclaredMethods()) {
            ;
        }
        ;
    }


    public AlertDialogWpp(Context context) {
        mContext = context;
        mAlertDialog = new AlertDialog.Builder(context);
        if (isAvailable) {
            try {
                mAlertDialogWpp = getAlertDialog.invoke(null, context);
            } catch (Exception e) {
                XposedBridge.log("[WAE] AlertDialogWpp instance failed, using system fallback");
                mIsUsingSystem = true;
            }
        } else {
            mIsUsingSystem = true;
        }
    }

    public Context getContext() {
        return mContext;
    }

    private boolean shouldUseSystem() {
        return true;
    }

    public AlertDialogWpp setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            title = "WaEnhancer";
        }
        mTitleText = title;
        mAlertDialog.setTitle(title);
        if (!shouldUseSystem()) {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title);
            } catch (Throwable t) {
                XposedBridge.log("[WAE] AlertDialogWpp setTitle failed on Wpp builder: " + t.getMessage());
            }
        }
        return this;
    }

    public AlertDialogWpp setTitle(int title) {
        mTitleText = getContext().getString(title);
        mAlertDialog.setTitle(title);
        if (!shouldUseSystem()) {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", getContext().getString(title));
            } catch (Throwable t) {
                XposedBridge.log("[WAE] AlertDialogWpp setTitle(int) failed on Wpp builder: " + t.getMessage());
            }
        }
        return this;
    }

    public AlertDialogWpp setTitle(CharSequence title) {
        if (title == null || title.toString().trim().isEmpty()) {
            title = "WaEnhancer";
        }
        mTitleText = title;
        mAlertDialog.setTitle(title);
        if (!shouldUseSystem()) {
            try {
                // Heuristic search for setTitle
                java.lang.reflect.Method setTitleMethod = ReflectionUtils.findMethodUsingFilterIfExists(mAlertDialogWpp.getClass(),
                    m -> m.getName().toLowerCase().contains("title") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(CharSequence.class));
                
                if (setTitleMethod != null) {
                    setTitleMethod.invoke(mAlertDialogWpp, title);
                } else {
                    XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title);
                }
            } catch (Throwable e) {
                XposedBridge.log("[WAE] AlertDialogWpp setTitle(CharSequence) failed on Wpp builder: " + e.getMessage());
            }
        }
        return this;
    }

    public AlertDialogWpp setMessage(CharSequence message) {
        if (message == null || message.toString().trim().isEmpty()) {
            message = "Are you sure you want to proceed?";
        }
        mMessageText = message;
        mAlertDialog.setMessage(message);
        if (!shouldUseSystem()) {
            try {
                if (setMessageMethod != null) {
                    setMessageMethod.invoke(mAlertDialogWpp, message);
                } else {
                    XposedHelpers.callMethod(mAlertDialogWpp, "setMessage", message);
                }
            } catch (Throwable e) {
                XposedBridge.log("[WAE] AlertDialogWpp setMessage failed, falling back to system: " + e.getMessage());
                mIsUsingSystem = true;
            }
        }
        return this;
    }

    public AlertDialogWpp setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
        mItems = items;
        mItemsListener = listener;
        mAlertDialog.setItems(items, listener);
        if (!shouldUseSystem()) {
            try {
                if (setItemsMethod != null) {
                    if (setItemsMethod.getParameterTypes()[0].equals(CharSequence[].class)) {
                        setItemsMethod.invoke(mAlertDialogWpp, items, listener);
                    } else {
                        setItemsMethod.invoke(mAlertDialogWpp, listener, items);
                    }
                } else {
                    XposedHelpers.callMethod(mAlertDialogWpp, "setItems", items, listener);
                }
            } catch (Throwable e) {
                XposedBridge.log("[WAE] AlertDialogWpp setItems failed on Wpp builder: " + e.getMessage());
            }
        }
        return this;
    }


    public AlertDialogWpp setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
        mMultiChoiceItems = items;
        mCheckedItems = checkedItems;
        mMultiChoiceListener = listener;
        mAlertDialog.setMultiChoiceItems(items, checkedItems, listener);
        if (!shouldUseSystem()) {
            try {
                if (setMultiChoiceItemsMethod != null) {
                    if (setMultiChoiceItemsMethod.getParameterTypes()[0].equals(CharSequence[].class)) {
                        setMultiChoiceItemsMethod.invoke(mAlertDialogWpp, items, checkedItems, listener);
                    } else {
                        setMultiChoiceItemsMethod.invoke(mAlertDialogWpp, listener, items, checkedItems);
                    }
                } else {
                    XposedHelpers.callMethod(mAlertDialogWpp, "setMultiChoiceItems", items, checkedItems, listener);
                }
            } catch (Exception e) {
                XposedBridge.log("[WAE] AlertDialogWpp setMultiChoiceItems failed on Wpp builder: " + e.getMessage());
            }
        }
        return this;
    }


    /**
     * Invoke a button method on the WhatsApp MaterialAlertDialog builder.
     * Uses cached Method objects if found by signature, falling back to name-based lookup.
     */
    private void callBuilderMethod(Method cachedMethod, String methodName, CharSequence text, DialogInterface.OnClickListener listener) {
        if (shouldUseSystem()) {
            return;
        }
        
        boolean success = false;
        if (cachedMethod != null) {
            try {
                if (CharSequence.class.isAssignableFrom(cachedMethod.getParameterTypes()[0])) {
                    cachedMethod.invoke(mAlertDialogWpp, text, listener);
                } else {
                    cachedMethod.invoke(mAlertDialogWpp, listener, text);
                }
                success = true;
            } catch (Throwable ignored) {}
        }
        
        if (!success) {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, methodName, text, listener);
                success = true;
            } catch (Throwable t1) {
                try {
                    XposedHelpers.callMethod(mAlertDialogWpp, methodName, listener, text);
                    success = true;
                } catch (Throwable t2) {
                    XposedBridge.log("[WAE] AlertDialogWpp button failed: " + methodName + ", falling back to system");
                    mIsUsingSystem = true;
                    // Apply to system builder so it's ready if we switch
                    if (methodName.equals("setPositiveButton")) mAlertDialog.setPositiveButton(text, listener);
                    else if (methodName.equals("setNegativeButton")) mAlertDialog.setNegativeButton(text, listener);
                    else if (methodName.equals("setNeutralButton")) mAlertDialog.setNeutralButton(text, listener);
                }
            }
        }
    }

    public AlertDialogWpp setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (text == null || text.toString().trim().isEmpty()) {
            text = "Cancel";
        }
        mNegativeButtonText = text;
        mNegativeListener = listener;
        mAlertDialog.setNegativeButton(text, listener);
        if (!shouldUseSystem()) {
            callBuilderMethod(setNegativeButtonMethod, "setNegativeButton", text, listener);
        }
        return this;
    }

    public AlertDialogWpp setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (text == null || text.toString().trim().isEmpty()) {
            text = "Dismiss";
        }
        mNeutralButtonText = text;
        mNeutralListener = listener;
        mAlertDialog.setNeutralButton(text, listener);
        if (!shouldUseSystem()) {
            callBuilderMethod(setNeutralButtonMethod, "setNeutralButton", text, listener);
        }
        return this;
    }

    public AlertDialogWpp setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (text == null || text.toString().trim().isEmpty()) {
            text = "OK";
        }
        mPositiveButtonText = text;
        mPositiveListener = listener;
        mAlertDialog.setPositiveButton(text, listener);
        if (!shouldUseSystem()) {
            callBuilderMethod(setPositiveButtonMethod, "setPositiveButton", text, listener);
        }
        return this;
    }

    public AlertDialogWpp setView(View view) {
        mCustomView = view;
        mAlertDialog.setView(view);
        if (!shouldUseSystem()) {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setView", view);
            } catch (Throwable t) {
                XposedBridge.log("[WAE] AlertDialogWpp setView failed on Wpp builder: " + t.getMessage());
            }
        }
        return this;
    }

    public AlertDialogWpp setCancelable(boolean cancelable) {
        mAlertDialog.setCancelable(cancelable);
        if (!shouldUseSystem()) {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setCancelable", cancelable);
            } catch (Throwable t) {
                XposedBridge.log("[WAE] AlertDialogWpp setCancelable failed on Wpp builder: " + t.getMessage());
            }
        }
        return this;
    }

    private boolean mIsBottomSheet = true;

    public AlertDialogWpp asBottomSheet() {
        mIsBottomSheet = true;
        return this;
    }

    private void applyBottomSheetStyle(Dialog d) {
        if (d == null) return;
        android.view.Window window = d.getWindow();
        if (window != null) {
            window.setGravity(android.view.Gravity.BOTTOM);
            window.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
            
            int backgroundColor = 0xFFFFFFFF;
            try {
                android.util.TypedValue typedValue = new android.util.TypedValue();
                if (mContext.getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true)) {
                    backgroundColor = typedValue.data;
                }
            } catch (Exception ignored) {}
            
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setColor(backgroundColor);
            float radius = 16 * mContext.getResources().getDisplayMetrics().density;
            drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
            window.setBackgroundDrawable(drawable);
            
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public Dialog create() {
        if (mCreate != null) return mCreate;
        if (mIsBottomSheet && mItems != null && mItems.length == 2 && mPositiveButtonText == null && mNegativeButtonText == null) {
            CharSequence item0 = mItems[0];
            CharSequence item1 = mItems[1];
            if (item0 == null || item0.toString().trim().isEmpty()) {
                item0 = "Phone Call";
            }
            if (item1 == null || item1.toString().trim().isEmpty()) {
                item1 = "WhatsApp Call";
            }
            mPositiveButtonText = item0;
            mPositiveListener = (dialogInterface, which) -> {
                if (mItemsListener != null) {
                    mItemsListener.onClick(dialogInterface, 0);
                }
            };
            mNegativeButtonText = item1;
            mNegativeListener = (dialogInterface, which) -> {
                if (mItemsListener != null) {
                    mItemsListener.onClick(dialogInterface, 1);
                }
            };
            mItems = null;
        }
        if (mIsBottomSheet) {
            try {
                android.app.Dialog dialog = new android.app.Dialog(mContext, android.R.style.Theme_Translucent_NoTitleBar);
                
                float density = mContext.getResources().getDisplayMetrics().density;
                int dp8 = (int) (8 * density);
                int dp12 = (int) (12 * density);
                int dp16 = (int) (16 * density);
                int dp20 = (int) (20 * density);
                
                boolean isDarkMode = false;
                try {
                    int nightModeFlags = mContext.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                    isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                } catch (Exception ignored) {}
                
                int backgroundColor = isDarkMode ? 0xFF1E1E1E : 0xFFFFFFFF;
                int primaryTextColor = isDarkMode ? 0xFFFFFFFF : 0xFF000000;
                int secondaryTextColor = isDarkMode ? 0xFFB0B0B0 : 0xFF666666;
                int accentColor = 0xFF008080;
                try {
                    android.util.TypedValue typedValue = new android.util.TypedValue();
                    if (mContext.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
                        primaryTextColor = typedValue.data;
                    }
                    if (mContext.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
                        secondaryTextColor = typedValue.data;
                    }
                    if (mContext.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
                        accentColor = typedValue.data;
                    }
                } catch (Exception ignored) {}
                
                final int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
                final int halfScreenHeight = screenHeight / 2;
                final float capHeight = screenHeight * 0.85f;
                
                final android.widget.RelativeLayout container = new android.widget.RelativeLayout(mContext);
                container.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                container.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                
                final android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(mContext);
                mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                mainLayout.setPadding(dp20, dp16, dp20, (int) (32 * density));
                
                android.widget.RelativeLayout.LayoutParams mainParams = new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
                mainParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
                mainLayout.setLayoutParams(mainParams);
                
                // Rounded background for the sheet with perfect outline clipping
                android.graphics.drawable.GradientDrawable bgDrawable = new android.graphics.drawable.GradientDrawable();
                bgDrawable.setColor(backgroundColor);
                float radius = 24 * density;
                bgDrawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
                mainLayout.setBackground(bgDrawable);
                mainLayout.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                mainLayout.setClipToOutline(true);
                mainLayout.setElevation(8 * density);
                
                // Drag Handle
                android.view.View dragHandle = new android.view.View(mContext);
                android.widget.LinearLayout.LayoutParams handleParams = new android.widget.LinearLayout.LayoutParams((int) (40 * density), (int) (4 * density));
                handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                handleParams.bottomMargin = dp16;
                dragHandle.setLayoutParams(handleParams);
                
                android.graphics.drawable.GradientDrawable handleDrawable = new android.graphics.drawable.GradientDrawable();
                handleDrawable.setColor(secondaryTextColor & 0x33FFFFFF | 0x33000000);
                handleDrawable.setCornerRadius(2 * density);
                dragHandle.setBackground(handleDrawable);
                mainLayout.addView(dragHandle);
                
                // Implement touch-to-drag downward and upward multi-state height gesture
                android.view.View.OnTouchListener dragListener = new android.view.View.OnTouchListener() {
                    private float initialY;
                    private int initialHeight;
                    private float initialTranslationY;
                    private boolean isDragging = false;
                    
                    @Override
                    public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                        switch (event.getAction()) {
                            case android.view.MotionEvent.ACTION_DOWN:
                                initialY = event.getRawY();
                                initialHeight = mainLayout.getHeight();
                                initialTranslationY = mainLayout.getTranslationY();
                                isDragging = true;
                                return true;
                            case android.view.MotionEvent.ACTION_MOVE:
                                if (!isDragging) return false;
                                float deltaY = event.getRawY() - initialY;
                                if (deltaY < 0) { // Dragging UP -> Increase height
                                    mainLayout.setTranslationY(0);
                                    int newHeight = (int) (initialHeight - deltaY);
                                    if (newHeight > capHeight) {
                                        newHeight = (int) capHeight;
                                    }
                                    android.view.ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                    lp.height = newHeight;
                                    mainLayout.setLayoutParams(lp);
                                } else { // Dragging DOWN -> Decrease height first, then translate down to dismiss
                                    if (initialHeight > halfScreenHeight) {
                                        int newHeight = (int) (initialHeight - deltaY);
                                        if (newHeight < halfScreenHeight) {
                                            newHeight = halfScreenHeight;
                                        }
                                        android.view.ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                        lp.height = newHeight;
                                        mainLayout.setLayoutParams(lp);
                                        mainLayout.setTranslationY(0);
                                    } else {
                                        mainLayout.setTranslationY(deltaY);
                                    }
                                }
                                return true;
                            case android.view.MotionEvent.ACTION_UP:
                                isDragging = false;
                                float currentTranslationY = mainLayout.getTranslationY();
                                int currentHeight = mainLayout.getHeight();
                                
                                if (currentTranslationY > (halfScreenHeight / 3)) {
                                    // Dismiss downwards
                                    mainLayout.animate()
                                            .translationY(screenHeight)
                                            .setDuration(200)
                                            .withEndAction(dialog::dismiss)
                                            .start();
                                } else {
                                    mainLayout.animate().translationY(0).setDuration(200).start();
                                    
                                    // Snap height to either collapsed or expanded state
                                    int targetHeight = halfScreenHeight;
                                    if (currentHeight > (halfScreenHeight + (capHeight - halfScreenHeight) / 2)) {
                                        targetHeight = (int) capHeight;
                                    }
                                    
                                    final int finalTargetHeight = targetHeight;
                                    android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(currentHeight, finalTargetHeight);
                                    animator.addUpdateListener(animation -> {
                                        android.view.ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                        lp.height = (int) animation.getAnimatedValue();
                                        mainLayout.setLayoutParams(lp);
                                    });
                                    animator.setDuration(200);
                                    animator.start();
                                }
                                return true;
                        }
                        return false;
                    }
                };
                mainLayout.setOnTouchListener(dragListener);
                dragHandle.setOnTouchListener(dragListener);
                
                // Title
                if (mTitleText != null) {
                    android.widget.TextView titleView = new android.widget.TextView(mContext);
                    titleView.setText(mTitleText);
                    titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
                    titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    titleView.setTextColor(primaryTextColor);
                    titleView.setPadding(0, 0, 0, dp12);
                    mainLayout.addView(titleView);
                }
                
                // Scrollable content
                androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(mContext);
                android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
                scrollView.setLayoutParams(scrollParams);
                
                android.widget.LinearLayout scrollContentLayout = new android.widget.LinearLayout(mContext);
                scrollContentLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                scrollContentLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                scrollView.addView(scrollContentLayout);
                
                if (mMessageText != null) {
                    android.widget.TextView messageView = new android.widget.TextView(mContext);
                    messageView.setText(mMessageText);
                    messageView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                    messageView.setTextColor(secondaryTextColor);
                    scrollContentLayout.addView(messageView);
                } else if (mItems == null && mCustomView == null && mMultiChoiceItems == null) {
                    android.widget.TextView messageView = new android.widget.TextView(mContext);
                    messageView.setText("Are you sure you want to proceed?");
                    messageView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                    messageView.setTextColor(secondaryTextColor);
                    scrollContentLayout.addView(messageView);
                }
                
                if (mCustomView != null) {
                    try {
                        android.view.ViewParent parent = mCustomView.getParent();
                        if (parent instanceof android.view.ViewGroup) {
                            ((android.view.ViewGroup) parent).removeView(mCustomView);
                        }
                    } catch (Exception ignored) {}
                    android.widget.LinearLayout.LayoutParams customParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    customParams.topMargin = dp8;
                    customParams.bottomMargin = dp8;
                    mCustomView.setLayoutParams(customParams);
                    scrollContentLayout.addView(mCustomView);
                }
                
                if (mItems != null) {
                    android.widget.LinearLayout itemsLayout = new android.widget.LinearLayout(mContext);
                    itemsLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    android.widget.LinearLayout.LayoutParams itemsParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    itemsParams.topMargin = dp8;
                    itemsParams.bottomMargin = dp8;
                    itemsLayout.setLayoutParams(itemsParams);
                    
                    for (int i = 0; i < mItems.length; i++) {
                        final int index = i;
                        android.widget.TextView itemView = new android.widget.TextView(mContext);
                        android.widget.LinearLayout.LayoutParams itemLp = new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        itemView.setLayoutParams(itemLp);
                        itemView.setText(mItems[index]);
                        itemView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                        itemView.setTextColor(primaryTextColor);
                        itemView.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        itemView.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
                        
                        android.graphics.drawable.ColorDrawable normalBg = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
                        android.content.res.ColorStateList itemRippleColor = android.content.res.ColorStateList.valueOf(secondaryTextColor & 0x15FFFFFF | 0x15000000);
                        android.graphics.drawable.RippleDrawable itemRipple = new android.graphics.drawable.RippleDrawable(itemRippleColor, normalBg, null);
                        itemView.setBackground(itemRipple);
                        
                        itemView.setOnClickListener(v -> {
                            if (mItemsListener != null) {
                                mItemsListener.onClick(dialog, index);
                            }
                            dialog.dismiss();
                        });
                        itemsLayout.addView(itemView);
                    }
                    scrollContentLayout.addView(itemsLayout);
                }
                
                if (mMultiChoiceItems != null) {
                    android.widget.LinearLayout itemsLayout = new android.widget.LinearLayout(mContext);
                    itemsLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    android.widget.LinearLayout.LayoutParams itemsParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    itemsParams.topMargin = dp8;
                    itemsParams.bottomMargin = dp8;
                    itemsLayout.setLayoutParams(itemsParams);
                    
                    for (int i = 0; i < mMultiChoiceItems.length; i++) {
                        final int index = i;
                        android.widget.LinearLayout itemRow = new android.widget.LinearLayout(mContext);
                        itemRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        itemRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        itemRow.setLayoutParams(rowLp);
                        itemRow.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
                        
                        android.widget.TextView labelView = new android.widget.TextView(mContext);
                        android.widget.LinearLayout.LayoutParams labelLp = new android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                        labelView.setLayoutParams(labelLp);
                        labelView.setText(mMultiChoiceItems[index]);
                        labelView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
                        labelView.setTextColor(primaryTextColor);
                        
                        android.widget.CompoundButton switchView;
                        try {
                            android.content.Context modContext = mContext.createPackageContext("com.waenhancer", android.content.Context.CONTEXT_IGNORE_SECURITY);
                            int themeResId = isDarkMode ? 
                                    com.google.android.material.R.style.Theme_Material3_Dark : 
                                    com.google.android.material.R.style.Theme_Material3_Light;
                            android.view.ContextThemeWrapper themedContext = new android.view.ContextThemeWrapper(modContext, themeResId);
                            switchView = new com.google.android.material.materialswitch.MaterialSwitch(themedContext);
                        } catch (Throwable t) {
                            de.robv.android.xposed.XposedBridge.log("[WAE] MaterialSwitch direct creation failed: " + t.getMessage());
                            try {
                                switchView = (android.widget.CompoundButton) de.robv.android.xposed.XposedHelpers.newInstance(
                                        de.robv.android.xposed.XposedHelpers.findClass("com.google.android.material.materialswitch.MaterialSwitch", AlertDialogWpp.class.getClassLoader()), mContext);
                            } catch (Throwable t1) {
                                try {
                                    switchView = (android.widget.CompoundButton) de.robv.android.xposed.XposedHelpers.newInstance(
                                            de.robv.android.xposed.XposedHelpers.findClass("androidx.appcompat.widget.SwitchCompat", AlertDialogWpp.class.getClassLoader()), mContext);
                                } catch (Throwable t2) {
                                    switchView = new android.widget.Switch(mContext);
                                }
                            }
                        }
                        final android.widget.CompoundButton finalSwitchView = switchView;
                        android.widget.LinearLayout.LayoutParams checkLp = new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        finalSwitchView.setLayoutParams(checkLp);
                        finalSwitchView.setChecked(mCheckedItems != null && index < mCheckedItems.length && mCheckedItems[index]);
                        finalSwitchView.setClickable(false);
                        
                        try {
                            // Multi-state color lists so unchecked switches are not colored
                            int[][] states = new int[][] {
                                new int[] { android.R.attr.state_checked },
                                new int[] { -android.R.attr.state_checked }
                            };
                            int[] thumbColors = new int[] {
                                isDarkMode ? 0xFF0A3F1F : 0xFF0B6623, // Forest/dark green thumb
                                isDarkMode ? 0xFF9E9E9E : 0xFFECECEC  // Neutral grey thumb
                            };
                            int[] trackColors = new int[] {
                                isDarkMode ? 0xFF57DF85 : 0xFF50D179, // Vibrant light green track
                                isDarkMode ? 0x33FFFFFF : 0x33000000  // Translucent neutral track
                            };
                            android.content.res.ColorStateList thumbStateList = new android.content.res.ColorStateList(states, thumbColors);
                            android.content.res.ColorStateList trackStateList = new android.content.res.ColorStateList(states, trackColors);
                            
                            if (finalSwitchView instanceof com.google.android.material.materialswitch.MaterialSwitch) {
                                ((com.google.android.material.materialswitch.MaterialSwitch) finalSwitchView).setThumbTintList(thumbStateList);
                                ((com.google.android.material.materialswitch.MaterialSwitch) finalSwitchView).setTrackTintList(trackStateList);
                            } else {
                                de.robv.android.xposed.XposedHelpers.callMethod(finalSwitchView, "setThumbTintList", thumbStateList);
                                de.robv.android.xposed.XposedHelpers.callMethod(finalSwitchView, "setTrackTintList", trackStateList);
                            }
                        } catch (Throwable t) {
                            try {
                                int[][] states = new int[][] {
                                    new int[] { android.R.attr.state_checked },
                                    new int[] { -android.R.attr.state_checked }
                                };
                                int[] buttonColors = new int[] {
                                    accentColor,
                                    isDarkMode ? 0xFF9E9E9E : 0xFFECECEC
                                };
                                finalSwitchView.setButtonTintList(new android.content.res.ColorStateList(states, buttonColors));
                            } catch (Throwable ignored) {}
                        }
                        
                        itemRow.addView(labelView);
                        itemRow.addView(finalSwitchView);
                        
                        android.graphics.drawable.ColorDrawable normalBg = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
                        android.content.res.ColorStateList itemRippleColor = android.content.res.ColorStateList.valueOf(secondaryTextColor & 0x15FFFFFF | 0x15000000);
                        android.graphics.drawable.RippleDrawable itemRipple = new android.graphics.drawable.RippleDrawable(itemRippleColor, normalBg, null);
                        itemRow.setBackground(itemRipple);
                        
                        itemRow.setOnClickListener(v -> {
                            boolean isChecked = !finalSwitchView.isChecked();
                            finalSwitchView.setChecked(isChecked);
                            if (mCheckedItems != null && index < mCheckedItems.length) {
                                mCheckedItems[index] = isChecked;
                            }
                            if (mMultiChoiceListener != null) {
                                mMultiChoiceListener.onClick(dialog, index, isChecked);
                            }
                        });
                        
                        itemsLayout.addView(itemRow);
                    }
                    scrollContentLayout.addView(itemsLayout);
                }
                
                mainLayout.addView(scrollView);
                
                // Bottom Buttons Layout (Stacked Vertically for Spacious Premium Look)
                android.widget.LinearLayout buttonsLayout = new android.widget.LinearLayout(mContext);
                buttonsLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                android.widget.LinearLayout.LayoutParams buttonsParams = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                buttonsParams.topMargin = dp16;
                buttonsLayout.setLayoutParams(buttonsParams);
                
                if (mPositiveButtonText != null) {
                    android.widget.TextView posButton = new android.widget.TextView(mContext);
                    android.widget.LinearLayout.LayoutParams posParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
                    if (mNegativeButtonText != null) {
                        posParams.bottomMargin = dp12; // Gap between stacked buttons
                    }
                    posButton.setLayoutParams(posParams);
                    posButton.setText(mPositiveButtonText);
                    posButton.setGravity(android.view.Gravity.CENTER);
                    posButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                    posButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    posButton.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
                    
                    android.graphics.drawable.GradientDrawable posBg = new android.graphics.drawable.GradientDrawable();
                    posBg.setColor(accentColor);
                    posBg.setCornerRadius(12 * density);
                    
                    android.content.res.ColorStateList posRippleColor = android.content.res.ColorStateList.valueOf(0x22FFFFFF);
                    android.graphics.drawable.RippleDrawable posRipple = new android.graphics.drawable.RippleDrawable(posRippleColor, posBg, null);
                    posButton.setBackground(posRipple);
                    posButton.setTextColor(isDarkMode ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                    
                    posButton.setOnClickListener(v -> {
                        if (mPositiveListener != null) {
                            mPositiveListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        }
                        dialog.dismiss();
                    });
                    buttonsLayout.addView(posButton);
                }
                
                if (mNegativeButtonText != null) {
                    android.widget.TextView negButton = new android.widget.TextView(mContext);
                    android.widget.LinearLayout.LayoutParams negParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
                    negButton.setLayoutParams(negParams);
                    negButton.setText(mNegativeButtonText);
                    negButton.setGravity(android.view.Gravity.CENTER);
                    negButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                    negButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    negButton.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
                    
                    android.graphics.drawable.GradientDrawable negBg = new android.graphics.drawable.GradientDrawable();
                    negBg.setColor(android.graphics.Color.TRANSPARENT);
                    negBg.setStroke((int) (1 * density), secondaryTextColor & 0x44FFFFFF | 0x44000000);
                    negBg.setCornerRadius(12 * density);
                    
                    android.content.res.ColorStateList negRippleColor = android.content.res.ColorStateList.valueOf(secondaryTextColor & 0x15FFFFFF | 0x15000000);
                    android.graphics.drawable.RippleDrawable negRipple = new android.graphics.drawable.RippleDrawable(negRippleColor, negBg, null);
                    negButton.setBackground(negRipple);
                    negButton.setTextColor(primaryTextColor);
                    
                     negButton.setOnClickListener(v -> {
                         if (mNegativeListener != null) {
                             mNegativeListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                         }
                         dialog.dismiss();
                     });
                     buttonsLayout.addView(negButton);
                 }
                 
                 mainLayout.addView(buttonsLayout);
                 container.addView(mainLayout);

                mainLayout.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mainLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int measuredHeight = mainLayout.getHeight();
                        int maxAllowedHeight = mIsFullHeight ? (int) capHeight : halfScreenHeight;
                        if (mIsFullHeight || measuredHeight > maxAllowedHeight) {
                            android.view.ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                            lp.height = maxAllowedHeight;
                            mainLayout.setLayoutParams(lp);
                            
                            android.widget.LinearLayout.LayoutParams sLp = (android.widget.LinearLayout.LayoutParams) scrollView.getLayoutParams();
                            sLp.height = 0;
                            sLp.weight = 1.0f;
                            scrollView.setLayoutParams(sLp);
                        }
                    }
                });
                
                // Clicking outside mainLayout dismisses the dialog
                container.setOnClickListener(v -> {
                    mainLayout.animate()
                            .translationY(screenHeight)
                            .setDuration(200)
                            .withEndAction(dialog::dismiss)
                            .start();
                });
                mainLayout.setOnClickListener(v -> {});
                
                dialog.setContentView(container);
                
                // Configure Window properties for true Bottom Sheet presentation
                android.view.Window window = dialog.getWindow();
                if (window != null) {
                    window.setGravity(android.view.Gravity.BOTTOM);
                    window.getDecorView().setPadding(0, 0, 0, 0);
                    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                    
                    // Add standard bottom sheet background dimming scrim
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setDimAmount(0.5f);
                    
                    window.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
                    window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                }
                
                mCreate = dialog;
                return mCreate;
            } catch (Throwable t) {
                XposedBridge.log("[WAE] BottomSheetDialog instantiation failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
        if (shouldUseSystem()) {
            mCreate = mAlertDialog.create();
        } else {
            try {
                mCreate = (Dialog) XposedHelpers.callMethod(mAlertDialogWpp, "create");
            } catch (Throwable t) {
                XposedBridge.log("[WAE] AlertDialogWpp.create() failed, using system fallback");
                mCreate = mAlertDialog.create();
            }
        }
        return mCreate;
    }

    public void dismiss() {
        if (mCreate == null) return;
        mCreate.dismiss();
    }

    public Dialog show() {
        if (mContext instanceof Activity) {
            Activity activity = (Activity) mContext;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return null;
            }
        }
        try {
            Dialog d = create();
            d.show();
            return d;
        } catch (Throwable t) {
            XposedBridge.log("[WAE] AlertDialogWpp.show() failed: " + t.getMessage());
            try {
                Dialog d = mAlertDialog.show();
                if (mIsBottomSheet) {
                    applyBottomSheetStyle(d);
                }
                return d;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

}
