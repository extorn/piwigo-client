package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 12/06/17.
 */

public class TagDeletedEvent {

    public final Tag tag;

    public TagDeletedEvent(final Tag tag) {
        this.tag = tag;
    }

    public Tag getTag() {
        return tag;
    }
}
