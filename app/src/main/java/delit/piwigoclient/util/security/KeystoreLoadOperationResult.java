package delit.piwigoclient.util.security;

import java.io.Serializable;
import java.security.Key;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeystoreLoadOperationResult implements Serializable {
    private final KeystoreLoadOperation loadOperation;
    private final Map<Key, Certificate[]> keystoreContent;
    private final List<SecurityOperationException> exceptionList;

    public KeystoreLoadOperationResult(KeystoreLoadOperation loadOperation) {
        this.loadOperation = loadOperation;
        this.keystoreContent = new HashMap<>();
        this.exceptionList = new ArrayList<>();
    }

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