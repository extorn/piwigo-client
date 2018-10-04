package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 13/06/17.
 */

public class AlbumAlteredEvent {
    public static final long ALL_ALBUMS_ID = -1;
    private long albumAltered;
    private long childAltered;
    private boolean cascadeToParents;

    public AlbumAlteredEvent(long albumAltered) {
        this(albumAltered, ALL_ALBUMS_ID);
    }

    public AlbumAlteredEvent(long albumAltered, long childAltered) {
        this(albumAltered, childAltered, false);
    }

    public AlbumAlteredEvent(long albumAltered, long childAltered, boolean cascadeToParents) {
        this.albumAltered = albumAltered;
        this.childAltered = childAltered;
        this.cascadeToParents = cascadeToParents;
    }

    public long getAlbumAltered() {
        return albumAltered;
    }

    public long getChildAltered() {
        return childAltered;
    }

    public boolean isCascadeToParents() {
        return cascadeToParents;
    }

    public boolean isRelevant(long viewedAlbum) {
        return albumAltered == viewedAlbum || albumAltered == ALL_ALBUMS_ID;
    }
}
