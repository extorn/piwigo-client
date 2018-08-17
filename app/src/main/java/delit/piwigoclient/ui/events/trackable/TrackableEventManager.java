package delit.piwigoclient.ui.events.trackable;

import android.os.Bundle;

import org.greenrobot.eventbus.EventBus;

public class TrackableEventManager {
    private static final String TRACKED_EVENT_ID = "trackedEventId";
    private int trackedEventId = -1;

    public void postTrackedEvent(TrackableRequestEvent event) {
        trackedEventId = event.getActionId();
        EventBus.getDefault().post(event);
    }

    public void postStickyTrackedEvent(TrackableRequestEvent event) {
        trackedEventId = event.getActionId();
        EventBus.getDefault().postSticky(event);
    }

    /**
     * removes the event if sticky too. Not idempotent method!
     *
     * @param event
     * @return
     */
    public boolean wasTrackingEvent(TrackableResponseEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        if (trackedEventId != event.getActionId()) {
            return false;
        }
        trackedEventId = -1;
        return true;
    }

    public boolean isTrackingEvent(TrackableResponseEvent event) {
        return trackedEventId == event.getActionId();
    }

    public Bundle onSaveInstanceState() {
        Bundle b = new Bundle();
        b.putInt(TRACKED_EVENT_ID, trackedEventId);
        return b;
    }

    public void onRestoreInstanceState(Bundle state) {
        trackedEventId = state.getInt(TRACKED_EVENT_ID);
    }
}
