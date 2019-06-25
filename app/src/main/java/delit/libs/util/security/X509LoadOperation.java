package delit.libs.util.security;

import java.io.File;
import java.io.Serializable;

public class X509LoadOperation implements Serializable {
    private static final long serialVersionUID = 6303313796725599161L;
    private final File file;

    public X509LoadOperation(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}