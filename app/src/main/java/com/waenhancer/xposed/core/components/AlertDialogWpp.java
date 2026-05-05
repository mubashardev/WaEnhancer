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

    public static void initDialog(ClassLoader loader) {
        try {
            getAlertDialog = Unobfuscator.loadMaterialAlertDialog(loader);
            if (getAlertDialog == null) {
                isAvailable = false;
                return;
            }
            Class<?> alertDialogClass = getAlertDialog.getReturnType();
            XposedBridge.log("[WAE] AlertDialogWpp: Initializing with class " + alertDialogClass.getName());

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
            if (setMessageMethod != null) XposedBridge.log("[WAE] AlertDialogWpp: Found setMessageMethod: " + setMessageMethod.getName());

            setItemsMethod = ReflectionUtils.findMethodUsingFilterIfExists(alertDialogClass,
                    method -> method.getParameterCount() == 2 &&
                    ((method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && CharSequence[].class.isAssignableFrom(method.getParameterTypes()[1])) ||
                     (CharSequence[].class.isAssignableFrom(method.getParameterTypes()[0]) && method.getParameterTypes()[1].equals(DialogInterface.OnClickListener.class))));
            if (setItemsMethod != null) XposedBridge.log("[WAE] AlertDialogWpp: Found setItemsMethod: " + setItemsMethod.getName());

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
                XposedBridge.log("[WAE] AlertDialogWpp: Found " + buttons.length + " button methods");
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
                
                XposedBridge.log("[WAE] AlertDialogWpp: Using button fallback mapping (Pos=" + setPositiveButtonMethod.getName() + ")");
            }

            isAvailable = true;
            XposedBridge.log("[WAE] AlertDialogWpp initialized successfully");
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
            XposedBridge.log("[WAE] " + m.getReturnType().getSimpleName() + " " + m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
        }
        XposedBridge.log("[WAE] ---------------------------------------");
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
        return mIsUsingSystem || mAlertDialogWpp == null;
    }

    public AlertDialogWpp setTitle(String title) {
        if (shouldUseSystem()) {
            mAlertDialog.setTitle(title);
        } else {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title);
            } catch (Throwable t) {
                mAlertDialog.setTitle(title);
            }
        }
        return this;
    }

    public AlertDialogWpp setTitle(int title) {
        if (shouldUseSystem()) {
            mAlertDialog.setTitle(title);
        } else {
            try {
                XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", getContext().getString(title));
            } catch (Throwable t) {
                mAlertDialog.setTitle(title);
            }
        }
        return this;
    }

    public AlertDialogWpp setTitle(CharSequence title) {
        if (title == null || title.toString().isEmpty()) {
            title = "Restart Required";
        }
        if (shouldUseSystem()) {
            mAlertDialog.setTitle(title);
        } else {
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
                mAlertDialog.setTitle(title);
            }
        }
        return this;
    }

    public AlertDialogWpp setMessage(CharSequence message) {
        if (message == null || message.toString().isEmpty()) {
            message = "WhatsApp needs to be restarted to apply the changes you made in WaEnhancer. Would you like to restart now?";
        }
        if (shouldUseSystem()) {
            mAlertDialog.setMessage(message);
        } else {
            try {
                if (setMessageMethod != null) {
                    setMessageMethod.invoke(mAlertDialogWpp, message);
                } else {
                    XposedHelpers.callMethod(mAlertDialogWpp, "setMessage", message);
                }
            } catch (Throwable e) {
                XposedBridge.log("[WAE] AlertDialogWpp setMessage failed, falling back to system: " + e.getMessage());
                mIsUsingSystem = true;
                mAlertDialog.setMessage(message);
            }
        }
        return this;
    }

    public AlertDialogWpp setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
        if (shouldUseSystem()) {
            mAlertDialog.setItems(items, listener);
        } else {
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
                mAlertDialog.setItems(items, listener);
            }
        }
        return this;
    }


    public AlertDialogWpp setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
        if (shouldUseSystem()) {
            mAlertDialog.setMultiChoiceItems(items, checkedItems, listener);
        } else {
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
                mAlertDialog.setMultiChoiceItems(items, checkedItems, listener);
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
        if (shouldUseSystem()) {
            mAlertDialog.setNegativeButton(text, listener);
        } else {
            callBuilderMethod(setNegativeButtonMethod, "setNegativeButton", text, listener);
        }
        return this;
    }

    public AlertDialogWpp setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (shouldUseSystem()) {
            mAlertDialog.setNeutralButton(text, listener);
        } else {
            callBuilderMethod(setNeutralButtonMethod, "setNeutralButton", text, listener);
        }
        return this;
    }

    public AlertDialogWpp setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (shouldUseSystem()) {
            mAlertDialog.setPositiveButton(text, listener);
        } else {
            callBuilderMethod(setPositiveButtonMethod, "setPositiveButton", text, listener);
        }
        return this;
    }

    public AlertDialogWpp setView(View view) {
        if (shouldUseSystem()) {
            mAlertDialog.setView(view);
        } else {
            XposedHelpers.callMethod(mAlertDialogWpp, "setView", view);
        }
        return this;
    }


    public Dialog create() {
        if (mCreate != null) return mCreate;
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

    public void show() {
        if (mContext instanceof Activity) {
            Activity activity = (Activity) mContext;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
        }
        try {
            if (shouldUseSystem()) {
                mAlertDialog.show();
            } else {
                create().show();
            }
        } catch (Throwable t) {
            XposedBridge.log("[WAE] AlertDialogWpp.show() failed: " + t.getMessage());
            // Last resort
            try { mAlertDialog.show(); } catch (Throwable ignored) {}
        }
    }

}
