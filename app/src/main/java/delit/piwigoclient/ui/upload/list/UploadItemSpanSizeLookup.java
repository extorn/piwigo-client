package delit.piwigoclient.ui.upload.list;

import androidx.recyclerview.widget.GridLayoutManager;

import delit.piwigoclient.ui.upload.FilesToUploadRecyclerViewAdapter;

public class UploadItemSpanSizeLookup<LVA extends FilesToUploadRecyclerViewAdapter<?, ?, ?>> extends GridLayoutManager.SpanSizeLookup {

    private final int spanCount;
    private final LVA viewAdapter;

    public UploadItemSpanSizeLookup(LVA viewAdapter, int spanCount) {
        this.viewAdapter = viewAdapter;
        this.spanCount = spanCount;
    }

  /*  @Override
    public int getSpanIndex(int position, int spanCount) {
        return position % spanCount;
    }*/

    @Override
    public int getSpanSize(int position) {
        int itemType = viewAdapter.getItemViewType(position);
        switch (itemType) {
            default:
            case FilesToUploadRecyclerViewAdapter.VIEW_TYPE_GRID:
                return 1;
            case FilesToUploadRecyclerViewAdapter.VIEW_TYPE_LIST:
                if(spanCount < 4) {
                    return spanCount;
                }
                return spanCount % 2;
        }
    }
}