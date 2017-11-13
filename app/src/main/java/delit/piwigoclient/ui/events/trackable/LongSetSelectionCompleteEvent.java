package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 19/09/17.
 */

public class LongSetSelectionCompleteEvent extends TrackableResponseEvent {

    private final HashSet<Long> currentSelection;

    public LongSetSelectionCompleteEvent(int actionId, HashSet<Long> currentSelection) {
        super(actionId);
        this.currentSelection = currentSelection;
    }

    public HashSet<Long> getCurrentSelection() {
        return currentSelection;
    }
}
