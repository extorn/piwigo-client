package delit.piwigoclient.ui.common.fragment;

import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.fragment.LongSetSelectFragment;
import delit.piwigoclient.ui.common.list.SelectableItemsAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

/**
 * Created by gareth on 03/01/18.
 */

public abstract class RecyclerViewLongSetSelectFragment<X extends Enableable & SelectableItemsAdapter, Z extends BaseRecyclerViewAdapterPreferences> extends LongSetSelectFragment<RecyclerView, X, Z> {

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
        getListAdapter().setSelectedItems(new HashSet(selectionIds));
    }

    @Override
    protected long[] getSelectedItemIds() {
        HashSet<Long> ids = getListAdapter().getSelectedItemIds();
        long[] result = new long[ids.size()];
        int i = 0;
        for(Long id : ids) {
            result[i++] = id;
        }
        return result;
    }
}
