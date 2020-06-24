package delit.libs.ui.view.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;

public class NumberPickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String STATE_MIN_VALUE = "NumberPickerPreference.min";
    private static final String STATE_MAX_VALUE = "NumberPickerPreference.max";
    private static final String STATE_WRAP_VALUES = "NumberPickerPreference.wrap";
    private static final String STATE_VALUE = "NumberPickerPreference.currentValue";
    private static final String TAG = "NumPickPrefDF";
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

        if (positiveResult) {
            mPicker.clearFocus();
            int pickerValue = mPicker.getValue();
            getPreference().setValue(pickerValue);
            if (getPreference().getOnPreferenceChangeListener() != null) {
                getPreference().getOnPreferenceChangeListener().onPreferenceChange(getPreference(), pickerValue);
            }
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
        mPicker = new NumberPicker(getContext());
        mPicker.setSaveFromParentEnabled(false);
        mPicker.setSaveEnabled(false);
        return mPicker;
    }

    /**
     * fill created view with data
     *
     * @param view
     */
    @Override
    protected void onBindDialogView(View view) {
        Logging.log(Log.DEBUG, TAG, "Binding data for preference : " + getPreference().getKey());
        super.onBindDialogView(view);
        Logging.log(Log.DEBUG, TAG, "Binding data for preference (applying values from state) : " + getPreference().getKey() + " : " + minValue + "-" + maxValue);
        NumberPicker picker = (NumberPicker) view;
        picker.setMinValue(minValue);
        picker.setMaxValue(maxValue);
        picker.setWrapSelectorWheel(wrapSelectionWheel);
        Logging.log(Log.DEBUG, TAG, "Binding data for preference (setting value) : " + getPreference().getKey() + " : " + selectedValue);
        picker.setValue(selectedValue);
        Logging.log(Log.DEBUG, TAG, "Binding data for preference (requesting focus) : " + getPreference().getKey());
        view.requestFocus();
        Logging.log(Log.DEBUG, TAG, "Binding data for preference (complete) : " + getPreference().getKey());
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
