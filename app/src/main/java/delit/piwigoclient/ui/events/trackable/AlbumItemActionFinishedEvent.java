package delit.piwigoclient.ui.events.trackable;

import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemActionFinishedEvent extends TrackableResponseEvent {

    private final GalleryItem item;

    public AlbumItemActionFinishedEvent(final int requestId, final GalleryItem item) {
        super(requestId);
        this.item = item;
    }

    public GalleryItem getItem() {
        return item;
    }
}
