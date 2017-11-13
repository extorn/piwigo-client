package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.User;

/**
 * Created by gareth on 21/06/17.
 */

public class ViewUserEvent {
    private User user;

    public ViewUserEvent(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
