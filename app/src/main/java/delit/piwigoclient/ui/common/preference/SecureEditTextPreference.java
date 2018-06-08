package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.util.SecurePrefsUtil;

/**
 * Created by gareth on 04/11/17.
 */

public class SecureEditTextPreference extends EditTextPreference implements SecurePreference<String> {
//    public SecureEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
//    }

    public SecureEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SecureEditTextPreference, defStyleAttr, 0);
        a.recycle();
    }

    public SecureEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecureEditTextPreference(Context context) {
        super(context);
    }

    /**
     * Attempts to persist a String to the {@link android.content.SharedPreferences}.
     * <p>
     * This will check if this Preference is persistent, get an editor from
     * the {@link PreferenceManager}, put in the string, and check if we should commit (and
     * commit if so).
     *
     * @param value The value to persist.
     * @return True if the Preference is persistent. (This is not whether the
     *         value was persisted, since we may not necessarily commit if there
     *         will be a batch commit later.)
     * @see #getPersistedString(String)
     */
    protected boolean persistString(String value) {
        return super.persistString(encrypt(value));
    }

    @Override
    public String encrypt(String value) {
        return SecurePrefsUtil.getInstance(getContext()).encryptValue(getKey(), value);
    }

    @Override
    public String decrypt(String encryptedVal, String defaultReturnValue) {
        if(defaultReturnValue == null) {
            if(encryptedVal == null) {
                return null;
            }
        } else if(defaultReturnValue.equals(encryptedVal)) {
            return defaultReturnValue;
        } else if(encryptedVal == null) {
            return defaultReturnValue;
        }
        return SecurePrefsUtil.getInstance(getContext()).decryptValue(getKey(), encryptedVal, defaultReturnValue);
    }

    /**
     * Attempts to get a persisted String from the {@link android.content.SharedPreferences}.
     * <p>
     * This will check if this Preference is persistent, get the SharedPreferences
     * from the {@link PreferenceManager}, and get the value.
     *
     * @param defaultReturnValue The default value to return if either the
     *            Preference is not persistent or the Preference is not in the
     *            shared preferences.
     * @return The value from the SharedPreferences or the default return
     *         value.
     * @see #persistString(String)
     */
    protected String getPersistedString(String defaultReturnValue) {
        String encryptedVal = super.getPersistedString(defaultReturnValue);
        return decrypt(encryptedVal, defaultReturnValue);
    }

}
