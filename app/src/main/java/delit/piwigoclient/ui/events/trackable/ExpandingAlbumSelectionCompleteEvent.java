package delit.piwigoclient.ui.events.trackable;

import java.util.HashMap;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItem;

/**
 * Created by gareth on 26/09/17.
 */

public class ExpandingAlbumSelectionCompleteEvent extends LongSetSelectionCompleteEvent {
    private final HashMap<CategoryItem, String> albumPaths;
    private final HashSet<CategoryItem> selectedItems;

    public ExpandingAlbumSelectionCompleteEvent(int actionId) {
        super(actionId, null);
        albumPaths = null;
        selectedItems = null;
    }

    public ExpandingAlbumSelectionCompleteEvent(int actionId, HashSet<Long> currentSelection, HashSet<CategoryItem> selectedItems, HashMap<CategoryItem, String> albumPaths) {
        super(actionId, currentSelection);
        this.selectedItems = selectedItems;
        this.albumPaths = albumPaths;
    }

    public String getAlbumPath(CategoryItem item) {
        if(albumPaths == null) {
            return null;
        }
        return albumPaths.get(item);
    }

    public HashSet<CategoryItem> getSelectedItems() {
        return selectedItems;
    }
}
