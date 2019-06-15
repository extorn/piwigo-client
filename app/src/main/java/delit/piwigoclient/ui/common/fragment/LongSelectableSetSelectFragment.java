package delit.piwigoclient.ui.common.fragment;

import android.view.View;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.list.SelectableItemsAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

public abstract class LongSelectableSetSelectFragment<Y extends View, X extends Enableable & SelectableItemsAdapter, Z extends BaseRecyclerViewAdapterPreferences> extends LongSetSelectFragment<Y, X, Z> {
    @Override
    public HashSet<Long> getCurrentSelection() {
        return getListAdapter().getSelectedItemIds();
    }

    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }
}
