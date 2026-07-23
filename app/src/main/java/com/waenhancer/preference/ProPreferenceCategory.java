package com.waenhancer.preference;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;

import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.utils.ProHelper;

/**
 * Custom PreferenceCategory that adds a Pro badge and handles active/missing Pro module states.
 */
public class ProPreferenceCategory extends PreferenceCategory {

    public ProPreferenceCategory(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public ProPreferenceCategory(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public ProPreferenceCategory(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        CharSequence originalTitle = getTitle();
        if (originalTitle == null) {
            originalTitle = "Pro Category";
        }

        boolean pluginInstalled = ProHelper.isPluginInstalled(context);
        if (pluginInstalled) {
            String newTitle = originalTitle + " <font color='#8B5CF6'><b>[Pro]</b></font>";
            setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));
        } else {
            String newTitle = originalTitle + " <font color='#EF4444'><b>[missing helper plugin]</b></font>";
            setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));
            setEnabled(false);
        }
    }
}