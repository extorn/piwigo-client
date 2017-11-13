package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.User;

/**
 * Created by gareth on 12/06/17.
 */

public class UserDeletedEvent {

    private final User user;

    public UserDeletedEvent(final User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
