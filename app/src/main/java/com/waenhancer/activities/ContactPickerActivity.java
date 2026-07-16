package com.waenhancer.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;
import com.waenhancer.adapter.ContactPickerAdapter;
import com.waenhancer.model.SelectableContact;
import com.waenhancer.preference.ContactPickerPreference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ContactPickerActivity extends BaseActivity {

    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_SELECTED_CONTACTS = "contacts";
    private static final int REQUEST_READ_CONTACTS = 4011;

    private final List<SelectableContact> allContacts = new ArrayList<>();
    private final List<SelectableContact> filteredContacts = new ArrayList<>();
    private ContactPickerAdapter adapter;
    private TextInputEditText searchBar;
    private MaterialButton selectAllButton;
    private MaterialButton saveButton;
    private String preferenceKey;
    private ArrayList<String> preselectedContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_picker);

        preferenceKey = getIntent().getStringExtra(EXTRA_KEY);
        preselectedContacts = getIntent().getStringArrayListExtra(EXTRA_SELECTED_CONTACTS);
        if (preselectedContacts == null) {
            preselectedContacts = new ArrayList<>();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.select_contacts);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        searchBar = findViewById(R.id.searchBar);
        selectAllButton = findViewById(R.id.selectAllButton);
        saveButton = findViewById(R.id.saveButton);
        RecyclerView contactListView = findViewById(R.id.contactListView);
        contactListView.setLayoutManager(new LinearLayoutManager(this));

        int limit = getIntent().getIntExtra("limit", -1);
        if (limit > 0) {
            selectAllButton.setVisibility(android.view.View.GONE);
        }

        adapter = new ContactPickerAdapter((contact, toSelectedState) -> {
            if (toSelectedState && limit != -1) {
                int currentSelectedCount = 0;
                for (SelectableContact c : allContacts) {
                    if (c.isSelected()) currentSelectedCount++;
                }
                if (currentSelectedCount >= limit) {
                    Toast.makeText(ContactPickerActivity.this, "Maximum " + limit + " contacts allowed", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            return true;
        });
        contactListView.setAdapter(adapter);

        searchBar.addTextChangedListener(new SimpleTextWatcher(this::filterContacts));
        selectAllButton.setOnClickListener(v -> toggleSelectAll());
        saveButton.setOnClickListener(v -> finishWithResult());

        ensureContactsPermissionAndLoad();
    }

    private void ensureContactsPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CONTACTS},
                REQUEST_READ_CONTACTS);
    }

    private void loadContacts() {
        CompletableFuture
                .supplyAsync(this::queryContacts)
                .thenAccept(contacts -> runOnUiThread(() -> {
                    allContacts.clear();
                    allContacts.addAll(contacts);
                    filterContacts(searchBar.getText() != null ? searchBar.getText().toString() : "");
                }));
    }

    @NonNull
    private List<SelectableContact> queryContacts() {
        Map<String, SelectableContact> uniqueContacts = new LinkedHashMap<>();

        // 1. Read phone contacts if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            String[] projection = {
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            };

            try (Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    ContactsContract.CommonDataKinds.Phone.NUMBER + " IS NOT NULL",
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    while (cursor.moveToNext()) {
                        String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                        String number = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                        String normalized = normalizePhone(number);
                        if (normalized.isEmpty() || uniqueContacts.containsKey(normalized)) {
                            continue;
                        }
                        String displayName = (name == null || name.trim().isEmpty()) ? normalized : name.trim();
                        uniqueContacts.put(normalized,
                                new SelectableContact(displayName, normalized, preselectedContacts.contains(normalized)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Read synced WhatsApp contacts from our local database
        try {
            java.util.ArrayList<com.waenhancer.xposed.core.db.DelMessageStore.ContactInfo> waContacts =
                    com.waenhancer.xposed.core.db.DelMessageStore.getInstance(this).getWhatsAppContacts();
            for (com.waenhancer.xposed.core.db.DelMessageStore.ContactInfo wa : waContacts) {
                String normalized = wa.number;
                if (normalized == null || normalized.isEmpty()) {
                    normalized = wa.jid.replace("@s.whatsapp.net", "").replace("@g.us", "");
                    if (normalized.contains("@")) normalized = normalized.split("@")[0];
                }
                normalized = normalizePhone(normalized);
                if (normalized.isEmpty()) continue;

                if (!uniqueContacts.containsKey(normalized)) {
                    String displayName = wa.displayName;
                    if (displayName == null || displayName.trim().isEmpty()) {
                        displayName = wa.waName;
                    }
                    if (displayName == null || displayName.trim().isEmpty()) {
                        displayName = normalized;
                    }
                    uniqueContacts.put(normalized,
                            new SelectableContact(displayName.trim(), normalized, preselectedContacts.contains(normalized)));
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        List<SelectableContact> resultList = new ArrayList<>(uniqueContacts.values());
        resultList.sort((c1, c2) -> {
            if (c1.isSelected() && !c2.isSelected()) {
                return -1;
            } else if (!c1.isSelected() && c2.isSelected()) {
                return 1;
            } else {
                return c1.getName().compareToIgnoreCase(c2.getName());
            }
        });
        return resultList;
    }

    private void filterContacts(@NonNull String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filteredContacts.clear();
        for (SelectableContact contact : allContacts) {
            if (needle.isEmpty()
                    || contact.getName().toLowerCase(Locale.ROOT).contains(needle)
                    || contact.getPhoneNumber().contains(needle)) {
                filteredContacts.add(contact);
            }
        }
        adapter.submitList(filteredContacts);
        updateSelectAllLabel();
    }

    private void toggleSelectAll() {
        boolean allSelected = areAllFilteredSelected();
        for (SelectableContact contact : filteredContacts) {
            contact.setSelected(!allSelected);
        }
        adapter.submitList(filteredContacts);
        updateSelectAllLabel();
    }

    private boolean areAllFilteredSelected() {
        if (filteredContacts.isEmpty()) {
            return false;
        }
        for (SelectableContact contact : filteredContacts) {
            if (!contact.isSelected()) {
                return false;
            }
        }
        return true;
    }

    private void updateSelectAllLabel() {
        selectAllButton.setText(areAllFilteredSelected() ? R.string.clear_selection : R.string.select_all);
    }

    private void finishWithResult() {
        ArrayList<String> selectedContacts = new ArrayList<>();
        for (SelectableContact contact : allContacts) {
            if (contact.isSelected()) {
                selectedContacts.add(contact.getPhoneNumber());
            }
        }
        Intent result = new Intent();
        result.putExtra("key", preferenceKey);
        result.putStringArrayListExtra("contacts", selectedContacts);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_READ_CONTACTS) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            Toast.makeText(this, "Listing WhatsApp contacts", Toast.LENGTH_SHORT).show();
            loadContacts();
        }
    }

    @NonNull
    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String normalized = PhoneNumberUtils.normalizeNumber(phone);
        return normalized != null ? normalized : phone.trim();
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final java.util.function.Consumer<String> onChanged;

        SimpleTextWatcher(java.util.function.Consumer<String> onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            onChanged.accept(s != null ? s.toString() : "");
        }
    }
}
