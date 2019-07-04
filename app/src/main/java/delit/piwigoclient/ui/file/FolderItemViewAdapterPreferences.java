package delit.piwigoclient.ui.file;

import android.os.Bundle;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.IOUtils;

public class FolderItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {

    public final static int ALPHABETICAL = 1;
    public final static int LAST_MODIFIED_DATE = 2;


    private String initialFolder;
    private boolean allowFileSelection;
    private boolean allowFolderSelection;
    private boolean multiSelectAllowed;
    private boolean showFolderContents;
    private SortedSet<String> visibleFileTypes;
    private int fileSortOrder = ALPHABETICAL;
    private SortedSet<String> initialSelection;
    private int columnsOfFolders = 3;
    private int columnsOfFiles = 2;
    private boolean showFilenames = true;
    private SortedSet<String> visibleMimeTypes;

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

    public FolderItemViewAdapterPreferences withVisibleContent(@Nullable Set<String> visibleFileTypes, int fileSortOrder) {
        if (visibleFileTypes != null) {
            this.visibleFileTypes = new TreeSet<>();
            for (String inputVal : visibleFileTypes) {
                this.visibleFileTypes.add(inputVal.toLowerCase());
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
        BundleUtils.putStringSet(b, "visibleFileTypes", visibleFileTypes);
        BundleUtils.putStringSet(b, "visibleMimeTypes", visibleMimeTypes);
        b.putString("initialFolder", initialFolder);
        BundleUtils.putStringSet(b, "initialSelection", initialSelection);
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
        visibleFileTypes = BundleUtils.getStringSet(b, "visibleFileTypes", new TreeSet<String>());
        visibleMimeTypes = BundleUtils.getStringSet(b, "visibleMimeTypes", new TreeSet<String>());
        initialFolder = b.getString("initialFolder");
        initialSelection = BundleUtils.getStringSet(b, "initialSelection", new TreeSet<String>());
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

    public SortedSet<String> getVisibleFileTypes() {
        return visibleFileTypes;
    }

    public SortedSet<String> getVisibleMimeTypes() {
        return visibleMimeTypes;
    }

    public void withVisibleMimeTypes(Set<String> visibleMimeTypes) {
        if (visibleMimeTypes != null) {
            this.visibleMimeTypes = new TreeSet<>(visibleMimeTypes);
        }
    }

    public SortedSet<String> getVisibleFileTypesForMimes(File folder) {
        SortedSet<String> processedExts = new TreeSet<>();
        SortedSet<String> wantedExts = new TreeSet<>();
        if (visibleMimeTypes != null) {
            for (File f : folder.listFiles()) {
                String ext = IOUtils.getFileExt(f.getName()).toLowerCase();
                if (processedExts.add(ext)) { // if we didn't check this ext already
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    if (mimeType != null) { // if null then it can't be a match!
                        for (String visibleMimeType : visibleMimeTypes) {
                            if ((visibleMimeType.endsWith("/") || visibleMimeType.endsWith("/*") || !visibleMimeType.contains("/")) && mimeType.startsWith(visibleMimeType)) {
                                wantedExts.add(ext);
                                break;
                            } else if (mimeType.equals(visibleMimeType)) {
                                wantedExts.add(ext);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return wantedExts;
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

    public SortedSet<String> getInitialSelection() {
        return initialSelection;
    }

    public void setInitialSelection(Set<String> initialSelection) {
        if (initialSelection != null) {
            this.initialSelection = new TreeSet<>(initialSelection);
        }
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