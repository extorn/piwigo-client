package delit.piwigoclient.ui.upload.list;

import android.view.View;

import delit.libs.ui.view.CustomClickTouchListener;
import delit.piwigoclient.ui.upload.FilesToUploadRecyclerViewAdapter;

public class UploadDataItemGridViewHolder<IVH extends UploadDataItemGridViewHolder<IVH,LVA,MSA>,LVA extends FilesToUploadRecyclerViewAdapter<LVA,MSA,IVH>, MSA extends UploadItemMultiSelectStatusAdapter<MSA,LVA,IVH>> extends UploadDataItemViewHolder<IVH, LVA, MSA> {
    private static final String TAG = "UploadDataItemGridVH";

    public UploadDataItemGridViewHolder(View view) {
        super(view);
    }

    @Override
    public void redisplayOldValues(UploadDataItem uploadDataItem, boolean allowItemDeletion) {
        super.redisplayOldValues(uploadDataItem, allowItemDeletion);
        setCompressionIconColor();
    }

    @Override
    public void fillValues(UploadDataItem uploadDataItem, boolean allowItemDeletion) {
        super.fillValues(uploadDataItem, allowItemDeletion);
        setCompressionIconColor();
    }

    private void setCompressionIconColor() {
        UploadDataItem item = getItem();
        getFileForUploadMimeTypeImageView().setBackgroundColor(getCompressionIconColor(item));
    }

    @Override
    public void cacheViewFieldsAndConfigure(FilesToUploadRecyclerViewAdapter.UploadAdapterPrefs adapterPrefs) {
        super.cacheViewFieldsAndConfigure(adapterPrefs);
        CustomClickTouchListener.callClickOnTouch(getFileForUploadMimeTypeImageView(), this::toggleCompression);
    }

    private void toggleCompression(View view) {
        getItem().setCompressThisFile(!Boolean.TRUE.equals(getItem().isCompressThisFile()));
        setCompressionIconColor();
    }

}
