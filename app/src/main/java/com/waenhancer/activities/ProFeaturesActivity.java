package com.waenhancer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textview.MaterialTextView;
import com.waenhancer.adapter.ProFeatureAdapter;
import com.waenhancer.model.SearchableFeature;
import com.waenhancer.utils.FeatureCatalog;
import com.waenhancer.xposed.utils.ProHelper;
import com.waenhancer.activities.base.BaseActivity;
import java.util.ArrayList;
import java.util.List;

public class ProFeaturesActivity extends BaseActivity {

    private LinearLayout proFeaturesSection;
    private RecyclerView proFeaturesRecycler;
    private ProFeatureAdapter proFeaturesAdapter;
    private MaterialTextView tvProFeaturesTitle;

    private LinearLayout limitedFreeFeaturesSection;
    private RecyclerView limitedFreeFeaturesRecycler;
    private ProFeatureAdapter limitedFreeFeaturesAdapter;
    private MaterialTextView tvLimitedFreeFeaturesTitle;

    private int getResId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResId("activity_pro_features", "layout"));

        Toolbar toolbar = findViewById(getResId("toolbar", "id"));
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        proFeaturesSection = findViewById(getResId("pro_features_section", "id"));
        proFeaturesRecycler = findViewById(getResId("pro_features_recycler", "id"));
        tvProFeaturesTitle = findViewById(getResId("tv_pro_features_title", "id"));

        limitedFreeFeaturesSection = findViewById(getResId("limited_free_features_section", "id"));
        limitedFreeFeaturesRecycler = findViewById(getResId("limited_free_features_recycler", "id"));
        tvLimitedFreeFeaturesTitle = findViewById(getResId("tv_limited_free_features_title", "id"));

        if (proFeaturesRecycler != null) {
            proFeaturesRecycler.setLayoutManager(new LinearLayoutManager(this));
            proFeaturesAdapter = new ProFeatureAdapter(this::navigateAndHighlightFeature);
            proFeaturesRecycler.setAdapter(proFeaturesAdapter);
        }

        if (limitedFreeFeaturesRecycler != null) {
            limitedFreeFeaturesRecycler.setLayoutManager(new LinearLayoutManager(this));
            limitedFreeFeaturesAdapter = new ProFeatureAdapter(this::navigateAndHighlightFeature);
            limitedFreeFeaturesRecycler.setAdapter(limitedFreeFeaturesAdapter);
        }

        loadFeatures();
    }

    private void loadFeatures() {
        String proStatus = ProHelper.getProStatus();
        boolean isPro = "ACTIVE".equalsIgnoreCase(proStatus);

        List<SearchableFeature> proFeatures = new ArrayList<>();
        List<SearchableFeature> limitedFreeFeatures = new ArrayList<>();
        try {
            List<SearchableFeature> allFeatures = FeatureCatalog.getAllFeatures(this);
            if (allFeatures != null) {
                for (SearchableFeature feature : allFeatures) {
                    String key = feature.getKey();
                    if ("file_size_spoofer".equals(key)
                            || "filter_group_members_messages".equals(key)
                            || "message_bomber".equals(key)
                            || "delete_message_file".equals(key)
                            || "pro_status_splitter".equals(key)
                            || "customize_status_view_category".equals(key)
                            || "always_typing_global".equals(key)
                            || "floating_bottom_bar_pill_design".equals(key)
                            || "filter_items".equals(key)
                            || "unlock_premium_customization".equals(key)
                            || "send_audio_as_voice_status".equals(key)) {

                        if (ProHelper.isLimitedFreePreferenceEnabled(key)) {
                            limitedFreeFeatures.add(feature);
                        } else {
                            proFeatures.add(feature);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (proFeaturesAdapter != null) {
            proFeaturesAdapter.setFeatures(proFeatures);
        }
        if (limitedFreeFeaturesAdapter != null) {
            limitedFreeFeaturesAdapter.setFeatures(limitedFreeFeatures);
        }

        if (proFeaturesSection != null) {
            proFeaturesSection.setVisibility(proFeatures.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (limitedFreeFeaturesSection != null) {
            limitedFreeFeaturesSection.setVisibility(limitedFreeFeatures.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (tvProFeaturesTitle != null) {
            tvProFeaturesTitle.setText(isPro ? "Exclusive Pro Features" : "Pro Features you're missing");
        }

        if (tvLimitedFreeFeaturesTitle != null) {
            tvLimitedFreeFeaturesTitle.setText(isPro ? "Active Promo Features" : "Limited Free Features (Pro Recommended)");
        }
    }

    private void navigateAndHighlightFeature(SearchableFeature feature) {
        try {
            String key = feature.getKey();
            SearchableFeature.FragmentType fragmentType = feature.getFragmentType();
            String typeName = fragmentType.name();
            int position = fragmentType.getPosition();

            if ("ACTIVITY".equals(typeName)) {
                if ("deleted_messages_activity".equals(key)) {
                    Intent intent = new Intent();
                    intent.setClassName(this, "com.waenhancer.activities.DeletedMessagesActivity");
                    startActivity(intent);
                }
                return;
            }

            String parentKey = feature.getParentKey();

            Intent intent = new Intent();
            intent.setClassName(this, "com.waenhancer.activities.MainActivity");
            intent.putExtra("navigate_to_fragment", position);
            intent.putExtra("scroll_to_preference", key);
            intent.putExtra("parent_preference", parentKey);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
