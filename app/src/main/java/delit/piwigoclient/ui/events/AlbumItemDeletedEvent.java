package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemDeletedEvent {

    public final GalleryItem item;

    public AlbumItemDeletedEvent(final GalleryItem item) {
        this.item = item;
    }

}
