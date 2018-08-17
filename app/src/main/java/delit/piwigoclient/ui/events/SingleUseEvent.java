package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 18/10/17.
 */

public class SingleUseEvent {

    private boolean handled;

    public synchronized boolean isHandled() {
        if (!handled) {
            handled = true;
            return false;
        }
        return true;
    }
}
