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
                    if (intent.getBooleanExtra("wae_optimize_db", false)) {
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
                        param.setResult(null); // Disable back button during optimization
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
        // Keep screen on
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide toolbar/action bar for full screen feel
        try {
            if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                var actionBar = ((androidx.appcompat.app.AppCompatActivity) activity).getSupportActionBar();
                if (actionBar != null) actionBar.hide();
            }
            if (activity.getActionBar() != null) {
                activity.getActionBar().hide();
            }
        } catch (Throwable ignored) {}

        // Match system bars to WhatsApp dark background
        try {
            activity.getWindow().setStatusBarColor(android.graphics.Color.parseColor("#0b141a"));
            activity.getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#0b141a"));
        } catch (Throwable ignored) {}

        // Build the layout programmatically
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(android.graphics.Color.parseColor("#0b141a")); // WhatsApp dark mode background

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
        circle.setColor(android.graphics.Color.parseColor("#1f2c34"));
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

        // Title TextView
        TextView title = new TextView(activity);
        title.setText("Database Optimization");
        title.setTextColor(android.graphics.Color.parseColor("#e9edef"));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        // Message/Subtitle TextView
        TextView message = new TextView(activity);
        message.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 32));
        message.setText("Creating speed-boosting database indexes for fast group member filtering...");
        message.setTextColor(android.graphics.Color.parseColor("#8696a0"));
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(message);

        // ProgressBar (WhatsApp Green accent, track #202c33, thin 4dp layout)
        final ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00a884")));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#202c33")));
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(activity, 4));
        progressBar.setLayoutParams(pbParams);
        root.addView(progressBar);

        // Progress/Percentage Text
        final TextView percentText = new TextView(activity);
        percentText.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 24));
        percentText.setText("0%");
        percentText.setTextColor(android.graphics.Color.parseColor("#e9edef"));
        percentText.setTextSize(14);
        percentText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        percentText.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(percentText);

        // Step list layout (Monospace updates)
        LinearLayout stepLayout = new LinearLayout(activity);
        stepLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stepLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stepLayoutParams.setMargins(dpToPx(activity, 8), 0, dpToPx(activity, 8), 0);
        stepLayout.setLayoutParams(stepLayoutParams);

        final TextView step1 = new TextView(activity);
        step1.setText("• Stopping background processes...");
        step1.setTextColor(android.graphics.Color.parseColor("#e9edef"));
        step1.setTextSize(13);
        stepLayout.addView(step1);

        final TextView step2 = new TextView(activity);
        step2.setText("• Optimizing message storage...");
        step2.setTextColor(android.graphics.Color.parseColor("#8696a0"));
        step2.setTextSize(13);
        step2.setPadding(0, dpToPx(activity, 8), 0, 0);
        stepLayout.addView(step2);

        final TextView step3 = new TextView(activity);
        step3.setText("• Rebuilding database indexes...");
        step3.setTextColor(android.graphics.Color.parseColor("#8696a0"));
        step3.setTextSize(13);
        step3.setPadding(0, dpToPx(activity, 8), 0, 0);
        stepLayout.addView(step3);

        root.addView(stepLayout);

        // Spacer to push card to the bottom
        android.view.View spacer = new android.view.View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        spacer.setLayoutParams(spacerParams);
        root.addView(spacer);

        // Warning Card (Bottom aligned with margin)
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(android.graphics.Color.parseColor("#1f2c34"));
        cardBg.setCornerRadius(dpToPx(activity, 12));
        card.setBackground(cardBg);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dpToPx(activity, 16));
        card.setLayoutParams(cardParams);
        card.setPadding(dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16));

        TextView cardTitle = new TextView(activity);
        cardTitle.setText("Keep WhatsApp open");
        cardTitle.setTextColor(android.graphics.Color.parseColor("#e9edef"));
        cardTitle.setTextSize(14);
        cardTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(cardTitle);

        TextView cardDesc = new TextView(activity);
        cardDesc.setText("Do not close the app or lock your device. Optimization takes up to 15 seconds.");
        cardDesc.setTextColor(android.graphics.Color.parseColor("#8696a0"));
        cardDesc.setTextSize(12);
        cardDesc.setPadding(0, dpToPx(activity, 6), 0, 0);
        card.addView(cardDesc);

        root.addView(card);

        activity.setContentView(root);

        final Handler mainHandler = new Handler(Looper.getMainLooper());

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
                    if (dbFile.exists()) {
                        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                        try {
                            db.execSQL("CREATE INDEX IF NOT EXISTS wae_msg_filter_idx ON message (chat_row_id, sender_jid_row_id, from_me, message_type)");
                            db.execSQL("CREATE INDEX IF NOT EXISTS wae_msg_from_me_idx ON message (chat_row_id, from_me, message_type)");
                        } finally {
                            db.close();
                        }
                    } else {
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

                        if (currentP >= 25 && currentP < 90) {
                            step1.setText("✓ Background services stopped");
                            step1.setTextColor(android.graphics.Color.parseColor("#00a884"));
                            step2.setTextColor(android.graphics.Color.parseColor("#e9edef"));
                        } else if (currentP >= 90) {
                            step1.setText("✓ Background services stopped");
                            step1.setTextColor(android.graphics.Color.parseColor("#00a884"));
                            step2.setText("✓ Message storage optimized");
                            step2.setTextColor(android.graphics.Color.parseColor("#00a884"));
                            step3.setTextColor(android.graphics.Color.parseColor("#e9edef"));
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
                    step1.setText("✓ Background services stopped");
                    step1.setTextColor(android.graphics.Color.parseColor("#00a884"));
                    step2.setText("✓ Message storage optimized");
                    step2.setTextColor(android.graphics.Color.parseColor("#00a884"));
                    step3.setText("✓ Database indexes rebuilt");
                    step3.setTextColor(android.graphics.Color.parseColor("#00a884"));
                } else {
                    percentText.setText("Failed");
                    step1.setTextColor(android.graphics.Color.parseColor("#ea0038"));
                    step2.setTextColor(android.graphics.Color.parseColor("#ea0038"));
                    step3.setTextColor(android.graphics.Color.parseColor("#ea0038"));
                }
            });

            try { Thread.sleep(600); } catch (InterruptedException ignored) {}

            mainHandler.post(() -> {
                if (finalSuccess) {
                    // Clean restart (Disabled for UI testing)
                    /*
                    try {
                        Intent restartIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                        restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(restartIntent);
                    } catch (Throwable ignored) {}
                    android.os.Process.killProcess(android.os.Process.myPid());
                    */
                    Toast.makeText(activity, "Optimization finished! (UI Test Mode)", Toast.LENGTH_LONG).show();
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

    @NonNull
    @Override
    public String getPluginName() {
        return "Activity Controller";
    }

}
