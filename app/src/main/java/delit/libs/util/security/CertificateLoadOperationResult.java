package delit.libs.util.security;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertificateLoadOperationResult implements Serializable {
    private static final long serialVersionUID = -7558414097661292429L;
    private final X509LoadOperation loadOperation;
    private final List<X509Certificate> certs;
    private CertificateLoadException exception;

    public CertificateLoadOperationResult(X509LoadOperation loadOperation) {
        this.loadOperation = loadOperation;
        this.certs = new ArrayList<>();
    }

    public CertificateLoadException getException() {
        return exception;
    }

    public void setException(CertificateLoadException exception) {
        this.exception = exception;
    }

    public List<X509Certificate> getCerts() {
        return certs;
    }

    public X509LoadOperation getLoadOperation() {
        return loadOperation;
    }
}