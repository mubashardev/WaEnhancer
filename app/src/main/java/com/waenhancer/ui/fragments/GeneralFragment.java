package com.waenhancer.ui.fragments;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.waenhancer.R;
import com.waenhancer.ui.fragments.base.BaseFragment;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;
import com.waenhancer.utils.RootUtils;

import java.io.File;

public class GeneralFragment extends BaseFragment {

    private TabLayout tabLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_general_tabbed, container, false);
        tabLayout = root.findViewById(R.id.general_tabs);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.general));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.home_screen));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.conversation));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (savedInstanceState == null) {
            switchTab(0);
        }

        return root;
    }

    private void switchTab(int position) {
        Fragment fragment = switch (position) {
            case 1 -> new HomeScreenGeneralPreference();
            case 2 -> new ConversationGeneralPreference();
            default -> new GeneralPreferenceFragment();
        };
        getChildFragmentManager().beginTransaction()
                .replace(R.id.general_frag_container, fragment)
                .commit();
    }

    public void showTab(String parentKey) {
        if (tabLayout == null) return;
        int tabIndex = 0;
        if ("general_home".equals(parentKey) || "general".equals(parentKey)) {
            tabIndex = 0;
        } else if ("homescreen".equals(parentKey)) {
            tabIndex = 1;
        } else if ("conversation".equals(parentKey)) {
            tabIndex = 2;
        }
        TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
        if (tab != null && tabLayout.getSelectedTabPosition() != tabIndex) {
            tab.select();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (androidx.fragment.app.Fragment fragment : getChildFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    public static class GeneralPreferenceFragment extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.fragment_general, rootKey);
            updatePluginPreference();
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
            updatePluginPreference();
            setupManageVersionsPref();
        }

        private void setupManageVersionsPref() {
            androidx.preference.Preference pref = findPreference("manage_supported_versions");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    android.content.Intent intent = new android.content.Intent(requireContext(),
                            SupportedVersionsActivity.class);
                    startActivity(intent);
                    return true;
                });
            }
        }

        private void updatePluginPreference() {
            android.content.Context context = getContext();
            if (context == null) return;
            androidx.preference.Preference pref = findPreference("unlock_limited_free");
            androidx.preference.Preference updatesPref = findPreference("pro_plugin_updates");
            androidx.preference.PreferenceCategory category = findPreference("plugin_pack_category");
            if (category == null) return;

            boolean isInstalled = com.waenhancer.xposed.utils.ProHelper.isPluginInstalled(context);
            if (isInstalled) {
                category.setVisible(true);
                if (pref != null) pref.setVisible(false);
                if (updatesPref != null) {
                    updatesPref.setVisible(true);
                    updatesPref.setOnPreferenceClickListener(preference -> {
                        try {
                            android.content.Intent intent = new android.content.Intent();
                            intent.setClassName("com.waex.helper", "com.waex.helper.activities.ProUpdateActivity");
                            var prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                            var colorPreset = prefs.getString("wae_color_preset", "green");
                            intent.putExtra("wae_color_preset", colorPreset);
                            startActivity(intent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(context, "Failed to launch update activity: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }
            } else {
                category.setVisible(true);
                if (pref != null) {
                    pref.setVisible(true);
                    pref.setOnPreferenceClickListener(preference -> {
                        com.waenhancer.xposed.utils.ProHelper.checkRootAndInstallPlugin(getActivity(), null);
                        return true;
                    });
                }
                if (updatesPref != null) updatesPref.setVisible(false);
            }
        }
    }

    public static class HomeScreenGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_homescreen, rootKey);
            setDisplayHomeAsUpEnabled(false);
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }
    }

    public static class ConversationGeneralPreference extends BasePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.preference_general_conversation, rootKey);
            setDisplayHomeAsUpEnabled(false);

            androidx.preference.EditTextPreference customLimitPref = findPreference("customforwardlimit");
            if (customLimitPref != null) {
                customLimitPref.setSummaryProvider(preference -> {
                    String val = customLimitPref.getText();
                    boolean hasKey = customLimitPref.getSharedPreferences() != null && customLimitPref.getSharedPreferences().contains("customforwardlimit");
                    if (!hasKey || android.text.TextUtils.isEmpty(val)) {
                        return getString(R.string.customforwardlimit_sum);
                    }
                    return val;
                });
                customLimitPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789"));
                    editText.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
                });
            }

            androidx.preference.Preference filterPref = findPreference("filter_group_members_messages");
            if (filterPref != null) {
                filterPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    return true;
                });
            }
        }

        /*
        private void handleFilterGroupMessagesEnable(final androidx.preference.Preference filterPref) {
            final Context context = getContext();
            if (context == null) return;

            // --- Build progress dialog ---
            int pad = dpToPx(context, 24);
            int padSmall = dpToPx(context, 8);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(pad, pad, pad, padSmall);

            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 8));
            progressBar.setLayoutParams(pbParams);
            root.addView(progressBar);

            TextView stepLabel = new TextView(context);
            stepLabel.setPadding(0, padSmall, 0, 0);
            stepLabel.setTextSize(13);
            stepLabel.setText("Starting…");
            root.addView(stepLabel);

            final androidx.appcompat.app.AlertDialog dialog =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("Optimizing WhatsApp Database")
                            .setView(root)
                            .setCancelable(false)
                            .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            Handler mainHandler = new Handler(Looper.getMainLooper());

            java.util.concurrent.CompletableFuture.runAsync(() -> {

                // Step 1 – root check (5%)
                updateProgress(progressBar, stepLabel, 5, "Checking root access… (5%)", mainHandler);

                if (!RootUtils.hasRootAccess()) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                .setTitle("Root Permission Required")
                                .setMessage("Root access is needed to optimize WhatsApp databases. Please grant root permission.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                    return;
                }

                // Step 2 – detect installs (15%)
                updateProgress(progressBar, stepLabel, 15, "Detecting WhatsApp installations… (15%)", mainHandler);

                final boolean waInstalled = isPackageInstalled(context, "com.whatsapp");
                final boolean waBusinessInstalled = isPackageInstalled(context, "com.whatsapp.w4b");

                if (!waInstalled && !waBusinessInstalled) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Step 3 – force stop (25%)
                updateProgress(progressBar, stepLabel, 25, "Stopping WhatsApp… (25%)", mainHandler);

                if (waInstalled)         RootUtils.runRootCommand("am force-stop com.whatsapp");
                if (waBusinessInstalled) RootUtils.runRootCommand("am force-stop com.whatsapp.w4b");

                // Brief pause to ensure process is dead
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                // Step 4 – check existing indexes (40%)
                updateProgress(progressBar, stepLabel, 40, "Checking database indexes… (40%)", mainHandler);

                final boolean waIndexed         = !waInstalled         || isDatabaseIndexed("com.whatsapp");
                final boolean waBusinessIndexed = !waBusinessInstalled || isDatabaseIndexed("com.whatsapp.w4b");

                if (waIndexed && waBusinessIndexed) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        if (filterPref instanceof androidx.preference.TwoStatePreference)
                            ((androidx.preference.TwoStatePreference) filterPref).setChecked(true);
                        Toast.makeText(context, "Group Message Filter enabled (Database already optimized).", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Step 5 – create indexes for WA and WA Business (Mocked progression up to 92%)
                final boolean[] dbSuccess = { true };
                final boolean[] dbFinished = { false };

                Thread dbThread = new Thread(() -> {
                    boolean res = true;
                    if (waInstalled && !waIndexed) {
                        res = createDatabaseIndexes("com.whatsapp");
                    }
                    if (waBusinessInstalled && !waBusinessIndexed && res) {
                        res = createDatabaseIndexes("com.whatsapp.w4b");
                    }
                    dbSuccess[0] = res;
                    dbFinished[0] = true;
                });
                dbThread.start();

                // Increment progress by 1% roughly every 288ms (52 steps from 40% to 92% takes 15 seconds)
                int progress = 40;
                while (!dbFinished[0]) {
                    if (progress < 92) {
                        progress++;
                        updateProgress(progressBar, stepLabel, progress, "Optimizing database… (" + progress + "%)", mainHandler);
                        try {
                            Thread.sleep(288);
                        } catch (InterruptedException e) {
                            break;
                        }
                    } else {
                        // Stuck at 92% waiting for database task to complete
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }

                // Step 6 – done (100%)
                final boolean finalSuccess = dbSuccess[0];
                final int endProgress = finalSuccess ? 100 : progress;
                updateProgress(progressBar, stepLabel, endProgress, finalSuccess ? "Done! (100%)" : "Failed.", mainHandler);

                // Small delay so user sees 100%
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}

                mainHandler.post(() -> {
                    dialog.dismiss();
                    if (finalSuccess) {
                        if (filterPref instanceof androidx.preference.TwoStatePreference)
                            ((androidx.preference.TwoStatePreference) filterPref).setChecked(true);
                        Toast.makeText(context, "Database optimized successfully!", Toast.LENGTH_LONG).show();
                    } else {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                .setTitle("Optimization Failed")
                                .setMessage("Could not create database indexes. Please ensure root access is granted and try again.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            });
        }
        */

        private void updateProgress(ProgressBar progressBar, TextView label, int progress, String text, Handler mainHandler) {
            mainHandler.post(() -> {
                ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progress);
                animator.setDuration(250);
                animator.start();
                label.setText(text);
            });
        }

        private boolean isPackageInstalled(Context context, String pkgName) {
            try {
                context.getPackageManager().getPackageInfo(pkgName, 0);
                return true;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        private boolean isDatabaseIndexed(String pkgName) {
            String srcDb = "/data/data/" + pkgName + "/databases/msgstore.db";
            String cacheDir = requireContext().getCacheDir().getAbsolutePath();
            String tmpDb = cacheDir + "/wae_check_" + pkgName + ".db";
            try {
                // nsenter -t 1 -m enters init's global mount namespace where all app paths exist
                RootUtils.runRootCommand("nsenter -t 1 -m -- cp " + srcDb + " " + tmpDb + " && chmod 666 " + tmpDb);
                RootUtils.runRootCommand("nsenter -t 1 -m -- sh -c '[ -f " + srcDb + "-wal ] && cp " + srcDb + "-wal " + tmpDb + "-wal && chmod 666 " + tmpDb + "-wal || true'");
                RootUtils.runRootCommand("nsenter -t 1 -m -- sh -c '[ -f " + srcDb + "-shm ] && cp " + srcDb + "-shm " + tmpDb + "-shm && chmod 666 " + tmpDb + "-shm || true'");

                if (!new File(tmpDb).exists()) {
                    Log.w("WaEnhancer", "isDatabaseIndexed: file not visible at " + tmpDb);
                    return false;
                }
                // Open READWRITE so SQLite can replay WAL
                SQLiteDatabase db = SQLiteDatabase.openDatabase(tmpDb, null,
                        SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                try (Cursor c = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='index' AND name='wae_msg_filter_idx'", null)) {
                    boolean indexed = c != null && c.moveToFirst();
                    Log.d("WaEnhancer", "isDatabaseIndexed: " + pkgName + " = " + indexed);
                    return indexed;
                } finally {
                    db.close();
                }
            } catch (Exception e) {
                Log.e("WaEnhancer", "isDatabaseIndexed failed for " + pkgName, e);
                return false;
            } finally {
                new File(tmpDb).delete();
                new File(tmpDb + "-wal").delete();
                new File(tmpDb + "-shm").delete();
            }
        }

        private boolean createDatabaseIndexes(String pkgName) {
            String srcDb = "/data/data/" + pkgName + "/databases/msgstore.db";
            String cacheDir = requireContext().getCacheDir().getAbsolutePath();
            String tmpDb = cacheDir + "/wae_idx_" + pkgName + ".db";
            try {
                // 1. Copy DB + WAL + SHM into our own cache dir (full rw access, SQLite can create WAL here)
                RootUtils.runRootCommand("nsenter -t 1 -m -- cp " + srcDb + " " + tmpDb + " && chmod 666 " + tmpDb);
                RootUtils.runRootCommand("nsenter -t 1 -m -- sh -c '[ -f " + srcDb + "-wal ] && cp " + srcDb + "-wal " + tmpDb + "-wal && chmod 666 " + tmpDb + "-wal || true'");
                RootUtils.runRootCommand("nsenter -t 1 -m -- sh -c '[ -f " + srcDb + "-shm ] && cp " + srcDb + "-shm " + tmpDb + "-shm && chmod 666 " + tmpDb + "-shm || true'");

                if (!new File(tmpDb).exists()) {
                    Log.e("WaEnhancer", "createDatabaseIndexes: file not visible at " + tmpDb);
                    return false;
                }

                // 2. Open and create indexes directly in our cache copy
                SQLiteDatabase db = SQLiteDatabase.openDatabase(tmpDb, null,
                        SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                try {
                    try (Cursor c = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                        if (c != null) c.moveToFirst();
                    }
                    db.execSQL("CREATE INDEX IF NOT EXISTS wae_msg_filter_idx ON message (chat_row_id, sender_jid_row_id, from_me, message_type)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS wae_msg_from_me_idx ON message (chat_row_id, from_me, message_type)");
                    // Switch to DELETE journal so we don't need to copy WAL back
                    try (Cursor c = db.rawQuery("PRAGMA journal_mode=DELETE", null)) {
                        if (c != null) c.moveToFirst();
                    }
                } finally {
                    db.close();
                }

                // 3. Copy modified DB back; restore original owner + permissions
                String uid = RootUtils.runRootCommand("nsenter -t 1 -m -- stat -c %u " + srcDb);
                String gid = RootUtils.runRootCommand("nsenter -t 1 -m -- stat -c %g " + srcDb);
                RootUtils.runRootCommand("nsenter -t 1 -m -- cp " + tmpDb + " " + srcDb);
                if (uid != null && gid != null && !uid.trim().isEmpty() && !gid.trim().isEmpty())
                    RootUtils.runRootCommand("nsenter -t 1 -m -- chown " + uid.trim() + ":" + gid.trim() + " " + srcDb);
                RootUtils.runRootCommand("nsenter -t 1 -m -- chmod 600 " + srcDb);
                RootUtils.runRootCommand("nsenter -t 1 -m -- rm -f " + srcDb + "-wal " + srcDb + "-shm");

                Log.d("WaEnhancer", "createDatabaseIndexes: success for " + pkgName);
                return true;
            } catch (Exception e) {
                Log.e("WaEnhancer", "createDatabaseIndexes failed for " + pkgName, e);
                return false;
            } finally {
                new File(tmpDb).delete();
                new File(tmpDb + "-wal").delete();
                new File(tmpDb + "-shm").delete();
            }
        }



        private int getThemeColor(Context context, int attr, int defaultColor) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
            return defaultColor;
        }

        private int dpToPx(Context context, int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round((float) dp * density);
        }

        @Override
        public void onResume() {
            super.onResume();
            setDisplayHomeAsUpEnabled(false);
        }
    }
}
