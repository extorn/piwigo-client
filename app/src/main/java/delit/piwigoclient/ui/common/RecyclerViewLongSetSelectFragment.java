package delit.piwigoclient.ui.common;

import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;

/**
 * Created by gareth on 03/01/18.
 */

public abstract class RecyclerViewLongSetSelectFragment<X extends Enableable & SelectableItemsAdapter> extends LongSetSelectFragment<RecyclerView, X> {

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
