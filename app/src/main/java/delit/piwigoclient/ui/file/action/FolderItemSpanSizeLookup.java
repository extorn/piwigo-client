package delit.piwigoclient.ui.file.action;

import androidx.recyclerview.widget.GridLayoutManager;

import delit.libs.core.util.Logging;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;

public class FolderItemSpanSizeLookup<LVA extends FolderItemRecyclerViewAdapter<?, ?, FolderItem,?,?>> extends GridLayoutManager.SpanSizeLookup {

    private final int spanCount;
    private final LVA viewAdapter;

    public FolderItemSpanSizeLookup(LVA viewAdapter, int spanCount) {
        this.viewAdapter = viewAdapter;
        this.spanCount = spanCount;
    }

    @Override
    public int getSpanSize(int position) {
        try {
            int itemType = viewAdapter.getItemViewType(position);
            switch (itemType) {
                case FolderItemRecyclerViewAdapter.VIEW_TYPE_FOLDER:
                    return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFolders();
                case FolderItemRecyclerViewAdapter.VIEW_TYPE_FILE:
                    return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFiles();
                default:
                    return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFiles();
            }
        } catch (IndexOutOfBoundsException e) {
            Logging.recordException(e);
            //TODO why does this occur? How?
            return 1;
        }
    }
}
