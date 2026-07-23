package com.waenhancer.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.waenhancer.R;
import com.google.android.material.slider.Slider;
public class FloatSeekBarPreference extends Preference {

    private float minValue;
    private float maxValue;
    private float valueSpacing;
    private String format;

    private Slider slider;
    private SeekBar seekBar;
    private TextView textView;

    private float defaultValue = 0F;
    private float newValue = 0F;

    public FloatSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    public FloatSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public FloatSeekBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.seekBarPreferenceStyle);
    }

    public FloatSeekBarPreference(Context context) {
        this(context, null);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray ta, int index) {
        defaultValue = ta.getFloat(index, 0F);
        return defaultValue;
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        float def = 0;
        if (defaultValue instanceof Float) {
            def = (Float) defaultValue;
        } else if (defaultValue instanceof String) {
            try {
                def = Float.parseFloat((String) defaultValue);
            } catch (Exception ignored) {}
        } else {
            def = this.defaultValue;
        }

        try {
            newValue = getPersistedFloat(def);
        } catch (Exception e) {
            // If it fails (e.g. ClassCastException), try to get it as an int and convert
            try {
                newValue = (float) getPersistedInt((int) def);
                // Also persist it as float for next time
                persistFloat(newValue);
            } catch (Exception e2) {
                newValue = def;
            }
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        slider = null;
        seekBar = null;

        var seekbarView = holder.findViewById(R.id.seekbar);
        if (seekbarView instanceof Slider materialSlider) {
            slider = materialSlider;
        } else if (seekbarView instanceof SeekBar legacySeekBar) {
            seekBar = legacySeekBar;
        }
        textView = (TextView) holder.findViewById(R.id.seekbar_value);

        if (slider != null) {
            bindMaterialSlider();
        } else if (seekBar != null) {
            bindLegacySeekBar();
        }
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        setWidgetLayoutResource(R.layout.pref_float_seekbar);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.FloatSeekBarPreference, defStyleAttr, defStyleRes);
        minValue = ta.getFloat(R.styleable.FloatSeekBarPreference_minValue, 0F);
        maxValue = ta.getFloat(R.styleable.FloatSeekBarPreference_maxValue, 1F);
        valueSpacing = ta.getFloat(R.styleable.FloatSeekBarPreference_valueSpacing, .1F);
        format = ta.getString(R.styleable.FloatSeekBarPreference_format);
        if (format == null) {
            format = "%3.1f";
        }
        ta.recycle();
    }

    public float getValue() {
        if (slider != null) {
            return slider.getValue();
        }
        if (seekBar != null) {
            return progressToValue(seekBar.getProgress());
        }
        return 0F;
    }

    public void setValue(float value) {
        newValue = value;
        persistFloat(value);
        notifyChanged();
    }

    private void bindMaterialSlider() {
        slider.setValueFrom(minValue);
        slider.setValueTo(maxValue);
        slider.setStepSize(valueSpacing);
        newValue = normalizeValue(newValue);
        slider.setValue(newValue);
        slider.setEnabled(isEnabled());
        slider.clearOnChangeListeners();
        slider.clearOnSliderTouchListeners();

        slider.addOnChangeListener((slider, value, fromUser) -> textView.setText(String.format(format, normalizeValue(value))));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                float value = normalizeValue(slider.getValue());
                newValue = value;
                persistFloat(value);
            }
        });

        textView.setText(String.format(format, newValue));
    }

    private void bindLegacySeekBar() {
        int maxSteps = Math.max(1, Math.round((maxValue - minValue) / valueSpacing));
        newValue = normalizeValue(newValue);
        seekBar.setMax(maxSteps);
        seekBar.setProgress(valueToProgress(newValue));
        seekBar.setEnabled(isEnabled());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textView.setText(String.format(format, progressToValue(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float value = normalizeValue(progressToValue(seekBar.getProgress()));
                newValue = value;
                persistFloat(value);
            }
        });

        textView.setText(String.format(format, newValue));
    }

    private int valueToProgress(float value) {
        return Math.round((value - minValue) / valueSpacing);
    }

    private float progressToValue(int progress) {
        return minValue + (progress * valueSpacing);
    }

    private float normalizeValue(float value) {
        float clamped = Math.max(minValue, Math.min(maxValue, value));
        if (valueSpacing <= 0F) return clamped;
        return minValue + (Math.round((clamped - minValue) / valueSpacing) * valueSpacing);
    }
}