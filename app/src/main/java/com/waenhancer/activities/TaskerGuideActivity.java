package com.waenhancer.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;

public class TaskerGuideActivity extends BaseActivity {

    private static final String ACTION_RECEIVED = "com.waenhancer.MESSAGE_RECEIVED";
    private static final String ACTION_SENT = "com.waenhancer.MESSAGE_SENT";

    private static final String AUTO_REPLY_SETUP =
            "── Tasker Setup ──\n\n" +
            "1. Open Tasker → Profiles → tap \"+\"\n" +
            "2. Select Event → System → Intent Received\n" +
            "3. Set Action to:\n" +
            "   com.waenhancer.MESSAGE_RECEIVED\n\n" +
            "4. Back → New Task → tap \"+\" to add action\n" +
            "5. Select System → Send Intent\n" +
            "6. Action: com.waenhancer.MESSAGE_SENT\n" +
            "7. Extra: number:%number\n" +
            "8. Extra: message:I'm busy right now!\n" +
            "9. Target: Broadcast Receiver\n\n" +
            "── MacroDroid Setup ──\n\n" +
            "1. Add Macro → Trigger → Intent Event\n" +
            "2. Action: com.waenhancer.MESSAGE_RECEIVED\n" +
            "3. Add Action → Send Intent\n" +
            "4. Action: com.waenhancer.MESSAGE_SENT\n" +
            "5. Add extras: number / message";

    private static final String TELEGRAM_SETUP =
            "── Tasker Setup ──\n\n" +
            "1. Open Tasker → Profiles → tap \"+\"\n" +
            "2. Select Event → System → Intent Received\n" +
            "3. Set Action to:\n" +
            "   com.waenhancer.MESSAGE_RECEIVED\n\n" +
            "4. Back → New Task → tap \"+\" to add action\n" +
            "5. Select Net → HTTP Request\n" +
            "6. Method: POST\n" +
            "7. URL: https://api.telegram.org/bot<TOKEN>/sendMessage\n" +
            "8. Body: chat_id=<CHAT_ID>&text=From %name: %message\n\n" +
            "── MacroDroid Setup ──\n\n" +
            "1. Trigger → Intent Event\n" +
            "   Action: com.waenhancer.MESSAGE_RECEIVED\n" +
            "2. Action → HTTP Request (POST)\n" +
            "3. URL: https://api.telegram.org/bot<TOKEN>/sendMessage\n" +
            "4. Body: chat_id=<CHAT_ID>&text=From {extra_name}: {extra_message}";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_guide);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Copy intent action buttons
        findViewById(R.id.btn_copy_incoming_action).setOnClickListener(v ->
                copyToClipboard("Intent Action", ACTION_RECEIVED));

        findViewById(R.id.btn_copy_outgoing_action).setOnClickListener(v ->
                copyToClipboard("Intent Action", ACTION_SENT));

        // Make variable rows clickable to copy
        setupVariableCopyListeners();

        // Expandable example cards
        setupExpandableCard(
                R.id.card_example_auto_reply,
                R.id.expandable_auto_reply,
                R.id.ic_expand_auto_reply,
                "Auto-Reply",
                AUTO_REPLY_SETUP
        );

        setupExpandableCard(
                R.id.card_example_telegram,
                R.id.expandable_telegram,
                R.id.ic_expand_telegram,
                "Forward to Telegram",
                TELEGRAM_SETUP
        );
    }

    /**
     * Walks through the incoming and outgoing cards, finding every variable-badge
     * LinearLayout and attaching a click-to-copy listener on the entire row.
     * The badge text (first child TextView) is used as the clipboard content.
     */
    private void setupVariableCopyListeners() {
        int[] cardIds = { R.id.card_incoming, R.id.card_outgoing };

        for (int cardId : cardIds) {
            MaterialCardView card = findViewById(cardId);
            attachBadgeClickListeners(card);
        }
    }

    private void attachBadgeClickListeners(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout row) {
                // A variable row is a horizontal LinearLayout whose first child
                // is a TextView with monospace font and a badge background.
                View first = row.getChildCount() > 0 ? row.getChildAt(0) : null;
                if (first instanceof android.widget.TextView badge
                        && badge.getBackground() != null
                        && badge.getText() != null
                        && badge.getText().length() > 0) {

                    String text = badge.getText().toString();
                    row.setClickable(true);
                    row.setFocusable(true);
                    row.setOnClickListener(v -> copyToClipboard("Variable", text));
                }

                // Recurse into nested layouts
                if (row.getChildCount() > 0) {
                    attachBadgeClickListeners(row);
                }
            } else if (child instanceof ViewGroup vg) {
                attachBadgeClickListeners(vg);
            }
        }
    }

    /**
     * Sets up an expandable card: tap toggles the body visibility with an
     * animated transition and rotates the chevron icon. Long-press opens a
     * full Material dialog with the complete setup guide + a Copy button.
     */
    private void setupExpandableCard(int cardResId, int bodyResId, int chevronResId,
                                      String title, String fullGuide) {
        MaterialCardView card = findViewById(cardResId);
        LinearLayout body = findViewById(bodyResId);
        ImageView chevron = findViewById(chevronResId);

        card.setOnClickListener(v -> {
            boolean expanding = body.getVisibility() != View.VISIBLE;

            // Animate the transition
            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition((ViewGroup) card.getParent(), transition);

            body.setVisibility(expanding ? View.VISIBLE : View.GONE);
            chevron.animate()
                    .rotation(expanding ? 270f : 90f)
                    .setDuration(200)
                    .start();
        });

        card.setOnLongClickListener(v -> {
            showGuideDialog(title, fullGuide);
            return true;
        });
    }

    private void showGuideDialog(String title, String guide) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Example: " + title)
                .setMessage(guide)
                .setPositiveButton("Copy Setup", (dialog, which) ->
                        copyToClipboard("Setup Guide", guide))
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        }
        Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
    }
}
