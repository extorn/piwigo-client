package delit.piwigoclient.ui.events.trackable;

import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 13/06/17.
 */

public class AlbumCreateNeededEvent extends TrackableRequestEvent {

    private final CategoryItemStub parentAlbum;

    public AlbumCreateNeededEvent(CategoryItemStub parentAlbum) {
        this.parentAlbum = parentAlbum;
    }

    public CategoryItemStub getParentAlbum() {
        return parentAlbum;
    }
}
