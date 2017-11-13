package delit.piwigoclient.ui.events;

import java.util.ArrayList;

import delit.piwigoclient.model.piwigo.User;

/**
 * Created by gareth on 05/11/17.
 */
public class UserNotUniqueWarningEvent {
    private final User userSelected;
    private final ArrayList<User> otherUsers;

    public UserNotUniqueWarningEvent(User userSelected, ArrayList<User> otherUsers) {
        this.userSelected = userSelected;
        this.otherUsers = otherUsers;
    }

    public ArrayList<User> getOtherUsers() {
        return otherUsers;
    }

    public User getUserSelected() {
        return userSelected;
    }
}
