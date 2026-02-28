package com.wmods.wppenhacer.preference;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.TextEditorActivity;
import com.wmods.wppenhacer.utils.FilePicker;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import kotlin.io.FilesKt;

public class ThemePreference extends Preference implements FilePicker.OnUriPickedListener {

    public static File rootDirectory = new File(App.getWaEnhancerFolder(), "themes");
    private android.app.Dialog mainDialog;

    public ThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        super.onClick();
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager())
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(getContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            App.showRequestStoragePermission((Activity) getContext());
        } else {
            showThemeDialog();
        }
    }

    @SuppressLint("ApplySharedPref")
    private void showThemeDialog() {
        final Context context = getContext();
        List<String> folders = getFolders();
        folders.add(0, "Default Theme");

        var folder_name = getSharedPreferences().getString(getKey(), null);

        com.google.android.material.bottomsheet.BottomSheetDialog builder = new com.google.android.material.bottomsheet.BottomSheetDialog(
                context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.preference_theme, null);
        builder.setContentView(dialogView);
        builder.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetDialog bsd = (com.google.android.material.bottomsheet.BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        LinearLayout folderListContainer = dialogView.findViewById(R.id.folder_list_container);
        Button newTheme = dialogView.findViewById(R.id.create_theme_button);
        newTheme.setOnClickListener(v -> showCreateNewThemeDialog());

        Button importTheme = dialogView.findViewById(R.id.import_theme_button);
        importTheme.setOnClickListener(v -> {
            FilePicker.setOnUriPickedListener(this);
            FilePicker.fileCapture.launch(new String[] { "application/zip" });
        });

        for (String folder : folders) {
            var cssFile = new File(rootDirectory, folder + "/style.css");
            if (!cssFile.exists() && !folder.equals("Default Theme")) {
                continue;
            }
            View itemView = LayoutInflater.from(context).inflate(R.layout.item_folder, null, false);
            TextView folderNameView = itemView.findViewById(R.id.folder_name);
            folderNameView.setText(folder);

            if (folder.equals(folder_name)) {
                folderNameView.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_material_green_dark_onPrimaryContainer));
            }
            if (cssFile.exists()) {
                var code = FilesKt.readText(cssFile, Charset.defaultCharset());
                var author = Utils.getAuthorFromCss(code);
                if (!TextUtils.isEmpty(author)) {
                    TextView authorView = itemView.findViewById(R.id.author);
                    authorView.setText(author);
                }
            }
            itemView.setOnClickListener(v -> {
                var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                sharedPreferences.edit().putString(getKey(), folder).commit();
                if (cssFile.exists()) {
                    var code = FilesKt.readText(cssFile, Charset.defaultCharset());
                    sharedPreferences.edit().putString("custom_css", code).commit();
                } else {
                    sharedPreferences.edit().putString("custom_css", "").commit();
                }
                mainDialog.dismiss();
            });
            View editButton = itemView.findViewById(R.id.edit_button);
            if (folder.equals("Default Theme")) {
                editButton.setVisibility(View.INVISIBLE);
            } else {
                editButton.setOnClickListener(v -> {
                    Intent intent = new Intent(context, TextEditorActivity.class);
                    intent.putExtra("folder_name", folder);
                    intent.putExtra("key", getKey());
                    ContextCompat.startActivity(context, intent, null);
                });
            }
            folderListContainer.addView(itemView);
        }
        mainDialog = builder;
        mainDialog.show();
    }

    private List<String> getFolders() {
        List<String> folderNames = new ArrayList<>();
        File[] folders = rootDirectory.listFiles(File::isDirectory);

        if (folders != null) {
            for (File folder : folders) {
                folderNames.add(folder.getName());
            }
        }

        return folderNames;
    }

    private void showCreateNewThemeDialog() {
        com.wmods.wppenhacer.ui.helpers.BottomSheetHelper.showInput(
                getContext(),
                getContext().getString(R.string.new_theme_name),
                "Theme Name",
                getContext().getString(R.string.create),
                folderName -> {
                    if (!TextUtils.isEmpty(folderName)) {
                        createNewFolder(folderName);
                    }
                });
    }

    private void createNewFolder(String folderName) {
        File rootDirectory = new File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes");
        File newFolder = new File(rootDirectory, folderName);
        if (!newFolder.exists()) {
            if (newFolder.mkdirs()) {
                mainDialog.dismiss();
                showThemeDialog();
            }
        }
    }

    @Override
    public void onUriPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Utils.showToast("Importing theme...", Toast.LENGTH_SHORT);
            try (var inputStream = getContext().getContentResolver().openInputStream(uri)) {
                var zipInputStream = new ZipInputStream(inputStream);
                ZipEntry zipEntry;

                String zipFileName = getZipFileName(uri);

                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    var entryName = zipEntry.getName();
                    var rootDirectory = new File(Environment.getExternalStorageDirectory(),
                            "Download/WaEnhancer/themes");

                    String folderName;
                    String targetPath;

                    int lastSlashIndex = entryName.lastIndexOf('/');
                    if (lastSlashIndex > 0) {
                        folderName = entryName.substring(0, lastSlashIndex);
                        targetPath = entryName;
                    } else {
                        folderName = zipFileName;
                        targetPath = zipFileName + "/" + entryName;
                    }

                    var newFolder = new File(rootDirectory, folderName);
                    if (!newFolder.exists()) {
                        newFolder.mkdirs();
                    }
                    if (entryName.endsWith("/"))
                        continue;
                    var file = new File(rootDirectory, targetPath);
                    Files.copy(zipInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                ((Activity) getContext()).runOnUiThread(() -> {
                    Utils.showToast(getContext().getString(R.string.theme_imported_successfully), Toast.LENGTH_SHORT);
                    mainDialog.dismiss();
                    showThemeDialog();
                });
            } catch (Exception ignored) {
            }
        });
    }

    private String getZipFileName(Uri uri) {
        String fileName = null;

        if (Objects.equals(uri.getScheme(), "content")) {
            try (var cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }

        if (fileName != null && fileName.toLowerCase().endsWith(".zip")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "imported_theme_" + System.currentTimeMillis();
        }

        return fileName;
    }
}