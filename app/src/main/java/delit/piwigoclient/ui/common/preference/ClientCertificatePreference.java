package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.util.AttributeSet;

import java.security.KeyStore;

import delit.piwigoclient.R;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 15/07/17.
 */

public class ClientCertificatePreference extends KeyStorePreference {
    private static final String TAG = "clientCertPref";

    public ClientCertificatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setJustKeysWanted(true);
    }

    public ClientCertificatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ClientCertificatePreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.dialogPreferenceStyle);
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