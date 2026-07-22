package com.waenhancer.preference;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.waenhancer.BuildConfig;
import android.content.SharedPreferences;
import android.widget.Toast;
import com.waenhancer.xposed.utils.ProHelper;
import rikka.material.preference.MaterialSwitchPreference;


/**
 * Refactored ProSwitchPreference: converted from a standard preference to a MaterialSwitchPreference
 * that toggles when Pro is active, or redirects to LicenseActivity when Pro is not active.
 */
public class ProSwitchPreference extends MaterialSwitchPreference {

    public ProSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public ProSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public ProSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private CharSequence originalSummary;
    private CharSequence originalTitle;

    private void init(Context context) {
        // Save the original summary and title defined in XML
        originalSummary = getSummary();
        originalTitle = getTitle();
        if (originalTitle == null) {
            originalTitle = "Pro Feature";
        }

        updateSummary();
    }

    /**
     * Updates the summary and title dynamically based on the verified status.
     */
    private void updateSummary() {
        if (!ProHelper.isPluginInstalled(getContext())) {
            setSummary("Helper Plugin Required");
            String newTitle = originalTitle + " <font color='#EF4444'><b>[missing helper plugin]</b></font>";
            setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));
            setEnabled(false);
            return;
        }

        boolean isVerified = getSafeSharedPreferences().getBoolean("is_pro_verified", false);
        boolean limitedFree = ProHelper.isLimitedFreePreferenceEnabled(getKey());

        // Pro badge color: Green (#22C55E) if active/verified/limited-free, Red (#EF4444) if inactive
        String tagColor = (isVerified || limitedFree) ? "#22C55E" : "#EF4444";
        String newTitle = originalTitle + " <font color='" + tagColor + "'><b>[Pro]</b></font>";
        setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));

        if (isVerified) {
            setSummary(originalSummary);
        } else if (limitedFree) {
            setSummary(originalSummary != null ? originalSummary + " (Limited Free)" : "Status: Limited Free Active");
        } else {
            setSummary("Activate Pro First");
        }
    }

    @Override
    protected void onClick() {
        if (!ProHelper.isPluginInstalled(getContext())) {
            ProHelper.navigateToPluginPack(getContext());
            return;
        }

        boolean isVerified = getSafeSharedPreferences().getBoolean("is_pro_verified", false);
        boolean limitedFree = ProHelper.isLimitedFreePreferenceEnabled(getKey());

        if (isVerified || limitedFree) {
            super.onClick();
        } else {
            Context context = getContext();
            try {
                Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                Intent intent = new Intent(context, clazz);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ClassNotFoundException e) {
                Toast.makeText(context, "Pro features are not available.", Toast.LENGTH_SHORT).show();
            }
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