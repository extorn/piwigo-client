package delit.piwigoclient.ui.permissions;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.common.MultiSourceListAdapter;
import delit.piwigoclient.ui.common.SelectableItemsAdapter;

/**
 * Created by gareth on 22/06/17.
 */

public class AlbumSelectionListAdapter extends MultiSourceListAdapter<CategoryItemStub> {

    public AlbumSelectionListAdapter(Context context, ArrayList<CategoryItemStub> availableItems, boolean isCheckable) {
        super(context, availableItems, isCheckable);
    }

    public AlbumSelectionListAdapter(Context context, ArrayList<CategoryItemStub> availableItems, HashSet<Long> indirectlySelectedItems, boolean isCheckable) {
        super(context, availableItems, indirectlySelectedItems, isCheckable);
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
