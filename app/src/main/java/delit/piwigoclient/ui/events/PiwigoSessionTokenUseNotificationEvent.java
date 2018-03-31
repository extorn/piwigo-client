package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 30/03/18.
 */

public class PiwigoSessionTokenUseNotificationEvent {

    private final String piwigoSessionToken;

    public PiwigoSessionTokenUseNotificationEvent(String piwigoSessionToken) {
        this.piwigoSessionToken = piwigoSessionToken;
    }

    public String getPiwigoSessionToken() {
        return piwigoSessionToken;
    }

}
