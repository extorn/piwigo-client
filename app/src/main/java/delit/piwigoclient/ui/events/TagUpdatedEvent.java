package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 12/06/17.
 */

public class TagUpdatedEvent {

    public final Tag tag;

    public TagUpdatedEvent(final Tag tag) {
        this.tag = tag;
    }

    public Tag getTag() {
        return tag;
    }
}
