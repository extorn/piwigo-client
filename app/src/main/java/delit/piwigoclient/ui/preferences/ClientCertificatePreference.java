package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.util.AttributeSet;

import java.security.KeyStore;

import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 15/07/17.
 */

public class ClientCertificatePreference extends KeyStorePreference {
    private static final String TAG = "clientCertPref";

    public ClientCertificatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(TAG, context, attrs, defStyleAttr);
        setJustKeysWanted(true);
    }

    public ClientCertificatePreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public ClientCertificatePreference(Context context) {
        this(context, null);
    }

    @Override
    protected void saveKeystore(KeyStore keystore) {
        X509Utils.saveClientKeystore(getContext(), keystore);
    }

    @Override
    protected KeyStore loadKeystore() {
        return X509Utils.loadClientKeystore(getContext());
    }
}