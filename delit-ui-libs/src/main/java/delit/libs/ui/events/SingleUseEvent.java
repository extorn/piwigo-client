package delit.libs.ui.events;

import java.util.Date;

/**
 * Created by gareth on 18/10/17.
 */

public class SingleUseEvent {

    private boolean handled;
    private Date eventRaisedAt;

    public SingleUseEvent() {
        eventRaisedAt = new Date();
    }

    public Date getEventRaisedAt() {
        return eventRaisedAt;
    }

    public synchronized boolean isHandled() {
        if (!handled) {
            handled = true;
            return false;
        }
        return true;
    }
}
