package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.Group;

/**
 * Created by gareth on 12/06/17.
 */

public class GroupUpdatedEvent {

    private final Group group;

    public GroupUpdatedEvent(final Group group) {
        this.group = group;
    }

    public Group getGroup() {
        return group;
    }
}
