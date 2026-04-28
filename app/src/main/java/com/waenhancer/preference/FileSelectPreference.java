package com.waenhancer.preference;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.waenhancer.App;
import com.waenhancer.R;
import com.waenhancer.utils.FilePicker;
import com.waenhancer.utils.RealPathUtil;
import com.waenhancer.xposed.features.general.LiteMode;
import com.waenhancer.xposed.utils.Utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class FileSelectPreference extends Preference implements Preference.OnPreferenceClickListener,
        FilePicker.OnFilePickedListener, FilePicker.OnUriPickedListener {

    private String[] mineTypes;
    private boolean selectDirectory;

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public FileSelectPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void showAlertPermission() {
        com.waenhancer.ui.helpers.BottomSheetHelper.showConfirmation(
                getContext(),
                getContext().getString(R.string.storage_permission),
                getContext().getString(R.string.permission_storage),
                getContext().getString(R.string.allow),
                false,
                () -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                    getContext().startActivity(intent);
                });
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        var prefs = getSafeSharedPreferences();

        if (prefs.getBoolean("lite_mode", false)) {
            String packageName = "";
            PackageInfo packageInfo = null;
            for (var possiblePackage : new String[] { "com.whatsapp", "com.whatsapp.w4b" }) {
                try {
                    packageInfo = getContext().getApplicationContext().getPackageManager()
                            .getPackageInfo(possiblePackage, PackageManager.GET_ACTIVITIES);
                    packageName = possiblePackage;
                    break;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (packageInfo == null) {
                Utils.showToast("Unable to find WhatsApp package. Using the system folder picker instead.",
                        Toast.LENGTH_LONG);
                if (selectDirectory) {
                    launchModernDirectoryPicker();
                }
                return true;
            }

            String className = null;
            if (packageInfo.activities != null) {
                for (var activity : packageInfo.activities) {
                    if (activity.name.endsWith("SettingsNotifications")) {
                        className = activity.name;
                        break;
                    }
                }
            }
            if (className == null) {
                Utils.showToast(
                        "Unable to find WhatsApp's folder picker activity. Using the system folder picker instead.",
                        Toast.LENGTH_LONG);
                if (selectDirectory) {
                    launchModernDirectoryPicker();
                }
                return true;
            }
            try {
                Intent intent = new Intent();
                intent.setClassName(packageName, className);
                intent.putExtra("key", getKey());
                intent.putExtra("download_mode", true);
                ((Activity) getContext()).startActivityForResult(intent, LiteMode.REQUEST_FOLDER);
            } catch (ActivityNotFoundException | SecurityException e) {
                Utils.showToast("Folder picker unavailable in WhatsApp. Using the system folder picker instead.",
                        Toast.LENGTH_LONG);
                if (selectDirectory) {
                    launchModernDirectoryPicker();
                }
            }
            return true;
        }

        if (selectDirectory) {
            launchModernDirectoryPicker();
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showAlertPermission();
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ((Activity) getContext()).requestPermissions(new String[] { Manifest.permission.READ_MEDIA_IMAGES }, 1);
                return true;
            }
        } else if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ((Activity) getContext()).requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, 1);
            return true;
        }

        FilePicker.setOnFilePickedListener(this);

        if (mineTypes.length == 1 && mineTypes[0].contains("image")) {
            FilePicker.setOnUriPickedListener(this);
            FilePicker.imageCapture.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(new ActivityResultContracts.PickVisualMedia.SingleMimeType(mineTypes[0])).build());
            return true;
        }
        FilePicker.fileCapture.launch(mineTypes);
        return false;
    }

    private void launchModernDirectoryPicker() {
        String currentFolder = getSafeSharedPreferences().getString(getKey(), "/sdcard/Download");
        String message = getContext().getString(R.string.folder_picker_description) + "\n\n"
                + getContext().getString(R.string.folder_picker_current, currentFolder);

        com.waenhancer.ui.helpers.BottomSheetHelper.showConfirmation(
                getContext(),
                getContext().getString(R.string.folder_picker_title),
                message,
                getContext().getString(R.string.folder_picker_action),
                false,
                () -> {
                    FilePicker.setOnUriPickedListener(this);
                    try {
                        if (FilePicker.directoryCapture == null) {
                            throw new IllegalStateException("Directory picker not available");
                        }
                        FilePicker.directoryCapture.launch(null);
                    } catch (Throwable throwable) {
                        Toast.makeText(getContext(), R.string.failed_open_folder_picker, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onFilePicked(File file) {
        if (file.isDirectory()) {
            try {
                var tmpFile = Files.write(new File(file, "tmp.file").toPath(), new byte[0]).toFile();
                boolean delete = tmpFile.delete();
            } catch (Exception ignored) {
                Toast.makeText(this.getContext(), R.string.failed_save_directory, Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (!file.canRead()) {
            Toast.makeText(this.getContext(), R.string.unable_to_read_this_file, Toast.LENGTH_SHORT).show();
            return;

        }
        getSafeSharedPreferences().edit().putString(getKey(), file.getAbsolutePath()).apply();
        setSummary(file.getAbsolutePath());
    }

    public void init(Context context, AttributeSet attrs) {
        setOnPreferenceClickListener(this);
        var typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.FileSelectPreference,
                0, 0);
        var attrsArray = typedArray.getTextArray(R.styleable.FileSelectPreference_android_entryValues);
        if (attrsArray != null) {
            mineTypes = Arrays.stream(attrsArray).map(String::valueOf).toArray(String[]::new);
        } else {
            mineTypes = new String[] { "*/*" };
        }
        selectDirectory = typedArray.getBoolean(R.styleable.FileSelectPreference_directory, false);
        var prefs = PreferenceManager.getDefaultSharedPreferences(context);
        var keyValue = prefs.getString(this.getKey(), null);
        setSummary(keyValue);
    }

    @Override
    public void onUriPicked(Uri uri) {
        if (selectDirectory) {
            handleDirectoryUri(uri);
            return;
        }

        ContentResolver contentResolver = getContext().getContentResolver();
        var type = Objects.requireNonNull(contentResolver.getType(uri));
        var extension = type.split("/")[1];
        var folder = new File(App.getWaEnhancerFolder(), "files");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        var outFile = new File(folder, this.getKey() + "." + extension);
        var editor = getSafeSharedPreferences().edit();
        editor.putString(getKey(), "").commit();
        setSummary(outFile.getAbsolutePath());
        CompletableFuture.runAsync(() -> {
            try (var inputStream = contentResolver.openInputStream(uri)) {
                Files.copy(inputStream, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Utils.showToast("Failed to save file: " + e, Toast.LENGTH_SHORT);
            }
            editor.putString(getKey(), outFile.getAbsolutePath()).commit();
        });

    }

    private void handleDirectoryUri(@NonNull Uri uri) {
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        try {
            String realPath = RealPathUtil.getRealFolderPath(getContext(), uri);
            if (realPath == null || realPath.isEmpty()) {
                throw new IllegalStateException("Could not resolve folder path");
            }
            getSafeSharedPreferences().edit().putString(getKey(), realPath).apply();
            setSummary(realPath);
            notifyChanged();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.failed_save_directory, Toast.LENGTH_SHORT).show();
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LiteMode.REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
            String storedPath = data.getStringExtra("path");
            if (storedPath == null || storedPath.trim().isEmpty()) {
                Toast.makeText(getContext(), R.string.failed_save_directory, Toast.LENGTH_SHORT).show();
                return;
            }

            String resolvedPath = storedPath;
            try {
                Uri uri = Uri.parse(storedPath);
                if (uri != null) {
                    String realPath = RealPathUtil.getRealFolderPath(getContext(), uri);
                    if (realPath != null && !realPath.isEmpty()) {
                        resolvedPath = realPath;
                    }
                }
            } catch (Exception ignored) {
            }
            getSafeSharedPreferences().edit().putString(getKey(), resolvedPath).apply();
            setSummary(resolvedPath);
            notifyChanged();
        }
    }

    @NonNull
    private android.content.SharedPreferences getSafeSharedPreferences() {
        android.content.SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            return prefs;
        }
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }

}
