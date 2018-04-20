package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;

/**
 * Created by gareth on 02/10/17.
 */

public class AlbumItemSelectedEvent {
    private final ResourceContainer album;
    private final GalleryItem selectedItem;

    public AlbumItemSelectedEvent(ResourceContainer album, GalleryItem selectedItem) {
        this.album = album;
        this.selectedItem = selectedItem;
    }

    public ResourceContainer getResourceContainer() {
        return album;
    }

    public GalleryItem getSelectedItem() {
        return selectedItem;
    }
}
