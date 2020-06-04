package delit.piwigoclient.ui.file;

import androidx.documentfile.provider.DocumentFile;

import java.util.Set;

import delit.libs.util.IOUtils;

public class SimpleDocumentFileFilter implements DocumentFileFilter {

    private Set<String> acceptableFileExts = null;
    private long maxSize = -1;

    public SimpleDocumentFileFilter withFileExtIn(Set<String> fileExts) {
        this.acceptableFileExts = fileExts;
        return this;
    }

    public SimpleDocumentFileFilter withMaxSizeBytes(long maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    @Override
    public boolean accept(DocumentFile f) {
        if(acceptableFileExts != null) {
            if(!acceptableFileExts.contains(IOUtils.getFileExt(f))) {
                return nonAcceptOverride(f);
            }
        }
        if(maxSize >= 0) {
            if(maxSize < f.length()) {
                return nonAcceptOverride(f);
            }
        }
        return acceptOverride(f);
    }

    /**
     * override this to provide custom accept behavior on what would usually fail
     *
     * @param f
     * @return false (don't accept file)
     */
    protected boolean nonAcceptOverride(DocumentFile f) {
        return false;
    }

    /**
     * override this to provide custom accept behavior on what would usually fail
     *
     * @param f
     * @return true (accept file)
     */
    protected boolean acceptOverride(DocumentFile f) {
        return true;
    }
}
