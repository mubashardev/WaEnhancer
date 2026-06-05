package com.waenhancer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.db.MessageHistory;
import com.waenhancer.xposed.features.listeners.ConversationItemListener;
import com.waenhancer.xposed.utils.Utils;

import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Handler;
import android.os.Looper;

public class HideSeenView extends Feature {

    private static final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();
    private static final ConcurrentHashMap<String, Boolean> loadedJids = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> loadingJids = new ConcurrentHashMap<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean refreshScheduled = new AtomicBoolean(false);

    public HideSeenView(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    public static void updateAllBubbleViews() {
        var adapter = ConversationItemListener.getAdapter();
        if (adapter instanceof CursorAdapter) {
            CursorAdapter cursorAdapter = (CursorAdapter) adapter;
            WppCore.getCurrentActivity().runOnUiThread(cursorAdapter::notifyDataSetChanged);
        }
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("hide_seen_view", false)) return;

        // Register listener
        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                if (fMessage.getKey().isFromMe) return;
                updateBubbleView(fMessage, viewGroup);
            }
        });
    }

    private static void ensureJidCacheLoaded(String jid) {
        if (loadedJids.containsKey(jid)) return;
        if (loadingJids.putIfAbsent(jid, Boolean.TRUE) != null) return;

        cacheExecutor.execute(() -> {
            try {
                MessageHistory.getInstance().getHideSeenMessages(jid, MessageHistory.MessageType.MESSAGE_TYPE, true);
                MessageHistory.getInstance().getHideSeenMessages(jid, MessageHistory.MessageType.MESSAGE_TYPE, false);
                MessageHistory.getInstance().getHideSeenMessages(jid, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                MessageHistory.getInstance().getHideSeenMessages(jid, MessageHistory.MessageType.VIEW_ONCE_TYPE, false);

                loadedJids.put(jid, Boolean.TRUE);
                requestRefresh();
            } catch (Throwable ignored) {
            } finally {
                loadingJids.remove(jid);
            }
        });
    }

    private static void requestRefresh() {
        if (!refreshScheduled.compareAndSet(false, true)) return;
        mainHandler.postDelayed(() -> {
            refreshScheduled.set(false);
            updateAllBubbleViews();
        }, 100);
    }

    @SuppressLint("ResourceType")
    private static void updateBubbleView(FMessageWpp fmessage, View viewGroup) {
        var userJid = fmessage.getKey().remoteJid;
        var messageId = fmessage.getKey().messageID;
        if (userJid.isNull()) return;
        var jid = userJid.getPhoneRawString();

        ensureJidCacheLoaded(jid);

        ImageView view = viewGroup.findViewById(Utils.getID("view_once_control_icon", "id"));
        if (view != null) {
            var messageOnce = MessageHistory.getInstance().getHideSeenMessageOnlyCache(jid, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE);
            if (messageOnce != null) {
                view.setColorFilter(messageOnce.viewed ? Color.GREEN : Color.RED);
            } else {
                view.setColorFilter(null);
            }
        }
        ViewGroup dateWrapper = viewGroup.findViewById(Utils.getID("date_wrapper", "id"));
        if (dateWrapper != null) {
            TextView status = dateWrapper.findViewById(0xf7ff2001);
            if (status == null) {
                status = new TextView(viewGroup.getContext());
                status.setId(0xf7ff2001);
                status.setTextSize(8);
                dateWrapper.addView(status);
            }
            var message = MessageHistory.getInstance().getHideSeenMessageOnlyCache(jid, messageId, MessageHistory.MessageType.MESSAGE_TYPE);
            if (message != null) {
                status.setVisibility(View.VISIBLE);
                status.setText(message.viewed ? "\uD83D\uDFE2" : "\uD83D\uDD34");
            } else {
                status.setVisibility(View.GONE);
            }
        }
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen View";
    }
}
