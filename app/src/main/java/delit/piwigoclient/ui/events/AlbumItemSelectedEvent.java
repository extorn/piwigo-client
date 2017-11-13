package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;

/**
 * Created by gareth on 02/10/17.
 */

public class AlbumItemSelectedEvent {
    private final PiwigoAlbum album;
    private final GalleryItem selectedItem;

    public AlbumItemSelectedEvent(PiwigoAlbum album, GalleryItem selectedItem) {
        this.album = album;
        this.selectedItem = selectedItem;
    }

    public PiwigoAlbum getAlbum() {
        return album;
    }

    public GalleryItem getSelectedItem() {
        return selectedItem;
    }
}
