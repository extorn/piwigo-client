package delit.piwigoclient.piwigoApi;

import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.X509TrustManager;

/**
 * Created by gareth on 04/08/17.
 */

public class CustomTrustManager implements X509TrustManager {
    private final X509TrustManager chainedTrustManager;
    private final String serverHostname;
    private boolean selfSignedCertificatesAllowed;
//    X509ExtendedTrustManager

    public CustomTrustManager(X509TrustManager chainedTrustManager, String serverHostname) {
        this.chainedTrustManager = chainedTrustManager;
        this.serverHostname = serverHostname;
    }

    public void setSelfSignedCertificatesAllowed(boolean selfSignedCertificatesAllowed) {
        this.selfSignedCertificatesAllowed = selfSignedCertificatesAllowed;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws
            CertificateException {
        chainedTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (selfSignedCertificatesAllowed && chain.length == 1) {
            testSelfSignedCertificate(chain[0]);
        } else {
            chainedTrustManager.checkServerTrusted(chain, authType);
        }
    }

    private void testSelfSignedCertificate(X509Certificate cert) throws CertificateException {
        Principal principal = cert.getSubjectDN();
        if (principal == null) {
            throw new CertificateException("No hostname (Subject - CN=....) specified in certificate");
        }
        String certHostname = principal.getName().toUpperCase().replaceAll("CN=", "");
        if (!certHostname.equals(serverHostname)) {
            throw new CertificateException("hostname on certificate (" + certHostname + ") does not match hostname of server (" + serverHostname + ")");
        }
        Date now = new Date();
        Date certValidTill = cert.getNotAfter();
        Date certValidFrom = cert.getNotBefore();
        if (certValidTill.before(now) || certValidFrom.after(now)) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH24:mm", Locale.UK);
            throw new CertificateException("Certificate is only valid between " + sdf.format(certValidFrom) + " - " + sdf.format(certValidTill));
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return chainedTrustManager.getAcceptedIssuers();
    }
}
