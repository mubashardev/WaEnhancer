package com.wmods.wppenhacer.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.model.StatusForwardRule;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatusForwardRulesAdapter
        extends RecyclerView.Adapter<StatusForwardRulesAdapter.RuleViewHolder> {

    public interface RuleChangeListener {
        void onRuleDeleted(int position);

        void onRuleChanged();

        void onSelectionChanged(int selectedCount);

        void onEnterSelectionMode();

        void onExitSelectionMode();
    }

    private final List<StatusForwardRule> rules;
    private final RuleChangeListener listener;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private boolean isSelectionMode = false;

    public StatusForwardRulesAdapter(List<StatusForwardRule> rules, RuleChangeListener listener) {
        this.rules = rules;
        this.listener = listener;
    }

    // ---- Selection helpers ----

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void enterSelectionMode(int firstSelected) {
        isSelectionMode = true;
        selectedPositions.clear();
        selectedPositions.add(firstSelected);
        notifyDataSetChanged();
        listener.onEnterSelectionMode();
        listener.onSelectionChanged(1);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void exitSelectionMode() {
        isSelectionMode = false;
        selectedPositions.clear();
        notifyDataSetChanged();
        listener.onExitSelectionMode();
    }

    public List<Integer> getSelectedPositions() {
        List<Integer> sorted = new ArrayList<>(selectedPositions);
        sorted.sort((a, b) -> b - a); // descending so removal doesn't shift indices
        return sorted;
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < rules.size(); i++)
            selectedPositions.add(i);
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedPositions.size());
    }

    // ---- Adapter ----

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_status_forward_rule, parent, false);
        return new RuleViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        holder.bind(rules.get(position), position);
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    class RuleViewHolder extends RecyclerView.ViewHolder {
        final TextInputEditText editText;
        final AutoCompleteTextView dropdown;
        final ImageButton btnDelete;
        final CheckBox checkBox;
        final Chip chipApplyText;
        final Chip chipApplyMedia;
        final Chip chipApplyVoice;
        TextWatcher currentWatcher;

        RuleViewHolder(@NonNull View v) {
            super(v);
            editText = v.findViewById(R.id.rule_text);
            dropdown = v.findViewById(R.id.rule_type_dropdown);
            btnDelete = v.findViewById(R.id.btn_delete_rule);
            checkBox = v.findViewById(R.id.rule_checkbox);
            chipApplyText = v.findViewById(R.id.chip_apply_text);
            chipApplyMedia = v.findViewById(R.id.chip_apply_media);
            chipApplyVoice = v.findViewById(R.id.chip_apply_voice);
        }

        void bind(StatusForwardRule rule, int pos) {
            Context ctx = itemView.getContext();

            // ---- Dropdown: Contains / Equals ----
            ArrayAdapter<CharSequence> sa = ArrayAdapter.createFromResource(
                    ctx, R.array.rule_type_entries, android.R.layout.simple_dropdown_item_1line);
            dropdown.setAdapter(sa);
            dropdown.setText(StatusForwardRule.TYPE_EQUALS.equals(rule.type) ? sa.getItem(1) : sa.getItem(0), false);
            dropdown.setOnItemClickListener((parent, view, position, id) -> {
                rule.type = position == 1 ? StatusForwardRule.TYPE_EQUALS : StatusForwardRule.TYPE_CONTAINS;
                listener.onRuleChanged();
            });

            // ---- EditText ----
            if (currentWatcher != null)
                editText.removeTextChangedListener(currentWatcher);
            editText.setText(rule.text);
            currentWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {
                }

                @Override
                public void onTextChanged(CharSequence s, int st, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    rule.text = s.toString();
                    listener.onRuleChanged();
                }
            };
            editText.addTextChangedListener(currentWatcher);

            // ---- Toggles ----
            chipApplyText.setOnCheckedChangeListener(null);
            chipApplyText.setChecked(rule.applyText);
            chipApplyText.setOnCheckedChangeListener((btn, checked) -> {
                rule.applyText = checked;
                listener.onRuleChanged();
            });

            chipApplyMedia.setOnCheckedChangeListener(null);
            chipApplyMedia.setChecked(rule.applyMedia);
            chipApplyMedia.setOnCheckedChangeListener((btn, checked) -> {
                rule.applyMedia = checked;
                listener.onRuleChanged();
            });

            // Voice is practically disabled, but sync data just in case
            chipApplyVoice.setOnCheckedChangeListener(null);
            chipApplyVoice.setChecked(rule.applyVoice);
            chipApplyVoice.setOnCheckedChangeListener((btn, checked) -> {
                rule.applyVoice = checked;
                listener.onRuleChanged();
            });

            // ---- Selection mode visibility ----
            checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
            dropdown.setEnabled(!isSelectionMode);
            editText.setEnabled(!isSelectionMode);
            chipApplyText.setEnabled(!isSelectionMode);
            chipApplyMedia.setEnabled(!isSelectionMode);
            // chipApplyVoice stays disabled as it's 'coming soon' in layout XML

            if (isSelectionMode) {
                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(selectedPositions.contains(pos));
                checkBox.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked)
                        selectedPositions.add(getAdapterPosition());
                    else
                        selectedPositions.remove(getAdapterPosition());
                    listener.onSelectionChanged(selectedPositions.size());
                });
                itemView.setOnClickListener(v -> checkBox.toggle());
                itemView.setOnLongClickListener(null);
            } else {
                checkBox.setOnCheckedChangeListener(null);
                itemView.setOnClickListener(null);
                // Long press → enter multi-select
                itemView.setOnLongClickListener(v -> {
                    enterSelectionMode(getAdapterPosition());
                    return true;
                });
                btnDelete.setOnClickListener(v -> {
                    int p = getAdapterPosition();
                    if (p != RecyclerView.NO_ID)
                        listener.onRuleDeleted(p);
                });
            }
        }
    }
}
