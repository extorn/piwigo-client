package delit.piwigoclient.ui.events.trackable;

import java.util.ArrayList;

/**
 * Created by gareth on 13/06/17.
 */

public class FolderSelectionNeededEvent extends TrackableRequestEvent {

    private boolean multiSelectAllowed;
    private String initialFolder;
    private boolean showFolderContents;

    public FolderSelectionNeededEvent() {
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

    public void setShowFolderContents(boolean showFolderContents) {
        this.showFolderContents = showFolderContents;
    }

    public void setMultiSelectAllowed(boolean multiSelectAllowed) {
        this.multiSelectAllowed = multiSelectAllowed;
    }

    public void setInitialFolder(String initialFolder) {
        this.initialFolder = initialFolder;
    }
}
