package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 26/09/17.
 */

public class AlbumSelectionCompleteEvent extends LongSetSelectionCompleteEvent {
    private final HashSet<CategoryItemStub> selectedItems;

    public AlbumSelectionCompleteEvent(int actionId, HashSet<Long> selectedItemIds, HashSet<CategoryItemStub> selectedItems) {
        super(actionId, selectedItemIds);
        this.selectedItems = selectedItems;
    }

    public HashSet<CategoryItemStub> getSelectedItems() {
        return selectedItems;
    }
}
