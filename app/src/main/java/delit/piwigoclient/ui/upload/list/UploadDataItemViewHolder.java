package delit.piwigoclient.ui.upload.list;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.material.button.MaterialButton;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.CustomClickTouchListener;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.ui.upload.FilesToUploadRecyclerViewAdapter;

public class UploadDataItemViewHolder<IVH extends UploadDataItemViewHolder<IVH,LVA,MSA>,LVA extends FilesToUploadRecyclerViewAdapter<LVA,MSA,IVH>, MSA extends UploadItemMultiSelectStatusAdapter<MSA,LVA,IVH>> extends CustomViewHolder<IVH, LVA, FilesToUploadRecyclerViewAdapter.UploadAdapterPrefs, UploadDataItem, MSA> implements PicassoLoader.PictureItemImageLoaderListener<ImageView> {
    private static final String TAG = "UploadDataItemVH";
    private ProgressIndicator progressBar;
    private TextView fileNameField;
    private TextView itemHeading;
    private AppCompatImageView fileForUploadImageView;
    private ResizingPicassoLoader<ImageView> imageLoader;
    private ImageView fileForUploadMimeTypeImageView;
    private ImageView fileUploadCancelledIndicator;
    private TextView previouslyUploadedMarkerField;

    public UploadDataItemViewHolder(View view) {
        super(view);
    }

    public AppCompatImageView getFileForUploadImageView() {
        return fileForUploadImageView;
    }

    @Override
    public void redisplayOldValues(UploadDataItem uploadDataItem, boolean allowItemDeletion) {
        super.redisplayOldValues(uploadDataItem, allowItemDeletion);

        // Always do these actions when asked to render.

        if (!imageLoader.isImageLoading() && !imageLoader.isImageLoaded()) {
            imageLoader.load();
        }
        updateProgressFields(uploadDataItem);
        setCompressionIconColor();
    }

