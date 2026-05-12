package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.waenhancer.xposed.utils.ThemeUtils;
import com.waenhancer.xposed.utils.XResManager;

/**
 * A self-contained, process-agnostic full-screen DialogFragment that wraps the
 * WaEnhancerX settings UI when injected into WhatsApp's process.
 *
 * Key design decisions:
 * - All UI is built programmatically via {@link SettingsViewBuilder} so we never
 *   inflate module XML layouts inside the host process (which crashes).
 * - All fragment navigation uses {@link #getChildFragmentManager()} so the
 *   back-stack is scoped to THIS dialog, not the host activity.
 * - Back-button interception pops our own child back-stack first; only dismisses
 *   the dialog when the stack is empty.
 */
public class EmbeddedSettingsDialogFragment extends DialogFragment {

    /** Tag used when committing child fragments so they can be found later. */
    static final String CHILD_FRAGMENT_TAG = "wae_settings_page";

    /** Stable view-ID for the fragment container generated at runtime. */
    private int mContainerId;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, resolveTheme());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Wrap the context so the module's resources / theme are used everywhere.
        android.content.Context ctx = requireModuleContext();

        // Build the chrome (toolbar + container) fully in code.
        SettingsViewBuilder.Host host = SettingsViewBuilder.buildHost(ctx);

        mContainerId = host.container.getId();

        // Back button in the toolbar pops our child back-stack.
        host.backButton.setOnClickListener(v -> handleBackPress());

        // Seed the root settings page if this is a fresh start.
        if (savedInstanceState == null) {
            showRootSettings();
        }

        // Update the toolbar title whenever the back-stack changes.
        getChildFragmentManager().addOnBackStackChangedListener(
                () -> syncToolbarTitle(host));
        syncToolbarTitle(host);

        return host.root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Remove the default dialog background so our programmatic view fills edge-to-edge.
        window.setBackgroundDrawableResource(android.R.color.transparent);
    }

    // -------------------------------------------------------------------------
    // Public API used by MenuHome / SettingsInjector
    // -------------------------------------------------------------------------

    /**
     * Navigate forward to an arbitrary embedded settings page, adding it to the
     * child back-stack so the back button works.
     *
     * @param fragment  The preference page fragment to show.
     * @param title     Optional toolbar title; may be null.
     */
    public void navigateTo(@NonNull Fragment fragment, @Nullable CharSequence title) {
        if (title != null && fragment.getArguments() == null) {
            Bundle args = new Bundle();
            args.putCharSequence(EmbeddedBasePreferenceFragment.ARG_TITLE, title);
            fragment.setArguments(args);
        }
        getChildFragmentManager()
                .beginTransaction()
                .replace(mContainerId, fragment, CHILD_FRAGMENT_TAG)
                .addToBackStack(CHILD_FRAGMENT_TAG)
                .commit();
    }

    /**
     * Called by the root settings page to expose the container ID so that
     * {@link EmbeddedBasePreferenceFragment#navigateToFragment()} can push
     * new pages without going through the host activity's FM.
     */
    public int getContainerId() {
        return mContainerId;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Navigate back; dismiss the dialog only when there is nothing left. */
    void handleBackPress() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            dismissAllowingStateLoss();
        }
    }

    private void showRootSettings() {
        EmbeddedMainFragment root = new EmbeddedMainFragment();
        getChildFragmentManager()
                .beginTransaction()
                .replace(mContainerId, root, CHILD_FRAGMENT_TAG)
                // Do NOT add to back-stack — this is the root.
                .commit();
    }

    private void syncToolbarTitle(@NonNull SettingsViewBuilder.Host host) {
        Fragment current = getChildFragmentManager()
                .findFragmentByTag(CHILD_FRAGMENT_TAG);
        CharSequence title = null;
        if (current instanceof EmbeddedBasePreferenceFragment) {
            title = ((EmbeddedBasePreferenceFragment) current).getToolbarTitle();
        } else if (current instanceof EmbeddedMainFragment) {
            title = "WaEnhancer";
        }
        if (title != null) {
            host.titleView.setText(title);
        }
        // Show the back arrow only when there is something to pop.
        int backStackCount = getChildFragmentManager().getBackStackEntryCount();
        host.backButton.setVisibility(backStackCount > 0 ? View.VISIBLE : View.GONE);
    }

    /** Returns a context wrapping the host context with module resources / theme. */
    @NonNull
    private android.content.Context requireModuleContext() {
        android.content.Context base = requireContext();
        int themeRes = resolveTheme();
        return new android.view.ContextThemeWrapper(base, themeRes) {
            @Override
            public android.content.res.Resources getResources() {
                if (XResManager.moduleResources != null) {
                    return XResManager.moduleResources;
                }
                return super.getResources();
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

    private int resolveTheme() {
        android.content.Context ctx = getContext();
        if (ctx == null) return com.waenhancer.R.style.Theme;
        return ThemeUtils.isNightMode(ctx)
                ? com.waenhancer.R.style.Theme
                : com.waenhancer.R.style.Theme_Light;
    }

    // -------------------------------------------------------------------------
    // Static factory — called from MenuHome / SettingsInjector
    // -------------------------------------------------------------------------

    /**
     * Show the embedded WaEnhancerX settings dialog inside {@code activity}.
     * Falls back to launching {@link EmbeddedSettingsActivity} in the module's
     * own process if the host activity doesn't have a FragmentManager.
     */
    public static void show(@NonNull Activity activity) {
        try {
            Object fm = null;
            try {
                fm = de.robv.android.xposed.XposedHelpers.callMethod(activity, "getSupportFragmentManager");
            } catch (Throwable ignored) {}

            if (fm != null) {
                EmbeddedSettingsDialogFragment dialog = new EmbeddedSettingsDialogFragment();
                try {
                    Object transaction = de.robv.android.xposed.XposedHelpers.callMethod(fm, "beginTransaction");
                    de.robv.android.xposed.XposedHelpers.callMethod(transaction, "add", dialog, "wae_embedded_settings");
                    de.robv.android.xposed.XposedHelpers.callMethod(transaction, "commitAllowingStateLoss");
                    ;
                    return;
                } catch (Throwable t) {
                    de.robv.android.xposed.XposedBridge.log(
                            "[WaEnhancer] show() via reflection failed: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log(
                    "[WaEnhancer] EmbeddedSettingsDialogFragment.show() error: " + t.getMessage());
        }

        // Fallback: open in module's own process.
        try {
            android.content.Intent intent = new android.content.Intent();
            intent.setComponent(new android.content.ComponentName(
                    com.waenhancer.BuildConfig.APPLICATION_ID,
                    EmbeddedSettingsActivity.class.getName()));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
    }
}
