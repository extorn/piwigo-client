package delit.piwigoclient.ui.upload.list;

import android.view.View;

import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.upload.FilesToUploadRecyclerViewAdapter;

public class UploadDataItemListViewHolder<IVH extends UploadDataItemListViewHolder<IVH,LVA,MSA>,LVA extends FilesToUploadRecyclerViewAdapter<LVA,MSA,IVH>, MSA extends UploadItemMultiSelectStatusAdapter<MSA,LVA,IVH>> extends UploadDataItemViewHolder<IVH, LVA, MSA> {
    private static final String TAG = "UploadDataItemListVH";
    private MaterialCheckboxTriState deleteAfterUploadCheckbox;
    private MaterialCheckboxTriState compressBeforeUploadCheckbox;

    public UploadDataItemListViewHolder(View view) {
        super(view);
    }

    @Override
    public void redisplayOldValues(UploadDataItem uploadDataItem, boolean allowItemDeletion) {
        super.redisplayOldValues(uploadDataItem, allowItemDeletion);
        setCompressionViewComponentValuesFromModel();
    }

    @Override
    public void fillValues(UploadDataItem uploadDataItem, boolean allowItemDeletion) {
        super.fillValues(uploadDataItem, allowItemDeletion);
        setCompressionViewComponentValuesFromModel();
    }

    private void setCompressionViewComponentValuesFromModel() {
        UploadDataItem item = getItem();
        compressBeforeUploadCheckbox.setChecked(item.isCompressThisFile());
        compressBeforeUploadCheckbox.setSecondaryChecked(item.isCompressByDefault());
        deleteAfterUploadCheckbox.setChecked(item.isDeleteAfterUpload());
        deleteAfterUploadCheckbox.setSecondaryChecked(item.isDeleteByDefault());
        getFileForUploadMimeTypeImageView().setBackgroundColor(getCompressionIconColor(item));
    }

    @Override
    public void cacheViewFieldsAndConfigure(FilesToUploadRecyclerViewAdapter.UploadAdapterPrefs adapterPrefs) {
        super.cacheViewFieldsAndConfigure(adapterPrefs);
        compressBeforeUploadCheckbox = itemView.findViewById(R.id.file_compress_before_upload_checkbox);
        compressBeforeUploadCheckbox.setOnCheckedChangeListener((v,isChecked)->{
            getItem().setCompressThisFile(isChecked);
            setCompressionViewComponentValuesFromModel();
        });
        deleteAfterUploadCheckbox = itemView.findViewById(R.id.file_delete_after_upload_checkbox);
        deleteAfterUploadCheckbox.setOnCheckedChangeListener((v,isChecked)->{
            getItem().setDeleteAfterUpload(isChecked);
        });
    }
}
