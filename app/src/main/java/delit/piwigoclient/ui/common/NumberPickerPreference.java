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

package delit.piwigoclient.ui.common;


import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;

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
    private final int maxValue;
    private final int minValue;
    private final boolean setDefaultOnAttach;
    private final boolean wrapPickList;
    private NumberPicker mPicker;
    private int multiplier;
    private int mNumber = 0;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference);
        int DEFAULT_maxValue = 100;
        maxValue = a.getInt(R.styleable.NumberPickerPreference_maxValue, DEFAULT_maxValue);
        int DEFAULT_minValue = 0;
        minValue = a.getInt(R.styleable.NumberPickerPreference_minValue, DEFAULT_minValue);
        int DEFAULT_multiplier = 1;
        multiplier = a.getInt(R.styleable.NumberPickerPreference_multiplier, DEFAULT_multiplier);
        mNumber = a.getInt(R.styleable.NumberPickerPreference_defaultValue, 0);
        setDefaultOnAttach = a.getBoolean(R.styleable.NumberPickerPreference_setDefaultOnAttach, true);
        wrapPickList = a.getBoolean(R.styleable.NumberPickerPreference_wrapPickList, false);
        a.recycle();
    }

    @Override
    protected View onCreateDialogView() {
        mPicker = new NumberPicker(getContext());
        mPicker.setMinValue(minValue);
        mPicker.setMaxValue(maxValue);
        mPicker.setValue(mNumber);
        mPicker.setWrapSelectorWheel(wrapPickList);
        return mPicker;
    }

    public int getMultiplier() {
        return multiplier;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        int pickerValue = mPicker.getValue();
        if (positiveResult) {
            mPicker.clearFocus();
            setValue(pickerValue);
        }
        if (getOnPreferenceChangeListener() != null) {
            getOnPreferenceChangeListener().onPreferenceChange(this, pickerValue);
        }
    }

    public void updateDefaultValue(int newDefault) {
        setDefaultValue(newDefault);
        setValue(getAdjustedPersistedInt(getPersistedInt(newDefault)));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if(setDefaultOnAttach) {
            setValue(restoreValue ? getAdjustedPersistedInt(getPersistedInt(mNumber * multiplier)) : (Integer) defaultValue);
        }
    }

    protected int getAdjustedPersistedInt(int defaultPersistedInt) {
        if (multiplier == 1) {
            return defaultPersistedInt;
        } else {
            int persistedInt = defaultPersistedInt;
            int adjustedValue = (int) Math.round((double) persistedInt / multiplier);
            return adjustedValue;
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

        if (value != mNumber) {
            mNumber = value;
            if (mPicker != null) {
                mPicker.setValue(mNumber);
            }
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
}