package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 07/07/17.
 */

public class UsernameSelectionNeededEvent extends LongSetSelectionNeededEvent {

    private final HashSet<Long> indirectSelection;

    public UsernameSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> indirectSelection, HashSet<Long> currentSelection) {
        super(allowMultiSelect, allowEditing, currentSelection);
        this.indirectSelection = indirectSelection;
    }

    public HashSet<Long> getIndirectSelection() {
        return indirectSelection;
    }
}
