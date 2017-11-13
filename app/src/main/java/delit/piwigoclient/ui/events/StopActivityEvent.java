package delit.piwigoclient.ui.events;

import android.app.Activity;
import android.content.Context;

import delit.piwigoclient.ui.events.trackable.TrackableResponseEvent;

/**
 * Created by gareth on 10/30/17.
 */

public class StopActivityEvent extends TrackableResponseEvent {

    public StopActivityEvent(int actionId) {
        super(actionId);
    }
}
