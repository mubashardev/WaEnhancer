package com.waenhancer.xposed.features.others;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.ThemeUtils;
import com.waenhancer.xposed.utils.XResManager;
import com.waenhancer.ui.fragments.CustomizationFragment;
import com.waenhancer.ui.fragments.GeneralFragment;
import com.waenhancer.ui.fragments.HomeFragment;
import com.waenhancer.ui.fragments.MediaFragment;
import com.waenhancer.ui.fragments.PrivacyFragment;

/**
 * Root fragment shown inside {@link EmbeddedSettingsDialogFragment}.
 *
 * Builds the tab-pager UI entirely in code so it runs correctly inside the
 * WhatsApp host process (where module XML cannot be inflated directly).
 *
 * Uses {@link #getChildFragmentManager()} and passes {@code this} (a Fragment)
 * to {@link FragmentStateAdapter} — the adapter accepts a Fragment host, which
 * correctly scopes all page-fragments to our child FM rather than the host
 * activity's FM.
 */
public class EmbeddedMainFragment extends Fragment {

    private static final int[] TAB_ICONS = {
            R.drawable.ic_general,
            R.drawable.ic_privacy,
            R.drawable.ic_home_black_24dp,
            R.drawable.ic_media,
            R.drawable.ic_dashboard_black_24dp
    };

    private ViewPager2 mViewPager;
    private TabLayout mTabLayout;

    /**
     * Wraps the base context with module resources and theme so that all views
     * created inside this fragment resolve strings, drawables, and styles from
     * the module's resource table — even when injected inside WhatsApp's process.
     */
    @Override
    public android.content.Context getContext() {
        android.content.Context base = super.getContext();
        if (base == null) return null;
        if (XResManager.moduleResources == null) return base;
        int themeRes = ThemeUtils.isNightMode(base)
                ? com.waenhancer.R.style.Theme
                : com.waenhancer.R.style.Theme_Light;
        return new android.view.ContextThemeWrapper(base, themeRes) {
            @Override
            public android.content.res.Resources getResources() {
                return XResManager.moduleResources;
            }

            @Override
            public Object getSystemService(String name) {
                if (LAYOUT_INFLATER_SERVICE.equals(name)) {
                    return android.view.LayoutInflater.from(getBaseContext()).cloneInContext(this);
                }
                return super.getSystemService(name);
            }
        };
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Build the tab + pager layout entirely in code.
        android.content.Context ctx = requireContext();

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // TabLayout
        mTabLayout = new TabLayout(ctx);
        mTabLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        root.addView(mTabLayout);

        // ViewPager2
        mViewPager = new ViewPager2(ctx);
        android.widget.LinearLayout.LayoutParams pagerParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mViewPager.setLayoutParams(pagerParams);
        root.addView(mViewPager);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Use THIS fragment as the lifecycle owner so pages are scoped correctly.
        mViewPager.setAdapter(new EmbeddedPagerAdapter(this));

        new TabLayoutMediator(mTabLayout, mViewPager, (tab, position) -> {
            tab.setText(getTabTitle(position));
            try {
                if (XResManager.moduleResources != null) {
                    tab.setIcon(DesignUtils.getDrawable(TAB_ICONS[position]));
                }
            } catch (Throwable ignored) {}
        }).attach();

        // Default to Home tab (position 2).
        mViewPager.setCurrentItem(2, false);
    }

    private String getTabTitle(int position) {
        String title = null;
        try {
            if (XResManager.moduleResources != null) {
                title = switch (position) {
                    case 0 -> com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.general);
                    case 1 -> com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.privacy);
                    case 2 -> com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.title_home);
                    case 3 -> com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.media);
                    case 4 -> com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.perso);
                    default -> null;
                };
            }
        } catch (Throwable ignored) {}

        if (title != null && !title.isEmpty()) {
            return title;
        }

        return switch (position) {
            case 0 -> "General";
            case 1 -> "Privacy";
            case 2 -> "Home";
            case 3 -> "Media";
            case 4 -> "Styles";
            default -> "?";
        };
    }

    // -------------------------------------------------------------------------
    // Pager adapter – hosted by THIS fragment, not the activity.
    // -------------------------------------------------------------------------

    private static final class EmbeddedPagerAdapter extends FragmentStateAdapter {

        EmbeddedPagerAdapter(@NonNull Fragment fragment) {
            // This constructor scopes all created fragments to the fragment's
            // childFragmentManager and viewLifecycleOwner.
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 0 -> new GeneralFragment();
                case 1 -> new PrivacyFragment();
                case 3 -> new MediaFragment();
                case 4 -> new CustomizationFragment();
                default -> new HomeFragment();
            };
        }

        @Override
        public int getItemCount() {
            return 5;
        }
    }
}
