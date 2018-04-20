package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 19/09/17.
 */

public class LongSetSelectionNeededEvent extends TrackableRequestEvent {
    private final boolean allowMultiSelect;
    private final boolean allowEditing;
    private final boolean initialSelectionLocked;
    private final HashSet<Long> initialSelection;

    public LongSetSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection) {
        this(allowMultiSelect, allowEditing, false, currentSelection);
    }

    public LongSetSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, boolean initialSelectionLocked, HashSet<Long> initialSelection) {
        this.allowMultiSelect = allowMultiSelect;
        this.initialSelection = initialSelection;
        this.allowEditing = allowEditing;
        this.initialSelectionLocked = initialSelectionLocked;
    }

    public HashSet<Long> getInitialSelection() {
        return initialSelection;
    }

    public boolean isInitialSelectionLocked() {
        return initialSelectionLocked;
    }

    public boolean isAllowMultiSelect() {
        return allowMultiSelect;
    }

    public boolean isAllowEditing() {
        return allowEditing;
    }
}
