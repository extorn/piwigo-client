package delit.libs.util.security;

import android.os.Parcel;
import android.os.Parcelable;

import java.security.Key;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.libs.ui.util.ParcelUtils;

public class KeystoreLoadOperationResult implements Parcelable {

    private final KeystoreLoadOperation loadOperation;
    private final Map<Key, Certificate[]> keystoreContent = new HashMap<>();
    private final List<SecurityOperationException> exceptionList  = new ArrayList<>();

    public KeystoreLoadOperationResult(KeystoreLoadOperation loadOperation) {
        this.loadOperation = loadOperation;
    }

    protected KeystoreLoadOperationResult(Parcel in) {
        loadOperation = in.readParcelable(KeystoreLoadOperation.class.getClassLoader());
        ParcelUtils.readMap(in, keystoreContent, Certificate.class.getClassLoader());
        in.readList(exceptionList, SecurityOperationException.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(loadOperation, flags);
        ParcelUtils.writeMap(dest, keystoreContent);
        dest.writeList(exceptionList);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<KeystoreLoadOperationResult> CREATOR = new Creator<KeystoreLoadOperationResult>() {
        @Override
        public KeystoreLoadOperationResult createFromParcel(Parcel in) {
            return new KeystoreLoadOperationResult(in);
        }

        @Override
        public KeystoreLoadOperationResult[] newArray(int size) {
            return new KeystoreLoadOperationResult[size];
        }
    };

    public KeystoreLoadOperation getLoadOperation() {
        return loadOperation;
    }

    public Map<Key, Certificate[]> getKeystoreContent() {
        return keystoreContent;
    }

    public void addException(SecurityOperationException e) {
        exceptionList.add(e);
    }

    public List<SecurityOperationException> getExceptionList() {
        return exceptionList;
    }
}