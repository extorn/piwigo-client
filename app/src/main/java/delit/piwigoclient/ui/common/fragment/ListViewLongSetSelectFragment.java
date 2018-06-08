package delit.piwigoclient.ui.common.fragment;

import android.support.annotation.LayoutRes;
import android.widget.ListView;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.list.SelectableItemsAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

/**
 * Created by gareth on 03/01/18.
 */

public abstract class ListViewLongSetSelectFragment<X extends Enableable, Z extends BaseRecyclerViewAdapterPreferences> extends LongSetSelectFragment<ListView, X, Z> {

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

    protected void selectNoneListItems() {
        ListView list = getList();
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    @Override
    protected void selectOnlyListItems(Set<Long> selectionIds) {
        X listAdapter = getListAdapter();
        if(listAdapter instanceof SelectableItemsAdapter) {
            ((SelectableItemsAdapter)listAdapter).setSelectedItems(new HashSet(selectionIds));
        } else {
            ListView list = getList();
            for (int i = 0; i < list.getCount(); i++) {
                long itemId = list.getItemIdAtPosition(i);
                list.setItemChecked(i, selectionIds.contains(itemId));
            }
        }
    }

    @Override
    protected long[] getSelectedItemIds() {
        ListView list = getList();
        return list.getCheckedItemIds();
    }
}
