package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 07/07/17.
 */

public class GroupSelectionNeededEvent extends LongSetSelectionNeededEvent {

    public GroupSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection) {
        super(allowMultiSelect, allowEditing, currentSelection);
    }
}
