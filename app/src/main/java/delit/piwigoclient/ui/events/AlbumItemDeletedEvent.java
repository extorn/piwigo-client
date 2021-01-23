package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemDeletedEvent<T extends GalleryItem> {

    public final T item;
    private final int albumResourceItemIdx;
    private final int albumResourceItemCount;

    public AlbumItemDeletedEvent(final T item, int albumItemIdx, int albumItemCount) {
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
