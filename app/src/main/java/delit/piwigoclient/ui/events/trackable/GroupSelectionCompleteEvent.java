package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Group;

/**
 * Created by gareth on 26/09/17.
 */

public class GroupSelectionCompleteEvent extends LongSetSelectionCompleteEvent {
    private final HashSet<Group> selectedItems;

    public GroupSelectionCompleteEvent(int actionId, HashSet<Long> selectedItemIds, HashSet<Group> selectedItems) {
        super(actionId, selectedItemIds);
        this.selectedItems = selectedItems;
    }

    public HashSet<Group> getSelectedItems() {
        return selectedItems;
    }
}
