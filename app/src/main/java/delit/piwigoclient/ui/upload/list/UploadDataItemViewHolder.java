package delit.piwigoclient.ui.upload.list;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.material.button.MaterialButton;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.ui.view.recycler.CustomViewHolder;
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
        } else {
            fileForUploadMimeTypeImageView.setVisibility(View.GONE);
        }
        updateProgressFields(uploadDataItem);

    }

    private void updateProgressFields(UploadDataItem uploadDataItem) {
        // Configure the item upload progress fields
        if(uploadDataItem.isUploadFailed()) {
            fileUploadCancelledIndicator.setVisibility(View.VISIBLE);
        } else {
            fileUploadCancelledIndicator.setVisibility(View.GONE);
        }
        if (uploadDataItem.uploadProgress != null && uploadDataItem.uploadProgress.inProgress()) {

            // Update the progress bar text and values
            @StringRes int progressTextResId = R.string.uploading_progress_bar_message;
            if (uploadDataItem.uploadProgress.isMidCompression()) {
                progressTextResId = R.string.compressing_progress_bar_message;
            }

            progressBar.showMultiProgressIndicator(progressTextResId, uploadDataItem.uploadProgress.getUploadProgress(), uploadDataItem.uploadProgress.getCompressionProgress());

            // if has finished compressing the file, update the file size heading text
            if (uploadDataItem.uploadProgress.getCompressionProgress() == 100) {
                // change the shown file name and size to be that of the compressed file
                try {
                    fileNameField.setText(uploadDataItem.getFilename(itemView.getContext()));
                    itemHeading.setText(uploadDataItem.getFileSizeStr(itemView.getContext()));
                } catch (IllegalStateException e) {
                    // don't care - this happens due to file being deleted post upload
                }
            } else if(!uploadDataItem.uploadProgress.isMidCompression()) {
                Logging.log(Log.ERROR, TAG, "Finished compression but not set to 100%");
            }
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
