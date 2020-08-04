package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.util.ProgressListener;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class FilesToUploadRecyclerViewAdapter extends RecyclerView.Adapter<FilesToUploadRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = "F2UAdapter";
    public static final int VIEW_TYPE_LIST = 0;
    public static final int VIEW_TYPE_GRID = 1;

    private UploadDataItemModel uploadDataItemsModel;
    private final RemoveListener listener;
    private int viewType = VIEW_TYPE_LIST;

    public FilesToUploadRecyclerViewAdapter(ArrayList<DocumentFile> filesToUpload, RemoveListener listener) {
        this.listener = listener;
        this.uploadDataItemsModel = new UploadDataItemModel(filesToUpload);
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

    @Override
    public long getItemId(int position) {
        return uploadDataItemsModel.getItemUid(position);
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

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final UploadDataItem uploadDataItem = uploadDataItemsModel.get(position);

        // Configure the progress bar (upload progress)

        holder.progressBar.hideProgressIndicator();

        if (uploadDataItem.uploadProgress != null) {
            if (uploadDataItem.uploadProgress.inProgress()) {
                @StringRes int progressTextResId = R.string.uploading_progress_bar_message;
                if (uploadDataItem.uploadProgress.uploadProgress <= 0 && uploadDataItem.uploadProgress.compressionProgress > 0) {
                    progressTextResId = R.string.compressing_progress_bar_message;
                }
                holder.progressBar.showMultiProgressIndicator(progressTextResId, uploadDataItem.uploadProgress.uploadProgress, uploadDataItem.uploadProgress.compressionProgress);
                if (uploadDataItem.uploadProgress.compressionProgress == 100) {
                    // change the shown file size to be that of the compressed file
                    try {
                        holder.itemHeading.setText(uploadDataItem.getFileSizeStr(holder.mView.getContext()));
                    } catch (IllegalStateException e) {
                        // don't care - this happens due to file being deleted post upload
                    }
                }
            } else {
                holder.progressBar.hideProgressIndicator();
            }

            // Now we've updated the progress bar, we can return, no need to reload the remainder of the fields as they won't have altered.
            if (holder.getOldPosition() < 0 && uploadDataItem.equals(holder.mItem)) {
                return;
            }

            // store a reference to the item in this recyclable holder.
            holder.mItem = uploadDataItem;
        }

        // configure the filename field
        UploadDataItem item = holder.mItem;
        if (item == null) {
            item = uploadDataItemsModel.get(position);
        }

        if (item != null) {
            holder.fileNameField.setText(item.getFilename(holder.mView.getContext()));
            try {
                holder.itemHeading.setText(item.getFileSizeStr(holder.mView.getContext()));
            } catch (IllegalStateException e) {
                // don't care - this happens due to file being deleted post upload
            }
            holder.imageLoader.setUriToLoad(item.uri.toString());
            holder.itemHeading.setVisibility(View.VISIBLE);
            holder.imageLoader.load();
        } else {
            // theoretically this shouldn't happen I think
            holder.imageLoader.setFileToLoad(null);
            holder.itemHeading.setVisibility(View.INVISIBLE);
            Logging.log(Log.ERROR, TAG, "file to upload cannot be rendered as is null");
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_LIST) {
            throw new RuntimeException("List style not supported any more");
//            view = LayoutInflater.from(parent.getContext())
//                    .inflate(R.layout.layout_upload_list_item_list_format, parent, false);
        } else if (viewType == VIEW_TYPE_GRID) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_list_item_upload_grid_format, parent, false);
        } else {
            throw new IllegalStateException("viewType not supported" + viewType);
        }

        final ViewHolder viewHolder = new ViewHolder(view);

        viewHolder.fileForUploadImageView.setOnClickListener(v -> {
            if (!viewHolder.imageLoader.isImageLoaded()) {
                viewHolder.imageLoader.cancelImageLoadIfRunning();
                viewHolder.imageLoader.loadNoCache();
            }
        });
        viewHolder.fileForUploadImageView.setOnLongClickListener(v -> {
            viewHolder.imageLoader.cancelImageLoadIfRunning();
            viewHolder.imageLoader.loadNoCache();
            return true;
        });

        viewHolder.deleteButton.setOnClickListener(v -> onDeleteButtonClicked(viewHolder, false));
        viewHolder.deleteButton.setOnLongClickListener(v -> {
            onDeleteButtonClicked(viewHolder, true);
            return true;
        });
        return viewHolder;
    }

    protected void onDeleteButtonClicked(ViewHolder viewHolder, boolean longClick) {
        listener.onRemove(this, viewHolder.mItem.uri, longClick);
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
        uploadDataItemsModel.updateUploadProgress(fileBeingUploaded, percentageComplete);
        notifyDataSetChanged();
    }

    void updateCompressionProgress(Uri fileBeingCompressed, Uri compressedFile, int percentageComplete) {
        uploadDataItemsModel.updateCompressionProgress(fileBeingCompressed, compressedFile, percentageComplete);
        notifyDataSetChanged();
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public List<Uri> getFiles() {
        return uploadDataItemsModel.getFilesSelectedForUpload();
    }

    public boolean contains(Uri uri) {
        return uploadDataItemsModel.contains(uri);
    }


    /**
     * Note that this will not invoke a media scanner call.
     *
     *
     * @param uploadDataItem
     * @return
     */
    public boolean add(@NonNull UploadDataItem uploadDataItem) {
        return uploadDataItemsModel.add(uploadDataItem);
    }

    /**
     * @param filesForUpload
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

    public static class UploadDataItem implements Parcelable {

        private static long nextUid;
        private final long uid;
        private String mimeType;
        private Uri uri;
        private String dataHashcode = null;
        private long dataLength = -1;
        private String filename;
        private UploadProgressInfo uploadProgress;

        public UploadDataItem(Uri uri, String filename, String mimeType) {
            this.uri = uri;
            this.mimeType = mimeType;
            uploadProgress = new UploadProgressInfo(uri);
            this.filename = filename;
            uid = getNextUid();
        }

        protected UploadDataItem(Parcel in) {
            uid = in.readLong();
            mimeType = in.readString();
            uri = in.readParcelable(Uri.class.getClassLoader());
            dataHashcode = in.readString();
            dataLength = in.readLong();
            filename = in.readString();
            uploadProgress = in.readParcelable(UploadProgressInfo.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(uid);
            dest.writeString(mimeType);
            dest.writeParcelable(uri, flags);
            dest.writeString(dataHashcode);
            dest.writeLong(dataLength);
            dest.writeString(filename);
            dest.writeParcelable(uploadProgress, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<UploadDataItem> CREATOR = new Creator<UploadDataItem>() {
            @Override
            public UploadDataItem createFromParcel(Parcel in) {
                return new UploadDataItem(in);
            }

            @Override
            public UploadDataItem[] newArray(int size) {
                return new UploadDataItem[size];
            }
        };

        public void calculateDataHashCode(Context context, ProgressListener progressListener) throws Md5SumUtils.Md5SumException {
            dataHashcode = Md5SumUtils.calculateMD5(context.getContentResolver(), uri, progressListener);
        }

        private static long getNextUid() {
            nextUid++;
            if (nextUid < 0) {
                nextUid = 0;
            }
            return nextUid;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getItemUid() {
            return uid;
        }

        private String getFileSizeStr(@NonNull Context context) {
            if(dataLength < 0) {
                Uri uri = this.uri;
                if (uploadProgress != null && uploadProgress.fileBeingUploaded != null) {
                    uri = uploadProgress.fileBeingUploaded;
                }
                dataLength = IOUtils.getFilesize(context, uri);
            }
            double sizeMb = IOUtils.bytesToMb(dataLength);
            return String.format(Locale.getDefault(), "%1$.2fMB", sizeMb);
        }

        public String getFilename(Context context) {
            if(filename == null) {
                filename = IOUtils.getFilename(context, uri);
            }
            return filename;
        }
    }

    private static class UploadDataItemModel implements Parcelable {

        public static final Parcelable.Creator<UploadDataItemModel> CREATOR
                = new Parcelable.Creator<UploadDataItemModel>() {
            public UploadDataItemModel createFromParcel(Parcel in) {
                return new UploadDataItemModel(in);
            }

            public UploadDataItemModel[] newArray(int size) {
                return new UploadDataItemModel[size];
            }
        };
        private ArrayList<UploadDataItem> uploadDataItems;

        public UploadDataItemModel(Parcel p) {
            uploadDataItems = ParcelUtils.readArrayList(p, UploadDataItem.class.getClassLoader());
        }

        public UploadDataItemModel(ArrayList<DocumentFile> filesToUpload) {
            this.uploadDataItems = new ArrayList<>();
            for (DocumentFile f : filesToUpload) {
                uploadDataItems.add(new UploadDataItem(f.getUri(), IOUtils.getFilename(f), f.getType()));
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeArrayList(dest, uploadDataItems);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        private UploadDataItem getUploadDataItemForFileBeingUploaded(Uri f) {
            for (UploadDataItem item : uploadDataItems) {
                if (item.uploadProgress != null && item.uploadProgress.fileBeingUploaded.equals(f)) {
                    return item;
                }
            }
            return null;
        }

        public boolean contains(Uri uri) {
            return getUploadDataItemForFileSelectedForUpload(uri) != null;
        }

        private UploadDataItem getUploadDataItemForFileSelectedForUpload(Uri uri) {
            for (UploadDataItem item : uploadDataItems) {
                if (item.uri.equals(uri)) {
                    return item;
                }
            }
            return null;
        }

        /**
         * @param filesForUpload
         * @return count of items actually added
         */
        public int addAll(List<UploadDataItem> filesForUpload) {

            Set<String> hashcodesForFilesToAll = new HashSet<>(filesForUpload.size());
            for(UploadDataItem item : filesForUpload) {
                hashcodesForFilesToAll.add(item.dataHashcode);
            }

            Set<String> hashCodesAlreadyPresent = findDataHashCodes(hashcodesForFilesToAll);
            int itemsAdded = 0;
            for(UploadDataItem item : filesForUpload) {
                if(!hashCodesAlreadyPresent.contains(item.dataHashcode)) {
                    uploadDataItems.add(item);
                    itemsAdded++;
                }
            }
            return itemsAdded;
        }

        private Set<String> findDataHashCodes(Set<String> dataHashCodesToFind) {
            HashSet<String> foundHashcodes = new HashSet<>();
            for(UploadDataItem item : uploadDataItems) {
                if(dataHashCodesToFind.contains(item.dataHashcode)) {
                    foundHashcodes.add(item.dataHashcode);
                }
            }
            return foundHashcodes;
        }

        public boolean add(UploadDataItem uploadItem) {
            return addAll(Collections.singletonList(uploadItem)) == 1;
        }

        public long getItemUid(int position) {
            UploadDataItem uploadDataItem = uploadDataItems.get(position);
            return uploadDataItem.getItemUid();
        }

        public void remove(Uri fileSelectedForUpload) {
            UploadDataItem uploadItem = getUploadDataItemForFileSelectedForUpload(fileSelectedForUpload);
            uploadDataItems.remove(uploadItem);
        }

        public UploadDataItem get(int position) {
            return uploadDataItems.get(position);
        }

        public Uri getFileSelectedForUpload(int position) {
            return uploadDataItems.get(position).uri;
        }

        public int size() {
            return uploadDataItems.size();
        }

        public void updateCompressionProgress(Uri fileBeingCompressed, Uri compressedFile, int percentageComplete) {
            UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingCompressed);
            if (uploadDataItem == null) {
                String filename = fileBeingCompressed == null ? null : fileBeingCompressed.toString();
                Logging.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
            } else {
                UploadProgressInfo progress = uploadDataItem.uploadProgress;
                if (progress != null) {
                    progress.compressionProgress = percentageComplete;
                    progress.fileBeingUploaded = compressedFile;
                }
            }
        }

        public void updateUploadProgress(Uri fileBeingUploaded, int percentageComplete) {
            UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingUploaded);
            if (uploadDataItem == null) {
                String filename = fileBeingUploaded == null ? null : fileBeingUploaded.toString();
                Logging.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
            } else {
                UploadProgressInfo progress = uploadDataItem.uploadProgress;
                if (progress != null) {
                    progress.uploadProgress = percentageComplete;
                } else {
                    // we're uploading a compressed file
                    uploadDataItem = getUploadDataItemForFileBeingUploaded(fileBeingUploaded);
                    progress = uploadDataItem.uploadProgress;
                    if (progress != null) {
                        progress.uploadProgress = percentageComplete;
                    } else {
                        String filename = fileBeingUploaded == null ? null : fileBeingUploaded.toString();
                        Logging.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
                    }
                }
            }
        }

        public void clear() {
            uploadDataItems.clear();
        }

        public List<Uri> getFilesSelectedForUpload() {
            ArrayList<Uri> filesSelectedForUpload = new ArrayList<>(uploadDataItems.size());
            for (UploadDataItem item : uploadDataItems) {
                filesSelectedForUpload.add(item.uri);
            }
            return filesSelectedForUpload;
        }
    }

    private static class UploadProgressInfo implements Parcelable {

        public static final Parcelable.Creator<UploadProgressInfo> CREATOR
                = new Parcelable.Creator<UploadProgressInfo>() {
            public UploadProgressInfo createFromParcel(Parcel in) {
                return new UploadProgressInfo(in);
            }

            public UploadProgressInfo[] newArray(int size) {
                return new UploadProgressInfo[size];
            }
        };
        private Uri fileBeingUploaded;
        private int uploadProgress;
        private int compressionProgress;

        private boolean inProgress() {
            return uploadProgress + compressionProgress > 0;
        }

        public UploadProgressInfo(Parcel p) {
            fileBeingUploaded = ParcelUtils.readParcelable(p, Uri.class);
            uploadProgress = p.readInt();
            compressionProgress = p.readInt();
        }

        public UploadProgressInfo(Uri fileToUpload) {
            this.fileBeingUploaded = fileToUpload; // this value will be replaced if we start getting compression progress updates
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeParcelable(dest, fileBeingUploaded);
            dest.writeInt(uploadProgress);
            dest.writeInt(compressionProgress);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    public interface RemoveListener {
        void onRemove(FilesToUploadRecyclerViewAdapter adapter, Uri itemToRemove, boolean longClick);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements PicassoLoader.PictureItemImageLoaderListener {
        public final View mView;
        private final ProgressIndicator progressBar;
        private final TextView fileNameField;
        private final TextView itemHeading;
        private final MaterialButton deleteButton;
        private final AppCompatImageView fileForUploadImageView;
        private final ResizingPicassoLoader imageLoader;
        // data!
        private UploadDataItem mItem;


        public ViewHolder(View view) {
            super(view);
            mView = view;
            progressBar = itemView.findViewById(R.id.file_for_upload_progress);
            fileNameField = itemView.findViewById(R.id.file_for_upload_txt);
            itemHeading = itemView.findViewById(R.id.file_for_upload_heading_txt);
            deleteButton = itemView.findViewById(R.id.file_for_upload_delete_button);
            if (view.getContext() == null) {
                throw new IllegalStateException("Context is not available in the view at this time");
            }
            fileForUploadImageView = itemView.findViewById(R.id.file_for_upload_img);

            imageLoader = new ResizingPicassoLoader<>(fileForUploadImageView, this, 0, 0);
            imageLoader.setUsePlaceholderIfNothingToLoad(true);

        }

        @Override
        public void onBeforeImageLoad(PicassoLoader loader) {
            fileForUploadImageView.setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageLoaded(PicassoLoader loader, boolean success) {
            fileForUploadImageView.setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageUnavailable(PicassoLoader loader, String lastLoadError) {
            fileForUploadImageView.setBackgroundColor(ContextCompat.getColor(fileForUploadImageView.getContext(), R.color.color_scrim_heavy));
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + " '" + mItem.uri + "'";
        }
    }
}
