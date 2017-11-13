package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 10/30/17.
 */

public class UnlockAppEvent {
    private String password;

    public UnlockAppEvent(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }
}
