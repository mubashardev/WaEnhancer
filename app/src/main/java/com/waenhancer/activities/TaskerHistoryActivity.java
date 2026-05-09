package com.waenhancer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.adapter.TaskerHistoryAdapter;
import com.waenhancer.model.TaskerEvent;
import com.waenhancer.utils.TaskerHistoryManager;

import java.util.List;

public class TaskerHistoryActivity extends BaseActivity {

    private TaskerHistoryAdapter adapter;
    private LinearLayout emptyState;
    private TaskerHistoryManager historyManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasker_history);

        historyManager = TaskerHistoryManager.getInstance(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_history) {
                confirmClearHistory();
                return true;
            }
            return false;
        });

        // RecyclerView
        RecyclerView rv = findViewById(R.id.rv_history);
        emptyState = findViewById(R.id.empty_state);

        adapter = new TaskerHistoryAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        List<TaskerEvent> events = historyManager.getRecentEvents();
        adapter.submitList(events);
        emptyState.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Delete all automation history? This cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    historyManager.clearHistory();
                    loadHistory();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
