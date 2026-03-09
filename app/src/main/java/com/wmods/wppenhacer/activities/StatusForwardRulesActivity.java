package com.wmods.wppenhacer.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.StatusForwardRulesAdapter;
import com.wmods.wppenhacer.model.StatusForwardRule;
import com.wmods.wppenhacer.activities.base.BaseActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StatusForwardRulesActivity extends BaseActivity
        implements StatusForwardRulesAdapter.RuleChangeListener {

    public static final String PREF_KEY = "auto_status_forward_rules_json";

    private StatusForwardRulesAdapter adapter;
    private final List<StatusForwardRule> rules = new ArrayList<>();
    private ActionMode actionMode;

    // ---- ActionMode for multi-select delete ----
    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_rules_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_delete_selected) {
                confirmDeleteSelected();
                return true;
            } else if (id == R.id.action_select_all) {
                adapter.selectAll();
                updateActionModeTitle();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            adapter.exitSelectionMode();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_forward_rules);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.auto_status_forward_rules);
        }

        loadRules();

        RecyclerView recyclerView = findViewById(R.id.rules_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StatusForwardRulesAdapter(rules, this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_rule);
        fab.setOnClickListener(v -> addNewRule());
    }

    private void addNewRule() {
        // Exit selection mode if active
        if (actionMode != null)
            actionMode.finish();
        rules.add(new StatusForwardRule(StatusForwardRule.TYPE_CONTAINS, ""));
        adapter.notifyItemInserted(rules.size() - 1);
        saveRules();
    }

    // ---- RuleChangeListener impl ----

    @Override
    public void onRuleDeleted(int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_rule)
                .setMessage(R.string.delete_rule_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    rules.remove(position);
                    adapter.notifyItemRemoved(position);
                    saveRules();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onRuleChanged() {
        saveRules();
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        updateActionModeTitle();
    }

    @Override
    public void onEnterSelectionMode() {
        actionMode = startSupportActionMode(actionModeCallback);
        updateActionModeTitle();
    }

    @Override
    public void onExitSelectionMode() {
        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
    }

    private void updateActionModeTitle() {
        if (actionMode != null) {
            int count = adapter.getSelectedCount();
            actionMode.setTitle(count + " " + getString(R.string.rules_selected));
        }
    }

    private void confirmDeleteSelected() {
        int count = adapter.getSelectedCount();
        if (count == 0)
            return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_rule)
                .setMessage(getString(R.string.delete_rules_confirm, count))
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteSelected())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void deleteSelected() {
        List<Integer> positions = adapter.getSelectedPositions(); // descending
        for (int pos : positions) {
            if (pos >= 0 && pos < rules.size()) {
                rules.remove(pos);
            }
        }
        if (actionMode != null)
            actionMode.finish();
        adapter.notifyDataSetChanged();
        saveRules();
        Toast.makeText(this, R.string.rules_deleted, Toast.LENGTH_SHORT).show();
    }

    // ---- Persistence ----

    private void loadRules() {
        rules.clear();
        String json = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PREF_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                rules.add(new StatusForwardRule(
                        obj.optString("type", StatusForwardRule.TYPE_CONTAINS),
                        obj.optString("text", ""),
                        obj.optBoolean("applyText", true),
                        obj.optBoolean("applyMedia", true),
                        obj.optBoolean("applyVoice", false)));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveRules() {
        JSONArray arr = new JSONArray();
        for (StatusForwardRule rule : rules) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", rule.type);
                obj.put("text", rule.text.trim());
                obj.put("applyText", rule.applyText);
                obj.put("applyMedia", rule.applyMedia);
                obj.put("applyVoice", rule.applyVoice);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putString(PREF_KEY, arr.toString()).apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveRules();
    }

    @Override
    public void onBackPressed() {
        if (actionMode != null) {
            actionMode.finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (actionMode != null) {
                actionMode.finish();
            } else {
                saveRules();
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
