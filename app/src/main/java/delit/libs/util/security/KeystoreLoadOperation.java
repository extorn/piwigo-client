package delit.libs.util.security;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeystoreLoadOperation extends X509LoadOperation {
    private final Map<String, char[]> aliasPassMapp;
    private List<String> aliasesToLoad;
    private char[] keystorePass;

    public KeystoreLoadOperation(DocumentFile file) {
        super(file);
        keystorePass = new char[0];
        aliasPassMapp = new HashMap<>();
        aliasesToLoad = null;
    }

    public static KeystoreLoadOperation from(X509LoadOperation loadOp) {
        if (loadOp instanceof KeystoreLoadOperation) {
            return (KeystoreLoadOperation) loadOp;
        }
        return new KeystoreLoadOperation(loadOp.getFile());
    }

    public List<String> getAliasesToLoad() {
        return aliasesToLoad;
    }

    public void addAliasToLoad(String alias) {
        if (aliasesToLoad == null) {
            aliasesToLoad = new ArrayList<>();
        }
        aliasesToLoad.add(alias);
    }

    public char[] getKeystorePass() {
        return keystorePass;
    }

    public void setKeystorePass(char[] keystorePass) {
        this.keystorePass = keystorePass;
    }

    public Map<String, char[]> getAliasPassMapp() {
        return aliasPassMapp;
    }

    public void removeAliasToLoad(String alias) {
        if (aliasesToLoad != null) {
            aliasesToLoad.remove(alias);
        }
    }
}