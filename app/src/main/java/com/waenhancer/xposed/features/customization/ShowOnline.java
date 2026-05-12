package com.waenhancer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.TextUtilsCompat;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.WaContactWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.core.devkit.UnobfuscatorCache;
import com.waenhancer.xposed.features.listeners.ContactItemListener;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ShowOnline extends Feature {

    private Object mStatusUser;
    private Object mInstancePresence;
    private Method sendPresenceMethod;
    private Method tcTokenMethod;
    private Method getStatusUser;
    private java.lang.reflect.Field fieldTokenDBInstance;
    private Class<?> tokenClass;

    public ShowOnline(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    private static void setStatus(String status, ImageView csDot, TextView lastSeenText) {
        if (!TextUtils.isEmpty(status) && status.trim().equals(UnobfuscatorCache.getInstance().getString("online"))) {
            if (csDot != null) {
                csDot.setVisibility(View.VISIBLE);
            }
        }

        if (lastSeenText != null) {
            if (!TextUtils.isEmpty(status)) {
                lastSeenText.setText(status);
                if (UnobfuscatorCache.getInstance().getString("online").equals(status)) {
                    lastSeenText.setTextColor(Color.GREEN);
                } else {
                    lastSeenText.setTextColor(0xffcac100);
                }
            } else {
                lastSeenText.setText("");
                lastSeenText.setTextColor(Color.GRAY);
            }
        }
    }

    @Override
    public void doHook() throws Throwable {

        var showOnlineText = prefs.getBoolean("showonlinetext", false);
        var showOnlineIcon = prefs.getBoolean("dotonline", false);
        if (!showOnlineText && !showOnlineIcon) return;

        var classViewHolder = Unobfuscator.loadViewHolder(classLoader);
        XposedBridge.hookAllConstructors(classViewHolder, new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var view = (View) param.args[1];
                var context = (Context) param.args[0];
                LinearLayout content = view.findViewById(Utils.getID("conversations_row_content", "id"));
                if (content == null) {
                    content = view.findViewById(Utils.getID("row_content", "id"));
                }
                if (showOnlineText) {
                    var linearLayout = new LinearLayout(context);
                    linearLayout.setGravity(Gravity.END | Gravity.TOP);
                    content.addView(linearLayout);

                    // Add TextView to show last seen time
                    TextView lastSeenText = new TextView(context);
                    lastSeenText.setId(0x7FFF0002);
                    lastSeenText.setTextSize(12f);
                    lastSeenText.setText("");
                    lastSeenText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
                    lastSeenText.setGravity(Gravity.CENTER_VERTICAL);
                    lastSeenText.setVisibility(View.VISIBLE);
                    linearLayout.addView(lastSeenText);
                }
                if (showOnlineIcon) {
                    var contactView = (FrameLayout) view.findViewById(Utils.getID("contact_selector", "id"));
                    var firstChild = contactView.getChildAt(0);
                    var isLeftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR;
                    if (firstChild instanceof ImageView) {
                        ImageView photoView = (ImageView) firstChild;
                        contactView.removeView(photoView);

                        if (photoView.getId() == View.NO_ID) {
                            photoView.setId(0x7FFF0004);
                        }

                        var relativeLayout = new RelativeLayout(context);
                        relativeLayout.setId(0x7FFF0003);
                        var params = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.addRule(RelativeLayout.CENTER_IN_PARENT);
                        photoView.setLayoutParams(params);
                        relativeLayout.addView(photoView);
                        contactView.addView(relativeLayout);

                        var imageView = new ImageView(context);
                        imageView.setId(0x7FFF0001);
                        var params2 = new RelativeLayout.LayoutParams(Utils.dipToPixels(13), Utils.dipToPixels(13));
                        params2.addRule(RelativeLayout.ALIGN_BOTTOM, photoView.getId());
                        params2.addRule(isLeftToRight ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, photoView.getId());
                        if (isLeftToRight) {
                            params2.rightMargin = Utils.dipToPixels(1.5f);
                        } else {
                            params2.leftMargin = Utils.dipToPixels(1.5f);
                        }
                        params2.bottomMargin = Utils.dipToPixels(1.5f);
                        imageView.setLayoutParams(params2);

                        android.graphics.drawable.GradientDrawable dotDrawable = new android.graphics.drawable.GradientDrawable();
                        dotDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        dotDrawable.setColor(0xFF25D366); // WhatsApp Green
                        boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        int strokeColor = isDark ? 0xFF121212 : Color.WHITE;
                        dotDrawable.setStroke(Utils.dipToPixels(1.5f), strokeColor);

                        imageView.setImageDrawable(dotDrawable);
                        imageView.setVisibility(View.INVISIBLE);
                        relativeLayout.addView(imageView);
                    } else if (firstChild instanceof RelativeLayout) {
                        RelativeLayout relativeLayout = (RelativeLayout) firstChild;
                        var photoView = (ImageView) relativeLayout.getChildAt(0);

                        if (photoView.getId() == View.NO_ID) {
                            photoView.setId(0x7FFF0004);
                        }

                        var params = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.addRule(RelativeLayout.CENTER_IN_PARENT);
                        photoView.setLayoutParams(params);

                        var imageView = new ImageView(context);
                        imageView.setId(0x7FFF0001);
                        var params2 = new RelativeLayout.LayoutParams(Utils.dipToPixels(13), Utils.dipToPixels(13));
                        params2.addRule(RelativeLayout.ALIGN_BOTTOM, photoView.getId());
                        params2.addRule(isLeftToRight ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, photoView.getId());
                        if (isLeftToRight) {
                            params2.rightMargin = Utils.dipToPixels(1.5f);
                        } else {
                            params2.leftMargin = Utils.dipToPixels(1.5f);
                        }
                        params2.bottomMargin = Utils.dipToPixels(1.5f);
                        imageView.setLayoutParams(params2);

                        android.graphics.drawable.GradientDrawable dotDrawable = new android.graphics.drawable.GradientDrawable();
                        dotDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        dotDrawable.setColor(0xFF25D366); // WhatsApp Green
                        boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        int strokeColor = isDark ? 0xFF121212 : Color.WHITE;
                        dotDrawable.setStroke(Utils.dipToPixels(1.5f), strokeColor);

                        imageView.setImageDrawable(dotDrawable);
                        imageView.setVisibility(View.INVISIBLE);
                        relativeLayout.addView(imageView);
                    }
                }
            }
        });

        getStatusUser = Unobfuscator.loadStatusUserMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(getStatusUser));
        sendPresenceMethod = Unobfuscator.loadSendPresenceMethod(classLoader);
        logDebug("sendPresenceMethod", Unobfuscator.getMethodDescriptor(sendPresenceMethod));
        tcTokenMethod = Unobfuscator.loadTcTokenMethod(classLoader);

        XposedBridge.hookAllConstructors(getStatusUser.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mStatusUser = param.thisObject;
            }
        });

        XposedBridge.hookAllConstructors(sendPresenceMethod.getDeclaringClass(), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mInstancePresence = param.thisObject;
            }
        });

        // load methods
        tokenClass = sendPresenceMethod.getParameterTypes()[2];
        fieldTokenDBInstance = ReflectionUtils.getFieldByExtendType(sendPresenceMethod.getDeclaringClass(), tcTokenMethod.getDeclaringClass());

        // Register listener
        ContactItemListener.contactListeners.add(new ContactItemListener.OnContactItemListener() {
            @Override
            @SuppressLint("ResourceType")
            public void onBind(WaContactWpp waContact, View view) {
                try {
                    var userJid = waContact.getUserJid();
                    if (userJid.isGroup()) return;

                    ImageView csDot = showOnlineIcon ? view.findViewById(0x7FFF0001) : null;
                    if (showOnlineIcon && csDot != null) {
                        csDot.setVisibility(View.INVISIBLE);
                    }
                    TextView lastSeenText = showOnlineText ? view.findViewById(0x7FFF0002) : null;

                    var tokenDBInstance = fieldTokenDBInstance.get(mInstancePresence);
                    var tokenData = ReflectionUtils.callMethod(tcTokenMethod, tokenDBInstance, userJid.userJid);
                    var tokenObj = tokenClass.getConstructors()[0].newInstance(tokenData == null ? null : XposedHelpers.getObjectField(tokenData, "A01"));
                    sendPresenceMethod.invoke(null, userJid.userJid, null, tokenObj, mInstancePresence);
                    var status = (String) ReflectionUtils.callMethod(getStatusUser, mStatusUser, waContact.getObject(), false);
                    setStatus(status, csDot, lastSeenText);
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Conversation";
    }
}
