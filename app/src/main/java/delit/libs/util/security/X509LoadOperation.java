package delit.libs.util.security;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.Serializable;

public class X509LoadOperation implements Serializable {
    private static final long serialVersionUID = 6303313796725599161L;
    private final DocumentFile file;

    public X509LoadOperation(DocumentFile file) {
        this.file = file;
    }

    public DocumentFile getFile() {
        return file;
    }
}