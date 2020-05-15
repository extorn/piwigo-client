package delit.piwigoclient.ui.file;

import androidx.documentfile.provider.DocumentFile;

public interface DocumentFileFilter {
     boolean accept(DocumentFile f);
}

