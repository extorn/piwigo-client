package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 07/07/17.
 */

public class AlbumPermissionsSelectionCompleteEvent extends TrackableResponseEvent {
    private final HashSet<Long> selectedAlbums;

    public AlbumPermissionsSelectionCompleteEvent(int actionId, HashSet<Long> selectedAlbums) {
        super(actionId);
        this.selectedAlbums = selectedAlbums;
    }

    public HashSet<Long> getSelectedAlbums() {
        return selectedAlbums;
    }
}
