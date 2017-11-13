package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.CategoryItem;

/**
 * Created by gareth on 02/10/17.
 */

public class AlbumSelectedEvent {
    private final CategoryItem album;


    public AlbumSelectedEvent(CategoryItem album) {
        this.album = album;
    }

    public CategoryItem getAlbum() {
        return album;
    }
}
