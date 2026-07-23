package com.waenhancer.adapter;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.waenhancer.R;
import com.waenhancer.model.FilterItem;
import com.waenhancer.xposed.utils.DesignUtils;

import java.util.List;
import java.util.Locale;

public class FilterItemsAdapter extends RecyclerView.Adapter<FilterItemsAdapter.FilterViewHolder> {

    public interface OnFilterActionListener {
        void onDelete(int position);
        void onEdit(int position);
    }

    private final List<FilterItem> filters;
    private final OnFilterActionListener actionListener;

    public FilterItemsAdapter(List<FilterItem> filters, OnFilterActionListener actionListener) {
        this.filters = filters;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_filter_id, parent, false);
        return new FilterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
        FilterItem item = filters.get(position);
        holder.bind(item, position, actionListener);
    }

    @Override
    public int getItemCount() {
        return filters.size();
    }

    static class FilterViewHolder extends RecyclerView.ViewHolder {
        private final TextView filterText;
        private final TextView filterSubtitle;
        private final ImageView filterIcon;
        private final ImageButton deleteBtn;

        FilterViewHolder(@NonNull View itemView) {
            super(itemView);
            filterText = itemView.findViewById(R.id.filter_id_text);
            filterSubtitle = itemView.findViewById(R.id.filter_subtitle_text);
            filterIcon = itemView.findViewById(R.id.filter_icon);
            deleteBtn = itemView.findViewById(R.id.btn_delete_filter);
        }

        void bind(FilterItem item, int position, OnFilterActionListener listener) {
            filterText.setText(item.id);
            
            // Customize icon and subtitle depending on behavior
            String subtitle = "";
            int accentColor = DesignUtils.resolveColorAttr(filterIcon.getContext(), android.R.attr.colorAccent);
            if (accentColor == 0) {
                accentColor = 0xFF25D366; // Fallback WhatsApp Green
            }

            switch (item.behavior) {
                case FilterItem.BEHAVIOR_GONE:
                    subtitle = "Behavior: Gone (Remove)";
                    filterIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    filterIcon.setImageTintList(ColorStateList.valueOf(0xFF888888));
                    break;
                case FilterItem.BEHAVIOR_COLOR:
                    subtitle = "Behavior: Change Color";
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setColor(item.color);
                    filterIcon.setImageDrawable(circle);
                    filterIcon.setImageTintList(null); // Clear tint
                    break;
                case FilterItem.BEHAVIOR_OPACITY:
                    subtitle = "Behavior: Opacity (" + item.opacity + "%)";
                    filterIcon.setImageResource(android.R.drawable.ic_menu_view);
                    filterIcon.setImageTintList(ColorStateList.valueOf(accentColor));
                    break;
                case FilterItem.BEHAVIOR_RESIZE:
                    subtitle = String.format(Locale.US, "Behavior: Resize (%.1fx)", item.scale);
                    filterIcon.setImageResource(android.R.drawable.ic_menu_crop);
                    filterIcon.setImageTintList(ColorStateList.valueOf(accentColor));
                    break;
            }
            filterSubtitle.setText(subtitle);

            // Clicking the card triggers Edit
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(position);
                }
            });

            // Clicking the trash icon triggers Delete
            deleteBtn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(position);
                }
            });
        }
    }
}