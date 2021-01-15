package delit.piwigoclient.ui.common.fragment;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

public abstract class ListViewLongSelectableSetSelectFragment<X extends Enableable & SelectableItemsAdapter<?>, Z extends BaseRecyclerViewAdapterPreferences<Z>> extends ListViewLongSetSelectFragment<X, Z> {
    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }
}
