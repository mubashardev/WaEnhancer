package com.waenhancer.xposed.core.components;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

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
    private TextView mPositiveButtonView = null;

    /** Returns the positive button view after {@link #show()} or {@link #create()} has been called. */
    public TextView getPositiveButton() {
        return mPositiveButtonView;
    }

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
        } catch (Throwable e) {
            isAvailable = false;
            XposedBridge.log("[WAEX] AlertDialogWpp init failed: " + e.getMessage());
            XposedBridge.log(e);
        }
    }


    public AlertDialogWpp(Context context) {
        mContext = context;
        mAlertDialog = new AlertDialog.Builder(context);
        if (isAvailable) {
            try {
                mAlertDialogWpp = getAlertDialog.invoke(null, context);
            } catch (Exception e) {
                // XposedBridge.log("[WAEX] AlertDialogWpp instance failed, using system fallback");
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
            title = "WaEnhancer X";
        }
        mTitleText = title;
        mAlertDialog.setTitle(title);
        if (!shouldUseSystem()) {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title);
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] AlertDialogWpp setTitle failed on Wpp builder: " + t.getMessage());
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
                XposedBridge.log("[WAEX] AlertDialogWpp setTitle(int) failed on Wpp builder: " + t.getMessage());
            }
        }
        return this;
    }

    public AlertDialogWpp setTitle(CharSequence title) {
        if (title == null || title.toString().trim().isEmpty()) {
            title = "WaEnhancer X";
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
                XposedBridge.log("[WAEX] AlertDialogWpp setTitle(CharSequence) failed on Wpp builder: " + e.getMessage());
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
                XposedBridge.log("[WAEX] AlertDialogWpp setMessage failed, falling back to system: " + e.getMessage());
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
                XposedBridge.log("[WAEX] AlertDialogWpp setItems failed on Wpp builder: " + e.getMessage());
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
                XposedBridge.log("[WAEX] AlertDialogWpp setMultiChoiceItems failed on Wpp builder: " + e.getMessage());
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
                    // XposedBridge.log("[WAEX] AlertDialogWpp button failed: " + methodName + ", falling back to system");
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
                XposedBridge.log("[WAEX] AlertDialogWpp setView failed on Wpp builder: " + t.getMessage());
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
                XposedBridge.log("[WAEX] AlertDialogWpp setCancelable failed on Wpp builder: " + t.getMessage());
            }
        }
        return this;
    }

    private boolean mIsBottomSheet = true;

    public AlertDialogWpp setBottomSheet(boolean isBottomSheet) {
        mIsBottomSheet = isBottomSheet;
        return this;
    }

    public AlertDialogWpp asBottomSheet() {
        mIsBottomSheet = true;
        return this;
    }

    private void applyBottomSheetStyle(Dialog d) {
        if (d == null) return;
        Window window = d.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
            
            int backgroundColor = 0xFFFFFFFF;
            try {
                TypedValue typedValue = new TypedValue();
                if (mContext.getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true)) {
                    backgroundColor = typedValue.data;
                }
            } catch (Exception ignored) {}
            
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(backgroundColor);
            float radius = 16 * mContext.getResources().getDisplayMetrics().density;
            drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
            window.setBackgroundDrawable(drawable);
            
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
                    int nightModeFlags = mContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
                } catch (Exception ignored) {}
                
                // Exact WhatsApp dark-mode surface colors extracted from native bottom sheet
                int backgroundColor = isDarkMode ? 0xFF12181c : 0xFFFFFFFF;
                int primaryTextColor = isDarkMode ? 0xFFe9edef : 0xFF111B21;
                int secondaryTextColor = isDarkMode ? 0xFF8696a0 : 0xFF667781;
                int accentColor = isDarkMode ? 0xFF21c063 : 0xFF008069;
                try {
                    TypedValue typedValue = new TypedValue();
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
                final float capHeight = screenHeight * 0.95f;
                
                final RelativeLayout container = new RelativeLayout(mContext);
                container.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                container.setBackgroundColor(Color.TRANSPARENT);
                
                final LinearLayout mainLayout = new LinearLayout(mContext);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setPadding(dp20, dp16, dp20, (int) (32 * density));
                
                RelativeLayout.LayoutParams mainParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                mainParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                mainLayout.setLayoutParams(mainParams);
                
                // Rounded background for the sheet with perfect outline clipping
                GradientDrawable bgDrawable = new GradientDrawable();
                bgDrawable.setColor(backgroundColor);
                float radius = 24 * density;
                bgDrawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
                mainLayout.setBackground(bgDrawable);
                mainLayout.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                mainLayout.setClipToOutline(true);
                mainLayout.setElevation(8 * density);
                
                // Drag Handle
                android.view.View dragHandle = new android.view.View(mContext);
                LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams((int) (40 * density), (int) (4 * density));
                handleParams.gravity = Gravity.CENTER_HORIZONTAL;
                handleParams.bottomMargin = dp16;
                dragHandle.setLayoutParams(handleParams);
                
                GradientDrawable handleDrawable = new GradientDrawable();
                handleDrawable.setColor(secondaryTextColor & 0x33FFFFFF | 0x33000000);
                handleDrawable.setCornerRadius(2 * density);
                dragHandle.setBackground(handleDrawable);
                mainLayout.addView(dragHandle);
                
                // Implement touch-to-drag downward gesture using GPU-accelerated translation
                android.view.View.OnTouchListener dragListener = new android.view.View.OnTouchListener() {
                    private float initialY;
                    private float initialTranslationY;
                    private boolean isDragging = false;
                    
                    @Override
                    public boolean onTouch(android.view.View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialY = event.getRawY();
                                initialTranslationY = mainLayout.getTranslationY();
                                isDragging = true;
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                if (!isDragging) return false;
                                float deltaY = event.getRawY() - initialY;
                                float targetTranslation = initialTranslationY + deltaY;
                                if (targetTranslation < 0) {
                                    targetTranslation = 0;
                                }
                                mainLayout.setTranslationY(targetTranslation);
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                isDragging = false;
                                float currentTranslationY = mainLayout.getTranslationY();
                                int sheetHeight = mainLayout.getHeight();
                                if (currentTranslationY > (sheetHeight / 4.0f) || currentTranslationY > 150 * density) {
                                    // Dismiss downwards
                                    mainLayout.animate()
                                            .translationY(screenHeight)
                                            .setDuration(200)
                                            .withEndAction(dialog::dismiss)
                                            .start();
                                } else {
                                    // Snap back to fully open
                                    mainLayout.animate()
                                            .translationY(0)
                                            .setDuration(200)
                                            .start();
                                }
                                return true;
                        }
                        return false;
                    }
                };
                mainLayout.setOnTouchListener(dragListener);
                dragHandle.setOnTouchListener(dragListener);
                
                // Title — WDSTextView
                if (mTitleText != null) {
                    TextView titleView = createWdsTextView(mContext);
                    titleView.setText(mTitleText);
                    titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    titleView.setTypeface(Typeface.DEFAULT_BOLD);
                    titleView.setTextColor(primaryTextColor);
                    titleView.setPadding(0, 0, 0, dp12);
                    mainLayout.addView(titleView);
                }
                
                // Scrollable content
                androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(mContext);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
                scrollView.setLayoutParams(scrollParams);
                
                LinearLayout scrollContentLayout = new LinearLayout(mContext);
                scrollContentLayout.setOrientation(LinearLayout.VERTICAL);
                scrollContentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                scrollView.addView(scrollContentLayout);
                
                if (mMessageText != null) {
                    TextView messageView = createWdsTextView(mContext);
                    messageView.setText(mMessageText);
                    messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    messageView.setTextColor(secondaryTextColor);
                    scrollContentLayout.addView(messageView);
                } else if (mItems == null && mCustomView == null && mMultiChoiceItems == null) {
                    TextView messageView = createWdsTextView(mContext);
                    messageView.setText("Are you sure you want to proceed?");
                    messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    messageView.setTextColor(secondaryTextColor);
                    scrollContentLayout.addView(messageView);
                }
                
                if (mCustomView != null) {
                    try {
                        android.view.ViewParent parent = mCustomView.getParent();
                        if (parent instanceof ViewGroup) {
                            ((ViewGroup) parent).removeView(mCustomView);
                        }
                    } catch (Exception ignored) {}
                    LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    customParams.topMargin = dp8;
                    customParams.bottomMargin = dp8;
                    mCustomView.setLayoutParams(customParams);
                    scrollContentLayout.addView(mCustomView);
                }
                
                if (mItems != null) {
                    LinearLayout itemsLayout = new LinearLayout(mContext);
                    itemsLayout.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams itemsParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    itemsParams.topMargin = dp8;
                    itemsParams.bottomMargin = dp8;
                    itemsLayout.setLayoutParams(itemsParams);
                    
                    for (int i = 0; i < mItems.length; i++) {
                        final int index = i;
                        TextView itemView = createWdsTextView(mContext);
                        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        itemView.setLayoutParams(itemLp);
                        itemView.setText(mItems[index]);
                        itemView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                        itemView.setTextColor(primaryTextColor);
                        itemView.setGravity(Gravity.CENTER_VERTICAL);
                        itemView.setPadding((int) (16 * density), (int) (14 * density), (int) (16 * density), (int) (14 * density));
                        
                        ColorDrawable normalBg = new ColorDrawable(Color.TRANSPARENT);
                        ColorStateList itemRippleColor = ColorStateList.valueOf(secondaryTextColor & 0x15FFFFFF | 0x15000000);
                        RippleDrawable itemRipple = new RippleDrawable(itemRippleColor, normalBg, null);
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
                    LinearLayout itemsLayout = new LinearLayout(mContext);
                    itemsLayout.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams itemsParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    itemsParams.topMargin = dp8;
                    itemsParams.bottomMargin = dp8;
                    itemsLayout.setLayoutParams(itemsParams);
                    
                    for (int i = 0; i < mMultiChoiceItems.length; i++) {
                        final int index = i;
                        LinearLayout itemRow = new LinearLayout(mContext);
                        itemRow.setOrientation(LinearLayout.HORIZONTAL);
                        itemRow.setGravity(Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        itemRow.setLayoutParams(rowLp);
                        itemRow.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
                        
                        // Label — WDSTextView
                        TextView labelView = createWdsTextView(mContext);
                        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                        labelView.setLayoutParams(labelLp);
                        labelView.setText(mMultiChoiceItems[index]);
                        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        labelView.setTextColor(primaryTextColor);

                        // Switch — WDSSwitch (native WhatsApp)
                        CompoundButton switchView;
                        try {
                            Class<?> wdsSwitchClass = mContext.getClassLoader().loadClass("com.whatsapp.ui.wds.components.toggle.WDSSwitch");
                            switchView = (CompoundButton) wdsSwitchClass.getConstructor(android.content.Context.class, AttributeSet.class).newInstance(mContext, null);
                        } catch (Throwable t) {
                            XposedBridge.log("[WAEX] WDSSwitch failed, fallback MaterialSwitch: " + t.getMessage());
                            try {
                                switchView = (CompoundButton) XposedHelpers.newInstance(
                                        XposedHelpers.findClass("com.google.android.material.materialswitch.MaterialSwitch", mContext.getClassLoader()), mContext);
                            } catch (Throwable t2) {
                                switchView = new Switch(mContext);
                            }
                        }
                        final CompoundButton finalSwitchView = switchView;
                        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        finalSwitchView.setLayoutParams(checkLp);
                        finalSwitchView.setChecked(mCheckedItems != null && index < mCheckedItems.length && mCheckedItems[index]);
                        finalSwitchView.setClickable(false);
                        
                        itemRow.addView(labelView);
                        itemRow.addView(finalSwitchView);
                        
                        ColorDrawable normalBg = new ColorDrawable(Color.TRANSPARENT);
                        ColorStateList itemRippleColor = ColorStateList.valueOf(secondaryTextColor & 0x15FFFFFF | 0x15000000);
                        RippleDrawable itemRipple = new RippleDrawable(itemRippleColor, normalBg, null);
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
                LinearLayout buttonsLayout = new LinearLayout(mContext);
                buttonsLayout.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                buttonsParams.topMargin = dp16;
                buttonsLayout.setLayoutParams(buttonsParams);
                
                // Positive Button — WDSButton FILLED
                if (mPositiveButtonText != null) {
                    View wdsPosBtn = createWdsButton(mContext, mPositiveButtonText, "FILLED");
                    if (wdsPosBtn != null) {
                        LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        if (mNegativeButtonText != null) posParams.bottomMargin = dp8;
                        wdsPosBtn.setLayoutParams(posParams);
                        wdsPosBtn.setOnClickListener(v -> {
                            if (mPositiveListener != null) {
                                mPositiveListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                            }
                            dialog.dismiss();
                        });
                        mPositiveButtonView = (TextView) wdsPosBtn;
                        buttonsLayout.addView(wdsPosBtn);
                    } else {
                        // Fallback plain TextView button
                        TextView posButton = createWdsTextView(mContext);
                        LinearLayout.LayoutParams posParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
                        if (mNegativeButtonText != null) posParams.bottomMargin = dp8;
                        posButton.setLayoutParams(posParams);
                        posButton.setText(mPositiveButtonText);
                        posButton.setGravity(Gravity.CENTER);
                        posButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        posButton.setTypeface(Typeface.DEFAULT_BOLD);
                        GradientDrawable posBg = new GradientDrawable();
                        posBg.setColor(accentColor);
                        posBg.setCornerRadius(24 * density);
                        posButton.setBackground(new RippleDrawable(
                                ColorStateList.valueOf(0x22FFFFFF), posBg, null));
                        posButton.setTextColor(isDarkMode ? Color.BLACK : Color.WHITE);
                        posButton.setOnClickListener(v -> {
                            if (mPositiveListener != null) mPositiveListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                            dialog.dismiss();
                        });
                        mPositiveButtonView = posButton;
                        buttonsLayout.addView(posButton);
                    }
                }

                // Negative Button — WDSButton OUTLINE (native WhatsApp outlined style)
                if (mNegativeButtonText != null) {
                    View wdsNegBtn = createWdsButton(mContext, mNegativeButtonText, "OUTLINE");
                    if (wdsNegBtn != null) {
                        LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        wdsNegBtn.setLayoutParams(negParams);
                        wdsNegBtn.setOnClickListener(v -> {
                            if (mNegativeListener != null) {
                                mNegativeListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                            }
                            dialog.dismiss();
                        });
                        buttonsLayout.addView(wdsNegBtn);
                    } else {
                        // Fallback — plain TextView with accent text color
                        TextView negButton = createWdsTextView(mContext);
                        LinearLayout.LayoutParams negParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
                        negButton.setLayoutParams(negParams);
                        negButton.setText(mNegativeButtonText);
                        negButton.setGravity(Gravity.CENTER);
                        negButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        negButton.setTypeface(Typeface.DEFAULT_BOLD);
                        negButton.setTextColor(accentColor);
                        negButton.setOnClickListener(v -> {
                            if (mNegativeListener != null) mNegativeListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                            dialog.dismiss();
                        });
                        buttonsLayout.addView(negButton);
                    }
                }
                 
                 mainLayout.addView(buttonsLayout);
                 container.addView(mainLayout);

                mainLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mainLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int measuredHeight = mainLayout.getHeight();
                        int defaultHeight = (int) (screenHeight * 0.60f);
                        int maxHeight = (int) capHeight;

                        if (mIsFullHeight) {
                            if (measuredHeight > defaultHeight) {
                                ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                lp.height = defaultHeight;
                                mainLayout.setLayoutParams(lp);
                                
                                LinearLayout.LayoutParams sLp = (LinearLayout.LayoutParams) scrollView.getLayoutParams();
                                sLp.height = 0;
                                sLp.weight = 1.0f;
                                scrollView.setLayoutParams(sLp);

                                setupExpandingGestures(scrollView, mainLayout, dragHandle, defaultHeight, maxHeight, screenHeight, density, dialog);
                            }
                        } else {
                            if (measuredHeight > halfScreenHeight) {
                                ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                lp.height = halfScreenHeight;
                                mainLayout.setLayoutParams(lp);
                                
                                LinearLayout.LayoutParams sLp = (LinearLayout.LayoutParams) scrollView.getLayoutParams();
                                sLp.height = 0;
                                sLp.weight = 1.0f;
                                scrollView.setLayoutParams(sLp);
                            }
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
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setGravity(Gravity.BOTTOM);
                    window.getDecorView().setPadding(0, 0, 0, 0);
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    
                    // Add standard bottom sheet background dimming scrim
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setDimAmount(0.5f);
                    
                    window.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                }
                
                mCreate = dialog;
                return mCreate;
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] BottomSheetDialog instantiation failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
        if (shouldUseSystem()) {
            mCreate = mAlertDialog.create();
        } else {
            try {
                mCreate = (Dialog) XposedHelpers.callMethod(mAlertDialogWpp, "create");
            } catch (Throwable t) {
                // XposedBridge.log("[WAEX] AlertDialogWpp.create() failed, using system fallback");
                mCreate = mAlertDialog.create();
            }
        }
        return mCreate;
    }

    private void setupExpandingGestures(
            final androidx.core.widget.NestedScrollView scrollView,
            final LinearLayout mainLayout,
            final android.view.View dragHandle,
            final int defaultHeight,
            final int maxHeight,
            final int screenHeight,
            final float density,
            final android.app.Dialog dialog) {

        android.view.View.OnTouchListener expandListener = new android.view.View.OnTouchListener() {
            private float initialY = 0;
            private int initialHeight;
            private boolean isExpanding = false;

            @Override
            public boolean onTouch(android.view.View v, MotionEvent event) {
                int currentHeight = mainLayout.getHeight();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        initialHeight = currentHeight;
                        isExpanding = false;
                        if (v == scrollView) {
                            scrollView.onTouchEvent(event);
                        }
                        return true; // Claim the touch gesture!
                    case MotionEvent.ACTION_MOVE:
                        if (initialY == 0) {
                            initialY = event.getRawY();
                            initialHeight = currentHeight;
                        }
                        float deltaY = event.getRawY() - initialY;
                        
                        // Dragging UP (deltaY < 0): Expand the sheet if it's not yet at maxHeight
                        if (deltaY < 0 && currentHeight < maxHeight) {
                            isExpanding = true;
                            int newHeight = (int) (initialHeight - deltaY);
                            if (newHeight > maxHeight) newHeight = maxHeight;
                            
                            ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                            lp.height = newHeight;
                            mainLayout.setLayoutParams(lp);
                            return true; // Consume event to prevent internal scrolling
                        }
                        
                        // Dragging DOWN (deltaY > 0): Shrink the sheet if it's above defaultHeight and scrollView is at the top
                        if (deltaY > 0 && !scrollView.canScrollVertically(-1)) {
                            if (currentHeight > defaultHeight) {
                                isExpanding = true;
                                int newHeight = (int) (initialHeight - deltaY);
                                if (newHeight < defaultHeight) newHeight = defaultHeight;
                                
                                ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                lp.height = newHeight;
                                mainLayout.setLayoutParams(lp);
                                return true; // Consume event
                            } else {
                                // Translate mainLayout down to dismiss
                                isExpanding = true;
                                mainLayout.setTranslationY(deltaY);
                                return true;
                            }
                        }
                        
                        // If we are not expanding/shrinking, forward the touch event to the ScrollView
                        if (v == scrollView) {
                            scrollView.onTouchEvent(event);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        initialY = 0; // Reset
                        if (isExpanding) {
                            isExpanding = false;
                            float translationY = mainLayout.getTranslationY();
                            if (translationY > 100 * density) {
                                // Dismiss downwards
                                mainLayout.animate()
                                        .translationY(screenHeight)
                                        .setDuration(200)
                                        .withEndAction(dialog::dismiss)
                                        .start();
                            } else if (translationY > 0) {
                                // Snap translation back
                                mainLayout.animate()
                                        .translationY(0)
                                        .setDuration(200)
                                        .start();
                            } else {
                                // Snap height to either defaultHeight or maxHeight
                                int targetHeight = (mainLayout.getHeight() > (defaultHeight + maxHeight) / 2) ? maxHeight : defaultHeight;
                                ValueAnimator animator = ValueAnimator.ofInt(mainLayout.getHeight(), targetHeight);
                                animator.setDuration(200);
                                animator.addUpdateListener(animation -> {
                                    ViewGroup.LayoutParams lp = mainLayout.getLayoutParams();
                                    lp.height = (int) animation.getAnimatedValue();
                                    mainLayout.setLayoutParams(lp);
                                });
                                animator.start();
                            }
                            return true;
                        }
                        if (v == scrollView) {
                            scrollView.onTouchEvent(event);
                            return true;
                        }
                        break;
                }
                return false;
            }
        };

        scrollView.setOnTouchListener(expandListener);
        mainLayout.setOnTouchListener(expandListener);
        if (dragHandle != null) {
            dragHandle.setOnTouchListener(expandListener);
        }
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
            XposedBridge.log("[WAEX] AlertDialogWpp.show() failed: " + t.getMessage());
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

    private TextView createWdsTextView(Context context) {
        try {
            Class<?> wdsTvClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.textview.WDSTextView");
            return (TextView) wdsTvClass.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
        } catch (Throwable t) {
            return new TextView(context);
        }
    }

    private View createWdsButton(Context context, CharSequence text, String variant) {
        try {
            Class<?> wdsButtonClass = context.getClassLoader().loadClass("com.whatsapp.ui.wds.components.button.WDSButton");
            View button = (View) wdsButtonClass.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
            ((TextView) button).setText(text);
            try {
                Class<?> variantClass = context.getClassLoader().loadClass("X.0xb");
                Object variantVal = Enum.valueOf((Class<Enum>) variantClass, variant);
                XposedHelpers.callMethod(button, "setVariant", variantVal);
            } catch (Throwable ignored) {}
            return button;
        } catch (Throwable t) {
            return null;
        }
    }

}
