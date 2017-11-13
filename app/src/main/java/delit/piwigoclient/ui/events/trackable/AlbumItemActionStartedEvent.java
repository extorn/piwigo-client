package delit.piwigoclient.ui.events.trackable;

import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemActionStartedEvent extends TrackableRequestEvent {

    private final GalleryItem item;

    public AlbumItemActionStartedEvent(final GalleryItem item) {
        this.item = item;
    }

    public GalleryItem getItem() {
        return item;
    }
}
