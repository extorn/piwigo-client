package delit.piwigoclient.ui.common;

import java.util.HashSet;

/**
 * Created by gareth on 03/01/18.
 */

public interface SelectableItemsAdapter<T> {
    HashSet<T> getSelectedItems();
    HashSet<Long> getSelectedItemIds();
    void clearSelectedItemIds();
    void selectAllItemIds();
    void setItemSelected(Long selectedItemId);
    void setSelectedItems(HashSet<Long> currentSelection);
    void setInitiallySelectedItems(HashSet<Long> initialSelection);
}
