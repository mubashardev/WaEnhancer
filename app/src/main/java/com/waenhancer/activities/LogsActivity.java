package com.waenhancer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;
import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.databinding.ActivityLogsBinding;
import com.waenhancer.databinding.FragmentLogViewerBinding;
import com.waenhancer.utils.LogManager;
import com.waenhancer.xposed.core.FeatureLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogsActivity extends BaseActivity {

    private ActivityLogsBinding binding;
    private volatile boolean rootGranted = false;
    private volatile boolean requestingRoot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        LogsPagerAdapter adapter = new LogsPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? R.string.whatsapp_logs : R.string.whatsapp_business_logs);
        }).attach();

        binding.switchLogging.setChecked(LogManager.isLoggingEnabled(this));
        binding.switchLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            if (!rootGranted) {
                buttonView.setChecked(false);
                Toast.makeText(this, R.string.root_required_for_logs, Toast.LENGTH_SHORT).show();
                return;
            }

            LogManager.setLoggingEnabled(this, isChecked);
            if (isChecked) {
                LogManager.clearLogs(FeatureLoader.PACKAGE_WPP);
                LogManager.clearLogs(FeatureLoader.PACKAGE_BUSINESS);
                new Thread(LogManager::clearRootLogcatBuffer).start();
                LogManager.addLog(FeatureLoader.PACKAGE_WPP, "[ui][I] === Logging session started ===");
                LogManager.addLog(FeatureLoader.PACKAGE_BUSINESS, "[ui][I] === Logging session started ===");
            } else {
                LogManager.addLog(FeatureLoader.PACKAGE_WPP, "[ui][I] === Logging session stopped ===");
                LogManager.addLog(FeatureLoader.PACKAGE_BUSINESS, "[ui][I] === Logging session stopped ===");
            }
            Toast.makeText(this, isChecked ? R.string.logging_started : R.string.logging_stopped, Toast.LENGTH_SHORT)
                    .show();
        });

        binding.btnGrantRoot.setOnClickListener(v -> requestRootPermission());
        updateRootUi();
    }

    public boolean hasRootAccess() {
        return rootGranted;
    }

    private void requestRootPermission() {
        if (requestingRoot) {
            return;
        }
        requestingRoot = true;
        binding.btnGrantRoot.setEnabled(false);
        binding.btnGrantRoot.setText(R.string.checking_root_access_button);
        binding.tvRootMessage.setText(R.string.root_request_in_progress);

        new Thread(() -> {
            boolean granted = LogManager.hasRootAccess();
            runOnUiThread(() -> {
                requestingRoot = false;
                rootGranted = granted;
                binding.btnGrantRoot.setEnabled(true);
                binding.btnGrantRoot.setText(R.string.grant_root_access);
                if (!granted) {
                    binding.tvRootMessage.setText(R.string.root_required_message_denied);
                    Toast.makeText(this, R.string.root_access_denied, Toast.LENGTH_SHORT).show();
                } else {
                    binding.tvRootMessage.setText(R.string.root_access_granted_message);
                }
                updateRootUi();
            });
        }).start();
    }

    private void updateRootUi() {
        binding.logsContentContainer.setVisibility(rootGranted ? View.VISIBLE : View.GONE);
        binding.rootRequiredContainer.setVisibility(rootGranted ? View.GONE : View.VISIBLE);
        binding.switchLogging.setEnabled(rootGranted);
        if (!rootGranted) {
            binding.switchLogging.setChecked(false);
        }
    }

    private static class LogsPagerAdapter extends FragmentStateAdapter {
        public LogsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return LogViewerFragment.newInstance(
                    position == 0 ? FeatureLoader.PACKAGE_WPP : FeatureLoader.PACKAGE_BUSINESS);
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class LogViewerFragment extends Fragment {
        private static final String ARG_PACKAGE = "package_name";

        private enum LogLevel {
            ALL, VERBOSE, DEBUG, INFO, WARN, ERROR
        }

        private FragmentLogViewerBinding binding;
        private String packageName;
        private String rawLogs = "";
        private String filteredLogs = "";
        private LogLevel selectedLevel = LogLevel.ALL;
        private volatile boolean loadingLogs = false;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadLogs();
                handler.postDelayed(this, 2000);
            }
        };

        public static LogViewerFragment newInstance(String packageName) {
            LogViewerFragment fragment = new LogViewerFragment();
            Bundle args = new Bundle();
            args.putString(ARG_PACKAGE, packageName);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                packageName = getArguments().getString(ARG_PACKAGE);
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            binding = FragmentLogViewerBinding.inflate(inflater, container, false);
            return binding.getRoot();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            var filterAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    new String[] {
                            getString(R.string.log_filter_all),
                            getString(R.string.log_filter_verbose),
                            getString(R.string.log_filter_debug),
                            getString(R.string.log_filter_info),
                            getString(R.string.log_filter_warn),
                            getString(R.string.log_filter_error)
                    });
            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerFilter.setAdapter(filterAdapter);
            binding.spinnerFilter.setSelection(0, false);
            binding.spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedLevel = mapPositionToLevel(position);
                    applyFilteredLogs();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            binding.btnShare.setOnClickListener(v -> shareFilteredLogs());

            binding.btnClear.setOnClickListener(v -> {
                LogManager.clearLogs(packageName);
                rawLogs = "";
                filteredLogs = "";
                loadLogs();
            });

            loadLogs();
        }

        @Override
        public void onResume() {
            super.onResume();
            handler.post(refreshRunnable);
        }

        @Override
        public void onPause() {
            super.onPause();
            handler.removeCallbacks(refreshRunnable);
        }

        private void loadLogs() {
            if (binding == null || loadingLogs) {
                return;
            }
            FragmentActivity activity = getActivity();
            if (activity == null) {
                return;
            }
            loadingLogs = true;
            final boolean shouldCaptureRoot = activity instanceof LogsActivity
                    && ((LogsActivity) activity).hasRootAccess()
                    && LogManager.isLoggingEnabled(activity);

            new Thread(() -> {
                try {
                    if (shouldCaptureRoot) {
                        LogManager.captureRootLogs(packageName);
                    }
                    String logs = LogManager.getLogs(packageName);
                    activity.runOnUiThread(() -> {
                        if (binding == null) {
                            loadingLogs = false;
                            return;
                        }
                        rawLogs = logs;
                        applyFilteredLogs();
                        loadingLogs = false;
                    });
                } catch (Exception ignored) {
                    activity.runOnUiThread(() -> loadingLogs = false);
                }
            }).start();
        }

        private void applyFilteredLogs() {
            if (binding == null) return;
            filteredLogs = filterLogs(rawLogs, selectedLevel);
            if (filteredLogs.isEmpty()) {
                binding.tvLogs.setVisibility(View.GONE);
                binding.tvEmpty.setVisibility(View.VISIBLE);
                binding.tvEmpty.setText(rawLogs.isEmpty()
                        ? R.string.no_logs_found
                        : R.string.no_logs_for_filter);
            } else {
                binding.tvLogs.setVisibility(View.VISIBLE);
                binding.tvEmpty.setVisibility(View.GONE);
                binding.tvLogs.setText(filteredLogs);
                binding.scrollView.post(() -> binding.scrollView.fullScroll(View.FOCUS_DOWN));
            }
        }

        private LogLevel mapPositionToLevel(int position) {
            switch (position) {
                case 1:
                    return LogLevel.VERBOSE;
                case 2:
                    return LogLevel.DEBUG;
                case 3:
                    return LogLevel.INFO;
                case 4:
                    return LogLevel.WARN;
                case 5:
                    return LogLevel.ERROR;
                default:
                    return LogLevel.ALL;
            }
        }

        private String filterLogs(String logs, LogLevel level) {
            if (logs == null || logs.isEmpty()) {
                return "";
            }
            if (level == LogLevel.ALL) {
                return logs;
            }

            StringBuilder result = new StringBuilder();
            String[] lines = logs.split("\n");
            for (String line : lines) {
                if (line == null || line.isEmpty()) {
                    continue;
                }
                LogLevel lineLevel = detectLevel(line);
                if (lineLevel == level) {
                    result.append(line).append("\n");
                }
            }
            return result.toString().trim();
        }

        private LogLevel detectLevel(String line) {
            if (line.contains("[logcat][V]")) return LogLevel.VERBOSE;
            if (line.contains("[logcat][D]")) return LogLevel.DEBUG;
            if (line.contains("[logcat][I]") || line.contains("[ui][I]")) return LogLevel.INFO;
            if (line.contains("[logcat][W]")) return LogLevel.WARN;
            if (line.contains("[logcat][E]") || line.contains("[logcat][A]")) return LogLevel.ERROR;
            return LogLevel.INFO;
        }

        private void shareFilteredLogs() {
            if (filteredLogs == null || filteredLogs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_logs_found, Toast.LENGTH_SHORT).show();
                return;
            }

            String filterName = selectedLevel.name().toLowerCase(Locale.US);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File shareDir = new File(requireContext().getCacheDir(), "shared_logs");
            if (!shareDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                shareDir.mkdirs();
            }

            File shareFile = new File(shareDir, "waenhancer_" + timestamp + "_" + filterName + ".txt");
            try (FileWriter writer = new FileWriter(shareFile, false)) {
                writer.write(filteredLogs);
            } catch (IOException e) {
                Toast.makeText(requireContext(), R.string.logs_share_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            var uri = FileProvider.getUriForFile(
                    requireContext(),
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    shareFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "WaEnhancer Logs (" + filterName + ")");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_logs)));
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            binding = null;
        }
    }
}
