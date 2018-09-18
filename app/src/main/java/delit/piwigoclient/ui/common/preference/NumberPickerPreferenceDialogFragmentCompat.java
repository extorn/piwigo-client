package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.NumberPicker;

public class NumberPickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private NumberPicker mPicker;

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
    protected View onCreateDialogView(Context context) {
//        return super.onCreateDialogView(context);
        NumberPickerPreference pref = getPreference();

        mPicker = new NumberPicker(getContext());
        mPicker.setMinValue(pref.getMinValue());
        mPicker.setMaxValue(pref.getMaxValue());
        mPicker.setWrapSelectorWheel(pref.isWrapPickList());
        mPicker.setValue(pref.getValue());
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
}
