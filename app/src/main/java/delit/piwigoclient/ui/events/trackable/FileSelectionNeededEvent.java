package delit.piwigoclient.ui.events.trackable;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 13/06/17.
 */

public class FileSelectionNeededEvent extends TrackableRequestEvent implements Parcelable {

    public final static int ALPHABETICAL = 1;
    public final static int LAST_MODIFIED_DATE = 2;
    private String selectedUriPermissionsForConsumerId;
    private boolean multiSelectAllowed;
    private Uri initialFolder;
    private boolean showFolderContents;
    private boolean allowFolderSelection;
    private boolean allowFileSelection;
    private Set<String> visibleFileTypes;
    private int fileSortOrder = ALPHABETICAL;
    private Set<Uri> initialSelection;
    private Set<String> visibleMimeTypes;

    public FileSelectionNeededEvent(Parcel in) {
        super(in);
        multiSelectAllowed = ParcelUtils.readBool(in);
        initialFolder = ParcelUtils.readParcelable(in, Uri.class);
        showFolderContents = ParcelUtils.readBool(in);
        allowFolderSelection = ParcelUtils.readBool(in);
        allowFileSelection = ParcelUtils.readBool(in);
        visibleFileTypes = ParcelUtils.readStringSet(in);
        fileSortOrder = in.readInt();
        initialSelection = ParcelUtils.readHashSet(in, Uri.class.getClassLoader());
        visibleMimeTypes = ParcelUtils.readStringSet(in);
        selectedUriPermissionsForConsumerId = ParcelUtils.readString(in);
    }

    public FileSelectionNeededEvent(boolean allowFileSelection, boolean allowFolderSelection, boolean multiSelectAllowed) {
        this.allowFileSelection = allowFileSelection;
        this.allowFolderSelection = allowFolderSelection;
        this.multiSelectAllowed = multiSelectAllowed;
    }

    public FileSelectionNeededEvent withInitialFolder(@NonNull Uri initialFolder) {
        this.initialFolder = initialFolder;
        return this;
    }

    public FileSelectionNeededEvent withVisibleContent(int fileSortOrder) {
        return withVisibleContent(null, fileSortOrder);
    }

    public FileSelectionNeededEvent withVisibleContent(@Nullable Set<String> visibleFileTypes, int fileSortOrder) {
        this.visibleFileTypes = visibleFileTypes;
        this.showFolderContents = true;
        this.fileSortOrder = fileSortOrder;
        return this;
    }

    public boolean isMultiSelectAllowed() {
        return multiSelectAllowed;
    }

    public Uri getInitialFolder() {
        return initialFolder;
    }

    public boolean isShowFolderContents() {
        return showFolderContents;
    }

    public boolean isAllowFileSelection() {
        return allowFileSelection;
    }

    public boolean isAllowFolderSelection() {
        return allowFolderSelection;
    }

    public Set<String> getVisibleFileTypes() {
        return visibleFileTypes;
    }

    public Set<String> getVisibleMimeTypes() {
        return visibleMimeTypes;
    }

    public String getSelectedUriPermissionsForConsumerId() {
        return selectedUriPermissionsForConsumerId;
    }

    public int getFileSortOrder() {
        return fileSortOrder;
    }

    public void withInitialSelection(Set<Uri> selection) {
        this.initialSelection = selection;
    }

    public Set<Uri> getInitialSelection() {
        return initialSelection;
    }

    public void setActionId(int actionId) {
        super.setActionId(actionId);
    }

    public void withVisibleMimeTypes(Set<String> visibleMimeTypes) {
        this.visibleMimeTypes = visibleMimeTypes;
    }

    public void withSelectedUriPermissionsForConsumerId(String selectedUriPermissionsForConsumerId) {
        this.selectedUriPermissionsForConsumerId = selectedUriPermissionsForConsumerId;
    }

    


    public static final Creator<FileSelectionNeededEvent> CREATOR = new Creator<FileSelectionNeededEvent>() {
        @Override
        public FileSelectionNeededEvent createFromParcel(Parcel in) {
            return new FileSelectionNeededEvent(in);
        }

        @Override
        public FileSelectionNeededEvent[] newArray(int size) {
            return new FileSelectionNeededEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeBool(dest, multiSelectAllowed);
        ParcelUtils.writeParcelable(dest, initialFolder);
        ParcelUtils.writeBool(dest, showFolderContents);
        ParcelUtils.writeBool(dest, allowFolderSelection);
        ParcelUtils.writeBool(dest, allowFileSelection);
        ParcelUtils.writeStringSet(dest, visibleFileTypes);
        dest.writeInt(fileSortOrder);
        ParcelUtils.writeSet(dest, initialSelection);
        ParcelUtils.writeStringSet(dest, visibleMimeTypes);
        dest.writeValue(selectedUriPermissionsForConsumerId);
    }
}
