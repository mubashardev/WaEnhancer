package com.waenhancer.xposed.features.others;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.waenhancer.R;
import com.waenhancer.utils.FilePicker;

/**
 * An activity that runs inside the WhatsApp process to host WaEnhancer settings
 * when the current screen cannot present a support-fragment dialog.
 */
public class EmbeddedSettingsActivity extends AppCompatActivity {

    private SettingsViewBuilder.Host host;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(com.waenhancer.xposed.utils.DesignUtils.isNightMode() ? R.style.Theme : R.style.Theme_Light);
        super.onCreate(savedInstanceState);
        host = SettingsViewBuilder.buildHost(this);
        setContentView(host.root);
        FilePicker.registerFilePicker(this);

        host.backButton.setOnClickListener(v -> handleBack());
        updateToolbarTitle();
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbarTitle);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(host.container.getId(), new EmbeddedSettingsFragment())
                    .commitNowAllowingStateLoss();
            updateToolbarTitle();
        }
    }

    @Override
    public void onBackPressed() {
        if (!handleBack()) {
            super.onBackPressed();
        }
    }

    private boolean handleBack() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return true;
        }
        finish();
        return false;
    }

    private void updateToolbarTitle() {
        Fragment current = getSupportFragmentManager().findFragmentById(host.container.getId());
        CharSequence title = getString(R.string.app_name);
        if (current instanceof EmbeddedBasePreferenceFragment embeddedFragment) {
            title = embeddedFragment.getToolbarTitle();
        }
        host.titleView.setText(title);
    }
}
