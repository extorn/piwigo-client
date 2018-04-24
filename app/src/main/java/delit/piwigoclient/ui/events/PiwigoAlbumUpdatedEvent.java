package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.PiwigoAlbum;

/**
 * Created by gareth on 03/04/18.
 */

public class PiwigoAlbumUpdatedEvent {

    private final PiwigoAlbum updatedAlbum;

    public PiwigoAlbumUpdatedEvent(PiwigoAlbum album) {
        this.updatedAlbum = album;
    }

    public PiwigoAlbum getUpdatedAlbum() {
        return updatedAlbum;
    }
}
