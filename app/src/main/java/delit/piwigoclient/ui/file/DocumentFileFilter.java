package delit.piwigoclient.ui.file;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

public interface DocumentFileFilter {
    static List<DocumentFile> filterDocumentFiles(DocumentFile[] files, DocumentFileFilter filter) {
        if(files == null) {
            return new ArrayList<>(0);
        }
        List<DocumentFile> acceptableFiles = new ArrayList<>(files.length);
        for (DocumentFile file : files) {
            if (filter.accept(file)) {
                acceptableFiles.add(file);
            }
        }
        return acceptableFiles;
    }

    boolean accept(DocumentFile f);
}

