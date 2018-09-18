package delit.piwigoclient.ui.common.preference;

import android.os.Bundle;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat;
import android.support.v7.preference.Preference;
import android.view.View;
import android.widget.EditText;

public class CustomEditTextPreferenceDialogFragmentCompat extends EditTextPreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private EditText editText;

    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    public CustomEditTextPreference getPreference() {
        return (CustomEditTextPreference)super.getPreference();
    }

    public static CustomEditTextPreferenceDialogFragmentCompat newInstance(String key) {
        final CustomEditTextPreferenceDialogFragmentCompat
                fragment = new CustomEditTextPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        editText = (EditText) view.findViewById(android.R.id.edit);
        int inputType = getPreference().getInputFieldType();
        if (inputType != -1) {
            editText.setInputType(inputType);
        }
    }
}
