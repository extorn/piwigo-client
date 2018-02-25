package delit.piwigoclient.util.security;

import java.io.File;
import java.io.Serializable;

public class X509LoadOperation implements Serializable {
    private File file;

    public X509LoadOperation(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}