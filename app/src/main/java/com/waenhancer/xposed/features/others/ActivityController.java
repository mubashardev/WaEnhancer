package com.waenhancer.xposed.features.others;

import static com.waenhancer.xposed.features.general.LiteMode.REQUEST_FOLDER;
import static com.waenhancer.xposed.features.general.LiteMode.getDownloadsUri;
import static com.waenhancer.xposed.features.general.LiteMode.processDownloadResult;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.preference.ContactPickerPreference;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ActivityController extends Feature {

    private static String Key;
    private static String pickingKey;
    private static Class<?> statusDistributionClass;
    private static boolean isOptimizing = false;

    public static void setPickingKey(String key) {
        pickingKey = key;
    }

    public static String getPickingKey() {
        return pickingKey;
    }

    public static Class<?> getStatusDistributionClass() {
        return statusDistributionClass;
    }

    public ActivityController(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var clazz = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                ".SettingsNotifications");
        Class<?> statusDistribution = Unobfuscator.loadStatusDistributionClass(classLoader);
        statusDistributionClass = statusDistribution;

        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String className = param.thisObject.getClass().getName();
                if (!className.endsWith(".SettingsNotifications"))
                    return;
                var activity = (Activity) param.thisObject;
                var intent = activity.getIntent();
                if (intent.getBooleanExtra("contact_mode", false)) {
                    contactController(intent, activity, statusDistribution);
                } else if (intent.getBooleanExtra("download_mode", false)) {
                    downloadController(activity, intent);
                }
            }
        });

        Class<?> aboutClass = WppCore.getAboutActivityClass(classLoader);
        if (aboutClass != null) {
            XposedHelpers.findAndHookMethod(aboutClass, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    Intent intent = activity.getIntent();
                    boolean shouldOptimize = intent.getBooleanExtra("wae_optimize_db", false);
                    XposedBridge.log("[WAEX] About onCreate called, wae_optimize_db: " + shouldOptimize);
                    if (shouldOptimize) {
                        runDbOptimizationScreen(activity);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(Activity.class, "onBackPressed", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject.getClass() != aboutClass)
                        return;
                    Activity activity = (Activity) param.thisObject;
                    if (activity.getIntent().getBooleanExtra("wae_optimize_db", false)) {
                        if (isOptimizing) {
                            param.setResult(null); // Disable back button during optimization (Step 2)
                        } else {
                            activity.finish(); // Allow user to exit in Step 1
                            param.setResult(null);
                        }
                    }
                }
            });
        }

        XposedHelpers.findAndHookMethod("com.whatsapp.status.audienceselector.StatusTemporalRecipientsActivity",
                classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var activity = (Activity) param.thisObject;
                        var intent = activity.getIntent();
                        if (intent.getBooleanExtra("contact_mode", false)) {
                            var toolbar = XposedHelpers.callMethod(activity, "getSupportActionBar");
                            var methods = ReflectionUtils.findAllMethodsUsingFilter(toolbar.getClass(),
                                    method -> method.getParameterCount() == 1
                                            && method.getParameterTypes()[0] == CharSequence.class);
                            ReflectionUtils.callMethod(methods[1], toolbar,
                                    com.waenhancer.xposed.core.FeatureLoader.getModuleString(activity, R.string.select_contacts));
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult", int.class, int.class, Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var activity = (Activity) param.thisObject;
                        var id = (int) param.args[0];
                        Intent intent = (Intent) param.args[2];

                        boolean isMyClass = (clazz == activity.getClass());

                        if (id == ContactPickerPreference.REQUEST_CONTACT_PICKER && intent != null) {
                            if (isMyClass && activity.getIntent() != null && activity.getIntent().getBooleanExtra("contact_mode", false)) {
                                processResultContact(intent, activity);
                                activity.finish();
                            } else {
                                processEmbeddedResultContact(intent, activity);
                            }
                            return;
                        }

                        if (!isMyClass)
                            return;

                        if (id == com.waenhancer.xposed.features.general.VideoNoteAttachment.REQUEST_PICK_VIDEO_NOTE
                                && intent != null) {
                            var uriStr = intent.getDataString();
                            Intent intent2 = new Intent();
                            intent2.putExtra("path", uriStr);
                            activity.setResult(Activity.RESULT_OK, intent2);
                            // VideoNoteAttachment needs to handle it via WppCore / broadcasting
                            com.waenhancer.xposed.features.general.VideoNoteAttachment
                                    .handleVideoPicked(intent.getData());
                        } else if (id == REQUEST_FOLDER && (int) param.args[1] == Activity.RESULT_OK) {
                            var uriStr = processDownloadResult(activity, intent);
                            Intent intent2 = new Intent();
                            intent2.putExtra("path", uriStr);
                            intent2.putExtra("key", Key);
                            /* Log removed */
                            activity.setResult(Activity.RESULT_OK, intent2);
                        }
                        activity.finish();
                    }
                });

    }

    private static void processEmbeddedResultContact(Intent intent, Activity activity) {
        try {
            var instance = intent.getExtras().get("status_distribution");
            var listContactsField = ReflectionUtils.findFieldUsingFilter(instance.getClass(),
                    field -> field.getType() == List.class);
            var listContacts = (List) ReflectionUtils.getObjectField(listContactsField, instance);
            var contacts = new ArrayList<String>();
            for (Object contactUserJid : listContacts) {
                var rawContacts = new FMessageWpp.UserJid(contactUserJid).getPhoneRawString();
                contacts.add(rawContacts);
            }
            if (pickingKey != null) {
                ContactPickerPreference.updatePreferenceValue(pickingKey, contacts);
            }
        } catch (Exception e) {
            de.robv.android.xposed.XposedBridge.log("[WaEnhancerX] Error processing embedded contact picker result: " + e.getMessage());
        }
    }

    private static void processResultContact(Intent intent, Activity activity) {
        var instance = intent.getExtras().get("status_distribution");
        var listContactsField = ReflectionUtils.findFieldUsingFilter(instance.getClass(),
                field -> field.getType() == List.class);
        var listContacts = (List) ReflectionUtils.getObjectField(listContactsField, instance);
        var contacts = new ArrayList<String>();
        for (Object contactUserJid : listContacts) {
            var rawContacts = new FMessageWpp.UserJid(contactUserJid).getPhoneRawString();
            contacts.add(rawContacts);
        }
        Intent intent2 = new Intent();
        intent2.putStringArrayListExtra("contacts", contacts);
        intent2.putExtra("key", Key);
        activity.setResult(Activity.RESULT_OK, intent2);
    }

    private void downloadController(Activity activity, Intent intent2) {
        Key = intent2.getStringExtra("key");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDownloadsUri());
        activity.startActivityForResult(intent, REQUEST_FOLDER);
    }

    private static void contactController(Intent intent, Activity activity, Class<?> statusDistribution)
            throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Key = intent.getStringExtra("key");
        var contacts = intent.getStringArrayListExtra("contacts");
        var intent2 = new Intent();
        intent2.setClassName(activity.getPackageName(),
                "com.whatsapp.status.audienceselector.StatusTemporalRecipientsActivity");
        intent2.putExtra("contact_mode", true);
        intent2.putExtra("is_black_list", false);
        List<Object> listContacts = new ArrayList<>();
        if (contacts != null) {
            for (String contact : contacts) {
                try {
                    Object jid = WppCore.createUserJid(contact);
                    listContacts.add(jid);
                } catch (Exception ignored) {
                }
            }
        }
        Constructor constructor = ReflectionUtils.findConstructorUsingFilter(statusDistribution,
                constructor1 -> constructor1.getParameterCount() > 5);
        Object[] params = ReflectionUtils.initArray(constructor.getParameterTypes());
        var lists = ReflectionUtils.findClassesOfType(constructor.getParameterTypes(), List.class);
        for (int i = 0; i < lists.size(); i++) {
            params[lists.get(i).first] = new ArrayList<>();
        }
        params[lists.get(0).first] = listContacts;
        Parcelable instance = (Parcelable) constructor.newInstance(params);
        intent2.putExtra("status_distribution", instance);
        activity.startActivityForResult(intent2, ContactPickerPreference.REQUEST_CONTACT_PICKER);
    }

    private void runDbOptimizationScreen(final Activity activity) {
        // Reset optimizing flag on start
        isOptimizing = false;

        // Keep screen on
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Show/style action bar natively
        try {
            if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                var actionBar = ((androidx.appcompat.app.AppCompatActivity) activity).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.show();
                    actionBar.setTitle("Database Optimization");
                    actionBar.setDisplayHomeAsUpEnabled(true);
                }
            } else if (activity.getActionBar() != null) {
                activity.getActionBar().show();
                activity.getActionBar().setTitle("Database Optimization");
                activity.getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        } catch (Throwable ignored) {}

        // Check if dark theme is active
        boolean isDarkTheme = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // Resolve theme colors dynamically
        android.util.TypedValue bgTv = new android.util.TypedValue();
        int colorBgVal = isDarkTheme ? 0xff0b141a : 0xffffffff;
        if (activity.getTheme().resolveAttribute(android.R.attr.windowBackground, bgTv, true)) {
            colorBgVal = bgTv.resourceId != 0 ? activity.getResources().getColor(bgTv.resourceId) : bgTv.data;
        }
        final String bgColor = String.format("#%06X", (0xFFFFFF & colorBgVal));

        android.util.TypedValue primaryTv = new android.util.TypedValue();
        int colorPrimaryVal = isDarkTheme ? 0xffe9edef : 0xff111b21;
        if (activity.getTheme().resolveAttribute(android.R.attr.textColorPrimary, primaryTv, true)) {
            colorPrimaryVal = primaryTv.resourceId != 0 ? activity.getResources().getColor(primaryTv.resourceId) : primaryTv.data;
        }
        final String txtPrimary = String.format("#%06X", (0xFFFFFF & colorPrimaryVal));

        android.util.TypedValue secondaryTv = new android.util.TypedValue();
        int colorSecondaryVal = isDarkTheme ? 0xff8696a0 : 0xff667781;
        if (activity.getTheme().resolveAttribute(android.R.attr.textColorSecondary, secondaryTv, true)) {
            colorSecondaryVal = secondaryTv.resourceId != 0 ? activity.getResources().getColor(secondaryTv.resourceId) : secondaryTv.data;
        }
        final String txtSecondary = String.format("#%06X", (0xFFFFFF & colorSecondaryVal));

        android.util.TypedValue accentTv = new android.util.TypedValue();
        int colorAccentVal = isDarkTheme ? 0xff00a884 : 0xff008069;
        boolean foundAccent = false;
        int colorAccentAttr = activity.getResources().getIdentifier("colorAccent", "attr", activity.getPackageName());
        if (colorAccentAttr != 0 && activity.getTheme().resolveAttribute(colorAccentAttr, accentTv, true)) {
            colorAccentVal = accentTv.resourceId != 0 ? activity.getResources().getColor(accentTv.resourceId) : accentTv.data;
            foundAccent = true;
        }
        if (!foundAccent) {
            int controlActivatedAttr = activity.getResources().getIdentifier("colorControlActivated", "attr", activity.getPackageName());
            if (controlActivatedAttr != 0 && activity.getTheme().resolveAttribute(controlActivatedAttr, accentTv, true)) {
                colorAccentVal = accentTv.resourceId != 0 ? activity.getResources().getColor(accentTv.resourceId) : accentTv.data;
            }
        }
        final String accentColor = isDarkTheme ? "#21c063" : "#008069";

        final String cardBgColor = isDarkTheme ? "#1f2c34" : "#f0f2f5";
        final String dividerColor = isDarkTheme ? "#202c33" : "#e9edef";
        final String failedColor = isDarkTheme ? "#ea0038" : "#ba1a1a";

        // Match system bars to WhatsApp background
        try {
            activity.getWindow().setStatusBarColor(android.graphics.Color.parseColor(bgColor));
            activity.getWindow().setNavigationBarColor(android.graphics.Color.parseColor(bgColor));
            
            // Set light status/navigation bar icons for light mode
            if (!isDarkTheme) {
                var decorView = activity.getWindow().getDecorView();
                int flags = decorView.getSystemUiVisibility();
                flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}

        // Build the layout programmatically
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(android.graphics.Color.parseColor(bgColor));

        // Padding
        int pad = dpToPx(activity, 28);
        root.setPadding(pad, dpToPx(activity, 48), pad, pad);

        // Circular Icon containing WaEnhancerX App Icon
        android.widget.FrameLayout iconFrame = new android.widget.FrameLayout(activity);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dpToPx(activity, 72), dpToPx(activity, 72));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.setMargins(0, 0, 0, dpToPx(activity, 24));
        iconFrame.setLayoutParams(iconParams);

        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(android.graphics.Color.parseColor(cardBgColor));
        iconFrame.setBackground(circle);

        android.widget.ImageView appIconView = new android.widget.ImageView(activity);
        boolean iconLoaded = false;
        try {
            android.graphics.drawable.Drawable appIcon = activity.getPackageManager().getApplicationIcon("com.waenhancer");
            appIconView.setImageDrawable(appIcon);
            iconLoaded = true;
        } catch (Throwable ignored) {}

        if (iconLoaded) {
            appIconView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            int padIcon = dpToPx(activity, 14); // Elegant padding for the launcher icon
            appIconView.setPadding(padIcon, padIcon, padIcon, padIcon);
            android.widget.FrameLayout.LayoutParams iconViewParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            appIconView.setLayoutParams(iconViewParams);
            iconFrame.addView(appIconView);
        } else {
            TextView fallbackText = new TextView(activity);
            fallbackText.setText("⚙️");
            fallbackText.setTextSize(32);
            fallbackText.setGravity(Gravity.CENTER);
            android.widget.FrameLayout.LayoutParams textParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            fallbackText.setLayoutParams(textParams);
            iconFrame.addView(fallbackText);
        }
        root.addView(iconFrame);

        // Title TextView (using WDSTextView)
        TextView title = createWDSTextView(activity);
        title.setText("Database Optimization");
        title.setTextColor(android.graphics.Color.parseColor(txtPrimary));
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        // Message/Subtitle TextView (using WDSTextView)
        final TextView message = createWDSTextView(activity);
        message.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 32));
        message.setText("Select optimizations to apply to your WhatsApp database.");
        message.setTextColor(android.graphics.Color.parseColor(txtSecondary));
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(message);

        // Step 1: Options Container
        final LinearLayout optionsContainer = new LinearLayout(activity);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        optionsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // Helper to load WDSSwitch
        Class<?> wdsSwitchClass = null;
        try {
            wdsSwitchClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.toggle.WDSSwitch");
        } catch (Throwable ignored) {}

        // Item 1: Group Message Filter
        LinearLayout item1 = new LinearLayout(activity);
        item1.setOrientation(LinearLayout.HORIZONTAL);
        item1.setGravity(Gravity.CENTER_VERTICAL);
        item1.setPadding(dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16));
        
        android.util.TypedValue outValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        item1.setBackgroundResource(outValue.resourceId);
        item1.setClickable(true);
        item1.setFocusable(true);

        LinearLayout textLayout1 = new LinearLayout(activity);
        textLayout1.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout1.setLayoutParams(textLp1);

        TextView label1 = createWDSTextView(activity);
        label1.setText("Group Message Filter");
        label1.setTextColor(android.graphics.Color.parseColor(txtPrimary));
        label1.setTextSize(16);
        label1.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        textLayout1.addView(label1);

        TextView desc1 = createWDSTextView(activity);
        desc1.setText("Rebuilds database indexes for lightning-fast group member message counting.");
        desc1.setTextColor(android.graphics.Color.parseColor(txtSecondary));
        desc1.setTextSize(13);
        desc1.setPadding(0, dpToPx(activity, 4), 0, 0);
        textLayout1.addView(desc1);
        item1.addView(textLayout1);

        android.view.View switchView1 = null;
        if (wdsSwitchClass != null) {
            try {
                switchView1 = (android.view.View) wdsSwitchClass.getConstructor(android.content.Context.class).newInstance(activity);
            } catch (Throwable ignored) {}
        }
        if (switchView1 == null) {
            switchView1 = new android.widget.Switch(activity);
        }
        final android.view.View finalSwitch1 = switchView1;
        if (finalSwitch1 instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) finalSwitch1).setChecked(true);
        }
        LinearLayout.LayoutParams swLp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        swLp1.setMarginStart(dpToPx(activity, 16));
        finalSwitch1.setLayoutParams(swLp1);
        item1.addView(finalSwitch1);
        
        item1.setOnClickListener(v -> {
            if (finalSwitch1 instanceof android.widget.CompoundButton) {
                ((android.widget.CompoundButton) finalSwitch1).toggle();
            }
        });

        optionsContainer.addView(item1);

        // Divider
        android.view.View optDivider = new android.view.View(activity);
        optDivider.setBackgroundColor(android.graphics.Color.parseColor(dividerColor));
        LinearLayout.LayoutParams optDivParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(activity, 1));
        optDivParams.setMargins(dpToPx(activity, 16), 0, dpToPx(activity, 16), 0);
        optDivider.setLayoutParams(optDivParams);
        optionsContainer.addView(optDivider);

        // Item 2: Separate Groups
        LinearLayout item2 = new LinearLayout(activity);
        item2.setOrientation(LinearLayout.HORIZONTAL);
        item2.setGravity(Gravity.CENTER_VERTICAL);
        item2.setPadding(dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16));
        item2.setBackgroundResource(outValue.resourceId);
        item2.setClickable(true);
        item2.setFocusable(true);

        LinearLayout textLayout2 = new LinearLayout(activity);
        textLayout2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout2.setLayoutParams(textLp2);

        TextView label2 = createWDSTextView(activity);
        label2.setText("Separate Groups");
        label2.setTextColor(android.graphics.Color.parseColor(txtPrimary));
        label2.setTextSize(16);
        label2.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        textLayout2.addView(label2);

        TextView desc2 = createWDSTextView(activity);
        desc2.setText("Optimizes chat database for fast loading of separate chats and groups tabs.");
        desc2.setTextColor(android.graphics.Color.parseColor(txtSecondary));
        desc2.setTextSize(13);
        desc2.setPadding(0, dpToPx(activity, 4), 0, 0);
        textLayout2.addView(desc2);
        item2.addView(textLayout2);

        android.view.View switchView2 = null;
        if (wdsSwitchClass != null) {
            try {
                switchView2 = (android.view.View) wdsSwitchClass.getConstructor(android.content.Context.class).newInstance(activity);
            } catch (Throwable ignored) {}
        }
        if (switchView2 == null) {
            switchView2 = new android.widget.Switch(activity);
        }
        final android.view.View finalSwitch2 = switchView2;
        if (finalSwitch2 instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) finalSwitch2).setChecked(true);
        }
        LinearLayout.LayoutParams swLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        swLp2.setMarginStart(dpToPx(activity, 16));
        finalSwitch2.setLayoutParams(swLp2);
        item2.addView(finalSwitch2);

        item2.setOnClickListener(v -> {
            if (finalSwitch2 instanceof android.widget.CompoundButton) {
                ((android.widget.CompoundButton) finalSwitch2).toggle();
            }
        });

        optionsContainer.addView(item2);

        // Spacer to push the warning text and button to the bottom of the screen
        android.view.View optSpacer = new android.view.View(activity);
        LinearLayout.LayoutParams optSpacerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        optSpacer.setLayoutParams(optSpacerParams);
        optionsContainer.addView(optSpacer);

        // Warning Text (Normal text above the continue button)
        TextView cardDesc = createWDSTextView(activity);
        cardDesc.setText("Do not close the app or lock your device. Optimization takes up to 15 seconds.");
        cardDesc.setTextColor(android.graphics.Color.parseColor(txtSecondary));
        cardDesc.setTextSize(13);
        cardDesc.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(dpToPx(activity, 16), 0, dpToPx(activity, 16), dpToPx(activity, 12));
        cardDesc.setLayoutParams(descParams);
        optionsContainer.addView(cardDesc);

        // Continue Button
        android.view.View continueBtn = null;
        try {
            Class<?> wdsButtonClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.button.WDSButton");
            continueBtn = (android.view.View) wdsButtonClass.getConstructor(android.content.Context.class, android.util.AttributeSet.class)
                    .newInstance(activity, null);
            
            // Set variant to FILLED to style it as a primary green button
            try {
                Class<?> variantEnum = activity.getClassLoader().loadClass("X.0xb");
                Object filledVariant = Enum.valueOf((Class<Enum>) variantEnum, "FILLED");
                XposedHelpers.callMethod(continueBtn, "setVariant", filledVariant);
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to set WDSButton variant: " + t.toString());
            }
        } catch (Throwable ignored) {}

        if (continueBtn == null) {
            continueBtn = new android.widget.Button(activity);
        }
        final android.view.View btnContinue = continueBtn;
        if (btnContinue instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) btnContinue;
            tv.setText("Continue");
            tv.setGravity(Gravity.CENTER);
        } else {
            try {
                XposedHelpers.callMethod(btnContinue, "setText", "Continue");
            } catch (Throwable ignored) {}
        }

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(activity, 48));
        btnParams.setMargins(dpToPx(activity, 16), 0, dpToPx(activity, 16), dpToPx(activity, 16));
        btnContinue.setLayoutParams(btnParams);
        optionsContainer.addView(btnContinue);

        root.addView(optionsContainer);

        // Step 2: Progress Container (Step 2)
        final LinearLayout progressContainer = new LinearLayout(activity);
        progressContainer.setOrientation(LinearLayout.VERTICAL);
        progressContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        progressContainer.setVisibility(View.GONE);

        // ProgressBar (WhatsApp Green accent, track dividerColor, thin 4dp layout)
        final ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        final String trueWaGreen = accentColor;
        final String pbTrackColor = isDarkTheme ? "#2a3942" : "#e9edef";
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(trueWaGreen)));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(pbTrackColor)));
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(activity, 4));
        progressBar.setLayoutParams(pbParams);
        progressContainer.addView(progressBar);

        // Progress/Percentage Text
        final TextView percentText = createWDSTextView(activity);
        percentText.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 24));
        percentText.setText("0%");
        percentText.setTextColor(android.graphics.Color.parseColor(txtPrimary));
        percentText.setTextSize(14);
        percentText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        percentText.setGravity(Gravity.CENTER_HORIZONTAL);
        progressContainer.addView(percentText);

        // Step list layout
        LinearLayout stepLayout = new LinearLayout(activity);
        stepLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stepLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stepLayoutParams.setMargins(dpToPx(activity, 8), 0, dpToPx(activity, 8), 0);
        stepLayout.setLayoutParams(stepLayoutParams);

        final TextView step1 = createWDSTextView(activity);
        step1.setTextSize(13);
        setStepState(activity, step1, "Stopping background processes...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
        stepLayout.addView(step1);

        final TextView step2 = createWDSTextView(activity);
        step2.setTextSize(13);
        step2.setPadding(0, dpToPx(activity, 8), 0, 0);
        setStepState(activity, step2, "Rebuilding message filter indexes...", STATE_INCOMPLETE, accentColor, txtPrimary, txtSecondary);
        stepLayout.addView(step2);

        final TextView step3 = createWDSTextView(activity);
        step3.setTextSize(13);
        step3.setPadding(0, dpToPx(activity, 8), 0, 0);
        setStepState(activity, step3, "Rebuilding database indexes...", STATE_INCOMPLETE, accentColor, txtPrimary, txtSecondary);
        stepLayout.addView(step3);

        progressContainer.addView(stepLayout);
        root.addView(progressContainer);

        activity.setContentView(root);

        // Enable/Disable Continue button dynamically
        if (finalSwitch1 instanceof android.widget.CompoundButton && finalSwitch2 instanceof android.widget.CompoundButton) {
            android.widget.CompoundButton s1 = (android.widget.CompoundButton) finalSwitch1;
            android.widget.CompoundButton s2 = (android.widget.CompoundButton) finalSwitch2;
            s1.setOnCheckedChangeListener((buttonView, isChecked) -> {
                btnContinue.setEnabled(s1.isChecked() || s2.isChecked());
                btnContinue.setAlpha((s1.isChecked() || s2.isChecked()) ? 1.0f : 0.5f);
            });
            s2.setOnCheckedChangeListener((buttonView, isChecked) -> {
                btnContinue.setEnabled(s1.isChecked() || s2.isChecked());
                btnContinue.setAlpha((s1.isChecked() || s2.isChecked()) ? 1.0f : 0.5f);
            });
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        btnContinue.setOnClickListener(v -> {
            boolean optFilter = finalSwitch1 instanceof android.widget.CompoundButton && ((android.widget.CompoundButton) finalSwitch1).isChecked();
            boolean optSeparate = finalSwitch2 instanceof android.widget.CompoundButton && ((android.widget.CompoundButton) finalSwitch2).isChecked();

            optionsContainer.setVisibility(View.GONE);
            progressContainer.setVisibility(View.VISIBLE);
            message.setText("Creating speed-boosting database indexes...");
            isOptimizing = true;

            // Set up steps visibility and text
            if (optFilter && optSeparate) {
                setStepState(activity, step1, "Stopping background processes...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
                setStepState(activity, step2, "Indexing group messages...", STATE_INCOMPLETE, accentColor, txtPrimary, txtSecondary);
                setStepState(activity, step3, "Optimizing chat database...", STATE_INCOMPLETE, accentColor, txtPrimary, txtSecondary);
                step3.setVisibility(View.VISIBLE);
            } else if (optFilter) {
                setStepState(activity, step1, "Stopping background processes...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
                setStepState(activity, step2, "Indexing group messages...", STATE_INCOMPLETE, accentColor, txtPrimary, txtSecondary);
                step3.setVisibility(View.GONE);
            } else {
                setStepState(activity, step1, "Stopping background processes...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
                setStepState(activity, step2, "Optimizing chat database...", STATE_INCOMPLETE, accentColor, txtPrimary, txtSecondary);
                step3.setVisibility(View.GONE);
            }

            runOptimizationTask(activity, mainHandler, progressBar, percentText, step1, step2, step3, optFilter, optSeparate,
                    accentColor, txtPrimary, txtSecondary, failedColor);
        });
    }

    private void runOptimizationTask(final Activity activity, final Handler mainHandler,
                                     final ProgressBar progressBar, final TextView percentText,
                                     final TextView step1, final TextView step2, final TextView step3,
                                     final boolean optFilter, final boolean optSeparate,
                                     final String accentColor, final String txtPrimary, final String txtSecondary,
                                     final String failedColor) {
        CompletableFuture.runAsync(() -> {
            // Kill other WhatsApp helper processes to avoid any database locking
            try {
                int myPid = android.os.Process.myPid();
                android.app.ActivityManager am = (android.app.ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.pid != myPid && info.processName != null && info.processName.startsWith("com.whatsapp:")) {
                            android.os.Process.killProcess(info.pid);
                        }
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log("[WAEX] Error killing helper processes: " + e.toString());
            }

            final boolean[] dbSuccess = { true };
            final boolean[] dbFinished = { false };

            Thread dbThread = new Thread(() -> {
                try {
                    java.io.File dbFile = activity.getDatabasePath("msgstore.db");
                    XposedBridge.log("[WAEX] dbThread started. optFilter=" + optFilter + ", optSeparate=" + optSeparate + ", dbFile=" + dbFile.getAbsolutePath());
                    if (dbFile.exists()) {
                        XposedBridge.log("[WAEX] Opening database for indexing...");
                        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                        XposedBridge.log("[WAEX] Database opened. Creating indexes...");
                        try {
                            if (optFilter) {
                                XposedBridge.log("[WAEX] Executing CREATE INDEX for wae_msg_filter_idx and wae_msg_from_me_idx...");
                                db.execSQL("CREATE INDEX IF NOT EXISTS wae_msg_filter_idx ON message (chat_row_id, sender_jid_row_id, from_me, message_type)");
                                db.execSQL("CREATE INDEX IF NOT EXISTS wae_msg_from_me_idx ON message (chat_row_id, from_me, message_type)");
                            }
                            if (optSeparate) {
                                XposedBridge.log("[WAEX] Executing CREATE INDEX for wae_chat_unseen_idx...");
                                db.execSQL("CREATE INDEX IF NOT EXISTS wae_chat_unseen_idx ON chat (unseen_message_count, archived, chat_lock, jid_row_id)");
                            }
                            XposedBridge.log("[WAEX] Index creation SQL commands executed successfully.");
                        } finally {
                            db.close();
                            XposedBridge.log("[WAEX] Database closed.");
                        }
                    } else {
                        XposedBridge.log("[WAEX] dbFile does not exist!");
                        dbSuccess[0] = false;
                    }
                } catch (Throwable e) {
                    XposedBridge.log("[WAEX] Optimization error: " + e.toString());
                    dbSuccess[0] = false;
                } finally {
                    dbFinished[0] = true;
                }
            });
            dbThread.start();

            // Progress Mocking from 0% to 92% over 8 seconds
            int progress = 0;
            while (!dbFinished[0]) {
                if (progress < 92) {
                    progress++;
                    final int currentP = progress;
                    mainHandler.post(() -> {
                        progressBar.setProgress(currentP);
                        percentText.setText(currentP + "%");

                        if (optFilter && optSeparate) {
                            if (currentP >= 25 && currentP < 70) {
                                setStepState(activity, step1, "Background services stopped", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                                setStepState(activity, step2, "Indexing group messages...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
                            } else if (currentP >= 70) {
                                setStepState(activity, step1, "Background services stopped", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                                setStepState(activity, step2, "Group messages indexed", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                                setStepState(activity, step3, "Optimizing chat database...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
                            }
                        } else {
                            if (currentP >= 30) {
                                setStepState(activity, step1, "Background services stopped", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                                setStepState(activity, step2, optFilter ? "Indexing group messages..." : "Optimizing chat database...", STATE_ACTIVE, accentColor, txtPrimary, txtSecondary);
                            }
                        }
                    });
                    try {
                        Thread.sleep(85); // 8000ms / 92 steps = ~86ms
                    } catch (InterruptedException e) {
                        break;
                    }
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            final boolean finalSuccess = dbSuccess[0];
            final int endProgress = finalSuccess ? 100 : progress;
            mainHandler.post(() -> {
                ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", endProgress);
                animator.setDuration(250);
                animator.start();

                if (finalSuccess) {
                    percentText.setText("100%");
                    if (optFilter && optSeparate) {
                        setStepState(activity, step1, "Background services stopped", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                        setStepState(activity, step2, "Group messages indexed", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                        setStepState(activity, step3, "Chat database optimized", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                    } else if (optFilter) {
                        setStepState(activity, step1, "Background services stopped", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                        setStepState(activity, step2, "Group messages indexed", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                    } else {
                        setStepState(activity, step1, "Background services stopped", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                        setStepState(activity, step2, "Chat database optimized", STATE_COMPLETED, accentColor, txtPrimary, txtSecondary);
                    }
                } else {
                    percentText.setText("Failed");
                    step1.setTextColor(android.graphics.Color.parseColor(failedColor));
                    step2.setTextColor(android.graphics.Color.parseColor(failedColor));
                    step3.setTextColor(android.graphics.Color.parseColor(failedColor));
                }
            });

            try { Thread.sleep(600); } catch (InterruptedException ignored) {}

            mainHandler.post(() -> {
                if (finalSuccess) {
                    Toast.makeText(activity, "Optimization completed successfully! Restarting WhatsApp...", Toast.LENGTH_LONG).show();
                    mainHandler.postDelayed(() -> {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }, 2000);
                } else {
                    // Show failure and allow exit via crash-proof standard AlertDialog
                    new android.app.AlertDialog.Builder(activity)
                            .setTitle("Optimization Failed")
                            .setMessage("Failed to optimize database. Please try again.")
                            .setPositiveButton("Exit", (d, w) -> {
                                android.os.Process.killProcess(android.os.Process.myPid());
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        });
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private TextView createWDSTextView(Activity activity) {
        try {
            Class<?> wdsTextViewClass = activity.getClassLoader().loadClass("com.whatsapp.ui.wds.components.textview.WDSTextView");
            return (TextView) wdsTextViewClass.getConstructor(android.content.Context.class).newInstance(activity);
        } catch (Throwable ignored) {
            return new TextView(activity);
        }
    }

    private static final int STATE_INCOMPLETE = 0;
    private static final int STATE_ACTIVE = 1;
    private static final int STATE_COMPLETED = 2;

    private void setStepState(Activity activity, TextView textView, String text, int state, String accentColor, String txtPrimary, String txtSecondary) {
        textView.setText(text);
        int size = dpToPx(activity, 16);
        android.graphics.drawable.Drawable drawable;
        if (state == STATE_COMPLETED) {
            drawable = new CheckCircleDrawable(android.graphics.Color.parseColor(accentColor));
            textView.setTextColor(android.graphics.Color.parseColor(accentColor));
        } else if (state == STATE_ACTIVE) {
            drawable = new BulletCircleDrawable(android.graphics.Color.parseColor(txtPrimary), true);
            textView.setTextColor(android.graphics.Color.parseColor(txtPrimary));
        } else {
            drawable = new BulletCircleDrawable(android.graphics.Color.parseColor(txtSecondary), false);
            textView.setTextColor(android.graphics.Color.parseColor(txtSecondary));
        }
        drawable.setBounds(0, 0, size, size);
        textView.setCompoundDrawables(drawable, null, null, null);
        textView.setCompoundDrawablePadding(dpToPx(activity, 12));
    }

    public static class CheckCircleDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint circlePaint;
        private final android.graphics.Paint checkPaint;
        private final android.graphics.Path checkPath;

        public CheckCircleDrawable(int color) {
            circlePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(color);
            circlePaint.setStyle(android.graphics.Paint.Style.FILL);

            checkPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            checkPaint.setColor(android.graphics.Color.WHITE);
            checkPaint.setStyle(android.graphics.Paint.Style.STROKE);
            checkPaint.setStrokeWidth(4f);
            checkPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(android.graphics.Paint.Join.ROUND);

            checkPath = new android.graphics.Path();
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float radius = Math.min(bounds.width(), bounds.height()) / 2f;

            // Draw circle
            canvas.drawCircle(cx, cy, radius, circlePaint);

            // Draw checkmark inside circle
            checkPath.reset();
            float size = radius * 2f;
            checkPath.moveTo(cx - size * 0.2f, cy + size * 0.02f);
            checkPath.lineTo(cx - size * 0.05f, cy + size * 0.17f);
            checkPath.lineTo(cx + size * 0.22f, cy - size * 0.13f);
            canvas.drawPath(checkPath, checkPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            circlePaint.setAlpha(alpha);
            checkPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            circlePaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    public static class BulletCircleDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint;
        private final boolean isActive;

        public BulletCircleDrawable(int color, boolean isActive) {
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            this.isActive = isActive;
            if (isActive) {
                paint.setStyle(android.graphics.Paint.Style.FILL);
            } else {
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
            }
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float radius;
            if (isActive) {
                radius = Math.min(bounds.width(), bounds.height()) / 4f; // smaller filled bullet
            } else {
                radius = Math.min(bounds.width(), bounds.height()) / 2.5f; // outline circle
            }
            canvas.drawCircle(cx, cy, radius, paint);
        }

        @Override
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {}
        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Activity Controller";
    }

}
