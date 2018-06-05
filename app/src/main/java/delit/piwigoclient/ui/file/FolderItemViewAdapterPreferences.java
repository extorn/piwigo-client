package delit.piwigoclient.ui.file;

import android.os.Bundle;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

public class FolderItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
    private String initialFolder;
    private boolean selectFiles;
    private boolean selectFolders;
    private boolean showOnlyAcceptableFiles;
    private ArrayList<String> acceptableFileExts;

    protected FolderItemViewAdapterPreferences(){}

    public FolderItemViewAdapterPreferences(String initialFolder){
        this.initialFolder = initialFolder;
    }

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
        b.putString("initialFolder", initialFolder);
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
        initialFolder = b.getString("initialFolder");
        super.loadFromBundle(b);
        return this;
    }

    public String getInitialFolder() {
        return initialFolder;
    }

    public File getInitialFolderAsFile() {
        return initialFolder != null ? new File(initialFolder) : null;
    }

    public boolean isSelectFiles() {
        return selectFiles;
    }

    public boolean isSelectFolders() {
        return selectFolders;
    }

    public FileFilter getFileFilter() {
        return new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return !showOnlyAcceptableFiles || (pathname.isDirectory() || filenameMatches(pathname));
            }

            private boolean filenameMatches(File pathname) {
                if(acceptableFileExts == null) {
                    return true;
                }
                for(String fileExt : acceptableFileExts) {
                    if(pathname.getName().endsWith(fileExt)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}