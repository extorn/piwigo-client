package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 12/06/17.
 */

public class PiwigoLoginSuccessEvent {
    private final boolean changePage;

    public PiwigoLoginSuccessEvent(boolean changePage) {
        this.changePage = changePage;
    }

    public boolean isChangePage() {
        return changePage;
    }
}
