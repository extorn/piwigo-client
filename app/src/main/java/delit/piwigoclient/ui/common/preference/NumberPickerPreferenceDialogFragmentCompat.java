package delit.piwigoclient.ui.common.preference;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.NumberPicker;

import delit.piwigoclient.util.DisplayUtils;

public class NumberPickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String STATE_MIN_VALUE = "NumberPickerPreference.min";
    private static final String STATE_MAX_VALUE = "NumberPickerPreference.max";
    private static final String STATE_WRAP_VALUES = "NumberPickerPreference.wrap";
    private static final String STATE_VALUE = "NumberPickerPreference.currentValue";
    private NumberPicker mPicker;
    private int minValue;
    private int maxValue;
    private boolean wrapSelectionWheel;
    private int selectedValue;


    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    @Override
    public NumberPickerPreference getPreference() {
        return (NumberPickerPreference) super.getPreference();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        int pickerValue = mPicker.getValue();
        if (positiveResult) {
            mPicker.clearFocus();
            getPreference().setValue(pickerValue);
        }
        if (getPreference().getOnPreferenceChangeListener() != null) {
            getPreference().getOnPreferenceChangeListener().onPreferenceChange(getPreference(), pickerValue);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState == null) {
            NumberPickerPreference pref = getPreference();
            maxValue = pref.getMaxValue();
            minValue = pref.getMinValue();
            selectedValue = pref.getValue();
            wrapSelectionWheel = pref.isWrapPickList();
        } else {
            maxValue = savedInstanceState.getInt(STATE_MAX_VALUE);
            minValue = savedInstanceState.getInt(STATE_MIN_VALUE);
            selectedValue = savedInstanceState.getInt(STATE_VALUE);
            wrapSelectionWheel = savedInstanceState.getBoolean(STATE_WRAP_VALUES);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_VALUE,selectedValue);
        outState.putInt(STATE_MIN_VALUE,minValue);
        outState.putInt(STATE_MAX_VALUE,maxValue);
        outState.putBoolean(STATE_WRAP_VALUES, wrapSelectionWheel);
    }

    @Override
    protected View onCreateDialogView(Context context) {
        NumberPickerPreference pref = getPreference();

        mPicker = new NumberPicker(getContext());
        mPicker.setMinValue(minValue);
        mPicker.setMaxValue(maxValue);
        mPicker.setWrapSelectorWheel(wrapSelectionWheel);
        mPicker.setValue(selectedValue);
        return mPicker;
    }


    public static DialogFragment newInstance(String key) {
        final NumberPickerPreferenceDialogFragmentCompat fragment =
                new NumberPickerPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
