package com.waenhancer.xposed.features.others;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;

/**
 * A lightweight activity in the MODULE's own process that hosts
 * {@link EmbeddedMainFragment}.
 *
 * This is the fallback used when WhatsApp's host activity does not support
 * getSupportFragmentManager() (e.g. very old obfuscated builds).
 *
 * Unlike the dialog path, this is a proper {@link BaseActivity} so all module
 * resources, themes, and navigation work natively.
 */
public class EmbeddedSettingsActivity extends BaseActivity {

    private static final String TAG_ROOT = "wae_root";
    private int mContainerId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Build a minimal full-screen container in code.
        FrameLayout container = new FrameLayout(this);
        mContainerId = View.generateViewId();
        container.setId(mContainerId);
        setContentView(container);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(mContainerId, new EmbeddedMainFragment(), TAG_ROOT)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
