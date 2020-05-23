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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;
import com.google.android.material.button.MaterialButton;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.Md5SumUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;

import static android.view.View.GONE;

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
    public static final String MEDIA_SCANNER_TASK_ID_FILES_FOR_UPLOAD = "id_filesForUpload";
    private final WeakReference<Context> contextRef;

    private UploadDataItemModel uploadDataItemsModel;
    private final RemoveListener listener;
    private final int scalingQuality = SCALING_QUALITY_MEDIUM;
    private int viewType = VIEW_TYPE_LIST;

    public FilesToUploadRecyclerViewAdapter(ArrayList<DocumentFile> filesToUpload, @NonNull Context context, RemoveListener listener) {
        contextRef = new WeakReference<>(context);
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

    private Context getContext() {
        return contextRef.get();
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final UploadDataItem uploadDataItem = uploadDataItemsModel.get(position);

        // Configure the progress bar (upload progress)

        holder.progressBar.setVisibility(GONE);
        holder.progressBarDescription.setVisibility(GONE);
        holder.progressBar.setIndeterminate(false);
        holder.progressBar.setProgress(0);
        holder.progressBar.setSecondaryProgress(0);

        if (uploadDataItem.uploadProgress != null) {
            if (uploadDataItem.uploadProgress.inProgress()) {
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBarDescription.setVisibility(View.VISIBLE);

                if (uploadDataItem.uploadProgress.uploadProgress < 0) {
                    holder.progressBar.setIndeterminate(true);
                } else {
                    holder.progressBar.setIndeterminate(false);
                    if (uploadDataItem.uploadProgress.uploadProgress > 0) {
                        holder.progressBarDescription.setText(R.string.uploading_progress_bar_message);
                    } else {
                        holder.progressBarDescription.setText(R.string.compressing_progress_bar_message);
                    }
                    holder.progressBar.setSecondaryProgress(uploadDataItem.uploadProgress.compressionProgress);
                    holder.progressBar.setProgress(uploadDataItem.uploadProgress.uploadProgress);
                    if (uploadDataItem.uploadProgress.compressionProgress == 100) {
                        // change the filesize to be that of the compressed file
                        try {
                            holder.itemHeading.setText(uploadDataItem.getFileSizeStr(getContext()));
                        } catch (IllegalStateException e) {
                            // don't care - this happens due to file being deleted post upload
                        }
                    }
                }
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
            holder.fileNameField.setText(item.getFilename(getContext()));
            try {
                holder.itemHeading.setText(item.getFileSizeStr(getContext()));
            } catch (IllegalStateException e) {
                // don't care - this happens due to file being deleted post upload
            }

            if (item.uri != null) {
                holder.imageLoader.setUriToLoad(item.uri.toString());
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

        viewHolder.fileForUploadImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!viewHolder.imageLoader.isImageLoaded()) {
                    viewHolder.imageLoader.loadNoCache();
                }
            }
        });

        viewHolder.deleteButton.setOnClickListener(v -> onDeleteButtonClicked(viewHolder, false));
        viewHolder.deleteButton.setOnLongClickListener(v -> {
            onDeleteButtonClicked(viewHolder, true);
            return true;
        });

        final ViewTreeObserver.OnPreDrawListener predrawListener = new UploadItemPreDrawListener(viewHolder, scalingQuality);
        viewHolder.fileForUploadImageView.addOnAttachStateChangeListener(new ImageViewAttachListener(viewHolder.fileForUploadImageView, predrawListener));

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

    public void updateUploadProgress(Uri fileBeingUploaded, int percentageComplete) {
        uploadDataItemsModel.updateUploadProgress(fileBeingUploaded, percentageComplete);
        notifyDataSetChanged();
    }

    public void updateCompressionProgress(Uri fileBeingCompressed, Uri compressedFile, int percentageComplete) {
        uploadDataItemsModel.updateCompressionProgress(fileBeingCompressed, compressedFile, percentageComplete);
        notifyDataSetChanged();
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public List<Uri> getFiles() {
        return uploadDataItemsModel.getFilesSelectedForUpload();
    }


    /**
     * Note that this will not invoke a media scanner call.
     *
     *
     * @param context
     * @param mediaContentUri
     * @return
     */
    public boolean add(@NonNull Context context, @NonNull Uri mediaContentUri) {
        return uploadDataItemsModel.add(context, mediaContentUri);
    }

    /**
     * @param filesForUpload
     * @return List of all files that were not already present
     */
    public ArrayList<Uri> addAll(List<Uri> filesForUpload) {
        ArrayList<Uri> newFiles = uploadDataItemsModel.addAll(getContext(), filesForUpload);
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
        private Uri uri;
        private String dataHashcode = null;
        private long dataLength = -1;
        private String filename = null;
        private UploadProgressInfo uploadProgress;

        public UploadDataItem(Parcel p) {
            uri = ParcelUtils.readParcelable(p, Uri.class);
            uploadProgress = ParcelUtils.readParcelable(p, UploadProgressInfo.class);
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

        public UploadDataItem(Uri uri) {
            this.uri = uri;
            uploadProgress = new UploadProgressInfo(uri);
            uid = getNextUid();
        }

        public String calculateDataHashCode(Context context) throws Md5SumUtils.Md5SumException {
            dataHashcode = Md5SumUtils.calculateMD5(context.getContentResolver(), uri);
            return dataHashcode;
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
            ParcelUtils.writeParcelable(dest, uri);
            ParcelUtils.writeParcelable(dest, uploadProgress);
            dest.writeValue(dataHashcode);
        }

        @Override
        public int describeContents() {
            return 0;
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
                DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
                if (!docFile.exists()) {
                    throw new IllegalStateException("file has already been deleted");
                }
                dataLength = docFile.length();
            }
            double sizeMb = BigDecimal.valueOf(dataLength).divide(BigDecimal.valueOf(1024 * 1024), 2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
            return String.format(Locale.getDefault(), "%1$.2fMB", sizeMb);
        }

        public String getFilename(Context context) {
            if(filename == null) {
                filename = DocumentFile.fromSingleUri(context, uri).getName();
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
                uploadDataItems.add(new UploadDataItem(f.getUri()));
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
         * @return List of all files that were not already present
         */
        public ArrayList<Uri> addAll(Context context, List<Uri> filesForUpload) {
            ArrayList<Uri> filesAdded = new ArrayList<>(filesForUpload.size());
            HashMap<String,UploadDataItem> itemsToAdd = new HashMap<>(filesForUpload.size());
            for (Uri f : filesForUpload) {
                UploadDataItem item = new UploadDataItem(f);
                try {
                    String dataHashCode = item.calculateDataHashCode(context);
                    itemsToAdd.put(dataHashCode, item);
                    filesAdded.add(item.uri);
                } catch (Md5SumUtils.Md5SumException e) {
                    Crashlytics.logException(e);
                }
            }
            Set<String> hashCodesAlreadyPresent = findDataHashCodes(itemsToAdd.keySet());
            for(String hashcode : hashCodesAlreadyPresent) {
                filesAdded.remove(Objects.requireNonNull(itemsToAdd.remove(hashcode)).uri);
            }
            uploadDataItems.addAll(itemsToAdd.values());
            return filesAdded;
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

        public boolean add(Context context, Uri uri) {
            return addAll(context, Collections.singletonList(uri)).size() == 1;
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
                Crashlytics.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
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
                        String filename = fileBeingUploaded == null ? null : fileBeingUploaded.toString();
                        Crashlytics.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + filename);
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

    public class ViewHolder extends RecyclerView.ViewHolder implements PicassoLoader.PictureItemImageLoaderListener {
        public final View mView;
        private final ProgressBar progressBar;
        private final TextView progressBarDescription;
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
            progressBarDescription = itemView.findViewById(R.id.file_for_upload_progress_description);
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
            return super.toString() + " '" + mItem.uri + "'";
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
