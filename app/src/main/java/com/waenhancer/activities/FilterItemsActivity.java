package com.waenhancer.activities;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.adapter.FilterItemsAdapter;
import com.waenhancer.model.FilterItem;
import com.waenhancer.views.dialog.SimpleColorPickerDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FilterItemsActivity extends BaseActivity implements FilterItemsAdapter.OnFilterActionListener {

    private final List<FilterItem> filtersList = new ArrayList<>();
    private FilterItemsAdapter adapter;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_items);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.filter_items_by_id);
        }

        emptyStateText = findViewById(R.id.empty_state_text);
        loadFilters();

        RecyclerView recyclerView = findViewById(R.id.filter_items_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FilterItemsAdapter(filtersList, this);
        recyclerView.setAdapter(adapter);

        updateEmptyState();

        FloatingActionButton fab = findViewById(R.id.fab_add_filter);
        fab.setOnClickListener(v -> showFilterEditDialog(null, false, -1));
    }

    private void loadFilters() {
        filtersList.clear();
        String rawFilters = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("filter_items", "");
        if (rawFilters != null && !rawFilters.trim().isEmpty()) {
            if (rawFilters.trim().startsWith("[")) {
                try {
                    JSONArray arr = new JSONArray(rawFilters);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String idStr = obj.optString("id", "").trim();
                        if (!idStr.isEmpty()) {
                            String behavior = obj.optString("behavior", FilterItem.BEHAVIOR_GONE);
                            int color = obj.optInt("color", 0xFFFF0000);
                            int opacity = obj.optInt("opacity", 100);
                            double scale = obj.optDouble("scale", 1.0);
                            filtersList.add(new FilterItem(idStr, behavior, color, opacity, (float) scale));
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("WAEX", "Failed to parse JSON filter_items", e);
                }
            } else {
                // Fallback to old newline-separated format
                String[] items = rawFilters.split("\n");
                for (String item : items) {
                    String cleaned = item.trim();
                    if (!cleaned.isEmpty()) {
                        filtersList.add(new FilterItem(cleaned, FilterItem.BEHAVIOR_GONE, 0xFFFF0000, 100, 1.0f));
                    }
                }
            }
        }
    }

    private void saveFilters() {
        JSONArray arr = new JSONArray();
        for (FilterItem item : filtersList) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("behavior", item.behavior);
                obj.put("color", item.color);
                obj.put("opacity", item.opacity);
                obj.put("scale", item.scale);
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        String filterString = arr.toString();
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString("filter_items", filterString)
                .putBoolean("need_restart", true)
                .apply();

        // Notify content provider and packages
        try {
            String authority = BuildConfig.APPLICATION_ID + ".hookprovider";
            getContentResolver().notifyChange(
                    Uri.parse("content://" + authority + "/preferences"),
                    null
            );

            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
            intent.setPackage("com.whatsapp");
            sendBroadcast(intent);

            Intent intent2 = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
            intent2.setPackage("com.whatsapp.w4b");
            sendBroadcast(intent2);

            // Send MANUAL_RESTART intent to prompt WhatsApp restart dialog
            ArrayList<String> titles = new ArrayList<>();
            titles.add(getString(R.string.filter_items_by_id));

            for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
                Intent restartIntent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
                restartIntent.setPackage(pkg);
                restartIntent.putStringArrayListExtra("changed_titles", titles);
                sendBroadcast(restartIntent);
            }
        } catch (Exception ignored) {}
    }

    private void updateEmptyState() {
        if (filtersList.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            emptyStateText.setVisibility(View.GONE);
        }
    }

    private void showFilterEditDialog(FilterItem item, boolean isEdit, int position) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter_edit, null);
        dialog.setContentView(dialogView);

        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText idInput = dialogView.findViewById(R.id.dialog_filter_id);
        TextInputLayout idInputLayout = dialogView.findViewById(R.id.dialog_filter_id_layout);
        AutoCompleteTextView behaviorDropdown = dialogView.findViewById(R.id.dialog_filter_behavior);
        
        View layoutColor = dialogView.findViewById(R.id.layout_change_color);
        View layoutOpacity = dialogView.findViewById(R.id.layout_opacity);
        TextInputEditText opacityInput = dialogView.findViewById(R.id.dialog_opacity_input);
        View layoutResize = dialogView.findViewById(R.id.layout_resize);
        TextView resizeScaleText = dialogView.findViewById(R.id.resize_scale_text);
        com.google.android.material.slider.Slider resizeSlider = dialogView.findViewById(R.id.dialog_resize_slider);

        // Prepopulate values
        if (isEdit && item != null) {
            titleView.setText("Edit Filter");
            idInput.setText(item.id);
            int behaviorIndex = getIndexFromBehavior(item.behavior);
            behaviorDropdown.setText(getBehaviorNameFromIndex(behaviorIndex), false);
            toggleBehaviorLayouts(behaviorIndex, layoutColor, layoutOpacity, layoutResize);
        } else {
            titleView.setText("Add Filter");
            behaviorDropdown.setText(getBehaviorNameFromIndex(0), false);
            toggleBehaviorLayouts(0, layoutColor, layoutOpacity, layoutResize);
        }

        // Setup behavior dropdown
        String[] behaviors = {"Gone (Remove)", "Change Color (Pro)", "Opacity (Pro)", "Resize (Pro)"};
        ArrayAdapter<String> behaviorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, behaviors);
        behaviorDropdown.setAdapter(behaviorAdapter);
        behaviorDropdown.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos > 0 && !com.waenhancer.xposed.utils.ProHelper.isFilterItemsProEnabled()) {
                Toast.makeText(FilterItemsActivity.this, "This behavior is for Pro users only. Please activate your license.", Toast.LENGTH_LONG).show();
                try {
                    Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                    Intent intent = new Intent(FilterItemsActivity.this, clazz);
                    startActivity(intent);
                } catch (ClassNotFoundException e) {
                    Toast.makeText(FilterItemsActivity.this, "License module not found.", Toast.LENGTH_SHORT).show();
                }
                behaviorDropdown.setText(getBehaviorNameFromIndex(0), false);
                toggleBehaviorLayouts(0, layoutColor, layoutOpacity, layoutResize);
            } else {
                toggleBehaviorLayouts(pos, layoutColor, layoutOpacity, layoutResize);
            }
        });

        // Setup Color Picker
        final int[] selectedColor = { (isEdit && item != null) ? item.color : 0xFFFF0000 };
        View colorPreview = dialogView.findViewById(R.id.color_preview_view);
        GradientDrawable previewDrawable = new GradientDrawable();
        previewDrawable.setShape(GradientDrawable.OVAL);
        previewDrawable.setColor(selectedColor[0]);
        colorPreview.setBackground(previewDrawable);

        dialogView.findViewById(R.id.btn_choose_color).setOnClickListener(v -> {
            new SimpleColorPickerDialog(this, selectedColor[0], color -> {
                selectedColor[0] = color;
                previewDrawable.setColor(color);
            }).show();
        });

        // Setup Opacity
        if (isEdit && item != null && FilterItem.BEHAVIOR_OPACITY.equals(item.behavior)) {
            opacityInput.setText(String.valueOf(item.opacity));
        } else {
            opacityInput.setText("100");
        }

        // Setup Resize Slider
        float initialScale = (isEdit && item != null) ? item.scale : 1.0f;
        if (initialScale < 0.1f) initialScale = 0.1f;
        if (initialScale > 3.0f) initialScale = 3.0f;
        resizeSlider.setValue(initialScale);
        resizeScaleText.setText("Scale: " + String.format("%.1fx", initialScale));
        resizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            resizeScaleText.setText("Scale: " + String.format("%.1fx", value));
        });

        // Buttons
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String idStr = idInput.getText() != null ? idInput.getText().toString().trim() : "";
            if (idStr.isEmpty()) {
                idInputLayout.setError("Filter ID cannot be empty");
                return;
            }

            int behaviorPos = getIndexFromBehaviorName(behaviorDropdown.getText().toString());
            if (behaviorPos > 0 && !com.waenhancer.xposed.utils.ProHelper.isFilterItemsProEnabled()) {
                Toast.makeText(FilterItemsActivity.this, "This behavior is for Pro users only. Please activate your license.", Toast.LENGTH_LONG).show();
                try {
                    Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                    Intent intent = new Intent(FilterItemsActivity.this, clazz);
                    startActivity(intent);
                } catch (ClassNotFoundException e) {
                    Toast.makeText(FilterItemsActivity.this, "License module not found.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            String behavior = getBehaviorFromIndex(behaviorPos);

            int opacity = 100;
            if (behavior.equals(FilterItem.BEHAVIOR_OPACITY)) {
                String opStr = opacityInput.getText() != null ? opacityInput.getText().toString().trim() : "";
                try {
                    opacity = Integer.parseInt(opStr);
                    if (opacity < 0 || opacity > 100) {
                        opacityInput.setError("Opacity must be between 0 and 100");
                        return;
                    }
                } catch (NumberFormatException e) {
                    opacityInput.setError("Invalid opacity percent");
                    return;
                }
            }

            float scale = resizeSlider.getValue();

            FilterItem resultItem = new FilterItem(idStr, behavior, selectedColor[0], opacity, scale);
            if (isEdit && position >= 0 && position < filtersList.size()) {
                filtersList.set(position, resultItem);
                adapter.notifyItemChanged(position);
            } else {
                filtersList.add(resultItem);
                adapter.notifyItemInserted(filtersList.size() - 1);
            }

            updateEmptyState();
            saveFilters();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void toggleBehaviorLayouts(int position, View layoutColor, View layoutOpacity, View layoutResize) {
        layoutColor.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        layoutOpacity.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        layoutResize.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
    }

    private String getBehaviorFromIndex(int index) {
        return switch (index) {
            case 1 -> FilterItem.BEHAVIOR_COLOR;
            case 2 -> FilterItem.BEHAVIOR_OPACITY;
            case 3 -> FilterItem.BEHAVIOR_RESIZE;
            default -> FilterItem.BEHAVIOR_GONE;
        };
    }

    private int getIndexFromBehavior(String behavior) {
        if (behavior == null) return 0;
        return switch (behavior) {
            case FilterItem.BEHAVIOR_COLOR -> 1;
            case FilterItem.BEHAVIOR_OPACITY -> 2;
            case FilterItem.BEHAVIOR_RESIZE -> 3;
            default -> 0;
        };
    }

    private String getBehaviorNameFromIndex(int index) {
        return switch (index) {
            case 1 -> "Change Color (Pro)";
            case 2 -> "Opacity (Pro)";
            case 3 -> "Resize (Pro)";
            default -> "Gone (Remove)";
        };
    }

    private int getIndexFromBehaviorName(String behaviorName) {
        if (behaviorName == null) return 0;
        return switch (behaviorName) {
            case "Change Color", "Change Color (Pro)" -> 1;
            case "Opacity", "Opacity (Pro)" -> 2;
            case "Resize", "Resize (Pro)" -> 3;
            default -> 0;
        };
    }

    @Override
    public void onDelete(int position) {
        if (position >= 0 && position < filtersList.size()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_filter)
                    .setMessage(R.string.delete_filter_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        filtersList.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, filtersList.size() - position);
                        updateEmptyState();
                        saveFilters();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    @Override
    public void onEdit(int position) {
        if (position >= 0 && position < filtersList.size()) {
            showFilterEditDialog(filtersList.get(position), true, position);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
