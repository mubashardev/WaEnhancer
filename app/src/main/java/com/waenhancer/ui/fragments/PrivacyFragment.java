package com.waenhancer.ui.fragments;

import static com.waenhancer.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.waenhancer.R;
import com.waenhancer.preference.ContactPickerPreference;
import com.waenhancer.preference.FileSelectPreference;
import com.waenhancer.ui.fragments.base.BasePreferenceFragment;
import com.waenhancer.xposed.features.general.LiteMode;
import com.waenhancer.xposed.core.WppCore;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.waenhancer.BuildConfig;
import com.waenhancer.activities.DeletedMessagesActivity;
import com.waenhancer.utils.RootUtils;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.Utils;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

public class PrivacyFragment extends BasePreferenceFragment {

    private static class ContactPrivacyInfo {
        String number;
        String name;
        boolean hasConflict;
        String rawJson;
        String path;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_privacy, rootKey);

        findPreference("open_deleted_messages").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), DeletedMessagesActivity.class));
            return true;
        });

        var alwaysTypingGlobal = (TwoStatePreference) findPreference("always_typing_global");
        var targetPref = (ListPreference) findPreference("always_typing_global_target");
        var modePref = (ListPreference) findPreference("always_typing_global_mode");
        var contactPicker = (ContactPickerPreference) findPreference("always_typing_contacts");

        if (alwaysTypingGlobal != null) {
            // Only check for conflicts if the feature is currently enabled
            // This avoids triggering a root access prompt on every screen load
            if (alwaysTypingGlobal.isChecked()) {
                boolean initialGhostT = mPrefs.getBoolean("ghostmode_t", false);
                boolean initialGhostR = mPrefs.getBoolean("ghostmode_r", false);
                if (initialGhostT || initialGhostR) {
                    alwaysTypingGlobal.setChecked(false);
                }
            }

            alwaysTypingGlobal.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean && (Boolean) newValue) {
                    // Check module-level prefs first (no root needed)
                    boolean ghostmode_t = mPrefs.getBoolean("ghostmode_t", false);
                    boolean ghostmode_r = mPrefs.getBoolean("ghostmode_r", false);
                    
                    if (ghostmode_t || ghostmode_r) {
                        new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Feature Conflict")
                            .setMessage("Always Typing Simulation cannot be enabled while Hide Typing or Hide Recording is enabled globally. Please disable them first.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                        return false; // Block enabling
                    }
                    
                    if (contactPicker != null && contactPicker.isVisible()) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            contactPicker.onPreferenceClick(contactPicker);
                        });
                    }
                }
                return true;
            });
        }

        if (targetPref != null && modePref != null && contactPicker != null) {
            // Initialize state
            updateAlwaysTypingPrefs(targetPref.getValue(), modePref.getValue(), targetPref, modePref, contactPicker);

            targetPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newTarget = (String) newValue;
                updateAlwaysTypingPrefs(newTarget, modePref.getValue(), targetPref, modePref, contactPicker);
                new Handler(Looper.getMainLooper()).post(this::updateAlwaysTypingConflicts);
                return true;
            });

            modePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newMode = (String) newValue;
                updateAlwaysTypingPrefs(targetPref.getValue(), newMode, targetPref, modePref, contactPicker);
                new Handler(Looper.getMainLooper()).post(this::updateAlwaysTypingConflicts);
                return true;
            });
        }

        // Add listeners to ghostmode_t and ghostmode_r to turn off always typing if they are enabled
        var ghostmodeTPref = findPreference("ghostmode_t");
        var ghostmodeRPref = findPreference("ghostmode_r");
        
        Preference.OnPreferenceChangeListener hideTypingChangeListener = (preference, newValue) -> {
            if (newValue instanceof Boolean && (Boolean) newValue) {
                if (alwaysTypingGlobal != null && alwaysTypingGlobal.isChecked()) {
                    alwaysTypingGlobal.setChecked(false);
                    Toast.makeText(requireContext(), 
                        "Smart Always Typing disabled due to Hide Typing / Ghost Mode activation.", 
                        Toast.LENGTH_LONG).show();
                }
            }
            return true;
        };
        
        if (ghostmodeTPref != null) ghostmodeTPref.setOnPreferenceChangeListener(hideTypingChangeListener);
        if (ghostmodeRPref != null) ghostmodeRPref.setOnPreferenceChangeListener(hideTypingChangeListener);

        var checkContactsPref = findPreference("always_typing_check_contacts");
        if (checkContactsPref != null) {
            checkContactsPref.setOnPreferenceClickListener(pref -> {
                showCheckContactsBottomSheet();
                return true;
            });
        }

        // Initialize conflicts check on load
        updateAlwaysTypingConflicts();
    }

    private void updateAlwaysTypingPrefs(String target, String mode,
                                         ListPreference targetPref,
                                         ListPreference modePref,
                                         ContactPickerPreference contactPicker) {
        Preference delayNotePref = findPreference("always_typing_delay_note");
        if ("0".equals(target)) {
            // All Contacts -> Only conversation mode is allowed
            modePref.setValue("1");
            modePref.setEnabled(false);
            contactPicker.setVisible(false);
            if (delayNotePref != null) {
                delayNotePref.setVisible(false);
            }
        } else {
            // Specific Contacts -> Allow choosing mode
            modePref.setEnabled(true);
            contactPicker.setVisible(true);

            // Set max selection based on mode
            if ("2".equals(mode)) {
                // Global Simulation Mode (App-scoped) -> Max 2 contacts
                contactPicker.setMaxSelection(2);
                if (delayNotePref != null) {
                    delayNotePref.setVisible(true);
                }
            } else {
                // Conversation Mode -> Unlimited
                contactPicker.setMaxSelection(-1);
                if (delayNotePref != null) {
                    delayNotePref.setVisible(false);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setDisplayHomeAsUpEnabled(false);
        updateAlwaysTypingConflicts();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK) {
            ContactPickerPreference contactPickerPref = findPreference(data.getStringExtra("key"));
            if (contactPickerPref != null) {
                contactPickerPref.handleActivityResult(requestCode, resultCode, data);
                updateAlwaysTypingConflicts();
            }
        } else if (requestCode == LiteMode.REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
            FileSelectPreference fileSelectPreference = findPreference(data.getStringExtra("key"));
            if (fileSelectPreference != null) {
                fileSelectPreference.handleActivityResult(requestCode, resultCode, data);
            }
        }
    }

    // ── Custom Privacy conflict and manager helpers ────────────────────────

    private String readPrefsFile(String path) {
        try {
            var bridge = WppCore.getClientBridge();
            if (bridge != null) {
                try (ParcelFileDescriptor pfd = bridge.openFile(path, false)) {
                    if (pfd != null) {
                        try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = fis.read(buffer)) != -1) {
                                bos.write(buffer, 0, read);
                            }
                            return bos.toString("UTF-8");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.d("PrivacyFragment", "Failed to read via bridge: " + t.getMessage());
        }
        return RootUtils.runRootCommand("cat " + path);
    }

    private List<ContactPrivacyInfo> getCustomPrivacyContacts() {
        List<ContactPrivacyInfo> list = new ArrayList<>();
        String[] paths = new String[]{
            "/data/data/com.whatsapp/shared_prefs/WaGlobal.xml",
            "/data/data/com.whatsapp.w4b/shared_prefs/WaGlobal.xml"
        };
        for (String path : paths) {
            String content = readPrefsFile(path);
            if (content != null && !content.isEmpty()) {
                Pattern pattern = Pattern.compile("<string name=\"([^\"]+)_privacy\">([^<]+)</string>");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    String number = matcher.group(1);
                    String jsonStr = matcher.group(2);
                    jsonStr = jsonStr.replace("&amp;", "&")
                                     .replace("&quot;", "\"")
                                     .replace("&lt;", "<")
                                     .replace("&gt;", ">");
                    try {
                        JSONObject json = new JSONObject(jsonStr);
                        boolean hasConflict = json.optBoolean("HideTyping", false) || json.optBoolean("HideRecording", false);
                        
                        ContactPrivacyInfo info = new ContactPrivacyInfo();
                        info.number = number;
                        info.hasConflict = hasConflict;
                        info.rawJson = jsonStr;
                        info.path = path;
                        list.add(info);
                    } catch (Exception ignored) {}
                }
            }
        }
        
        // Resolve names for all resolved numbers in one query
        Set<String> cleanNumbers = new HashSet<>();
        for (ContactPrivacyInfo info : list) {
            cleanNumbers.add(info.number.replaceAll("\\D", ""));
        }
        Map<String, String> namesMap = resolveContactNames(requireContext(), cleanNumbers);
        for (ContactPrivacyInfo info : list) {
            String cleanNum = info.number.replaceAll("\\D", "");
            info.name = namesMap.getOrDefault(cleanNum, info.number);
        }
        
        return list;
    }

    private Map<String, String> resolveContactNames(Context context, Set<String> cleanNumbers) {
        Map<String, String> resolved = new HashMap<>();
        if (cleanNumbers.isEmpty()) return resolved;
        
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                    String number = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                    if (number != null) {
                        String cleanNum = number.replaceAll("\\D", "");
                        if (cleanNumbers.contains(cleanNum) && name != null && !name.trim().isEmpty()) {
                            resolved.put(cleanNum, name.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("PrivacyFragment", "Error querying contacts: " + e.getMessage());
        }
        return resolved;
    }

    private List<ContactPrivacyInfo> getConflictingContacts() {
        List<ContactPrivacyInfo> conflicts = new ArrayList<>();
        
        var globalPref = (TwoStatePreference) findPreference("always_typing_global");
        if (globalPref == null || !globalPref.isChecked()) {
            return conflicts;
        }
        
        // Only call root-based scan AFTER confirming the feature is enabled
        List<ContactPrivacyInfo> allCustom = getCustomPrivacyContacts();
        
        var targetPref = (ListPreference) findPreference("always_typing_global_target");
        String target = targetPref != null ? targetPref.getValue() : "1";
        
        if ("0".equals(target)) {
            // All Contacts -> any contact with custom privacy
            conflicts.addAll(allCustom);
        } else {
            // Specific Contacts -> selected contacts with HideTyping or HideRecording enabled
            String listStr = mPrefs.getString("always_typing_contacts", "");
            Set<String> selectedCleanNumbers = new HashSet<>();
            if (listStr != null && listStr.length() > 2) {
                String cleanStr = listStr.substring(1, listStr.length() - 1);
                for (String item : cleanStr.split(",")) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty()) {
                        selectedCleanNumbers.add(trimmed.replaceAll("\\D", ""));
                    }
                }
            }
            for (ContactPrivacyInfo info : allCustom) {
                String infoClean = info.number.replaceAll("\\D", "");
                if (selectedCleanNumbers.contains(infoClean) && info.hasConflict) {
                    conflicts.add(info);
                }
            }
        }
        return conflicts;
    }

    private void updateAlwaysTypingConflicts() {
        var warningPref = findPreference("always_typing_conflict_warning");
        var checkContactsPref = findPreference("always_typing_check_contacts");
        if (warningPref == null || checkContactsPref == null) return;

        var globalPref = (TwoStatePreference) findPreference("always_typing_global");
        boolean isEnabled = globalPref != null && globalPref.isChecked();

        // We no longer scan in the background to avoid repeated root prompts.
        // We keep the warning hidden until an explicit check is run,
        // and keep the "Check Contacts" button visible when the feature is enabled.
        warningPref.setVisible(false);
        checkContactsPref.setVisible(isEnabled);
    }

    private void showCheckContactsBottomSheet() {
        List<ContactPrivacyInfo> conflicts = getConflictingContacts();
        if (conflicts.isEmpty()) {
            Toast.makeText(requireContext(), "No custom privacy conflicts detected.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = 
                new BottomSheetDialog(requireContext());
        
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                Utils.dipToPixels(20),
                Utils.dipToPixels(20),
                Utils.dipToPixels(20),
                Utils.dipToPixels(20)
        );

        TextView titleView = new TextView(requireContext());
        titleView.setText("Per-Contact Custom Privacy Conflicts");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(DesignUtils.getPrimaryTextColor());
        titleView.setPadding(0, 0, 0, Utils.dipToPixels(15));
        layout.addView(titleView);

        ListView listView = new ListView(requireContext());
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dipToPixels(300) // limit height
        );
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        MaterialButton closeBtn = 
                new MaterialButton(requireContext());
        closeBtn.setText("Dismiss");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, Utils.dipToPixels(15), 0, 0);
        closeBtn.setLayoutParams(btnParams);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        layout.addView(closeBtn);

        dialog.setContentView(layout);
        
        class ConflictAdapter extends BaseAdapter {
            private final List<ContactPrivacyInfo> mList;
            ConflictAdapter(List<ContactPrivacyInfo> list) {
                mList = list;
            }
            @Override
            public int getCount() { return mList.size(); }
            @Override
            public Object getItem(int position) { return mList.get(position); }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ContactPrivacyInfo info = mList.get(position);
                LinearLayout itemLayout;
                TextView nameTextView;
                Button clearBtn;
                if (convertView == null) {
                    itemLayout = new LinearLayout(requireContext());
                    itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                    itemLayout.setPadding(0, Utils.dipToPixels(10), 0, Utils.dipToPixels(10));
                    itemLayout.setGravity(Gravity.CENTER_VERTICAL);

                    nameTextView = new TextView(requireContext());
                    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    nameTextView.setLayoutParams(textParams);
                    nameTextView.setTextColor(DesignUtils.getPrimaryTextColor());
                    nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    itemLayout.addView(nameTextView);

                    clearBtn = new Button(requireContext());
                    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(Utils.dipToPixels(64), Utils.dipToPixels(36));
                    clearBtn.setLayoutParams(btnParams);
                    clearBtn.setText("Clear");
                    clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    clearBtn.setTextColor(Color.WHITE);
                    clearBtn.setBackgroundColor(0xFFD32F2F); // Red
                    itemLayout.addView(clearBtn);
                } else {
                    itemLayout = (LinearLayout) convertView;
                    nameTextView = (TextView) itemLayout.getChildAt(0);
                    clearBtn = (Button) itemLayout.getChildAt(1);
                }

                nameTextView.setText(info.name);
                clearBtn.setOnClickListener(v -> {
                    clearCustomPrivacyForContact(info);
                    mList.remove(position);
                    notifyDataSetChanged();
                    updateAlwaysTypingConflicts();
                    if (mList.isEmpty()) {
                        dialog.dismiss();
                    }
                });

                return itemLayout;
            }
        }

        listView.setAdapter(new ConflictAdapter(conflicts));
        dialog.show();
    }

    private void clearCustomPrivacyForContact(ContactPrivacyInfo info) {
        try {
            String content = readPrefsFile(info.path);
            if (content == null || content.isEmpty()) return;

            String regex = "\\s*<string name=\"" + Pattern.quote(info.number + "_privacy") + "\">.*?</string>";
            String newContent = content.replaceAll(regex, "");

            File tempFile = new File(requireContext().getCacheDir(), "temp_waglobal.xml");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(newContent);
            }

            String cmd = "cat " + tempFile.getAbsolutePath() + " > " + info.path;
            RootUtils.runRootCommand(cmd);

            tempFile.delete();

            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
            intent.setPackage("com.whatsapp");
            requireContext().sendBroadcast(intent);

            Intent intent2 = new Intent(BuildConfig.APPLICATION_ID + ".PREFS_CHANGED");
            intent2.setPackage("com.whatsapp.w4b");
            requireContext().sendBroadcast(intent2);

            Intent restartIntent = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
            restartIntent.setPackage("com.whatsapp");
            requireContext().sendBroadcast(restartIntent);

            Intent restartIntent2 = new Intent(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART");
            restartIntent2.setPackage("com.whatsapp.w4b");
            requireContext().sendBroadcast(restartIntent2);

            Toast.makeText(requireContext(), "Cleared custom privacy for " + info.name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("PrivacyFragment", "Error clearing custom privacy: " + e.getMessage());
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}