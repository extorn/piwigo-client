package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 21/06/17.
 */

public class ViewTagEvent {
    private final Tag tag;

    public ViewTagEvent(Tag tag) {
        this.tag = tag;
    }

    public Tag getTag() {
        return tag;
    }
}
