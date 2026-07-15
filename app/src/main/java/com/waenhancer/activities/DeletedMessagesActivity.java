package com.waenhancer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.databinding.ActivityDeletedMessagesBinding;
import com.waenhancer.ui.fragments.DeletedMessagesFragment;
import com.waenhancer.xposed.utils.ProHelper;

public class DeletedMessagesActivity extends BaseActivity {

    private ActivityDeletedMessagesBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeletedMessagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            setupViewPager();
        }
    }

    private void setupViewPager() {
        binding.viewPager.setAdapter(new androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            @androidx.annotation.NonNull
            @Override
            public androidx.fragment.app.Fragment createFragment(int position) {
                return DeletedMessagesFragment.newInstance(position == 1);
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Individuals" : "Groups");
        }).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_deleted_messages_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        boolean isProVerified = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_pro_verified", false);
        boolean limitedFree = ProHelper.isLimitedFreePreferenceEnabled("recover_deleted_media");

        if (!ProHelper.isPluginInstalled(this)) {
            ProHelper.navigateToPluginPack(this);
            return;
        }

        if (!isProVerified && !limitedFree) {
            try {
                Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                Intent intent = new Intent(this, clazz);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (ClassNotFoundException ignored) {
            }
            return;
        }

        showSettingsDialog();
    }

    private void showSettingsDialog() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        int itemPadding = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);

        MaterialSwitch recoverMediaSwitch = new MaterialSwitch(this);
        recoverMediaSwitch.setText(R.string.recover_deleted_media);
        recoverMediaSwitch.setChecked(prefs.getBoolean("recover_deleted_media", false));
        recoverMediaSwitch.setPadding(0, itemPadding, 0, itemPadding);
        recoverMediaSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("recover_deleted_media", isChecked).apply());
        layout.addView(recoverMediaSwitch);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.deleted_messages_settings_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
