package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.ResourceContainer;

/**
 * Created by gareth on 03/04/18.
 */

public class PiwigoAlbumUpdatedEvent {

    private final ResourceContainer updatedAlbum;

    public PiwigoAlbumUpdatedEvent(ResourceContainer album) {
        this.updatedAlbum = album;
    }

    public ResourceContainer getUpdatedAlbum() {
        return updatedAlbum;
    }
}
