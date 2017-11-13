package delit.piwigoclient.ui.events.trackable;

/**
 * Created by gareth on 02/10/17.
 */

public class TrackableResponseEvent {
    private final int actionId;

    public TrackableResponseEvent(int actionId) {
        this.actionId = actionId;
    }

    public int getActionId() {
        return actionId;
    }
}
