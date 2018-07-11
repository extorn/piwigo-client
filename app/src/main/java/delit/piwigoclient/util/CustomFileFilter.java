package delit.piwigoclient.util;

import java.io.File;
import java.io.FileFilter;
import java.util.List;

public class CustomFileFilter implements FileFilter {

        private int maxSizeMb;
        private List<String> acceptableFileExtList;

        public CustomFileFilter withMaxSizeMb(int maxSizeMb) {
            this.maxSizeMb = maxSizeMb;
            return this;
        }

        public CustomFileFilter withFileExtIn(List<String> acceptableFileExtList) {
            this.acceptableFileExtList = acceptableFileExtList;
            return this;
        }

        @Override
        public boolean accept(File pathname) {
            if(!pathname.isFile()) {
                return false;
            }
            if(!isFilenameMatch(pathname.getName())) {
                return false;
            }
            return isFilesizeMatch(pathname);
        }

        private boolean isFilesizeMatch(File pathname) {
            long sizeBytes = pathname.length();
            long sizeMb = Math.round((double)sizeBytes / 1024 / 1024);
            return sizeMb < maxSizeMb;
        }

        private boolean isFilenameMatch(String filename) {
            boolean filenameMatches = false;
            for(String acceptableExt : acceptableFileExtList) {
                if (filename.endsWith(acceptableExt)) {
                    filenameMatches = true;
                    break;
                }
            }
            return filenameMatches;
        }
    }