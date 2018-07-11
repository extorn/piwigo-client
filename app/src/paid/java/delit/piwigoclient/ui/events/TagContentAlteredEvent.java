package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 21/04/18.
 */

public class TagContentAlteredEvent {
    private final long id;
    private final int contentChange;

    public TagContentAlteredEvent(long id, int contentChange) {
        this.id = id;
        this.contentChange = contentChange;
    }

    public int getContentChange() {
        return contentChange;
    }

    public long getId() {
        return id;
    }
}
