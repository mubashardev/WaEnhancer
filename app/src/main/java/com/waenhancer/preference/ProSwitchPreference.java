package com.waenhancer.preference;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.waenhancer.BuildConfig;


/**
 * Refactored ProSwitchPreference: converted from a standard preference to a MaterialSwitchPreference
 * that toggles when Pro is active, or redirects to LicenseActivity when Pro is not active.
 */
public class ProSwitchPreference extends rikka.material.preference.MaterialSwitchPreference {

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

    private void init(Context context) {
        // Format the title to display the Pro badge
        CharSequence originalTitle = getTitle();
        if (originalTitle == null) {
            originalTitle = "Pro Feature";
        }
        
        if (BuildConfig.HAS_PRO_FEATURES) {
            String newTitle = originalTitle + " <font color='#8B5CF6'><b>[Pro]</b></font>";
            setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));
        } else {
            String newTitle = originalTitle + " <font color='#EF4444'><b>[missing pro module]</b></font>";
            setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));
            setEnabled(false);
        }

        updateSummary();
    }

    /**
     * Updates the summary text dynamically based on the verified status.
     */
    private void updateSummary() {
        if (!BuildConfig.HAS_PRO_FEATURES) {
            setSummary("Pro module not loaded");
            return;
        }

        boolean isVerified = getSafeSharedPreferences().getBoolean("is_pro_verified", false);
        if (isVerified) {
            setSummary("Status: Pro Active");
        } else {
            setSummary("Activate Pro First");
        }
    }

    @Override
    protected void onClick() {
        boolean isVerified = getSafeSharedPreferences().getBoolean("is_pro_verified", false);
        if (isVerified) {
            super.onClick();
        } else {
            Context context = getContext();
            try {
                Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
                Intent intent = new Intent(context, clazz);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ClassNotFoundException e) {
                android.widget.Toast.makeText(context, "Pro features are not available.", android.widget.Toast.LENGTH_SHORT).show();
            }
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
