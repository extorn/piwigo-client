package delit.piwigoclient.ui.common.preference;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat;
import android.support.v7.preference.Preference;
import android.view.View;
import android.widget.EditText;

import delit.piwigoclient.util.DisplayUtils;

public class CustomEditTextPreferenceDialogFragmentCompat extends EditTextPreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private EditText editText;
    private int inputType;
    private String STATE_INPUT_TYPE = "CustomEditTextPreference.InputType";

    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    public CustomEditTextPreference getPreference() {
        return (CustomEditTextPreference)super.getPreference();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState == null) {
            inputType = getPreference().getInputFieldType();
        } else {
            inputType = savedInstanceState.getInt(STATE_INPUT_TYPE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_INPUT_TYPE, inputType);
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
        editText = view.findViewById(android.R.id.edit);
        if (inputType != -1) {
            editText.setInputType(inputType);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
