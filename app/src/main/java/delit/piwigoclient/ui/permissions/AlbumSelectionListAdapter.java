package delit.piwigoclient.ui.permissions;

import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.ui.view.list.MultiSourceListAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 22/06/17.
 */

public class AlbumSelectionListAdapter extends MultiSourceListAdapter<CategoryItemStub, BaseRecyclerViewAdapterPreferences> {

    public AlbumSelectionListAdapter(ArrayList<CategoryItemStub> availableItems, BaseRecyclerViewAdapterPreferences adapterPreferences) {
        super(availableItems, adapterPreferences);
    }

    public AlbumSelectionListAdapter(ArrayList<CategoryItemStub> availableItems, HashSet<Long> indirectlySelectedItems, BaseRecyclerViewAdapterPreferences adapterPreferences) {
        super(availableItems, indirectlySelectedItems, adapterPreferences);
    }

    @Override
    public long getItemId(CategoryItemStub item) {
        return item.getId();
    }

    @Override
    public Long getItemParentId(CategoryItemStub item) {
        return item.getParentId();
    }

}
