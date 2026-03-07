package com.wmods.wppenhacer.xposed.features.general;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class VideoNoteAttachment extends Feature {

    public static final int REQUEST_PICK_VIDEO_NOTE = 0x8891;
    private static final java.util.WeakHashMap<Object, Boolean> fakeVideoNotes = new java.util.WeakHashMap<>();

    public VideoNoteAttachment(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("video_note_attachment", false))
            return;

        // ── 1. Inject "Video Note" button into the attachment picker ────────────
        final int rowId = Utils.getID("attach_picker_row", "id");

        XposedBridge.hookAllMethods(ViewGroup.class, "addView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View added = (View) param.args[0];
                if (added == null || rowId <= 0 || added.getId() != rowId)
                    return;
                ViewGroup parent = (ViewGroup) param.thisObject;

                added.post(() -> {
                    try {
                        if (parent.findViewWithTag("wae_videonote") != null)
                            return;

                        // Find last attach_picker_row inside parent
                        ViewGroup lastRow = null;
                        ViewGroup firstRow = null;
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            View child = parent.getChildAt(i);
                            if (child.getId() == rowId && child instanceof ViewGroup) {
                                if (firstRow == null)
                                    firstRow = (ViewGroup) child;
                                lastRow = (ViewGroup) child;
                            }
                        }
                        if (lastRow == null)
                            return;

                        // Get exact item size from a known button in row 1
                        int btnW = Utils.dipToPixels(88);
                        int btnH = Utils.dipToPixels(140);
                        if (firstRow != null && firstRow.getChildCount() > 0) {
                            View ref = firstRow.getChildAt(0);
                            if (ref.getWidth() > 0)
                                btnW = ref.getWidth();
                            if (ref.getHeight() > 0)
                                btnH = ref.getHeight();
                        }
                        injectVideoNoteItem(lastRow, btnW, btnH);
                    } catch (Exception e) {
                        // Ignore
                    }
                });
            }
        });

        // ── 2. Intercept the Activity result when the user picks a video ────────
        // ActivityController filters by a proxy class, so we hook separately here.
        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult",
                int.class, int.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int requestCode = (int) param.args[0];
                        if (requestCode != REQUEST_PICK_VIDEO_NOTE)
                            return;

                        int resultCode = (int) param.args[1];
                        Intent data = (Intent) param.args[2];
                        if (resultCode != Activity.RESULT_OK || data == null) {
                            return;
                        }

                        Uri videoUri = data.getData();
                        sendVideoToConversation((Activity) param.thisObject, videoUri);
                    }
                });
        // ── 3. Force mediaType 81 (PTV) on message creation ────────────
        try {
            Class<?> abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader);
            if (abstractMediaMessageClass != null) {
                XposedBridge.hookAllConstructors(abstractMediaMessageClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object fMessage = param.thisObject;
                        com.wmods.wppenhacer.xposed.core.components.FMessageWpp wrappedMsg = new com.wmods.wppenhacer.xposed.core.components.FMessageWpp(
                                fMessage);

                        int mediaType = wrappedMsg.getMediaType();
                        // Video mediaType is normally 3. If we intercept construction of a video,
                        // we'll check if it's meant to be a Video Note (e.g. via our "Video Note"
                        // button)
                        if (mediaType == 3) {
                            java.io.File mediaFile = wrappedMsg.getMediaFile();
                            if (mediaFile != null
                                    && mediaFile.getAbsolutePath().toLowerCase().contains("whatsapp video notes")) {
                                java.lang.reflect.Field mediaTypeField = Unobfuscator.loadMediaTypeField(classLoader);
                                mediaTypeField.setAccessible(true);
                                mediaTypeField.setInt(fMessage, 81);
                                fakeVideoNotes.put(fMessage, true);
                            } else {
                                // Also check our custom "wae_force_ptv" static flag that we can set during
                                // picking
                                if (VideoNoteAttachment.FORCE_NEXT_VIDEO_AS_PTV) {
                                    java.lang.reflect.Field mediaTypeField = Unobfuscator
                                            .loadMediaTypeField(classLoader);
                                    mediaTypeField.setAccessible(true);
                                    mediaTypeField.setInt(fMessage, 81);
                                    fakeVideoNotes.put(fMessage, true);

                                    // Reset the flag after consuming it
                                    VideoNoteAttachment.FORCE_NEXT_VIDEO_AS_PTV = false;
                                }
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            // Ignore
        }

        // ── 4. Prevent UI crash due to ClassCastException in ListView ────────────
        // When forcing a regular Video to act as a PTV, the UI tries to cast the
        // FMessageVideo
        // to FMessageVideoNote and crashes. We catch the exception in the adapter and
        // return a dummy view.
        try {
            XposedHelpers.findAndHookMethod(android.widget.ListView.class, "setAdapter",
                    android.widget.ListAdapter.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity activity = WppCore.getCurrentActivity();
                            if (activity == null || !activity.getClass().getSimpleName().equals("Conversation"))
                                return;

                            android.widget.ListView listView = (android.widget.ListView) param.thisObject;
                            if (listView.getId() != android.R.id.list)
                                return;

                            android.widget.ListAdapter adapter = (android.widget.ListAdapter) param.args[0];
                            if (adapter instanceof android.widget.HeaderViewListAdapter) {
                                adapter = ((android.widget.HeaderViewListAdapter) adapter).getWrappedAdapter();
                            }
                            if (adapter == null)
                                return;

                            final android.widget.ListAdapter finalAdapter = adapter;
                            try {
                                java.lang.reflect.Method getViewMethod = finalAdapter.getClass().getDeclaredMethod(
                                        "getView", int.class, android.view.View.class, android.view.ViewGroup.class);
                                java.lang.reflect.Method getItemViewTypeMethod = null;
                                try {
                                    getItemViewTypeMethod = finalAdapter.getClass().getDeclaredMethod("getItemViewType",
                                            int.class);
                                } catch (Exception ignored) {
                                }

                                if (crashPreventHook != null)
                                    crashPreventHook.unhook();

                                XC_MethodHook toggleMediaHook = new XC_MethodHook() {
                                    private final ThreadLocal<Object> tempPtvMessage = new ThreadLocal<>();

                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param2) throws Throwable {
                                        try {
                                            int position = (int) param2.args[0];
                                            Object fMessage = finalAdapter.getItem(position);
                                            if (fMessage != null) {
                                                com.wmods.wppenhacer.xposed.core.components.FMessageWpp wrapped = new com.wmods.wppenhacer.xposed.core.components.FMessageWpp(
                                                        fMessage);
                                                int mediaType = wrapped.getMediaType();
                                                // Only camouflage our artificially faked Video Notes in memory
                                                if (mediaType == 81 && fakeVideoNotes.containsKey(fMessage)) {
                                                    java.lang.reflect.Field mediaTypeField = Unobfuscator
                                                            .loadMediaTypeField(classLoader);
                                                    mediaTypeField.setAccessible(true);
                                                    mediaTypeField.setInt(fMessage, 3);
                                                    tempPtvMessage.set(fMessage);
                                                }
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param2) throws Throwable {
                                        try {
                                            Object fMessage = tempPtvMessage.get();
                                            if (fMessage != null) {
                                                java.lang.reflect.Field mediaTypeField = Unobfuscator
                                                        .loadMediaTypeField(classLoader);
                                                mediaTypeField.setAccessible(true);
                                                mediaTypeField.setInt(fMessage, 81);
                                                tempPtvMessage.remove();
                                            }

                                            // Fallback transparent view if it still crashes inside getView
                                            if (param2.method.getName().equals("getView") && param2.hasThrowable()) {
                                                Throwable t = param2.getThrowable();
                                                if (t instanceof ClassCastException
                                                        || (t.getCause() != null
                                                                && t.getCause() instanceof ClassCastException)
                                                        || t instanceof IllegalStateException || (t.getCause() != null
                                                                && t.getCause() instanceof IllegalStateException)) {
                                                    if (t instanceof ClassCastException) {
                                                        ClassCastException cce = (ClassCastException) (t instanceof ClassCastException
                                                                ? t
                                                                : t.getCause());
                                                        if (cce.getMessage() != null
                                                                && cce.getMessage().contains("cannot be cast to ")) {
                                                            String targetClassName = cce.getMessage()
                                                                    .split("cannot be cast to ")[1].trim();
                                                            try {
                                                                targetVideoNoteClass = Class.forName(targetClassName,
                                                                        false, activity.getClassLoader());
                                                            } catch (Exception ignored) {
                                                            }
                                                        }
                                                    }
                                                    param2.setThrowable(null);

                                                    android.content.Context ctx = activity;
                                                    if (param2.args[2] != null) {
                                                        ctx = ((android.view.View) param2.args[2]).getContext();
                                                    }

                                                    android.widget.FrameLayout dummy = new android.widget.FrameLayout(
                                                            ctx);
                                                    dummy.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1));
                                                    dummy.setBackgroundColor(Color.TRANSPARENT);
                                                    param2.setResult(dummy);
                                                }
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                };

                                crashPreventHook = XposedBridge.hookMethod(getViewMethod, toggleMediaHook);
                                if (getItemViewTypeMethod != null) {
                                    XposedBridge.hookMethod(getItemViewTypeMethod, toggleMediaHook);
                                }
                            } catch (Exception e) {
                            }
                        }
                    });
        } catch (Exception e) {
        }

        // ── 5. Bypass preview screen ────────────
        try {
            XposedHelpers.findAndHookMethod("com.whatsapp.mediacomposer.ui.app.MediaComposerActivity", classLoader,
                    "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity activity = (Activity) param.thisObject;
                            Intent intent = activity.getIntent();
                            if (intent != null && intent.getBooleanExtra("is_media_ptv", false)) {
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        int sendId = activity.getResources().getIdentifier("send", "id",
                                                activity.getPackageName());
                                        android.view.View sendBtn = activity.findViewById(sendId);
                                        if (sendBtn != null) {
                                            sendBtn.performClick();
                                        }
                                    } catch (Exception e) {
                                    }
                                }, 250);
                            }
                        }
                    });
        } catch (Exception e) {
        }

        // ── 6. Prevent High Pri Worker crash during Protobuf serialization
        // ────────────
        // The High Pri Worker expects `1Q1` (FMessageVideoNote) but gets `1PQ`
        // (FMessageVideo) because we modified mediaType.
        // We find the method that throws the Exception, and if the argument is a Video
        // trying to be a VideoNote, we swap it.
        try {
            String apkPath = com.wmods.wppenhacer.xposed.utils.Utils.getApplication().getApplicationInfo().sourceDir;
            var dexkit = org.luckypray.dexkit.DexKitBridge.create(apkPath);
            var methodDataList = dexkit.findMethod(org.luckypray.dexkit.query.FindMethod.create()
                    .matcher(org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                            .addUsingString("FMessagePushToVideoProtobuf: message type is not supported ",
                                    org.luckypray.dexkit.query.enums.StringMatchType.Contains)));
            if (!methodDataList.isEmpty()) {
                var methodData = methodDataList.get(0);
                java.lang.reflect.Method protoBuilderMethod = methodData.getMethodInstance(classLoader);

                // Find FMessageVideoNote class
                if (targetVideoNoteClass == null) {
                    // Fallback to searching subclasses of FMessageVideo (which is what we get when
                    // we don't have targetClass)
                    // We will resolve it lazily in the hook below.
                }

                XposedBridge.hookMethod(protoBuilderMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object fMessage = param.args[0];
                        if (fMessage != null) {
                            com.wmods.wppenhacer.xposed.core.components.FMessageWpp wrapped = new com.wmods.wppenhacer.xposed.core.components.FMessageWpp(
                                    fMessage);
                            if (wrapped.getMediaType() == 81) {
                                if (targetVideoNoteClass == null || fMessage.getClass() != targetVideoNoteClass) {
                                    // It's a Video masquerading as a Video Note.
                                    // Swap the object by instantiating the correct class safely and copying fields.
                                    Object newVideoNote = allocateNewVideoNoteCopy(fMessage, classLoader);
                                    if (newVideoNote != null) {
                                        param.args[0] = newVideoNote;
                                    }
                                }
                            }
                        }
                    }
                });
            }
            dexkit.close();
        } catch (Exception e) {
            XposedBridge.log("VideoNoteAttachment Protobuf hook error: " + e.getMessage());
        }
    }

    private static Object allocateNewVideoNoteCopy(Object fMessage, ClassLoader classLoader) {
        try {
            Class<?> abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader);
            if (abstractMediaMessageClass == null)
                return null;

            // Get Key and Timestamp from the original fMessage to use in constructors
            Object key = null;
            long timestamp = 0L;
            Class<?> searchClass = fMessage.getClass();
            while (searchClass != null && searchClass != Object.class) {
                try {
                    java.lang.reflect.Field kf = searchClass.getDeclaredField("key");
                    kf.setAccessible(true);
                    key = kf.get(fMessage);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    java.lang.reflect.Field tf = searchClass.getDeclaredField("timestamp");
                    tf.setAccessible(true);
                    timestamp = (long) tf.get(fMessage);
                } catch (NoSuchFieldException ignored) {
                }
                if (key != null && timestamp != 0L)
                    break;
                searchClass = searchClass.getSuperclass();
            }

            Object newVideoNote = null;
            java.lang.reflect.Field mediaTypeField = Unobfuscator.loadMediaTypeField(classLoader);
            mediaTypeField.setAccessible(true);

            if (targetVideoNoteClass != null) {
                try {
                    java.lang.reflect.Constructor<?>[] constructors = targetVideoNoteClass.getDeclaredConstructors();
                    for (java.lang.reflect.Constructor<?> c : constructors) {
                        Class<?>[] pTypes = c.getParameterTypes();
                        if (pTypes.length == 2 && pTypes[1] == long.class) {
                            c.setAccessible(true);
                            newVideoNote = c.newInstance(key, timestamp);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (newVideoNote == null) {
                // Find all subclasses of abstractMediaMessageClass
                String apkP = com.wmods.wppenhacer.xposed.utils.Utils.getApplication().getApplicationInfo().sourceDir;
                var dk = org.luckypray.dexkit.DexKitBridge.create(apkP);
                var subclasses = dk.findClass(org.luckypray.dexkit.query.FindClass.create()
                        .matcher(org.luckypray.dexkit.query.matchers.ClassMatcher.create()
                                .superClass(abstractMediaMessageClass.getName())));

                for (var clsData : subclasses) {
                    if (clsData.getName().contains("$"))
                        continue;
                    Class<?> cls = clsData.getInstance(classLoader);
                    if (cls != null) {
                        try {
                            // Find constructor (Key, timestamp)
                            java.lang.reflect.Constructor<?>[] constructors = cls.getDeclaredConstructors();
                            for (java.lang.reflect.Constructor<?> c : constructors) {
                                Class<?>[] pTypes = c.getParameterTypes();
                                if (pTypes.length == 2 && pTypes[1] == long.class) {
                                    c.setAccessible(true);
                                    Object potentialMsg = c.newInstance(key, timestamp);
                                    int mType = mediaTypeField.getInt(potentialMsg);
                                    if (mType == 81) {
                                        targetVideoNoteClass = cls;
                                        newVideoNote = potentialMsg;
                                        break;
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (newVideoNote != null)
                        break;
                }
                dk.close();
            }

            if (newVideoNote != null) {
                // Deep copy fields from parents to ensure file paths, bytes, etc. are passed
                Class<?> currentClass = fMessage.getClass();
                while (currentClass != null && currentClass != Object.class) {
                    for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                            field.setAccessible(true);
                            try {
                                Object val = field.get(fMessage);
                                // Skip key and timestamp as we passed them exactly in constructor
                                if (field.getName().equals("key") || field.getName().equals("timestamp"))
                                    continue;

                                Class<?> targetSearchClass = targetVideoNoteClass;
                                while (targetSearchClass != null) {
                                    try {
                                        java.lang.reflect.Field tf = targetSearchClass
                                                .getDeclaredField(field.getName());
                                        tf.setAccessible(true);
                                        tf.set(newVideoNote, val);
                                        break;
                                    } catch (NoSuchFieldException e) {
                                        targetSearchClass = targetSearchClass.getSuperclass();
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    currentClass = currentClass.getSuperclass();
                }
            }
            return newVideoNote;
        } catch (Exception e) {
            XposedBridge.log("Failed to allocate FMessageVideoNote copy: " + e.getMessage());
            return null;
        }
    }

    private static de.robv.android.xposed.XC_MethodHook.Unhook crashPreventHook;
    private static Class<?> targetVideoNoteClass = null;
    // A flag to communicate between the picker result and the message builder
    public static boolean FORCE_NEXT_VIDEO_AS_PTV = false;

    // ── Inject our item into the last row ──────────────────────────────────────
    private void injectVideoNoteItem(ViewGroup row, int btnW, int btnH) {
        Activity activity = WppCore.getCurrentActivity();
        if (activity == null)
            return;

        boolean isDark = (row.getContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int bgColor = isDark ? Color.parseColor("#233138") : Color.WHITE;
        int strokeColor = isDark ? Color.parseColor("#344A55") : Color.parseColor("#E1E4E8");
        int textColor = isDark ? Color.parseColor("#E9EDF0") : Color.parseColor("#4B5563");

        LinearLayout item = new LinearLayout(row.getContext());
        item.setOrientation(LinearLayout.VERTICAL);
        // Center contents properly in the grid cell
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setTag("wae_videonote");
        // Restore btnH so it matches the other grid items' strict bounds
        item.setLayoutParams(new LinearLayout.LayoutParams(btnW, btnH));
        // Remove manual padding to let Gravity.CENTER handle alignment
        item.setPadding(0, 0, 0, 0);

        // Icon — rounded square matching WhatsApp's style
        int squareSize = Utils.dipToPixels(54);
        ImageView icon = new ImageView(row.getContext());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(squareSize, squareSize);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(iconLp);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(Utils.dipToPixels(12), Utils.dipToPixels(12),
                Utils.dipToPixels(12), Utils.dipToPixels(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Utils.dipToPixels(18)); // Slightly rounder, 18dp is common for WA Squircles
        bg.setColor(bgColor);
        bg.setStroke(Utils.dipToPixels(1), strokeColor);
        icon.setBackground(bg);

        int drawableId = Utils.getID("ic_attach_video", "drawable");
        if (drawableId <= 0)
            drawableId = Utils.getID("ic_video", "drawable");
        if (drawableId <= 0)
            drawableId = android.R.drawable.ic_menu_camera;
        icon.setImageResource(drawableId);
        icon.setColorFilter(Color.parseColor("#E91E8C")); // Magenta-pink icon color

        // Label
        TextView label = new TextView(row.getContext());
        label.setText("Video Note");
        label.setTextSize(12f);
        label.setTextColor(textColor);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(1);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = Utils.dipToPixels(6);
        labelLp.gravity = Gravity.CENTER_HORIZONTAL;
        label.setLayoutParams(labelLp);

        item.addView(icon);
        item.addView(label);

        item.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Experimental Feature")
                    .setMessage(
                            "The Video Note feature is currently under development and may cause crashes. Do you want to continue?")
                    .setPositiveButton("Continue", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("video/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        activity.startActivityForResult(
                                Intent.createChooser(intent, "Select Video for Video Note"),
                                REQUEST_PICK_VIDEO_NOTE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        row.addView(item);
    }

    // ── Send the picked video to the current conversation ─────────────────────
    private static void sendVideoToConversation(Activity activity, Uri videoUri) {
        try {
            // Grant WhatsApp read permission on the URI
            activity.grantUriPermission("com.whatsapp", videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Determine if we are in a WhatsApp conversation by checking the intent JID
            Intent conversationIntent = activity.getIntent();
            String jid = conversationIntent != null
                    ? conversationIntent.getStringExtra("jid")
                    : null;

            // Arm the PTV override hook!
            VideoNoteAttachment.FORCE_NEXT_VIDEO_AS_PTV = true;

            if (jid != null) {
                // We're in a conversation - share the video directly to this chat.
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("video/mp4");
                send.setPackage("com.whatsapp");
                send.putExtra(Intent.EXTRA_STREAM, videoUri);
                send.putExtra("jid", jid);
                // Magic flags from WhatsApp intent inspections
                send.putExtra("is_media_ptv", true);
                send.putExtra("origin", 5);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                activity.startActivity(send);
            } else {
                // Fallback: open WhatsApp share sheet
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("video/*");
                send.setPackage("com.whatsapp");
                send.putExtra(Intent.EXTRA_STREAM, videoUri);
                // Magic flags from WhatsApp intent inspections
                send.putExtra("is_media_ptv", true);
                send.putExtra("origin", 5);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(
                        Intent.createChooser(send, "Send Video Note via WhatsApp"));
            }

            Utils.showToast("Preparing Video Note…", Toast.LENGTH_SHORT);
        } catch (Exception e) {
            XposedBridge.log("VideoNoteAttachment sendVideo: " + e.getMessage());
            Utils.showToast("Error: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    /**
     * Called by ActivityController if it handles the result separately (kept for
     * compat).
     */
    public static void handleVideoPicked(Uri videoUri) {
        if (videoUri == null)
            return;
        Activity activity = WppCore.getCurrentActivity();
        if (activity != null)
            sendVideoToConversation(activity, videoUri);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Video Note Attachment";
    }
}
