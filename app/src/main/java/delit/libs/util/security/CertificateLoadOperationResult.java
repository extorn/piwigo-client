package delit.libs.util.security;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.util.ParcelUtils;

public class CertificateLoadOperationResult implements Parcelable {
    private final X509LoadOperation loadOperation;
    private final List<X509Certificate> certs = new ArrayList<>();
    private CertificateLoadException exception;

    public CertificateLoadOperationResult(X509LoadOperation loadOperation) {
        this.loadOperation = loadOperation;
    }

    protected CertificateLoadOperationResult(Parcel in) {
        loadOperation = in.readParcelable(X509LoadOperation.class.getClassLoader());
        in.readList(certs, X509Certificate.class.getClassLoader());
        exception = (CertificateLoadException) in.readSerializable();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(loadOperation, flags);
        dest.writeList(certs);
        dest.writeSerializable(exception);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CertificateLoadOperationResult> CREATOR = new Creator<CertificateLoadOperationResult>() {
        @Override
        public CertificateLoadOperationResult createFromParcel(Parcel in) {
            return new CertificateLoadOperationResult(in);
        }

        @Override
        public CertificateLoadOperationResult[] newArray(int size) {
            return new CertificateLoadOperationResult[size];
        }
    };

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