package delit.piwigoclient.ui.common.fragment;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;

public abstract class ListViewLongSelectableSetSelectFragment<F extends ListViewLongSelectableSetSelectFragment<F,FUIH,X,Z>,FUIH extends FragmentUIHelper<FUIH,F>, X extends Enableable & SelectableItemsAdapter<?>, Z extends BaseRecyclerViewAdapterPreferences<Z>> extends ListViewLongSetSelectFragment<F,FUIH,X, Z> {
    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }
}
