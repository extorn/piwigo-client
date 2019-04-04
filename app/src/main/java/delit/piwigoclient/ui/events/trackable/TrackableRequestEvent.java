package delit.piwigoclient.ui.events.trackable;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by gareth on 02/10/17.
 */

public class TrackableRequestEvent implements Serializable {
    private static final AtomicInteger actionIdGenerator = new AtomicInteger(0);
    private static final long serialVersionUID = 8955893506818711893L;
    private int actionId;

    public TrackableRequestEvent() {
        actionId = getNextEventId();
    }

    public synchronized static int getNextEventId() {
        short id = (short) actionIdGenerator.incrementAndGet();
        if (id < 0) {
            id = 0;
            actionIdGenerator.set(0);
        }
        return id;
    }

    public int getActionId() {
        return actionId;
    }

    protected void setActionId(int actionId) {
        this.actionId = actionId;
    }
}
