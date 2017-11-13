package delit.piwigoclient.ui.events.trackable;

/**
 * Created by gareth on 13/06/17.
 */

public class AlbumCreatedEvent extends TrackableResponseEvent {
    private final long newAlbumId;
    private final Long parentAlbumId;

    public AlbumCreatedEvent(int actionId, Long parentAlbumId, long newAlbumId) {
        super(actionId);
        this.parentAlbumId = parentAlbumId;
        this.newAlbumId = newAlbumId;
    }

    public long getNewAlbumId() {
        return newAlbumId;
    }

    public Long getParentAlbumId() {
        return parentAlbumId;
    }
}
