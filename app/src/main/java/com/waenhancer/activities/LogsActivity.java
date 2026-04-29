package com.waenhancer.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.google.android.material.tabs.TabLayoutMediator;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.databinding.ActivityLogsBinding;
import com.waenhancer.databinding.FragmentLogViewerBinding;
import com.waenhancer.utils.LogManager;
import com.waenhancer.xposed.core.FeatureLoader;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;

public class LogsActivity extends BaseActivity {

    private ActivityLogsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        LogsPagerAdapter adapter = new LogsPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? R.string.whatsapp_logs : R.string.whatsapp_business_logs);
        }).attach();

        binding.switchLogging.setChecked(LogManager.isLoggingEnabled(this));
        binding.switchLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LogManager.setLoggingEnabled(this, isChecked);
            Toast.makeText(this, isChecked ? R.string.logging_started : R.string.logging_stopped, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private static class LogsPagerAdapter extends FragmentStateAdapter {
        public LogsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return LogViewerFragment.newInstance(position == 0 ? FeatureLoader.PACKAGE_WPP : FeatureLoader.PACKAGE_BUSINESS);
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class LogViewerFragment extends Fragment {
        private static final String ARG_PACKAGE = "package_name";
        private FragmentLogViewerBinding binding;
        private String packageName;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadLogs();
                handler.postDelayed(this, 2000); // Refresh every 2 seconds
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
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            binding = FragmentLogViewerBinding.inflate(inflater, container, false);
            return binding.getRoot();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            binding.btnCopy.setOnClickListener(v -> {
                String logs = binding.tvLogs.getText().toString();
                if (!logs.isEmpty()) {
                    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Logs", logs);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            });

            binding.btnClear.setOnClickListener(v -> {
                LogManager.clearLogs(packageName);
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
            if (binding == null) return;
            String logs = LogManager.getLogs(packageName);
            if (logs.isEmpty()) {
                binding.tvLogs.setVisibility(View.GONE);
                binding.tvEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.tvLogs.setVisibility(View.VISIBLE);
                binding.tvEmpty.setVisibility(View.GONE);
                binding.tvLogs.setText(logs);
                
                // Auto-scroll to bottom if at the bottom or near it
                binding.scrollView.post(() -> binding.scrollView.fullScroll(View.FOCUS_DOWN));
            }
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            binding = null;
        }
    }
}
