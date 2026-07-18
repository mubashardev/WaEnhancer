package com.waenhancer.preference;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.waenhancer.R;
import com.waenhancer.utils.FilePicker;
import com.waenhancer.utils.RealPathUtil;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import androidx.preference.PreferenceViewHolder;
import com.waenhancer.ui.helpers.BottomSheetHelper;

public class FileReaderPreference extends Preference implements Preference.OnPreferenceClickListener,
        FilePicker.OnFilePickedListener, FilePicker.OnUriPickedListener {

    private static final String[] XML_MIME_TYPE = { "text/xml", "application/xml" };
    private String xmlContent;
    private String filePath;

    public FileReaderPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public FileReaderPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public FileReaderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void showAlertPermission() {
        BottomSheetHelper.showConfirmation(
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
        FilePicker.setOnUriPickedListener(this);
        if (FilePicker.fileCapture == null) {
            Toast.makeText(getContext(), "Please use the standalone WaEnhancerX app for file operations.", Toast.LENGTH_SHORT).show();
            return true;
        }
        FilePicker.fileCapture.launch(XML_MIME_TYPE);
        return true;
    }

    @Override
    public void onFilePicked(File file) {
        if (!file.canRead()) {
            Toast.makeText(this.getContext(), R.string.unable_to_read_this_file, Toast.LENGTH_SHORT).show();
            return;
        }

        processXmlFile(file);
    }

    @Override
    public void onUriPicked(Uri uri) {
        try {
            String realPath = RealPathUtil.getRealFilePath(getContext(), uri);
            if (realPath != null) {
                File file = new File(realPath);
                processXmlFile(file);
            } else {
                InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    processXmlStream(inputStream, uri.getLastPathSegment());
                }
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error processing XML file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processXmlFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            processXmlStream(fis, file.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error reading XML file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processXmlStream(InputStream inputStream, String filePath)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        // Parse XML document
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        // Convert document to string
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);

        this.xmlContent = writer.toString();
        this.filePath = filePath;

        // Save the XML content in the preference
        getSafeSharedPreferences().edit()
                .putString(getKey(), xmlContent)
                .apply();

        // Display file path in summary
        setSummary(filePath);
        setWidgetLayoutResource(R.layout.layout_delete_widget);
        notifyChanged();
        Toast.makeText(getContext(), "XML file loaded successfully", Toast.LENGTH_SHORT).show();
    }

    private void init(Context context, AttributeSet attrs) {
        setOnPreferenceClickListener(this);

        // Get saved values if they exist
        String savedXml = PreferenceManager.getDefaultSharedPreferences(context).getString(this.getKey(), null);
        if (savedXml != null) {
            this.xmlContent = savedXml;
            setSummary(this.filePath != null ? this.filePath : "XML content loaded");
            setWidgetLayoutResource(R.layout.layout_delete_widget);
        } else {
            setWidgetLayoutResource(0);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View deleteBtn = holder.findViewById(R.id.delete_button);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle("Remove Custom KeyBox")
                        .setMessage("Are you sure you want to remove the imported custom keybox.xml file?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            xmlContent = null;
                            filePath = null;
                            getSafeSharedPreferences().edit()
                                    .remove(getKey())
                                    .remove("keybox_verify_status")
                                    .remove("keybox_verify_time")
                                    .apply();
                            setSummary("No file selected");
                            setWidgetLayoutResource(0);
                            notifyChanged();
                            Toast.makeText(getContext(), "Custom KeyBox removed", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }
    }

    @NonNull
    private SharedPreferences getSafeSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            return prefs;
        }
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }
}