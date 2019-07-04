package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.libs.ui.util.MediaScanner;
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
    private final UploadProgressModel uploadProgressModel;
    private final RemoveListener listener;
    private final int scalingQuality = SCALING_QUALITY_MEDIUM;
    private final MediaScanner mediaScanner;
    private boolean useDarkMode;
    private HashMap<File, Uri> currentDisplayContentUris = new HashMap<>();
    private int viewType = VIEW_TYPE_LIST;

    public FilesToUploadRecyclerViewAdapter(ArrayList<File> filesToUpload, MediaScanner mediaScanner, @NonNull Context context, RemoveListener listener) {
        this.listener = listener;
        this.uploadProgressModel = new UploadProgressModel(filesToUpload);
        this.mediaScanner = mediaScanner;
        this.setHasStableIds(true);
        updateUris();
    }

    private void updateUris() {
        if (uploadProgressModel.filesToUpload.isEmpty()) {
            currentDisplayContentUris.clear();
            return;
        }
        notifyDataSetChanged();
        mediaScanner.invokeScan(new MediaScanner.MediaScannerScanTask(uploadProgressModel.filesToUpload, 15) {
            @Override
            public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {
                currentDisplayContentUris.putAll(batchResults);
                notifyItemRangeChanged(firstResultIdx, batchResults.size());
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return uploadProgressModel.getItemUid(position);
    }

    public void remove(File item) {
        uploadProgressModel.remove(item);
        notifyDataSetChanged();
    }

    public void setViewType(int viewType) {
        if (viewType != VIEW_TYPE_GRID && viewType != VIEW_TYPE_LIST) {
            throw new IllegalArgumentException("illegal view type");
        }
        this.viewType = viewType;
    }

    public void setUseDarkMode(boolean useDarkMode) {
        this.useDarkMode = useDarkMode;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final ProgressInfo progressInfo = uploadProgressModel.get(position);

        View itemView = holder.mView;

        // Configure the progress bar (upload progress)

        if (progressInfo != null) {
            holder.progressBar.setVisibility(View.VISIBLE);
            if (progressInfo.uploadProgress < 0) {
                holder.progressBar.setIndeterminate(true);
            } else {
                holder.progressBar.setIndeterminate(false);
                holder.progressBar.setSecondaryProgress(progressInfo.compressionProgress);
                holder.progressBar.setProgress(progressInfo.uploadProgress);
                if (progressInfo.compressionProgress == 100) {
                    // change the filesize to be that of the compressed file
                    holder.itemHeading.setText(getFileSizeStr(progressInfo.fileBeingUploaded));
                }
            }
        } else {
            holder.progressBar.setVisibility(View.GONE);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(0);
            holder.progressBar.setSecondaryProgress(0);
        }

        // Now we've updated the progress bar, we can return, no need to reload the remainder of the fields as they won't have altered.
        if (holder.getOldPosition() < 0 && holder.mItem != null && progressInfo.isForItem(holder.mItem)) {
            return;
        }

        // store the item in this recyclable holder.
        holder.mItem = progressInfo.rawFile;

        // configure the filename field
        holder.fileNameField.setText(holder.mItem.getName());

        Uri itemUri = currentDisplayContentUris.get(holder.mItem);
        if (itemUri != null) {
            holder.imageLoader.setUriToLoad(itemUri.toString());
        } else {
            holder.imageLoader.setFileToLoad(holder.mItem);
        }

        holder.itemHeading.setVisibility(View.VISIBLE);
        holder.itemHeading.setText(getFileSizeStr(holder.mItem));

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

        if (useDarkMode) {
            view.setBackgroundColor(Color.WHITE);
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

    private String getFileSizeStr(File f) {
        long bytes = f.length();
        double sizeMb = ((double) bytes) / 1024 / 1024;
        return String.format("%1$.2fMB", sizeMb);
    }

    protected void onDeleteButtonClicked(ViewHolder viewHolder, boolean longClick) {
        listener.onRemove(this, viewHolder.mItem, longClick);
    }

    @Override
    public int getItemViewType(int position) {
        // only storing items of one type.
        return viewType;
    }

    @Override
    public int getItemCount() {
        return uploadProgressModel.size();
    }

    public void updateUploadProgress(File fileBeingUploaded, int percentageComplete) {
        uploadProgressModel.updateUploadProgress(fileBeingUploaded, percentageComplete);
        notifyDataSetChanged();
    }

    public void updateCompressionProgress(File fileBeingCompressed, File compressedFile, int percentageComplete) {
        uploadProgressModel.updateCompressionProgress(fileBeingCompressed, compressedFile, percentageComplete);
        notifyDataSetChanged();
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public ArrayList<File> getFiles() {
        return new ArrayList<>(uploadProgressModel.filesToUpload);
    }

    /**
     * @param filesForUpload
     * @return List of all files that were not already present
     */
    public ArrayList<File> addAll(List<File> filesForUpload) {
        ArrayList<File> newFiles = uploadProgressModel.addAll(filesForUpload);
        updateUris();
        notifyDataSetChanged();
        return newFiles;
    }

    public void clear() {
        uploadProgressModel.clear();
        notifyDataSetChanged();
    }

    private static class UploadProgressModel {

        private ArrayList<File> filesToUpload;
        private HashMap<File, ProgressInfo> progressDetails;

        public UploadProgressModel(ArrayList<File> filesToUpload) {
            this.filesToUpload = new ArrayList<>();
            progressDetails = new HashMap<>();
            addAll(filesToUpload);
        }

        /**
         * @param filesForUpload
         * @return List of all files that were not already present
         */
        public ArrayList addAll(List<File> filesForUpload) {
            ArrayList<File> newFiles = new ArrayList<>(filesForUpload);
            newFiles.removeAll(filesToUpload);
            filesToUpload.addAll(newFiles);
            for (File f : newFiles) {
                progressDetails.put(f, new ProgressInfo(f));
            }
            return newFiles;
        }

        public long getItemUid(int position) {
            return progressDetails.get(filesToUpload.get(position)).uid;
        }

        public void remove(File item) {
            filesToUpload.remove(item);
            progressDetails.remove(item);
        }

        public ProgressInfo get(int position) {
            File key = filesToUpload.get(position);
            if (key == null) {
                return null;
            }
            return progressDetails.get(key);
        }

        public int size() {
            return progressDetails.size();
        }

        public void updateCompressionProgress(File fileBeingCompressed, File compressedFile, int percentageComplete) {
            ProgressInfo progress = progressDetails.get(fileBeingCompressed);
            if (progress != null) {
                progress.compressionProgress = percentageComplete;
                progress.fileBeingUploaded = compressedFile;
            }
        }

        public void updateUploadProgress(File fileBeingUploaded, int percentageComplete) {
            ProgressInfo progress = progressDetails.get(fileBeingUploaded); // try to retrieve detail for this file presuming it was that requested by user
            if (progress != null) {
                progress.uploadProgress = percentageComplete;
            } else {
                // we're uploading a compressed file (need to hunt for the original file (Slow but not hundreds of compressed files I hope!)
                for (ProgressInfo item : progressDetails.values()) {
                    if (fileBeingUploaded.equals(item.fileBeingUploaded)) {
                        progress = item;
                        break;
                    }
                }
                if (progress != null) {
                    progress.uploadProgress = percentageComplete;
                } else {
                    Crashlytics.log(Log.ERROR, TAG, "Unable to locate upload progress object for file : " + fileBeingUploaded == null ? null : fileBeingUploaded.getAbsolutePath());
                }
            }
        }

        public void clear() {
            filesToUpload.clear();
            progressDetails.clear();
        }
    }

    private static class ProgressInfo {
        private static long nextUid;
        File fileBeingUploaded;
        File rawFile;
        int uploadProgress;
        int compressionProgress;
        long uid;

        public ProgressInfo(File fileToUpload) {
            this.rawFile = fileToUpload;
            this.fileBeingUploaded = fileToUpload; // this value will be replaced if we start getting compression progress updates
            this.uid = getNextUid();
        }

        private static long getNextUid() {
            nextUid++;
            if (nextUid < 0) {
                nextUid = 0;
            }
            return nextUid;
        }

        public boolean isForItem(File item) {
            return item != null && (item.equals(fileBeingUploaded) || item.equals(rawFile));
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
        public File mItem;


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

            imageLoader = new ResizingPicassoLoader(fileForUploadImageView, this, 0, 0);

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
            return super.toString() + " '" + mItem.getName() + "'";
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
