package delit.piwigoclient.ui.common.fragment;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 03/01/18.
 */

public abstract class RecyclerViewLongSetSelectFragment<F extends RecyclerViewLongSetSelectFragment<F,FUIH, LVA, P,T>,FUIH extends FragmentUIHelper<FUIH,F>, LVA extends Enableable & SelectableItemsAdapter<T>, P extends BaseRecyclerViewAdapterPreferences<P>, T> extends LongSelectableSetSelectFragment<F,FUIH,RecyclerView, LVA, P,T> {

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.layout_fullsize_recycler_list;
    }

    protected void selectAllListItems() {
        getListAdapter().selectAllItemIds();
    }

    protected void selectNoneListItems() {
        getListAdapter().clearSelectedItemIds();
    }

    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }

    @Override
    protected long[] getSelectedItemIds() {
        LVA listAdapter = getListAdapter();
        long[] result = null;
        if(listAdapter != null) {
            HashSet<Long> ids = listAdapter.getSelectedItemIds();
            result = new long[ids.size()];
            int i = 0;
            for (Long id : ids) {
                result[i++] = id;
            }
        }
        return result;
    }
}
