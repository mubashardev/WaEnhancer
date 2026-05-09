package com.waenhancer.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.waenhancer.R;
import com.waenhancer.model.TaskerEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskerHistoryAdapter extends RecyclerView.Adapter<TaskerHistoryAdapter.EventViewHolder> {

    private final List<TaskerEvent> events = new ArrayList<>();
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

    public void submitList(@NonNull List<TaskerEvent> newEvents) {
        events.clear();
        events.addAll(newEvents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tasker_history, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(events.get(position));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {

        private final View iconBg;
        private final ImageView iconDirection;
        private final TextView tvNumber;
        private final TextView tvTimestamp;
        private final TextView tvTypeBadge;
        private final TextView tvMessagePreview;

        // Incoming = green tones, Outgoing = blue tones
        private static final int COLOR_INCOMING = 0xFF2E7D32;   // Green 800
        private static final int COLOR_INCOMING_BG = 0x1A4CAF50; // Green with 10% alpha
        private static final int COLOR_OUTGOING = 0xFF1565C0;   // Blue 800
        private static final int COLOR_OUTGOING_BG = 0x1A2196F3; // Blue with 10% alpha

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            iconBg = itemView.findViewById(R.id.icon_bg);
            iconDirection = itemView.findViewById(R.id.icon_direction);
            tvNumber = itemView.findViewById(R.id.tv_number);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvTypeBadge = itemView.findViewById(R.id.tv_type_badge);
            tvMessagePreview = itemView.findViewById(R.id.tv_message_preview);
        }

        void bind(@NonNull TaskerEvent event) {
            boolean incoming = TaskerEvent.TYPE_INCOMING.equals(event.type);

            // Icon: down-arrow for incoming, up-arrow for outgoing
            iconDirection.setRotation(incoming ? 90f : -90f);
            int tintColor = incoming ? COLOR_INCOMING : COLOR_OUTGOING;
            int bgColor = incoming ? COLOR_INCOMING_BG : COLOR_OUTGOING_BG;
            iconDirection.setColorFilter(tintColor);
            iconBg.setBackgroundTintList(ColorStateList.valueOf(bgColor));

            // Number
            tvNumber.setText(event.targetNumber != null ? event.targetNumber : "Unknown");

            // Timestamp
            tvTimestamp.setText(TIME_FORMAT.format(new Date(event.timestamp)));

            // Type badge
            tvTypeBadge.setText(incoming ? "↓ INCOMING" : "↑ OUTGOING");
            tvTypeBadge.setTextColor(tintColor);
            tvTypeBadge.setBackgroundTintList(ColorStateList.valueOf(bgColor));

            // Message preview
            if (event.messagePreview != null && !event.messagePreview.isEmpty()) {
                tvMessagePreview.setVisibility(View.VISIBLE);
                tvMessagePreview.setText(event.messagePreview);
            } else {
                tvMessagePreview.setVisibility(View.GONE);
            }
        }
    }
}
