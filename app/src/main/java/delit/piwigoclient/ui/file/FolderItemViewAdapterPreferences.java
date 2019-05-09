package delit.piwigoclient.ui.file;

import android.os.Bundle;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

public class FolderItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {

    public final static int ALPHABETICAL = 1;
    public final static int LAST_MODIFIED_DATE = 2;


    private String initialFolder;
    private boolean allowFileSelection;
    private boolean allowFolderSelection;
    private boolean multiSelectAllowed;
    private boolean showFolderContents;
    private ArrayList<String> visibleFileTypes;
    private int fileSortOrder = ALPHABETICAL;
    private ArrayList<String> initialSelection;
    private int columnsOfFolders = 3;
    private int columnsOfFiles = 2;
    private boolean showFilenames = true;

    protected FolderItemViewAdapterPreferences() {
    }

    public FolderItemViewAdapterPreferences(boolean allowFileSelection, boolean allowFolderSelection, boolean multiSelectAllowed) {
        this.allowFileSelection = allowFileSelection;
        this.allowFolderSelection = allowFolderSelection;
        this.multiSelectAllowed = multiSelectAllowed;
    }

    public FolderItemViewAdapterPreferences withInitialFolder(@NonNull String initialFolder) {
        this.initialFolder = initialFolder;
        return this;
    }

    public FolderItemViewAdapterPreferences withColumnsOfFiles(int columnsOfFiles) {
        this.columnsOfFiles = columnsOfFiles;
        return this;
    }

    public FolderItemViewAdapterPreferences withColumnsOfFolders(int columnsOfFolders) {
        this.columnsOfFolders = columnsOfFolders;
        return this;
    }

    public FolderItemViewAdapterPreferences withVisibleContent(int fileSortOrder) {
        return withVisibleContent(null, fileSortOrder);
    }

    public FolderItemViewAdapterPreferences withShowFilenames(boolean showFilenames) {
        this.showFilenames = showFilenames;
        return this;
    }

    public FolderItemViewAdapterPreferences withVisibleContent(@Nullable ArrayList<String> visibleFileTypes, int fileSortOrder) {
        if (visibleFileTypes != null) {
            this.visibleFileTypes = new ArrayList<>(visibleFileTypes);
            for (int i = 0; i < visibleFileTypes.size(); i++) {
                visibleFileTypes.set(i, visibleFileTypes.get(i).toLowerCase());
            }
        }
        this.showFolderContents = true;
        this.fileSortOrder = fileSortOrder;
        return this;
    }

    public Bundle storeToBundle(Bundle parent) {
        Bundle b = new Bundle();
        b.putBoolean("allowFileSelection", allowFileSelection);
        b.putBoolean("allowFolderSelection", allowFolderSelection);
        b.putBoolean("multiSelectAllowed", multiSelectAllowed);
        b.putBoolean("showFolderContents", showFolderContents);
        b.putInt("fileSortOrder", fileSortOrder);
        b.putInt("columnsOfFolders", columnsOfFolders);
        b.putInt("columnsOfFiles", columnsOfFiles);
        b.putBoolean("showFilenames", showFilenames);
        b.putStringArrayList("visibleFileTypes", visibleFileTypes);
        b.putString("initialFolder", initialFolder);
        b.putStringArrayList("initialSelection", initialSelection);
        parent.putBundle("FolderItemViewAdapterPreferences", b);
        super.storeToBundle(b);
        return parent;
    }

    public FolderItemViewAdapterPreferences loadFromBundle(Bundle parent) {
        Bundle b = parent.getBundle("FolderItemViewAdapterPreferences");
        allowFileSelection = b.getBoolean("allowFileSelection");
        allowFolderSelection = b.getBoolean("allowFolderSelection");
        multiSelectAllowed = b.getBoolean("multiSelectAllowed");
        showFolderContents = b.getBoolean("showFolderContents");
        fileSortOrder = b.getInt("fileSortOrder");
        columnsOfFolders = b.getInt("columnsOfFolders");
        columnsOfFiles = b.getInt("columnsOfFiles");
        showFilenames = b.getBoolean("showFilenames");
        visibleFileTypes = b.getStringArrayList("visibleFileTypes");
        initialFolder = b.getString("initialFolder");
        initialSelection = b.getStringArrayList("initialSelection");
        super.loadFromBundle(b);
        return this;
    }

    public String getInitialFolder() {
        return initialFolder;
    }

    public File getInitialFolderAsFile() {
        return initialFolder != null ? new File(initialFolder) : null;
    }

    public boolean isAllowFolderSelection() {
        return allowFolderSelection;
    }

    public boolean isAllowFileSelection() {
        return allowFileSelection;
    }

    public int getFileSortOrder() {
        return fileSortOrder;
    }

    public CustomFileFilter getFileFilter() {
        return new CustomFileFilter();
    }

    public ArrayList<String> getVisibleFileTypes() {
        return visibleFileTypes;
    }

    public class CustomFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return !showFolderContents || (pathname.isDirectory() || filenameMatches(pathname));
        }

        private boolean filenameMatches(File pathname) {
            if (visibleFileTypes == null) {
                return true;
            }
            for (String fileExt : visibleFileTypes) {
                if (pathname.getName().toLowerCase().endsWith(fileExt)) {
                    return true;
                }
            }
            return false;
        }
    }

    public ArrayList<String> getInitialSelection() {
        return initialSelection;
    }

    public void setInitialSelection(ArrayList<String> initialSelection) {
        this.initialSelection = initialSelection;
    }

    public int getColumnsOfFiles() {
        return columnsOfFiles;
    }

    public int getColumnsOfFolders() {
        return columnsOfFolders;
    }

    public boolean isShowFilenames() {
        return showFilenames;
    }
}