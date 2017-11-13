package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.util.AttributeSet;

import java.security.KeyStore;

import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 15/07/17.
 */

public class TrustedCaCertificatesPreference extends KeyStorePreference {
    private static final String TAG = "trustedCaCertPref";

    public TrustedCaCertificatesPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(TAG, context, attrs, defStyleAttr);
    }

    public TrustedCaCertificatesPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public TrustedCaCertificatesPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void saveKeystore(KeyStore keystore) {
        X509Utils.saveTrustedCaKeystore(getContext(), keystore);
    }

    @Override
    protected KeyStore loadKeystore() {
        return X509Utils.loadTrustedCaKeystore(getContext());
    }
}