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
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.util.ParcelUtils;
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
    public static final int SCALING_QUALITY_PERFECT = Integer.MAX_VALUE;
    public static final int SCALING_QUALITY_VHIGH = 960;
    public static final int SCALING_QUALITY_HIGH = 480;
    public static final int SCALING_QUALITY_MEDIUM = 240;
    public static final int SCALING_QUALITY_LOW = 120;
    public static final int SCALING_QUALITY_VLOW = 60;

    private UploadDataItemModel uploadDataItemsModel;
    private final RemoveListener listener;
    private final int scalingQuality = SCALING_QUALITY_MEDIUM;
    private final MediaScanner mediaScanner;
    private int viewType = VIEW_TYPE_LIST;

    public FilesToUploadRecyclerViewAdapter(ArrayList<File> filesToUpload, MediaScanner mediaScanner, @NonNull Context context, RemoveListener listener) {
        this.listener = listener;
        this.uploadDataItemsModel = new UploadDataItemModel(filesToUpload);
        this.mediaScanner = mediaScanner;
        this.setHasStableIds(true);
        updateUris();
    }

    public Bundle onSaveInstanceState(Bundle b, String key) {
        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putParcelable("data", uploadDataItemsModel);
        b.putBundle(key, savedInstanceState);
        return b;
    }

    public void onRestoreInstanceState(Bundle b, String key) {
        Bundle savedInstanceState = b.getBundle(key);
        uploadDataItemsModel = savedInstanceState.getParcelable("data");
    }

    private void updateUris() {
        mediaScanner.invokeScan(new MediaScanner.MediaScannerScanTask(uploadDataItemsModel.getFilesSelectedForUpload(), 15) {
            @Override
            public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {
                for (Map.Entry<File, Uri> item : batchResults.entrySet()) {
                    uploadDataItemsModel.addMediaContentUri(item.getKey(), item.getValue());
                }
                notifyItemRangeChanged(firstResultIdx, batchResults.size());
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return uploadDataItemsModel.getItemUid(position);
    }

    public void remove(File fileSelectedForUpload) {
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

        if (uploadDataItem.uploadProgress != null) {
            holder.progressBar.setVisibility(View.VISIBLE);
            if (uploadDataItem.uploadProgress.uploadProgress < 0) {
                holder.progressBar.setIndeterminate(true);
            } else {
                holder.progressBar.setIndeterminate(false);
                holder.progressBar.setSecondaryProgress(uploadDataItem.uploadProgress.compressionProgress);
                holder.progressBar.setProgress(uploadDataItem.uploadProgress.uploadProgress);
                if (uploadDataItem.uploadProgress.compressionProgress == 100) {
                    // change the filesize to be that of the compressed file
                    holder.itemHeading.setText(uploadDataItem.getFileSizeStr());
                }
            }
            // Now we've updated the progress bar, we can return, no need to reload the remainder of the fields as they won't have altered.
            if (holder.getOldPosition() < 0 && uploadDataItem.equals(holder.mItem)) {
                return;
            }

            // store a reference to the item in this recyclable holder.
            holder.mItem = uploadDataItem;

        } else {
            holder.progressBar.setVisibility(View.GONE);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(0);
            holder.progressBar.setSecondaryProgress(0);
        }

        // configure the filename field
        UploadDataItem item = holder.mItem;
        if (item == null) {
            item = uploadDataItemsModel.get(position);
        }

        if (item != null) {
            holder.fileNameField.setText(item.fileToUpload.getName());
            holder.itemHeading.setText(item.getFileSizeStr());

            if (item.mediaStoreReference != null) {
                holder.imageLoader.setUriToLoad(item.mediaStoreReference.toString());
            } else {
                // TODO is the media store reference always up to date? Can it be relied upon to be?
//                holder.imageLoader.setFileToLoad(item.fileToUpload);
            }
            holder.itemHeading.setVisibility(View.VISIBLE);
        } else {
            // theoretically this shouldn't happen I think
            holder.imageLoader.setFileToLoad(null);
            holder.itemHeading.setVisibility(View.INVISIBLE);
            Crashlytics.log(Log.ERROR, TAG, "file to upload cannot be rendered as is null");
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_LIST) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_upload_list_item_list_format, parent, false);

        } else if (viewType == VIEW_TYPE_GRID) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_upload_list_item_grid_format, parent, false);
        } else {
            throw new IllegalStateException("viewType not supported" + viewType);
        }

        final ViewHolder viewHolder = new ViewHolder(view);

        viewHolder.fileForUploadImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!viewHolder.imageLoader.isImageLoaded()) {
                    viewHolder.imageLoader.loadNoCache();
                }
            }
        });

        viewHolder.deleteButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteButtonClicked(viewHolder, false);
            }
        });
        viewHolder.deleteButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onDeleteButtonClicked(viewHolder, true);
                return true;
            }
        });

        final ViewTreeObserver.OnPreDrawListener predrawListener = new UploadItemPreDrawListener(viewHolder, scalingQuality);
        viewHolder.fileForUploadImageView.addOnAttachStateChangeListener(new ImageViewAttachListener(viewHolder.fileForUploadImageView, predrawListener));

        return viewHolder;
    }

    protected void onDeleteButtonClicked(ViewHolder viewHolder, boolean longClick) {
        listener.onRemove(this, viewHolder.mItem.fileToUpload, longClick);
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

    public void updateUploadProgress(File fileBeingUploaded, int percentageComplete) {
        uploadDataItemsModel.updateUploadProgress(fileBeingUploaded, percentageComplete);
        notifyDataSetChanged();
    }

    public void updateCompressionProgress(File fileBeingCompressed, File compressedFile, int percentageComplete) {
        uploadDataItemsModel.updateCompressionProgress(fileBeingCompressed, compressedFile, percentageComplete);
        notifyDataSetChanged();
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public List<File> getFiles() {
        return uploadDataItemsModel.getFilesSelectedForUpload();
    }


    /**
     * Note that this will not invoke a media scanner call.
     *
     * @param fileForUpload
     * @param mediaContentUri
     * @return
     */
    public boolean add(@NonNull File fileForUpload, @NonNull Uri mediaContentUri) {
        return uploadDataItemsModel.add(fileForUpload, mediaContentUri);
    }
    /**
     * @param filesForUpload
     * @return List of all files that were not already present
     */
    public ArrayList<File> addAll(List<File> filesForUpload) {
        ArrayList<File> newFiles = uploadDataItemsModel.addAll(filesForUpload);
        updateUris();
        notifyDataSetChanged();
        return newFiles;
    }

    public void clear() {
        uploadDataItemsModel.clear();
        notifyDataSetChanged();
    }

    private static class UploadDataItem implements Parcelable {

        private static long nextUid;
        private final long uid;
        private File fileToUpload;
        private Uri mediaStoreReference;
        private UploadProgressInfo uploadProgress;

        public UploadDataItem(Parcel p) {
            fileToUpload = ParcelUtils.readFile(p);
            mediaStoreReference = ParcelUtils.readUri(p);
            uploadProgress = p.readParcelable(UploadProgressInfo.class.getClassLoader());
            uid = getNextUid();
        }

        public static final Parcelable.Creator<UploadDataItem> CREATOR
                = new Parcelable.Creator<UploadDataItem>() {
            public UploadDataItem createFromParcel(Parcel in) {
                return new UploadDataItem(in);
            }

            public UploadDataItem[] newArray(int size) {
                return new UploadDataItem[size];
            }
        };

        public UploadDataItem(File f, Uri uri) {
            this(f);
            mediaStoreReference = uri;
        }

        public UploadDataItem(File f) {
            fileToUpload = f;
            uploadProgress = new UploadProgressInfo(f);
            uid = getNextUid();
        }

        private static long getNextUid() {
            nextUid++;
            if (nextUid < 0) {
                nextUid = 0;
            }
            return nextUid;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeFile(dest, fileToUpload);
            ParcelUtils.writeUri(dest, mediaStoreReference);
            dest.writeParcelable(uploadProgress, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public long getItemUid() {
            return uid;
        }

        private String getFileSizeStr() {
            File f = fileToUpload;
            if (uploadProgress != null && uploadProgress.fileBeingUploaded != null) {
                f = uploadProgress.fileBeingUploaded;
            }
            long bytes = f.length();
            double sizeMb = ((double) bytes) / 1024 / 1024;
            return String.format(Locale.getDefault(), "%1$.2fMB", sizeMb);
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

        public UploadDataItemModel(ArrayList<File> filesToUpload) {
            this.uploadDataItems = new ArrayList<>();
            for (File f : filesToUpload) {
                uploadDataItems.add(new UploadDataItem(f));
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

        private boolean hasUploadDataItemForFileSelectedForUpload(File f) {
            return getUploadDataItemForFileSelectedForUpload(f) != null;
        }

        private UploadDataItem getUploadDataItemForFileBeingUploaded(File f) {
            for (UploadDataItem item : uploadDataItems) {
                if (item.uploadProgress != null && item.uploadProgress.fileBeingUploaded.equals(f)) {
                    return item;
                }
            }
            return null;
        }

        private UploadDataItem getUploadDataItemForFileSelectedForUpload(File f) {
            for (UploadDataItem item : uploadDataItems) {
                if (item.fileToUpload.equals(f)) {
                    return item;
                }
            }
            return null;
        }

        /**
         * @param filesForUpload
         * @return List of all files that were not already present
         */
        public ArrayList<File> addAll(List<File> filesForUpload) {
            ArrayList<File> filesAdded = new ArrayList<>(filesForUpload.size());
            for (File f : filesForUpload) {
                if (!hasUploadDataItemForFileSelectedForUpload(f)) {
                    uploadDataItems.add(new UploadDataItem(f));
                    filesAdded.add(f);
                }
            }
            return filesAdded;
        }

        public boolean add(File fileForUpload, Uri mediaContentUri) {
            if (!hasUploadDataItemForFileSelectedForUpload(fileForUpload)) {
                UploadDataItem newUploadItem = new UploadDataItem(fileForUpload, mediaContentUri);
                uploadDataItems.add(newUploadItem);
                return true;
            }
            return false;
        }

        public long getItemUid(int position) {
            UploadDataItem uploadDataItem = uploadDataItems.get(position);
            return uploadDataItem.getItemUid();
        }

        public void remove(File fileSelectedForUpload) {
            UploadDataItem uploadItem = getUploadDataItemForFileSelectedForUpload(fileSelectedForUpload);
            uploadDataItems.remove(uploadItem);
        }

        public UploadDataItem get(int position) {
            return uploadDataItems.get(position);
        }

        public File getFileSelectedForUpload(int position) {
            return uploadDataItems.get(position).fileToUpload;
        }

        public int size() {
            return uploadDataItems.size();
        }

        public void updateCompressionProgress(File fileBeingCompressed, File compressedFile, int percentageComplete) {
            UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingCompressed);
            UploadProgressInfo progress = uploadDataItem.uploadProgress;
            if (progress != null) {
                progress.compressionProgress = percentageComplete;
                progress.fileBeingUploaded = compressedFile;
            }
        }

        public void updateUploadProgress(File fileBeingUploaded, int percentageComplete) {
            UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingUploaded);
            if (uploadDataItem == null) {
                String filename = fileBeingUploaded == null ? null : fileBeingUploaded.getAbsolutePath();
                Crashlytics.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
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
                        String filename = fileBeingUploaded == null ? null : fileBeingUploaded.getAbsolutePath();
                        Crashlytics.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
                    }
                }
            }
        }

        public void clear() {
            uploadDataItems.clear();
        }

        public List<File> getFilesSelectedForUpload() {
            ArrayList<File> filesSelectedForUpload = new ArrayList<>(uploadDataItems.size());
            for (UploadDataItem item : uploadDataItems) {
                filesSelectedForUpload.add(item.fileToUpload);
            }
            return filesSelectedForUpload;
        }

        public void addMediaContentUri(File f, Uri uri) {
            UploadDataItem item = getUploadDataItemForFileSelectedForUpload(f);
            if (item != null) {
                item.mediaStoreReference = uri;
            }
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
        private File fileBeingUploaded;
        private int uploadProgress;
        private int compressionProgress;

        public UploadProgressInfo(Parcel p) {
            fileBeingUploaded = ParcelUtils.readFile(p);
            uploadProgress = p.readInt();
            compressionProgress = p.readInt();
        }

        public UploadProgressInfo(File fileToUpload) {
            this.fileBeingUploaded = fileToUpload; // this value will be replaced if we start getting compression progress updates
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeFile(dest, fileBeingUploaded);
            dest.writeInt(uploadProgress);
            dest.writeInt(compressionProgress);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    public interface RemoveListener {
        void onRemove(FilesToUploadRecyclerViewAdapter adapter, File itemToRemove, boolean longClick);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements PicassoLoader.PictureItemImageLoaderListener {
        public final View mView;
        private final ProgressBar progressBar;
        private final TextView fileNameField;
        private final TextView itemHeading;
        private final ImageButton deleteButton;
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
            Context context = view.getContext();
            if (context == null) {
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
            fileForUploadImageView.setBackgroundColor(Color.DKGRAY);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mItem.fileToUpload.getName() + "'";
        }
    }

    private static class UploadItemPreDrawListener implements ViewTreeObserver.OnPreDrawListener {

        private final ViewHolder viewHolder;
        private final int scalingQuality;

        public UploadItemPreDrawListener(ViewHolder viewHolder, int scalingQuality) {
            this.viewHolder = viewHolder;
            this.scalingQuality = scalingQuality;
        }

        @Override
        public boolean onPreDraw() {
            try {
                if (!viewHolder.imageLoader.isImageLoaded() && !viewHolder.imageLoader.isImageLoading() && !viewHolder.imageLoader.isImageUnavailable()) {
                    int imgSize = scalingQuality;
                    if (imgSize == Integer.MAX_VALUE) {
                        imgSize = viewHolder.fileForUploadImageView.getMeasuredWidth();
                    } else {
                        // need that math.max to ensure that the image size remains positive
                        //FIXME How can this ever be called before the ImageView object has a size?
                        imgSize = Math.max(SCALING_QUALITY_VLOW, Math.min(scalingQuality, viewHolder.fileForUploadImageView.getMeasuredWidth()));
                    }
                    viewHolder.imageLoader.setResizeTo(imgSize, imgSize);
                    viewHolder.imageLoader.load();
                }
            } catch (IllegalStateException e) {
                Crashlytics.logException(e);
                // image loader not configured yet...
            }
            return true;
        }
    }

    private static class ImageViewAttachListener implements View.OnAttachStateChangeListener {
        private final ViewTreeObserver.OnPreDrawListener predrawListener;
        private final AppCompatImageView imageView;

        public ImageViewAttachListener(AppCompatImageView imageView, ViewTreeObserver.OnPreDrawListener predrawListener) {
            this.imageView = imageView;
            this.predrawListener = predrawListener;
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            imageView.getViewTreeObserver().addOnPreDrawListener(predrawListener);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            imageView.getViewTreeObserver().removeOnPreDrawListener(predrawListener);
        }
    }
}
