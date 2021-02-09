package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

/**
 * Created by gareth on 12/06/17.
 */

public class PiwigoActivePluginsReceivedEvent {
    private final PiwigoSessionDetails credentials;

    public PiwigoActivePluginsReceivedEvent(PiwigoSessionDetails credentials) {
        this.credentials = credentials;
    }

    public PiwigoSessionDetails getCredentials() {
        return credentials;
    }
}
