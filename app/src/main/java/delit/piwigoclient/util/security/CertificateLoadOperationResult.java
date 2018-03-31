package delit.piwigoclient.util.security;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertificateLoadOperationResult implements Serializable {
    private CertificateLoadException exception;
    private X509LoadOperation loadOperation;
    private List<X509Certificate> certs;

    public CertificateLoadOperationResult(X509LoadOperation loadOperation) {
        this.loadOperation = loadOperation;
        this.certs = new ArrayList<>();
    }

    public void setException(CertificateLoadException exception) {
        this.exception = exception;
    }

    public CertificateLoadException getException() {
        return exception;
    }

    public List<X509Certificate> getCerts() {
        return certs;
    }

    public X509LoadOperation getLoadOperation() {
        return loadOperation;
    }
}