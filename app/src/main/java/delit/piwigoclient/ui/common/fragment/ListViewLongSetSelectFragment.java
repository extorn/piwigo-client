package delit.piwigoclient.ui.common.fragment;

import android.widget.ListView;

import androidx.annotation.LayoutRes;

import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.R;

/**
 * Created by gareth on 03/01/18.
 */

public abstract class ListViewLongSetSelectFragment<X extends Enableable & SelectableItemsAdapter<?>, Z extends BaseRecyclerViewAdapterPreferences<Z>> extends LongSetSelectFragment<ListView, X, Z> {

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.layout_fullsize_list;
    }

    protected void selectAllListItems() {
        ListView list = getList();
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, true);
        }
//        list.deferNotifyDataSetChanged();
    }

    @Override
    public HashSet<Long> getCurrentSelection() {
        X adapter = getListAdapter();
        if (adapter == null) {
            return super.getCurrentSelection();
        }
        return adapter.getSelectedItemIds();
    }

    protected void selectNoneListItems() {
        ListView list = getList();
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        getListAdapter().setSelectedItems(new HashSet<>(selectionIds));
    }

    @Override
    protected long[] getSelectedItemIds() {
        ListView list = getList();
        return list.getCheckedItemIds();
    }
}
