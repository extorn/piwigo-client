package delit.piwigoclient.ui.events.trackable;

import delit.piwigoclient.model.piwigo.CategoryItem;

/**
 * Created by gareth on 13/06/17.
 */

public class AlbumCreatedEvent extends TrackableResponseEvent {
    private final Long parentAlbumId;
    private final CategoryItem albumDetail;

    public AlbumCreatedEvent(int actionId, Long parentAlbumId, CategoryItem albumDetail) {
        super(actionId);
        this.parentAlbumId = parentAlbumId;
        this.albumDetail = albumDetail;
    }

    public long getNewAlbumId() {
        return albumDetail.getId();
    }

    public Long getParentAlbumId() {
        return parentAlbumId;
    }

    public CategoryItem getAlbumDetail() {
        return albumDetail;
    }
}