    @Override
    public void fillValues(UploadDataItem uploadDataItem, boolean allowItemDeletion) {
        setItem(uploadDataItem);
        fileNameField.setText(uploadDataItem.getFilename(itemView.getContext()));
        try {
            itemHeading.setText(uploadDataItem.getFileSizeStr(itemView.getContext()));
        } catch (IllegalStateException e) {
            // don't care - this happens due to file being deleted post upload
        }
        itemHeading.setVisibility(View.VISIBLE);


        imageLoader.setUriToLoad(uploadDataItem.getUri().toString());
        imageLoader.load();

        fileForUploadMimeTypeImageView.setVisibility(View.VISIBLE);
        if (MimeTypes.isVideo(uploadDataItem.getMimeType())) {
            fileForUploadMimeTypeImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.getResources(), R.drawable.ic_movie_filter_black_24px, itemView.getContext().getTheme()));
            fileForUploadMimeTypeImageView.setVisibility(View.VISIBLE);
        } else if (MimeTypes.isAudio(uploadDataItem.getMimeType())) {
            fileForUploadMimeTypeImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.getResources(), R.drawable.ic_audiotrack_black_24dp, itemView.getContext().getTheme()));
            fileForUploadMimeTypeImageView.setVisibility(View.VISIBLE);
        } else if (IOUtils.isImage(uploadDataItem.getMimeType())) {
            fileForUploadMimeTypeImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.getResources(), R.drawable.ic_baseline_image_black_24, itemView.getContext().getTheme()));
            fileForUploadMimeTypeImageView.setVisibility(View.VISIBLE);
        } else {
            fileForUploadMimeTypeImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.getResources(), R.drawable.ic_file_black_24dp, itemView.getContext().getTheme()));
            fileForUploadMimeTypeImageView.setVisibility(View.GONE);
        }
        if(uploadDataItem.isPreviouslyUploaded()) {
            previouslyUploadedMarkerField.setVisibility(View.VISIBLE);
        } else {
            previouslyUploadedMarkerField.setVisibility(View.GONE);
        }
        setCompressionIconColor();

        updateProgressFields(uploadDataItem);

    }

    private void setCompressionIconColor() {
        UploadDataItem item = getItem();
        @ColorRes int colorId;
        if(item.isCompressThisFile() == null) {
            if(item.isCompressByDefault()) {
                // compression set (may or may not require it)
                colorId = R.color.compress_this_file_yes_default;
            } else {
                if(item.isNeedsCompression()) {
                    // compression not set, but requires it
                    colorId = R.color.compress_this_file_needed;
                } else {
                    // compression not set, does not require it
                    colorId = R.color.transparent;
                }
            }
        } else {
            if(item.isCompressThisFile()) {
                // force compression
                colorId = R.color.compress_this_file_yes;
            } else {
                if(item.isNeedsCompression()) {
                    // force no compression but requires it
                    colorId = R.color.compress_this_file_needed;
                } else {
                    if(item.isCompressByDefault()) {
                        // force no compression (overriding default)
                        colorId = R.color.compress_this_file_no;
                    } else {
                        // compression not set either for this file or as a default
                        colorId = R.color.transparent;
                    }
                }
            }
        }
        fileForUploadMimeTypeImageView.setBackgroundColor(itemView.getResources().getColor(colorId));
    }

    private void updateProgressFields(UploadDataItem uploadDataItem) {
        // Configure the item upload progress fields
        if(uploadDataItem.isUploadFailed()) {
            fileUploadCancelledIndicator.setVisibility(View.VISIBLE);
        } else {
            fileUploadCancelledIndicator.setVisibility(View.GONE);
        }
        if (uploadDataItem.uploadProgress != null && uploadDataItem.uploadProgress.isCompressingOrUploading()) {

            // Update the progress bar text and values
            @StringRes int progressTextResId = R.string.uploading_progress_bar_message;
            if (uploadDataItem.uploadProgress.isMidCompression()) {
                if(uploadDataItem.uploadProgress.getCompressionProgress() == 100) {
                    progressTextResId = R.string.compressed_progress_bar_message;
                } else {
                    progressTextResId = R.string.compressing_progress_bar_message;
                }
                // change the shown file name and size to be that of the compressed file
                try {
                    //only refresh these if the size changes - realistically this is a solid test.
                    if(!itemHeading.getText().equals(uploadDataItem.getFileSizeStr(itemView.getContext()))) {
                        fileNameField.setText(uploadDataItem.getFilename(itemView.getContext()));
                        itemHeading.setText(uploadDataItem.getFileSizeStr(itemView.getContext()));
                    }
                } catch (IllegalStateException e) {
                    // don't care - this happens due to file being deleted post upload
                }
                if(uploadDataItem.uploadProgress.isMidUpload()) {
                    Logging.log(Log.ERROR, TAG, "Finished compression but not set to 100%");
                }
            }
            progressBar.showMultiProgressIndicator(progressTextResId, uploadDataItem.uploadProgress.getUploadProgress(), uploadDataItem.uploadProgress.getCompressionProgress());

        } else {
            progressBar.hideProgressIndicator();
        }
    }

    @Override
    public void cacheViewFieldsAndConfigure(FilesToUploadRecyclerViewAdapter.UploadAdapterPrefs adapterPrefs) {
        fileUploadCancelledIndicator = itemView.findViewById(R.id.cancelled_indicator);
        progressBar = itemView.findViewById(R.id.file_for_upload_progress);
        fileNameField = itemView.findViewById(R.id.file_for_upload_txt);
        itemHeading = itemView.findViewById(R.id.file_for_upload_heading_txt);
        MaterialButton deleteButton = itemView.findViewById(R.id.file_for_upload_delete_button);
        
        fileForUploadImageView = itemView.findViewById(R.id.file_for_upload_img);
        fileForUploadMimeTypeImageView = itemView.findViewById(R.id.type_indicator);
        previouslyUploadedMarkerField = itemView.findViewById(R.id.file_previously_uploaded);

        CustomClickTouchListener.callClickOnTouch(fileForUploadMimeTypeImageView, this::toggleCompression);

        imageLoader = new ResizingPicassoLoader<>(fileForUploadImageView, this, 0, 0);
        imageLoader.setUsePlaceholderIfNothingToLoad(true);

        fileForUploadImageView.setOnClickListener(v -> {
            if (!imageLoader.isImageLoaded()) {
                imageLoader.cancelImageLoadIfRunning();
                imageLoader.loadFromServer();
            }
        });
        fileForUploadImageView.setOnLongClickListener(v -> {
            imageLoader.cancelImageLoadIfRunning();
            imageLoader.loadFromServer();
            return true;
        });

        deleteButton.setOnClickListener(v -> onDeleteButtonClicked((IVH) this, false));
        deleteButton.setOnLongClickListener(v -> {
            onDeleteButtonClicked((IVH) this, true);
            return true;
        });
    }

    private void toggleCompression(View view) {
        getItem().setCompressThisFile(!Boolean.TRUE.equals(getItem().isCompressThisFile()));
        setCompressionIconColor();
    }

    private void onDeleteButtonClicked(IVH vh, boolean longClick) {
        getItemActionListener().getParentAdapter().onDeleteButtonClicked(vh, longClick);
    }

    @Override
    public void setChecked(boolean checked) {
        if(checked) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void onBeforeImageLoad(PicassoLoader<ImageView> loader) {
        fileForUploadImageView.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onImageLoaded(PicassoLoader<ImageView> loader, boolean success) {
        fileForUploadImageView.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onImageUnavailable(PicassoLoader<ImageView> loader, String lastLoadError) {
        fileForUploadImageView.setBackgroundColor(ContextCompat.getColor(fileForUploadImageView.getContext(), R.color.color_scrim_heavy));
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " '" + getItem().getUri() + "'";
    }
}
