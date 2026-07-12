package com.waenhancer.xposed.features.media;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.components.AlertDialogWpp;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.Utils;

import java.io.File;

import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class DownloadVideoNote extends Feature {

    public DownloadVideoNote(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    private final java.util.WeakHashMap<View, View.OnLongClickListener> originalListeners = new java.util.WeakHashMap<>();
    private boolean isBypassingLongClick = false;

    private boolean isSelectionModeActive(Context context) {
        try {
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) context;
                android.view.View decorView = activity.getWindow().getDecorView();
                
                int barId = activity.getResources().getIdentifier("action_mode_bar", "id", "android");
                if (barId != 0) {
                    android.view.View bar = decorView.findViewById(barId);
                    if (bar != null && bar.getVisibility() == android.view.View.VISIBLE) {
                        return true;
                    }
                }
                
                int appCompatBarId = activity.getResources().getIdentifier("action_context_bar", "id", activity.getPackageName());
                if (appCompatBarId != 0) {
                    android.view.View bar = decorView.findViewById(appCompatBarId);
                    if (bar != null && bar.getVisibility() == android.view.View.VISIBLE) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private View.OnLongClickListener getOriginalListener(View view) {
        try {
            java.lang.reflect.Field listenerInfoField = View.class.getDeclaredField("mListenerInfo");
            listenerInfoField.setAccessible(true);
            Object listenerInfo = listenerInfoField.get(view);
            if (listenerInfo != null) {
                java.lang.reflect.Field longClickListenerField = listenerInfo.getClass().getDeclaredField("mOnLongClickListener");
                longClickListenerField.setAccessible(true);
                return (View.OnLongClickListener) longClickListenerField.get(listenerInfo);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void setupLongClickHooks(View view, View.OnLongClickListener customListener, boolean isRoot) {
        if (view == null) return;
        View.OnLongClickListener orig = getOriginalListener(view);
        if (isRoot || orig != null || view.isClickable()) {
            if (orig != null && !orig.getClass().getName().contains("DownloadVideoNote")) {
                if (!originalListeners.containsKey(view)) {
                    originalListeners.put(view, orig);
                }
            }
            view.setOnLongClickListener(customListener);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setupLongClickHooks(vg.getChildAt(i), customListener, false);
            }
        }
    }

    @Override
    public void doHook() throws Throwable {
        reloadPrefs();
        if (!prefs.getBoolean("download_video_note", false)) return;

        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                // Check if message is a video note (mediaType 81)
                boolean isVideoNote = fMessage.getMediaType() == 81;
                if (!isVideoNote) {
                    try {
                        File file = fMessage.getMediaFile();
                        if (file != null && file.getAbsolutePath().toLowerCase().contains("whatsapp video notes")) {
                            isVideoNote = true;
                        }
                    } catch (Throwable ignored) {}
                }

                if (!isVideoNote) return;

                var listener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (isBypassingLongClick || isSelectionModeActive(v.getContext())) {
                            return false;
                        }
                        showBottomSheetOptions(v.getContext(), fMessage, () -> {
                            View current = v;
                            View.OnLongClickListener orig = null;
                            while (current != null) {
                                orig = originalListeners.get(current);
                                if (orig != null) {
                                    break;
                                }
                                if (current == viewGroup) {
                                    break;
                                }
                                android.view.ViewParent parent = current.getParent();
                                current = (parent instanceof View) ? (View) parent : null;
                            }

                            try {
                                isBypassingLongClick = true;
                                if (orig != null) {
                                    orig.onLongClick(current);
                                } else {
                                    viewGroup.performLongClick();
                                }
                            } finally {
                                isBypassingLongClick = false;
                            }
                        });
                        return true;
                    }
                };
                setupLongClickHooks(viewGroup, listener, true);
            }
        });
    }

    private void showBottomSheetOptions(Context context, FMessageWpp fMessage, Runnable triggerDefaultLongClick) {
        try {
            AlertDialogWpp alert = new AlertDialogWpp(context);
            alert.setTitle("Video Note Options");
            alert.setMessage("Choose what you want to do with this video note");
            alert.setItems(new CharSequence[]{"Save to Gallery", "Select Message"}, (dialog, which) -> {
                if (which == 0) {
                    saveVideoNote(fMessage);
                } else if (which == 1) {
                    triggerDefaultLongClick.run();
                }
            });
            alert.show();
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Error showing video note bottom sheet: " + t.getMessage());
            triggerDefaultLongClick.run();
        }
    }

    private void saveVideoNote(FMessageWpp fMessage) {
        try {
            File file = fMessage.getMediaFile();
            if (file == null || !file.exists()) {
                Utils.showToast("Video Note is not fully downloaded yet. Please wait.", Toast.LENGTH_SHORT);
                return;
            }

            var userJid = fMessage.getKey().remoteJid;
            var destination = Utils.getDestination("Video Notes");
            var name = Utils.generateName(userJid, "mp4");
            var error = Utils.copyFile(file, destination, name);

            if (TextUtils.isEmpty(error)) {
                Utils.showToast("Saved to: " + destination + name, Toast.LENGTH_LONG);
            } else {
                Utils.showToast("Error saving: " + error, Toast.LENGTH_LONG);
            }
        } catch (Throwable e) {
            Utils.showToast("Error: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download Video Note";
    }
}
