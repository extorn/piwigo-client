package delit.piwigoclient.ui.file;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

import delit.libs.util.IOUtils;

public class LegacyFileFilter implements FileFilter {
    private boolean showFolderContents;
    private Set<String> visibleFileTypes;

    public LegacyFileFilter(boolean showFolderContents, Set<String> visibleFileTypes) {
        this.showFolderContents = showFolderContents;
        this.visibleFileTypes = visibleFileTypes;
    }

    @Override
        public boolean accept(File pathname) {
            return !showFolderContents || (pathname.isDirectory() || (pathname.isFile()  && filenameMatches(pathname)));
        }

        private boolean filenameMatches(File pathname) {
            if (visibleFileTypes == null) {
                return true;
            }
            String thisExt = IOUtils.getFileExt(pathname.getName());
            if(thisExt == null) {
                return false;
            }
            return visibleFileTypes.contains(thisExt);
        }
    }