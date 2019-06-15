package delit.piwigoclient.ui.common.fragment;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.list.SelectableItemsAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

public abstract class ListViewLongSelectableSetSelectFragment<X extends Enableable & SelectableItemsAdapter<?>, Z extends BaseRecyclerViewAdapterPreferences> extends ListViewLongSetSelectFragment<X, Z> {
    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }
}
