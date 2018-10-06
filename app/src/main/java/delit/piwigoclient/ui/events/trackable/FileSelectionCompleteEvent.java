package delit.piwigoclient.ui.events.trackable;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by gareth on 13/06/17.
 */

public class FileSelectionCompleteEvent extends TrackableResponseEvent {

    private long actionTimeMillis;
    private final ArrayList<File> selectedFiles;

    public FileSelectionCompleteEvent(int actionId, ArrayList<File> selectedFiles, long actionTimeMillis) {
        super(actionId);
        this.selectedFiles = selectedFiles;
        this.actionTimeMillis = actionTimeMillis;
    }

    public long getActionTimeMillis() {
        return actionTimeMillis;
    }

    public ArrayList<File> getSelectedFiles() {
        return selectedFiles;
    }
}
