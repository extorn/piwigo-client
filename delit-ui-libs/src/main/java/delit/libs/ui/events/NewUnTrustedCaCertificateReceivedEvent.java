package delit.libs.ui.events;

import java.security.cert.X509Certificate;
import java.util.HashMap;

/**
 * Created by gareth on 17/10/17.
 */

public class NewUnTrustedCaCertificateReceivedEvent extends SingleUseEvent {
    private final HashMap<String, X509Certificate> untrustedCerts;
    private final X509Certificate endCertificate;

    public NewUnTrustedCaCertificateReceivedEvent(X509Certificate endCertificate, HashMap<String, X509Certificate> untrustedCerts) {
        this.endCertificate = endCertificate;
        this.untrustedCerts = untrustedCerts;
    }

    public X509Certificate getEndCertificate() {
        return endCertificate;
    }

    public HashMap<String, X509Certificate> getUntrustedCerts() {
        return untrustedCerts;
    }
}
