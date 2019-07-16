package delit.piwigoclient.ui.common.fragment;

import android.view.View;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

public abstract class LongSelectableSetSelectFragment<Y extends View, X extends Enableable & SelectableItemsAdapter, Z extends BaseRecyclerViewAdapterPreferences> extends LongSetSelectFragment<Y, X, Z> {
    @Override
    public HashSet<Long> getCurrentSelection() {
        X adapter = getListAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getSelectedItemIds();
    }

    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }
}
