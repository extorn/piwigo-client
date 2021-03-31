package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 26/09/17.
 */

public class TagSelectionCompleteEvent extends LongSetSelectionCompleteEvent {
    private final HashSet<Tag> selectedItems;

    public TagSelectionCompleteEvent(int actionId, HashSet<Long> selectedItemIds, HashSet<Tag> selectedItems) {
        super(actionId, selectedItemIds);
        this.selectedItems = selectedItems;
    }

    public HashSet<Tag> getSelectedItems() {
        return selectedItems;
    }
}
