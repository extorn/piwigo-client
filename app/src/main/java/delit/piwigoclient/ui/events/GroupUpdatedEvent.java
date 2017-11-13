package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.Group;

/**
 * Created by gareth on 12/06/17.
 */

public class GroupUpdatedEvent {

    public final Group group;

    public GroupUpdatedEvent(final Group group) {
        this.group = group;
    }

    public Group getGroup() {
        return group;
    }
}
