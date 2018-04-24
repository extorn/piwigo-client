package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemDeletedEvent {

    public final GalleryItem item;
    private final long albumResourceItemIdx;
    private final long albumResourceItemCount;

    public AlbumItemDeletedEvent(final GalleryItem item, long albumItemIdx, long albumItemCount) {
        this.item = item;
        this.albumResourceItemIdx = albumItemIdx;
        this.albumResourceItemCount = albumItemCount;
    }

    public long getAlbumResourceItemCount() {
        return albumResourceItemCount;
    }

    public long getAlbumResourceItemIdx() {
        return albumResourceItemIdx;
    }
}
