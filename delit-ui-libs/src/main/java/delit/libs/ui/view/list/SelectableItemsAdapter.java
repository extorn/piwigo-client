package delit.libs.ui.view.list;

import java.util.HashSet;

/**
 * Created by gareth on 03/01/18.
 */

public interface SelectableItemsAdapter<T> {
    HashSet<T> getSelectedItems();

    void setSelectedItems(HashSet<Long> currentSelection);

    HashSet<Long> getSelectedItemIds();

    void clearSelectedItemIds();

    void selectAllItemIds();

    void setItemSelected(Long selectedItemId);

    void setInitiallySelectedItems(HashSet<Long> initialSelection);

    boolean isAllowItemDeselection(long itemId);
}
