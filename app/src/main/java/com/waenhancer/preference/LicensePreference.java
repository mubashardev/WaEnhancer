package com.waenhancer.preference;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.widget.Toast;
import com.waenhancer.xposed.utils.ProHelper;



/**
 * Clickable preference that navigates to LicenseActivity for license key verification.
 */
public class LicensePreference extends Preference implements Preference.OnPreferenceClickListener {

    public LicensePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public LicensePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public LicensePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOnPreferenceClickListener(this);

        // Standardize the Pro look and feel to match existing pro switch preferences
        String titleHtml = "WAEX Helper <font color='#8B5CF6'><b>[Pro]</b></font>";
        setTitle(Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY));

        updateSummary();
    }

    /**
     * Dynamically updates the summary text based on the active license state.
     */
    private void updateSummary() {
        if (!ProHelper.isPluginInstalled(getContext())) {
            setSummary("Plugin Required");
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
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Context context = getContext();
        if (!ProHelper.isPluginInstalled(context)) {
            ProHelper.navigateToPluginPack(context);
            return true;
        }
        try {
            Class<?> clazz = Class.forName("com.waenhancer.activities.LicenseActivity");
            Intent intent = new Intent(context, clazz);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ClassNotFoundException e) {
            Toast.makeText(context, "Pro features are not available.", Toast.LENGTH_SHORT).show();
        }
        return true;
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