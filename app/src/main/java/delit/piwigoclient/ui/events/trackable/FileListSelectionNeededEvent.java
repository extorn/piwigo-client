package delit.piwigoclient.ui.events.trackable;

import java.util.ArrayList;

/**
 * Created by gareth on 13/06/17.
 */

public class FileListSelectionNeededEvent extends TrackableRequestEvent {

    private ArrayList<String> allowedFileTypes;
    private boolean useAlphabeticalSortOrder;

    public FileListSelectionNeededEvent() {
    }

    public void setAllowedFileTypes(ArrayList<String> allowedFileTypes) {
        this.allowedFileTypes = allowedFileTypes;
    }

    public void setUseAlphabeticalSortOrder(boolean useAlphabeticalSortOrder) {
        this.useAlphabeticalSortOrder = useAlphabeticalSortOrder;
    }

    public boolean isUseAlphabeticalSortOrder() {
        return useAlphabeticalSortOrder;
    }

    public ArrayList<String> getAllowedFileTypes() {
        return allowedFileTypes;
    }
}
