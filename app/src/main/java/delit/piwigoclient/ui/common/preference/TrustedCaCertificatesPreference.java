package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.util.AttributeSet;

import java.security.KeyStore;

import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 15/07/17.
 */

public class TrustedCaCertificatesPreference extends KeyStorePreference {
    private static final String TAG = "trustedCaCertPref";

    public TrustedCaCertificatesPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr,defStyleRes);
    }

    public TrustedCaCertificatesPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TrustedCaCertificatesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrustedCaCertificatesPreference(Context context) {
        super(context);
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