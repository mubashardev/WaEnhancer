package com.waenhancer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.waenhancer.model.SearchableFeature;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import com.waenhancer.xposed.utils.ProHelper;

/**
 * Premium adapter designed exclusively for rendering active Pro features
 * in a stunning Material Design 3 card format.
 */
public class ProFeatureAdapter extends RecyclerView.Adapter<ProFeatureAdapter.ViewHolder> {

    private final List<SearchableFeature> features;
    private final OnFeatureClickListener listener;

    public interface OnFeatureClickListener {
        void onFeatureClick(SearchableFeature feature);
    }

    public ProFeatureAdapter(OnFeatureClickListener listener) {
        this.features = new ArrayList<>();
        this.listener = listener;
    }

    public void setFeatures(List<SearchableFeature> newFeatures) {
        this.features.clear();
        if (newFeatures != null) {
            this.features.addAll(newFeatures);
        }
        notifyDataSetChanged();
    }

    private static int getResId(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = getResId(parent.getContext(), "item_pro_feature_card", "layout");
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(features.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return features.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView summaryView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            Context context = itemView.getContext();
            iconView = itemView.findViewById(getResId(context, "pro_feature_icon", "id"));
            titleView = itemView.findViewById(getResId(context, "pro_feature_title", "id"));
            summaryView = itemView.findViewById(getResId(context, "pro_feature_summary", "id"));
        }

        public void bind(SearchableFeature feature, OnFeatureClickListener clickListener) {
            Context context = itemView.getContext();
            try {
                String title = feature.getTitle();
                String summary = feature.getSummary();
                String key = feature.getKey();

                boolean isPro = "ACTIVE".equalsIgnoreCase(ProHelper.getProStatus());
                boolean isLimitedFree = ProHelper.isLimitedFreePreferenceEnabled(key);
                if (isLimitedFree && !isPro) {
                    titleView.setText(title + " (Limited Free)");
                } else {
                    titleView.setText(title);
                }

                summaryView.setText(summary);

                // Map custom premium icons based on feature keys
                int iconRes = getResId(context, "ic_general", "drawable");
                if ("delete_message_file".equals(key) || "delete_message_file_sent".equals(key)) {
                    iconRes = getResId(context, "ic_delete", "drawable");
                } else if ("message_bomber".equals(key)) {
                    iconRes = getResId(context, "edit", "drawable");
                } else if ("pro_status_splitter".equals(key)) {
                    iconRes = getResId(context, "ic_media", "drawable");
                } else if ("customize_status_view_category".equals(key)) {
                    iconRes = getResId(context, "eye_enabled", "drawable");
                } else if ("always_typing_global".equals(key)) {
                    iconRes = getResId(context, "edit2", "drawable");
                } else if ("floating_bottom_bar_pill_design".equals(key) || "unlock_premium_customization".equals(key)) {
                    iconRes = getResId(context, "ic_palette", "drawable");
                } else if ("file_size_spoofer".equals(key)) {
                    iconRes = getResId(context, "ic_media", "drawable");
                } else if ("filter_group_members_messages".equals(key)) {
                    iconRes = getResId(context, "ic_general", "drawable"); // Fallback to general or similar safe drawable
                }
                iconView.setImageResource(iconRes);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Click listener integration
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onFeatureClick(feature);
                }
            });
        }
    }
}
