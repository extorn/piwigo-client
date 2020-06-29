package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import delit.libs.ui.view.preference.CustomEditTextPreference;
import delit.libs.ui.view.preference.SecurePreference;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;

/**
 * Created by gareth on 04/11/17.
 */
//TODO these need to move to using byte arrays for security. Currently have to use strings as extend from EditTextPref.
public class SecureEditTextPreference extends CustomEditTextPreference implements SecurePreference<CharSequence> {
    public SecureEditTextPreference(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        initPreference(context, attrs);
    }

    public SecureEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initPreference(context, attrs);
    }

    public SecureEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public SecureEditTextPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    private void initPreference(Context context, AttributeSet attrs) {
        setDialogLayoutResource(R.layout.preference_custom_edit_text);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SecureEditTextPreference, 0, 0);
        a.recycle();
    }

    @Override
    public CharSequence encrypt(CharSequence value) {
        if(value == null) {
            return null;
        }
        return SecurePrefsUtil.getInstance(getContext(), BuildConfig.APPLICATION_ID).encryptValue(getKey(), value.toString());
    }

    @Override
    public String decrypt(CharSequence encryptedVal, CharSequence defaultReturnValue) {
        String defaultReturnValueStr = defaultReturnValue != null ? defaultReturnValue.toString() : null;

        if (encryptedVal == null || encryptedVal.equals(defaultReturnValue)) {
            return defaultReturnValueStr; // no way the value is encrypted if it is null or matches the default value (logically unencrypted)
        }
        // should always have a valid encrypted value at this point even if empty
        String encryptedStr = encryptedVal.toString();


        if (getPreferenceDataStore() != null) {
            return SecurePrefsUtil.getInstance(getContext(), BuildConfig.APPLICATION_ID).decryptValue(getPreferenceDataStore(), getKey(), encryptedStr, defaultReturnValueStr, getContext());
        } else {
            return SecurePrefsUtil.getInstance(getContext(), BuildConfig.APPLICATION_ID).decryptValue(getContext(), getPreferenceManager().getSharedPreferences(), getKey(), encryptedStr, defaultReturnValueStr);
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // don't call super. We need to intercept the call to decrypt the persisted value first
        setText(decrypt(getPersistedString((String) defaultValue), (String)defaultValue));
    }

    @Override
    public void setText(String text) {
        CharSequence encryptedText = encrypt(text);
        super.setText(encryptedText != null ? encryptedText.toString() : null);
    }

    @Override
    public String getText() {
        return decrypt(super.getText(), null);
    }
}
