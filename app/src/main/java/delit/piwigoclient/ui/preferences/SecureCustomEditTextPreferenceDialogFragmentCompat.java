package delit.piwigoclient.ui.preferences;

import android.os.Bundle;

import delit.libs.ui.view.preference.CustomEditTextPreferenceDialogFragmentCompat;

public class SecureCustomEditTextPreferenceDialogFragmentCompat<T extends SecureEditTextPreference> extends CustomEditTextPreferenceDialogFragmentCompat<T> {

    public static CustomEditTextPreferenceDialogFragmentCompat newInstance(String key) {
        final SecureCustomEditTextPreferenceDialogFragmentCompat
                fragment = new SecureCustomEditTextPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected CharSequence processTextOnRetrieveBeforeDisplay(T preference, CharSequence text) {
        return text != null ? preference.decrypt(text, null) : null; // default is never used - text is always a value
    }

    @Override
    protected CharSequence processTextOnRetrieveFromPreference(T preference, String text) {
        return text != null ? preference.encrypt(text) : null;
    }
}
