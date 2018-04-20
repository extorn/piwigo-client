package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 07/07/17.
 */

public class TagSelectionNeededEvent extends LongSetSelectionNeededEvent {

    public TagSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, boolean initialSelectionLocked, HashSet<Long> initialSelection) {
        super(allowMultiSelect, allowEditing, initialSelectionLocked, initialSelection);
    }
}
