package delit.piwigoclient.ui.common;

import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.widget.ListView;

import java.util.HashSet;

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

    protected SelectableItemsAdapter getAdapter() {
        RecyclerView.Adapter adapter = getList().getAdapter();
        return (SelectableItemsAdapter)adapter;
    }

    protected void selectAllListItems() {
        getAdapter().selectAllItemIds();
    }

    protected void selectNoneListItems() {
        getAdapter().clearSelectedItemIds();
    }

    @Override
    protected long[] getSelectedItemIds() {
        HashSet<Long> ids = getAdapter().getSelectedItemIds();
        long[] result = new long[ids.size()];
        int i = 0;
        for(Long id : ids) {
            result[i++] = id;
        }
        return result;
    }
}
