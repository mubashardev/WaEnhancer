package com.waenhancer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.waenhancer.R;
import com.waenhancer.model.Recording;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {

    public interface OnRecordingActionListener {
        void onPlay(Recording recording);
        void onShare(Recording recording);
        void onDelete(Recording recording);
        void onLongPress(Recording recording, int position);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    private final OnRecordingActionListener listener;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    private List<Recording> recordings = new ArrayList<>();
    private boolean selectionMode;
    private OnSelectionChangeListener selectionChangeListener;

    public RecordingsAdapter(@NonNull OnRecordingActionListener listener) {
        this.listener = listener;
    }

    public void setRecordings(@NonNull List<Recording> recordings) {
        this.recordings = recordings;
        clearSelection();
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        if (this.selectionMode != selectionMode) {
            this.selectionMode = selectionMode;
            if (!selectionMode) {
                selectedPositions.clear();
            }
            notifyDataSetChanged();
        }
    }

    public void setSelectionChangeListener(OnSelectionChangeListener selectionChangeListener) {
        this.selectionChangeListener = selectionChangeListener;
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        notifySelectionChanged();
    }

    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < recordings.size(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectedPositions.clear();
        selectionMode = false;
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    @NonNull
    public List<Recording> getSelectedRecordings() {
        List<Recording> selected = new ArrayList<>();
        for (int position : selectedPositions) {
            if (position < recordings.size()) {
                selected.add(recordings.get(position));
            }
        }
        return selected;
    }

    private void notifySelectionChanged() {
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recording recording = recordings.get(position);
        holder.contactName.setText(recording.getContactName());
        holder.duration.setText(recording.getFormattedDuration());
        holder.details.setText(recording.getFormattedSize() + " • " + dateFormat.format(new Date(recording.getDate())));

        boolean selected = selectedPositions.contains(position);
        holder.checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.actionsContainer.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        holder.checkbox.setChecked(selected);
        holder.card.setChecked(selected);

        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(position);
            } else {
                listener.onPlay(recording);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (!selectionMode) {
                listener.onLongPress(recording, position);
            }
            return true;
        });
        holder.checkbox.setOnClickListener(v -> toggleSelection(position));
        holder.btnPlay.setOnClickListener(v -> listener.onPlay(recording));
        holder.btnShare.setOnClickListener(v -> listener.onShare(recording));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(recording));
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final CheckBox checkbox;
        final ImageView icon;
        final TextView contactName;
        final TextView duration;
        final TextView details;
        final LinearLayout actionsContainer;
        final ImageButton btnPlay;
        final ImageButton btnShare;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            checkbox = itemView.findViewById(R.id.checkbox);
            icon = itemView.findViewById(R.id.icon);
            contactName = itemView.findViewById(R.id.contact_name);
            duration = itemView.findViewById(R.id.duration);
            details = itemView.findViewById(R.id.details);
            actionsContainer = itemView.findViewById(R.id.actions_container);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
