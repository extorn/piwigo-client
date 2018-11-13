package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 07/07/17.
 */

public class AlbumPermissionsSelectionCompleteEvent extends TrackableResponseEvent {
    private final HashSet<Long> selectedAlbumIds;
    private final HashSet<String> selectedAlbums;

    public AlbumPermissionsSelectionCompleteEvent(int actionId, HashSet<Long> selectedAlbumIds, HashSet<String> selectedAlbums) {
        super(actionId);
        this.selectedAlbumIds = selectedAlbumIds;
        this.selectedAlbums = selectedAlbums;
    }

    public HashSet<Long> getSelectedAlbumIds() {
        return selectedAlbumIds;
    }

    public HashSet<String> getSelectedAlbums() {
        return selectedAlbums;
    }
}
