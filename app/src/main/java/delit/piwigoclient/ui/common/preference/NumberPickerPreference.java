/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package delit.piwigoclient.ui.common.preference;


import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.EditText;

import delit.piwigoclient.R;

/**
 * A {@link Preference} that allows for string
 * input.
 * <p>
 * It is a subclass of {@link DialogPreference} and shows the {@link EditText}
 * in a dialog. This {@link EditText} can be modified either programmatically
 * via getEditText(), or through XML by setting any EditText
 * attributes on the NumberPickerPreference.
 * <p>
 * This preference will store a string into the SharedPreferences.
 * <p>
 * See android.R.styleable#EditText EditText Attributes.
 */
public class NumberPickerPreference extends DialogPreference {
    private int maxValue;
    private int minValue;
    private boolean setDefaultOnAttach;
    private boolean wrapPickList;
    private int multiplier;
    private int value = 0;

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        initPreference(context, attrs);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initPreference(context, attrs);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public NumberPickerPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    private void initPreference(Context context, AttributeSet attrs) {
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference);
        int DEFAULT_maxValue = 100;
        maxValue = a.getInt(R.styleable.NumberPickerPreference_maxValue, DEFAULT_maxValue);
        int DEFAULT_minValue = 0;
        minValue = a.getInt(R.styleable.NumberPickerPreference_minValue, DEFAULT_minValue);
        int DEFAULT_multiplier = 1;
        multiplier = a.getInt(R.styleable.NumberPickerPreference_multiplier, DEFAULT_multiplier);
        value = a.getInt(R.styleable.NumberPickerPreference_defValue, 0);
        setDefaultOnAttach = a.getBoolean(R.styleable.NumberPickerPreference_setDefaultOnAttach, true);
        wrapPickList = a.getBoolean(R.styleable.NumberPickerPreference_wrapPickList, false);
        a.recycle();
    }



    public int getMultiplier() {
        return multiplier;
    }

    public void updateDefaultValue(int newDefault) {
        setDefaultValue(newDefault);
        setValue(getAdjustedPersistedInt(getPersistedInt(newDefault)));
        notifyChanged();
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (setDefaultOnAttach) {
            setValue(restoreValue ? getAdjustedPersistedInt(getPersistedInt(value * multiplier)) : (Integer) defaultValue);
        }
    }

    protected int getAdjustedPersistedInt(int defaultPersistedInt) {
        if (multiplier == 1) {
            return defaultPersistedInt;
        } else {
            return (int) Math.round((double) defaultPersistedInt / multiplier);
        }
    }

    @Override
    protected boolean persistInt(int value) {
        if (multiplier == 1) {
            return super.persistInt(value);
        } else {
            return super.persistInt(value * multiplier);
        }
    }

    public void setValue(int value) {
        if (shouldPersist()) {
            persistInt(value);
        }

        if (value != this.value) {
            this.value = value;
            notifyChanged();
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
    }

    /**
     * Returns the summary of this ListPreference. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
//        int adjustedValue = (int) Math.round((double) value / getMultiplier());
        CharSequence summary = super.getSummary();
        if (summary != null) {
            return String.format(summary.toString(), value);
        }
        return null;
    }


    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public boolean isWrapPickList() {
        return wrapPickList;
    }

    public int getValue() {
        return value;
    }
}