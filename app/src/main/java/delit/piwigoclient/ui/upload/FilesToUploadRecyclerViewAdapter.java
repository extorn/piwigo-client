package delit.piwigoclient.ui.upload;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.SimpleRecyclerViewAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.upload.list.UploadDataItemModel;
import delit.piwigoclient.ui.upload.list.UploadDataItemViewHolder;
import delit.piwigoclient.ui.upload.list.UploadItemMultiSelectStatusAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class FilesToUploadRecyclerViewAdapter<LVA extends FilesToUploadRecyclerViewAdapter<LVA,MSA,VH>, MSA extends UploadItemMultiSelectStatusAdapter<MSA, LVA,VH>, VH extends UploadDataItemViewHolder<VH, LVA,MSA>> extends SimpleRecyclerViewAdapter<LVA, UploadDataItem, FilesToUploadRecyclerViewAdapter.UploadAdapterPrefs, VH, MSA> {

    private static final String TAG = "F2UAdapter";
    public static final int VIEW_TYPE_LIST = 0;
    public static final int VIEW_TYPE_GRID = 1;

    private UploadDataItemModel uploadDataItemsModel;
    private final RemoveListener<LVA,MSA,VH> listener;
    private int viewType = VIEW_TYPE_LIST;

    public FilesToUploadRecyclerViewAdapter(@NonNull ArrayList<DocumentFile> filesToUpload, @NonNull RemoveListener<LVA,MSA,VH> listener) {
        super((MSA) new UploadItemMultiSelectStatusAdapter<MSA,LVA,VH>(), new UploadAdapterPrefs());
        this.listener = listener;
        this.uploadDataItemsModel = new UploadDataItemModel(filesToUpload);
        setItems(uploadDataItemsModel.getUploadDataItemsReference());
        this.setHasStableIds(true);
    }

    public Bundle onSaveInstanceState(Bundle b, String key) {
        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putParcelable("data", uploadDataItemsModel);
        b.putBundle(key, savedInstanceState);
        return b;
    }

    public void onRestoreInstanceState(Bundle b, String key) {
        Bundle savedInstanceState = b.getBundle(key);
        if (savedInstanceState != null) {
            uploadDataItemsModel = savedInstanceState.getParcelable("data");
        }
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        return (VH)new UploadDataItemViewHolder<>(view);
    }

    @Override
    protected void removeItemFromInternalStore(int idxToRemove) {
        uploadDataItemsModel.remove(idxToRemove);
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GRID) {
            return LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_list_item_upload_grid_format, parent, false);
        } else {
            throw new IllegalStateException("viewType not supported" + viewType);
        }
    }

    public void removeAll(Collection<Uri> uris) {
        for(Uri uri : uris) {
            uploadDataItemsModel.remove(uri);
        }
        notifyDataSetChanged();
    }

    public void remove(Uri fileSelectedForUpload) {
        uploadDataItemsModel.remove(fileSelectedForUpload);
        notifyDataSetChanged();
    }

    public void setViewType(int viewType) {
        if (viewType != VIEW_TYPE_GRID && viewType != VIEW_TYPE_LIST) {
            throw new IllegalArgumentException("illegal view type");
        }
        this.viewType = viewType;
    }

    public void onDeleteButtonClicked(VH viewHolder, boolean longClick) {
        listener.onRemove((LVA)this, viewHolder.getItem().getUri(), longClick);
    }

    @Override
    public int getItemViewType(int position) {
        // only storing items of one type.
        return viewType;
    }

    @Override
    public int getItemCount() {
        return uploadDataItemsModel.size();
    }

    void updateUploadProgress(Uri fileBeingUploaded, int percentageComplete) {
        UploadDataItem item = uploadDataItemsModel.updateUploadProgress(fileBeingUploaded, percentageComplete);
        notifyItemChanged(uploadDataItemsModel.getItemPosition(item));
    }

    void updateCompressionProgress(Uri fileBeingCompressed, Uri compressedFile, int percentageComplete) {
        UploadDataItem item = uploadDataItemsModel.updateCompressionProgress(fileBeingCompressed, compressedFile, percentageComplete);
        notifyItemChanged(uploadDataItemsModel.getItemPosition(item));
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public Map<Uri,Long> getFilesAndSizes() {
        return uploadDataItemsModel.getFilesSelectedForUpload();
    }

    public boolean contains(Uri uri) {
        return uploadDataItemsModel.contains(uri);
    }


    /**
     * Note that this will not invoke a media scanner call.
     *
     *
     * @param uploadDataItem metadata for an item being uploaded, its upload progress, etc
     * @return true if the item was added (will be false if a matching item is already present based on file hashcode)
     */
    public boolean add(@NonNull UploadDataItem uploadDataItem) {
        return uploadDataItemsModel.add(uploadDataItem);
    }

    /**
     * @param filesForUpload files to be uploaded
     * @return List of all files that were not already present
     */
    public int addAll(List<UploadDataItem> filesForUpload) {
        int itemsAdded = uploadDataItemsModel.addAll(filesForUpload);
        notifyDataSetChanged();
        return itemsAdded;
    }

    public void clear() {
        uploadDataItemsModel.clear();
        notifyDataSetChanged();
    }

    String getItemMimeType(int i) {
        return uploadDataItemsModel.get(i).getMimeType();
    }

    public void setUploadDataItemsModel(UploadDataItemModel uploadDataItemsModel) {
        this.uploadDataItemsModel = uploadDataItemsModel;
        setItems(this.uploadDataItemsModel.getUploadDataItemsReference());
    }

    public UploadDataItemModel getUploadDataItemsModel() {
        return uploadDataItemsModel;
    }

    public interface RemoveListener<LVA extends FilesToUploadRecyclerViewAdapter<LVA,MSA,VH>, MSA extends UploadItemMultiSelectStatusAdapter<MSA, LVA,VH>, VH extends UploadDataItemViewHolder<VH, LVA,MSA>> {
        void onRemove(LVA adapter, Uri itemToRemove, boolean longClick);
    }

    public static class UploadAdapterPrefs extends BaseRecyclerViewAdapterPreferences<UploadAdapterPrefs> { }

}
