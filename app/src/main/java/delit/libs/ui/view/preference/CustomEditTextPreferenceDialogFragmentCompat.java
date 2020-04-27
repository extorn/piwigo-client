package delit.libs.ui.view.preference;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.button.AppCompatCheckboxTriState;
import delit.piwigoclient.R;

public class CustomEditTextPreferenceDialogFragmentCompat<T extends CustomEditTextPreference> extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private static final String STATE_TEXT_VAL = "CURRENT_UI_VAL";
    private EditText editText;
    private int inputType;
    private String STATE_INPUT_TYPE = "CustomEditTextPreference.InputType";
    private CharSequence currentText;

    @Override
    public T findPreference(CharSequence key) {
        return getPreference();
    }

    public T getPreference() {
        return (T)super.getPreference();
    }

    protected EditText getEditText() {
        return editText;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState == null) {
            inputType = getPreference().getInputFieldType();
        } else {
            inputType = savedInstanceState.getInt(STATE_INPUT_TYPE);
        }
        if (savedInstanceState == null) {
            setCurrentTextFromPreference(getPreference());
        } else {
            setCurrentText(savedInstanceState.getCharSequence(STATE_TEXT_VAL));
        }
    }

    /**
     * Secure preferences will override this to ensure they get an encrypted version of the text.
     *
     * @param preference
     */
    private void setCurrentTextFromPreference(T preference) {
        setCurrentText(processTextOnRetrieveFromPreference(preference, preference.getText()));
    }

    /**
     * Override if needed - for example to encrypt value retrieved from preference
     * (this would be needed to avoid a non encrypted copy being stored to saved state
     * for example). - Remember to also override @see(#processTextOnRetrieveBeforeDisplay)
     * if your override this method).
     * @param text
     * @return
     */
    protected CharSequence processTextOnRetrieveFromPreference(T preference, String text) {
        return text;
    }

    /**
     * Override if needed - for example to decrypt value to show in edit field
     * @param text
     * @return
     */
    protected CharSequence processTextOnRetrieveBeforeDisplay(T preference, CharSequence text) {
        return text;
    }

    private void setCurrentText(CharSequence text) {
        currentText = text;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_INPUT_TYPE, inputType);
        outState.putCharSequence(STATE_TEXT_VAL, currentText);
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
        editText.requestFocus();
        editText.setText(getCurrentTextForDisplay());
        // Place cursor at the end
        editText.setSelection(editText.getText().length());

        boolean isTextPassword = InputType.TYPE_TEXT_VARIATION_PASSWORD == (getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD);
        boolean isNumberPassword = InputType.TYPE_NUMBER_VARIATION_PASSWORD == (getEditText().getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        boolean isWebPassword = InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD == (getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);

        AppCompatCheckboxTriState viewUnencryptedToggle = view.findViewById(R.id.toggle_visibility);
        if (viewUnencryptedToggle != null) {
            if(isTextPassword || isNumberPassword || isWebPassword) {
                viewUnencryptedToggle.setOnCheckedChangeListener(new PasswordInputToggle(getEditText().getInputType()));
            } else {
                viewUnencryptedToggle.setVisibility(View.GONE);
            }
        }
    }

    private class PasswordInputToggle implements CompoundButton.OnCheckedChangeListener {
        int defaultInputType;

        public PasswordInputToggle(int defaultInputType) {
            this.defaultInputType = defaultInputType;
        }

        public int getVisibleInputType(int currentInputType) {
            return defaultInputType | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        }

        public int getDefaultInputType() {
            return defaultInputType;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked) {
                getEditText().setInputType(getVisibleInputType(getEditText().getInputType()));
            } else {
                getEditText().setInputType(getDefaultInputType());
            }
        }
    }

    private CharSequence getCurrentTextForDisplay() {
        return processTextOnRetrieveBeforeDisplay(getPreference(), currentText);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }

    @Override
    protected boolean needInputMethod() {
        // We want the input method to show, if possible, when dialog is displayed
        return true;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {

        if (positiveResult) {
            String value = editText.getText().toString();
            if (getPreference().callChangeListener(value)) {
                getPreference().setText(value);
            }
        }
    }
}
