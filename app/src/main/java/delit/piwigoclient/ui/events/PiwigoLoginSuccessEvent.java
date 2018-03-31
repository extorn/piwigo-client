package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

/**
 * Created by gareth on 12/06/17.
 */

public class PiwigoLoginSuccessEvent {
    private final boolean changePage;
    private final PiwigoSessionDetails oldCredentials;

    public PiwigoLoginSuccessEvent(PiwigoSessionDetails oldCredentials, boolean changePage) {
        this.changePage = changePage;
        this.oldCredentials = oldCredentials;
    }

    public PiwigoSessionDetails getOldCredentials() {
        return oldCredentials;
    }

    public boolean isChangePage() {
        return changePage;
    }
}
