package delit.piwigoclient.ui.events.trackable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Created by gareth on 13/06/17.
 */

public class FileSelectionNeededEvent extends TrackableRequestEvent {

    public final static int ALPHABETICAL = 1;
    public final static int LAST_MODIFIED_DATE = 2;
    private boolean multiSelectAllowed;
    private String initialFolder;
    private boolean showFolderContents;
    private boolean allowFolderSelection;
    private boolean allowFileSelection;
    private Set<String> visibleFileTypes;
    private int fileSortOrder = ALPHABETICAL;
    private Set<String> initialSelection;
    private Set<String> visibleMimeTypes;

    public FileSelectionNeededEvent(boolean allowFileSelection, boolean allowFolderSelection, boolean multiSelectAllowed) {
        this.allowFileSelection = allowFileSelection;
        this.allowFolderSelection = allowFolderSelection;
        this.multiSelectAllowed = multiSelectAllowed;
    }

    public FileSelectionNeededEvent withInitialFolder(@NonNull String initialFolder) {
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

    public String getInitialFolder() {
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

    public int getFileSortOrder() {
        return fileSortOrder;
    }

    public void withInitialSelection(Set<String> selection) {
        this.initialSelection = selection;
    }

    public Set<String> getInitialSelection() {
        return initialSelection;
    }

    public void setActionId(int actionId) {
        super.setActionId(actionId);
    }

    public void withVisibleMimeTypes(Set<String> visibleMimeTypes) {
        this.visibleMimeTypes = visibleMimeTypes;
    }
}
