package delit.piwigoclient.ui.file;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.MimeTypeFilter;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.database.UriPermissionUse;

public class FolderItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<FolderItemViewAdapterPreferences> {

    public final static int ALPHABETICAL = 1;
    public final static int LAST_MODIFIED_DATE = 2;


    private Uri initialFolder;
    private boolean allowFileSelection;
    private boolean allowFolderSelection;
    private boolean multiSelectAllowed;
    private boolean showFolderContents;
    private SortedSet<String> visibleFileTypes;
    private int fileSortOrder = ALPHABETICAL;
    private SortedSet<Uri> initialSelection;
    private int columnsOfFolders = 3;
    private int columnsOfFiles = 2;
    private boolean showFilenames = true;
    private SortedSet<String> visibleMimeTypes;
    private @NonNull String selectedUriPermissionsForConsumerId = UriPermissionUse.TRANSIENT;
    private String selectedUriPermissionConsumerPurpose;
    private int selectedUriPermissionFlags;

    protected FolderItemViewAdapterPreferences() {
    }

    public FolderItemViewAdapterPreferences(boolean allowFileSelection, boolean allowFolderSelection, boolean multiSelectAllowed) {
        this.allowFileSelection = allowFileSelection;
        this.allowFolderSelection = allowFolderSelection;
        this.multiSelectAllowed = multiSelectAllowed;
    }

    public FolderItemViewAdapterPreferences withInitialFolder(@Nullable Uri initialFolder) {
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
        } else {
            this.visibleFileTypes = new TreeSet<>();
        }
        this.showFolderContents = true;
        this.fileSortOrder = fileSortOrder;
        return this;
    }

    @Override
    protected String getBundleName() {
        return "FolderItemViewAdapterPreferences";
    }

    @Override
    protected String writeContentToBundle(Bundle b) {
        super.writeContentToBundle(b);
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
        b.putParcelable("initialFolder", initialFolder);
        BundleUtils.putSortedSet(b, "initialSelection", initialSelection);
        b.putString("selectedUriPermissionsForConsumerId", selectedUriPermissionsForConsumerId);
        b.putString("selectedUriPermissionConsumerPurpose", selectedUriPermissionConsumerPurpose);
        b.putInt("selectedUriPermissionFlags", selectedUriPermissionFlags);
        return getBundleName();
    }

    @Override
    protected void readContentFromBundle(Bundle b) {
        super.readContentFromBundle(b);
        allowFileSelection = b.getBoolean("allowFileSelection");
        allowFolderSelection = b.getBoolean("allowFolderSelection");
        multiSelectAllowed = b.getBoolean("multiSelectAllowed");
        showFolderContents = b.getBoolean("showFolderContents");
        fileSortOrder = b.getInt("fileSortOrder");
        columnsOfFolders = b.getInt("columnsOfFolders");
        columnsOfFiles = b.getInt("columnsOfFiles");
        showFilenames = b.getBoolean("showFilenames");
        visibleFileTypes = BundleUtils.getStringSet(b, "visibleFileTypes", new TreeSet<>());
        if (visibleFileTypes != null && visibleFileTypes.isEmpty()) {
            visibleFileTypes = null;
        }
        visibleMimeTypes = BundleUtils.getStringSet(b, "visibleMimeTypes", new TreeSet<>());
        initialFolder = b.getParcelable("initialFolder");
        initialSelection = BundleUtils.readSortedSet(b, "initialSelection", new TreeSet<>());
        selectedUriPermissionsForConsumerId = Objects.requireNonNull(b.getString("selectedUriPermissionsForConsumerId"));
        selectedUriPermissionConsumerPurpose = b.getString("selectedUriPermissionConsumerPurpose");
        selectedUriPermissionFlags = b.getInt("selectedUriPermissionFlags");
    }


    public @Nullable Uri getInitialFolder() {
        return initialFolder;
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

    public SortedSet<String> getVisibleFileTypes() {
        return new TreeSet<>(visibleFileTypes);
    }

    public SortedSet<String> getVisibleMimeTypes() {
        return new TreeSet<>(visibleMimeTypes);
    }

    public void withVisibleMimeTypes(Set<String> visibleMimeTypes) {
        if (visibleMimeTypes != null) {
            this.visibleMimeTypes = new TreeSet<>(visibleMimeTypes);
        }
    }

    public SortedSet<String> getVisibleFileTypesForMimes(@NonNull Map<String,String> extToMimeMap) {
        SortedSet<String> wantedExts = new TreeSet<>();
        if (visibleMimeTypes != null) {
            for(Map.Entry<String,String> extToMime : extToMimeMap.entrySet()) {
                if(null != MimeTypeFilter.matches(extToMime.getValue(), CollectionUtils.asStringArray(visibleMimeTypes))) {
                    wantedExts.add(extToMime.getKey());
                }
            }
        }
        return wantedExts;
    }

    public SortedSet<Uri> getInitialSelection() {
        return initialSelection;
    }

    public void setInitialSelection(Set<Uri> initialSelection) {
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

    public void withSelectedUriPermissionsForConsumerId(@NonNull String selectedUriPermissionsForConsumerId) {
        this.selectedUriPermissionsForConsumerId = selectedUriPermissionsForConsumerId;
    }

    public @NonNull String getSelectedUriPermissionConsumerId() {
        return selectedUriPermissionsForConsumerId;
    }

    public boolean isShowFolderContent() {
        return showFolderContents;
    }

    public String getSelectedUriPermissionConsumerPurpose() {
        return selectedUriPermissionConsumerPurpose;
    }

    public void setSelectedUriPermissionConsumerPurpose(String selectedUriPermissionConsumerPurpose) {
        this.selectedUriPermissionConsumerPurpose = selectedUriPermissionConsumerPurpose;
    }

    public int getSelectedUriPermissionFlags() {
        return selectedUriPermissionFlags;
    }

    public void setSelectedUriPermissionFlags(int selectedUriPermissionFlags) {
        this.selectedUriPermissionFlags = selectedUriPermissionFlags;
    }

    public SortedSet<String> getFileTypesForVisibleMimes() {
        TreeSet<String> fileExts = new TreeSet<>();
        if(visibleMimeTypes == null) {
            return fileExts;
        }
        for(String mime : visibleMimeTypes) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if(ext != null) {
                fileExts.add(ext);
            }
        }
        return fileExts;
    }
}