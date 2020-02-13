package delit.piwigoclient.ui.common.fragment;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.R;

/**
 * Created by gareth on 03/01/18.
 */

public abstract class RecyclerViewLongSetSelectFragment<X extends Enableable & SelectableItemsAdapter<?>, Z extends BaseRecyclerViewAdapterPreferences> extends LongSelectableSetSelectFragment<RecyclerView, X, Z> {

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
        X listAdapter = getListAdapter();
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
