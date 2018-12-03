package delit.piwigoclient.piwigoApi.http;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;

import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import cz.msebera.android.httpclient.conn.ssl.TrustSelfSignedStrategy;
import delit.piwigoclient.ui.events.NewUnTrustedCaCertificateReceivedEvent;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 17/10/17.
 */

public class UntrustedCaCertificateInterceptingTrustStrategy extends TrustSelfSignedStrategy implements Observer {

    private static Observable observable = new Observable();
    private final Set<String> certThumbprints;
    private final Set<String> preNotifiedCerts;

    public UntrustedCaCertificateInterceptingTrustStrategy(KeyStore trustedCAKeystore, Set<String> preNotifiedCertsSet) {
        certThumbprints = X509Utils.listAliasesInStore(trustedCAKeystore);
        preNotifiedCerts = new HashSet<>(preNotifiedCertsSet);
        observable.addObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        String thumbprint = (String) arg;
        synchronized (preNotifiedCerts) {
            preNotifiedCerts.add(thumbprint);
        }
    }

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            HashMap<String, X509Certificate> untrustedCerts = new HashMap<>();

            for (int i = 0; i < chain.length; i++) {
                if (i == 0 && chain.length > 1) {
                    // ignore the first certificate unless it is self signed.
                    continue;
                }
                String thumbprint = X509Utils.getCertificateThumbprint(chain[i]);
                if (!certThumbprints.contains(thumbprint)) {
                    // stop this certificate firing an event again.
                    synchronized (preNotifiedCerts) {
                        if (!preNotifiedCerts.contains(thumbprint)) {
                            preNotifiedCerts.add(thumbprint);
                            untrustedCerts.put(thumbprint, chain[i]);
                            observable.notifyObservers(thumbprint);
                        }
                    }
                }
            }
            if (untrustedCerts.size() > 0) {
                EventBus.getDefault().post(new NewUnTrustedCaCertificateReceivedEvent(chain[0], untrustedCerts));
            }
        } catch (NoSuchAlgorithmException e) {
            Crashlytics.logException(e);
            throw new CertificateException("Unable to calculate thumbprint for recieved certificate", e);
        }
        return false;
    }
}
