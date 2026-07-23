package com.jaredrummler.android.colorpicker;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.app.AlertDialog;

public class ColorPreferenceWithDefault extends ColorPreferenceCompat {

    public ColorPreferenceWithDefault(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorPreferenceWithDefault(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onClick() {
        new AlertDialog.Builder(getContext())
                .setTitle(getTitle())
                .setItems(new CharSequence[]{"Custom Color...", "Theme Default"}, (dialog, which) -> {
                    if (which == 0) {
                        super.onClick();
                    } else {
                        saveValue(0);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
