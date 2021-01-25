package delit.libs.util;

import android.webkit.MimeTypeMap;

import androidx.core.content.MimeTypeFilter;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;

public class CustomFileFilter implements FileFilter {

    private int maxSizeMb;
    private Collection<String> acceptableFileExtList;
    private boolean allowVideosToExceedLimit;

    public CustomFileFilter withMaxSizeMb(int maxSizeMb, boolean allowVideosToExceedLimit) {
        this.maxSizeMb = maxSizeMb;
        this.allowVideosToExceedLimit = allowVideosToExceedLimit;
        return this;
    }

    public CustomFileFilter withFileExtIn(Collection<String> acceptableFileExtList) {
        this.acceptableFileExtList = acceptableFileExtList;
        return this;
    }

    @Override
    public boolean accept(File pathname) {
        if (!pathname.isFile()) {
            return false;
        }
        if (!isFilenameMatch(pathname.getName())) {
            return false;
        }
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String mimeType = map.getMimeTypeFromExtension(IOUtils.getFileExt(pathname.getName()));
        if (mimeType == null || MimeTypeFilter.matches(mimeType.toLowerCase(), "video/*") || !allowVideosToExceedLimit) {
            return isFilesizeMatch(pathname);
        }
        return true;
    }

    private boolean isFilesizeMatch(File pathname) {
        long sizeBytes = pathname.length();
        long sizeMb = Math.round((double) sizeBytes / 1024 / 1024);
        return sizeMb < maxSizeMb;
    }

    private boolean isFilenameMatch(String filename) {
        boolean filenameMatches = false;
        if (acceptableFileExtList != null) {
            for (String acceptableExt : acceptableFileExtList) {
                if (filename.endsWith(acceptableExt)) {
                    filenameMatches = true;
                    break;
                }
            }
        }
        return filenameMatches;
    }
}