package com.waenhancer.xposed.features.media;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.Utils;

import java.io.File;

import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.waenhancer.xposed.core.WppCore;

public class DownloadVideoNote extends Feature {

    public DownloadVideoNote(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
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

                // Find the action button container next to the bubble
                int actionButtonId = Utils.getID("action_button", "id");
                if (actionButtonId == 0) return;

                View actionButton = viewGroup.findViewById(actionButtonId);
                if (actionButton instanceof ImageView) {
                    ImageView origAction = (ImageView) actionButton;
                    ViewGroup parent = (ViewGroup) origAction.getParent();
                    if (parent instanceof LinearLayout) {
                        LinearLayout actionContainer = (LinearLayout) parent;

                        String tag = "wae_download_video_note_btn";
                        if (actionContainer.findViewWithTag(tag) != null) return;

                        // Create duplicate button
                        ImageView downloadBtn = new ImageView(actionButton.getContext());
                        downloadBtn.setTag(tag);

                        // Try to load our custom download drawable from the module package
                        Drawable customIcon = null;
                        try {
                            Context modContext = actionButton.getContext().createPackageContext(
                                    "com.waenhancer",
                                    Context.CONTEXT_IGNORE_SECURITY
                            );
                            int resId = modContext.getResources().getIdentifier("download", "drawable", "com.waenhancer");
                            if (resId != 0) {
                                customIcon = modContext.getResources().getDrawable(resId);
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[WAEX] Failed to load custom download drawable: " + e.toString());
                        }

                        // Fall back to original action button image if custom drawable fails
                        if (customIcon != null) {
                            downloadBtn.setImageDrawable(customIcon);
                        } else {
                            downloadBtn.setImageDrawable(origAction.getDrawable());
                        }

                        if (origAction.getBackground() != null) {
                            try {
                                downloadBtn.setBackground(origAction.getBackground().getConstantState().newDrawable());
                            } catch (Throwable e) {
                                downloadBtn.setBackground(origAction.getBackground());
                            }
                        }
                        downloadBtn.setScaleType(origAction.getScaleType());
                        downloadBtn.setPadding(
                                origAction.getPaddingLeft(),
                                origAction.getPaddingTop(),
                                origAction.getPaddingRight(),
                                origAction.getPaddingBottom()
                        );

                        // Copy layout params
                        ViewGroup.LayoutParams origLp = origAction.getLayoutParams();
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                origLp.width,
                                origLp.height
                        );
                        if (origLp instanceof LinearLayout.LayoutParams) {
                            LinearLayout.LayoutParams origLpLinear = (LinearLayout.LayoutParams) origLp;
                            lp.gravity = origLpLinear.gravity;
                            lp.weight = origLpLinear.weight;
                            lp.setMargins(
                                    origLpLinear.leftMargin,
                                    origLpLinear.topMargin,
                                    origLpLinear.rightMargin,
                                    origLpLinear.bottomMargin
                            );
                        }
                        downloadBtn.setLayoutParams(lp);

                        // Set tap-to-save click listener
                        downloadBtn.setOnClickListener(v -> saveVideoNote(fMessage));

                        // Add the button right next to the original action button
                        actionContainer.addView(downloadBtn);
                    }
                }
            }
        });
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
            
            // Build a unique, deterministic filename using the messageID to prevent duplicates
            var contactName = WppCore.getContactName(userJid);
            var number = userJid.getPhoneRawString();
            var name = Utils.toValidFileName(contactName) + "_" + number + "_" + fMessage.getKey().messageID + ".mp4";
            
            File destFile = new File(destination, name);
            if (destFile.exists()) {
                Utils.showToast("Already saved!", Toast.LENGTH_SHORT);
                return;
            }

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