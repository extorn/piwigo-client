package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.Group;

/**
 * Created by gareth on 21/06/17.
 */

public class ViewGroupEvent {
    private final Group group;

    public ViewGroupEvent(Group group) {
        this.group = group;
    }

    public Group getGroup() {
        return group;
    }
}
