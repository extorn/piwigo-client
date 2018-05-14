package delit.piwigoclient.ui.events.trackable;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by gareth on 13/06/17.
 */

public class FolderSelectionCompleteEvent extends TrackableResponseEvent {

    private final ArrayList<File> selectedFolders;

    public FolderSelectionCompleteEvent(int actionId, ArrayList<File> selectedFolders) {
        super(actionId);
        this.selectedFolders = selectedFolders;
    }

    public ArrayList<File> getSelectedFolders() {
        return selectedFolders;
    }
}
