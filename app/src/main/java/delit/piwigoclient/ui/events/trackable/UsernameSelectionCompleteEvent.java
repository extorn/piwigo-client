package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Username;

/**
 * Created by gareth on 26/09/17.
 */

public class UsernameSelectionCompleteEvent extends LongSetSelectionCompleteEvent {
    private final HashSet<Username> selectedItems;

    public UsernameSelectionCompleteEvent(int actionId, HashSet<Long> selectedItemIds, HashSet<Username> selectedItems) {
        super(actionId, selectedItemIds);
        this.selectedItems = selectedItems;
    }

    public HashSet<Username> getSelectedItems() {
        return selectedItems;
    }
}
