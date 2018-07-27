package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemDeletedEvent {

    public final GalleryItem item;
    private final int albumResourceItemIdx;
    private final int albumResourceItemCount;

    public AlbumItemDeletedEvent(final GalleryItem item, int albumItemIdx, int albumItemCount) {
        this.item = item;
        this.albumResourceItemIdx = albumItemIdx;
        this.albumResourceItemCount = albumItemCount;
    }

    public int getAlbumResourceItemCount() {
        return albumResourceItemCount;
    }

    public int getAlbumResourceItemIdx() {
        return albumResourceItemIdx;
    }
}
