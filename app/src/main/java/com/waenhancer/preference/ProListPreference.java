package com.waenhancer.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Html;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import com.waenhancer.BuildConfig;
import com.waenhancer.R;
import com.waenhancer.ui.helpers.BottomSheetHelper;
import com.waenhancer.xposed.utils.ProHelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import android.content.res.TypedArray;
import android.util.Log;
import android.widget.Toast;

/**
 * A {@link ListPreference} that marks certain entry values as requiring an active Pro license.
 *
 * <p>Usage in XML:
 * <pre>
 *   &lt;com.waenhancer.preference.ProListPreference
 *       app:entries="@array/my_entries"
 *       app:entryValues="@array/my_values"
 *       app:proEntryValues="@array/my_pro_values" /&gt;
 * </pre>
 *
 * <p>Values listed in {@code proEntryValues} will display a purple [Pro] badge in the
 * selection dialog and redirect to LicenseActivity when selected by non-Pro users.
 */
public class ProListPreference extends ListPreference {

    /** Values in this set require an active Pro license to select. */
    private Set<String> mProValues = new HashSet<>();

    public ProListPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public ProListPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public ProListPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ProListPreference(@NonNull Context context) {
        super(context);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) return;

        // Read the custom app:proEntryValues attribute
        int[] attrsArray = new int[]{ R.attr.proEntryValues };
        try (TypedArray ta = context.obtainStyledAttributes(attrs, attrsArray)) {
            int resId = ta.getResourceId(0, 0);
            if (resId != 0) {
                String[] proVals = context.getResources().getStringArray(resId);
                mProValues = new HashSet<>(Arrays.asList(proVals));
            }
        } catch (Exception ignored) {}

        // Stamp [Pro] badge into entry display labels for Pro-restricted values
        applyProBadgesToEntries();
    }

    /**
     * Appends a coloured [Pro] badge to every display entry whose corresponding value is Pro-restricted.
     */
    private void applyProBadgesToEntries() {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null) return;

        CharSequence[] stamped = new CharSequence[entries.length];
        for (int i = 0; i < entries.length; i++) {
            String val = entryValues[i].toString();
            if (mProValues.contains(val)) {
                if (ProHelper.isPluginInstalled(getContext())) {
                    stamped[i] = Html.fromHtml(
                            entries[i] + " <font color='#8B5CF6'><b>[Pro]</b></font>",
                            Html.FROM_HTML_MODE_LEGACY);
                } else {
                    stamped[i] = Html.fromHtml(
                            entries[i] + " <font color='#EF4444'><b>[Pro — plugin missing]</b></font>",
                            Html.FROM_HTML_MODE_LEGACY);
                }
            } else {
                stamped[i] = entries[i];
            }
        }
        setEntries(stamped);
    }

    @Override
    protected void onClick() {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null) {
            super.onClick();
            return;
        }

        String currentValue = getValue();
        CharSequence title = getDialogTitle();
        if (title == null) {
            title = getTitle();
        }
        String titleStr = title != null ? title.toString() : "";

        if ("floating_bottom_bar_pill_design".equals(getKey())) {
            BottomSheetHelper.showPillDesignChoice(
                    getContext(),
                    titleStr,
                    entries,
                    entryValues,
                    currentValue,
                    (index, selectedValue) -> {
                        // Check if this value requires Pro
                        if (mProValues.contains(selectedValue) && !isProActive()) {
                            if (ProHelper.isProEnabled()) {
                                refreshConfigAndApply(selectedValue);
                            } else {
                                openLicenseActivity();
                            }
                            return;
                        }

                        // Valid selection — persist and close
                        if (callChangeListener(selectedValue)) {
                            setValue(selectedValue);
                        }
                    }
            );
        } else {
            BottomSheetHelper.showSingleChoice(
                    getContext(),
                    titleStr,
                    entries,
                    entryValues,
                    currentValue,
                    (index, selectedValue) -> {
                        // Check if this value requires Pro
                        if (mProValues.contains(selectedValue) && !isProActive()) {
                            if (ProHelper.isProEnabled()) {
                                refreshConfigAndApply(selectedValue);
                            } else {
                                openLicenseActivity();
                            }
                            return;
                        }

                        // Valid selection — persist and close
                        if (callChangeListener(selectedValue)) {
                            setValue(selectedValue);
                        }
                    }
            );
        }
    }

    private void refreshConfigAndApply(final String selectedValue) {
        Context context = getContext();
        if (context == null) return;

        final AlertDialog progressDialog = new AlertDialog.Builder(context)
                .setTitle("Refreshing Configuration")
                .setMessage("Please wait while we update your license settings from the server...")
                .setCancelable(false)
                .create();

        progressDialog.show();

        ProHelper.silentCheck(context, new Runnable() {
            @Override
            public void run() {
                if (progressDialog.isShowing()) {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {}
                }

                if (isProActive()) {
                    if (callChangeListener(selectedValue)) {
                        setValue(selectedValue);
                        Toast.makeText(
                                getContext(),
                                "Pro features refreshed. Selection applied!",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                } else {
                    openLicenseActivity();
                }
            }
        });
    }

    @Override
    public void setValue(String value) {
        if (mProValues != null && mProValues.contains(value) && !isProActive()) {
            super.setValue("regular");
            return;
        }
        super.setValue(value);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isProActive() {
        if (!ProHelper.isPluginInstalled(getContext())) {
            return false;
        }
        try {
            return ProHelper.isPillDesignProEnabled();
        } catch (Throwable t) {
            Log.e("WaeX-Pref", "isProActive: exception", t);
            return false;
        }
    }

    private void openLicenseActivity() {
        Context ctx = getContext();
        if (!ProHelper.isPluginInstalled(ctx)) {
            ProHelper.navigateToPluginPack(ctx);
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
            Intent intent = new Intent(ctx, clazz);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (ClassNotFoundException e) {
            Toast.makeText(
                    ctx,
                    "Pro features are not available. Activate a Pro license first.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}