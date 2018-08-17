package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 13/06/17.
 */

public class AlbumAlteredEvent {
    public static final long ALL_ALBUMS_ID = -1;
    public final long id;

    public AlbumAlteredEvent(long id) {
        this.id = id;
    }
}
