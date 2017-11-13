package delit.piwigoclient.ui.events.trackable;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by gareth on 13/06/17.
 */

public class FileListSelectionCompleteEvent extends TrackableResponseEvent {

    private final ArrayList<File> selectedFiles;

    public FileListSelectionCompleteEvent(int actionId, ArrayList<File> selectedFiles) {
        super(actionId);
        this.selectedFiles = selectedFiles;
    }

    public ArrayList<File> getSelectedFiles() {
        return selectedFiles;
    }
}
