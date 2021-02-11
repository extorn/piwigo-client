package delit.piwigoclient.ui.common.fragment;

import android.view.View;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;

public abstract class LongSelectableSetSelectFragment<F extends LongSelectableSetSelectFragment<F,FUIH,Y,X,Z,T>,FUIH extends FragmentUIHelper<FUIH,F>, Y extends View, X extends Enableable & SelectableItemsAdapter<T>, Z extends BaseRecyclerViewAdapterPreferences<Z>, T> extends LongSetSelectFragment<F,FUIH,Y, X, Z> {
    @Override
    public HashSet<Long> getCurrentSelection() {
        X adapter = getListAdapter();
        if (adapter == null) {
            return super.getCurrentSelection();
        }
        return adapter.getSelectedItemIds();
    }

    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }
}
