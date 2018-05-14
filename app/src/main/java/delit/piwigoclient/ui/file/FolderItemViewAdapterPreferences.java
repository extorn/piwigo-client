package delit.piwigoclient.ui.file;

import android.os.Bundle;

import java.util.ArrayList;

import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

public class FolderItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
        private boolean selectFiles;
        private boolean selectFolders;
        private boolean showOnlyAcceptableFiles;
        private ArrayList<String> acceptableFileExts;

        protected FolderItemViewAdapterPreferences(){}

        public FolderItemViewAdapterPreferences forFolderSelection(boolean showFiles) {
            selectFiles = false;
            selectFolders = true;
            this.showOnlyAcceptableFiles = showFiles;
            return this;
        }

        public FolderItemViewAdapterPreferences forFileSelection(boolean hideNonSelectableFiles, ArrayList<String> acceptableFileExts ) {
            selectFiles = true;
            selectFolders = false;
            this.showOnlyAcceptableFiles = !hideNonSelectableFiles;
            this.acceptableFileExts = acceptableFileExts;
            return this;
        }

        public Bundle storeToBundle(Bundle parent) {
            Bundle b = new Bundle();
            b.putBoolean("selectFiles", selectFiles);
            b.putBoolean("selectFolder", selectFolders);
            b.putBoolean("showOnlyAcceptableFiles", showOnlyAcceptableFiles);
            b.putStringArrayList("acceptableFileExts", acceptableFileExts);
            parent.putBundle("FolderItemViewAdapterPreferences", b);
            super.storeToBundle(b);
            return parent;
        }

        public FolderItemViewAdapterPreferences loadFromBundle(Bundle parent) {
            Bundle b = parent.getBundle("FolderItemViewAdapterPreferences");
            selectFolders = b.getBoolean("selectFolder");
            selectFiles = b.getBoolean("selectFiles");
            showOnlyAcceptableFiles = b.getBoolean("showOnlyAcceptableFiles");
            acceptableFileExts = b.getStringArrayList("acceptableFileExts");
            super.loadFromBundle(b);
            return this;
        }

    public boolean isSelectFiles() {
        return selectFiles;
    }

    public boolean isSelectFolders() {
        return selectFolders;
    }
}