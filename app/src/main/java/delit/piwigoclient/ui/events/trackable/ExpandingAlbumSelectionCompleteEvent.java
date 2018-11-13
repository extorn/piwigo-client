package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItem;

/**
 * Created by gareth on 26/09/17.
 */

public class ExpandingAlbumSelectionCompleteEvent extends LongSetSelectionCompleteEvent {
    private final HashSet<CategoryItem> selectedItems;

    public ExpandingAlbumSelectionCompleteEvent(int actionId, HashSet<Long> currentSelection, HashSet<CategoryItem> selectedItems) {
        super(actionId, currentSelection);
        this.selectedItems = selectedItems;
    }

    public HashSet<CategoryItem> getSelectedItems() {
        return selectedItems;
    }
}
