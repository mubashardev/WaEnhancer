package com.waenhancer.xposed.features.others;

import android.app.Dialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import com.waenhancer.xposed.utils.Utils;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.waenhancer.R;
import com.waenhancer.utils.FilePicker;
import com.waenhancer.xposed.utils.ThemeUtils;
import com.waenhancer.xposed.utils.ResId;

public class EmbeddedSettingsDialogFragment extends DialogFragment {

    private SettingsViewBuilder.Host host;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            int themeRes = ThemeUtils.isNightMode(getContext()) ? ResId.style.Theme : ResId.style.Theme_Light;
            if (themeRes == 0) {
                themeRes = ThemeUtils.isNightMode(getContext())
                        ? android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen
                        : android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen;
            }
            setStyle(STYLE_NORMAL, themeRes);
        } catch (Throwable t) {
            Utils.log("[WaEnhancer] EmbeddedSettingsDialogFragment: Error in onCreate: " + t.getMessage());
            t.printStackTrace();
        }
    }

    @Override
    public android.content.Context getContext() {
        android.content.Context context = super.getContext();
        if (context == null) return null;
        int themeRes = ThemeUtils.isNightMode(context) ? ResId.style.Theme : ResId.style.Theme_Light;
        return new android.view.ContextThemeWrapper(context, themeRes) {
            @Override
            public android.content.res.Resources getResources() {
                if (com.waenhancer.xposed.utils.XResManager.moduleResources != null) {
                    return com.waenhancer.xposed.utils.XResManager.moduleResources;
                }
                return super.getResources();
            }

            @Override
            public Object getSystemService(String name) {
                if (android.content.Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                    return android.view.LayoutInflater.from(getBaseContext()).cloneInContext(this);
                }
                return super.getSystemService(name);
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        try {
            host = SettingsViewBuilder.buildHost(getContext());
            return host.root;
        } catch (Throwable t) {
            Utils.log("[WaEnhancer] EmbeddedSettingsDialogFragment: Error in onCreateView: " + t.getMessage());
            t.printStackTrace();
            return new View(getContext());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        try {
            super.onViewCreated(view, savedInstanceState);
            if (host == null) return;

            if (savedInstanceState == null) {
                getChildFragmentManager()
                        .beginTransaction()
                        .replace(host.container.getId(), new EmbeddedSettingsFragment())
                        .commitNowAllowingStateLoss();
            }

            Runnable updateToolbar = new Runnable() {
                @Override
                public void run() {
                    Fragment current = getChildFragmentManager().findFragmentById(host.container.getId());
                    CharSequence title = getString(ResId.string.app_name);
                    if (current instanceof EmbeddedBasePreferenceFragment) {
                        EmbeddedBasePreferenceFragment embeddedFragment = (EmbeddedBasePreferenceFragment) current;
                        title = embeddedFragment.getToolbarTitle();
                    }
                    host.titleView.setText(title);
                    host.backButton.setOnClickListener(v -> handleBack());
                }
            };
            getChildFragmentManager().addOnBackStackChangedListener(updateToolbar::run);
            updateToolbar.run();

            try {
                requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (!handleBack()) {
                                    dismissAllowingStateLoss();
                                }
                            }
                        });
            } catch (Throwable t) {
                Utils.log("[WaEnhancer] EmbeddedSettingsDialogFragment: OnBackPressedDispatcher not supported fallback used.");
            }
        } catch (Throwable t) {
            Utils.log("[WaEnhancer] EmbeddedSettingsDialogFragment: Error in onViewCreated: " + t.getMessage());
            t.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(resolveStatusBarColor());
        dialog.setOnKeyListener((d, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP
                        && handleBack());
    }

    private boolean handleBack() {
        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            getChildFragmentManager().popBackStack();
            return true;
        }
        dismissAllowingStateLoss();
        return false;
    }

    private int resolveStatusBarColor() {
        try {
            var res = requireActivity().getResources();
            int id = res.getIdentifier("colorPrimaryDark", "color", requireActivity().getPackageName());
            if (id != 0) {
                return res.getColor(id, requireActivity().getTheme());
            }
        } catch (Throwable ignored) {
        }
        return ThemeUtils.isNightMode(requireActivity()) ? 0xff121b22 : 0xff00695c;
    }
}
