package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 19/09/17.
 */

public class LongSetSelectionNeededEvent extends TrackableRequestEvent {
    private final boolean allowMultiSelect;
    private final boolean allowEditing;
    private final HashSet<Long> currentSelection;

    public LongSetSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection) {
        this.allowMultiSelect = allowMultiSelect;
        this.currentSelection = currentSelection;
        this.allowEditing = allowEditing;
    }

    public HashSet<Long> getCurrentSelection() {
        return currentSelection;
    }

    public boolean isAllowMultiSelect() {
        return allowMultiSelect;
    }

    public boolean isAllowEditing() {
        return allowEditing;
    }
}
