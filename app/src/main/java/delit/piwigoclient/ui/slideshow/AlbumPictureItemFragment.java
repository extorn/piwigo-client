package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.ortiz.touch.TouchImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToFileHandler;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.DisplayUtils;

public class AlbumPictureItemFragment extends SlideshowItemFragment<PictureResourceItem> {

    private static final String STATE_CURRENT_IMAGE_URL = "currentImageUrl";
    private String currentImageUrlDisplayed;
    private PicassoLoader loader;
    private TouchImageView imageView;

    public AlbumPictureItemFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
    }

    public static AlbumPictureItemFragment newInstance(PictureResourceItem galleryItem, long albumResourceItemIdx, long albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment fragment = new AlbumPictureItemFragment();
        fragment.setArguments(buildArgs(galleryItem, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
        return fragment;
    }
/*

    public static Bundle buildArgs(ResourceItem model, long albumResourceItemIdx, long albumResourceItemCount, long totalResourceItemCount) {
        return SlideshowItemFragment.buildArgs(model, albumResourceItemIdx, albumResourceItemCount,totalResourceItemCount);
    }
*/

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CURRENT_IMAGE_URL, currentImageUrlDisplayed);
    }

    /**
     * TODO break this code into two pieces - part to make the view and part that populates it (so we can reuse more of the view) DITTO video fragment.
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @param model
     * @return
     */
    @Nullable
    @Override
    public View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState, final PictureResourceItem model) {

        imageView = new TouchImageView(getContext());
        imageView.setMinimumHeight(DisplayUtils.dpToPx(getContext(), 120));
        imageView.setMinimumWidth(DisplayUtils.dpToPx(getContext(), 120));
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(layoutParams);

        CustomImageButton directDownloadButton = container.findViewById(R.id.slideshow_resource_action_direct_download);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_file_download_black_24px).into(directDownloadButton);
        directDownloadButton.setVisibility(View.GONE);

        loader = new PicassoLoader(imageView) {

            @Override
            public void load() {
                showProgressIndicator();
                imageView.setBackgroundColor(Color.TRANSPARENT);
                super.load();
            }

            @Override
            protected void onImageLoad(boolean success) {
                if(success) {
                    imageView.resetZoom();
                } else {
                    imageView.setBackgroundColor(Color.DKGRAY);
                }
                EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken()));
                hideProgressIndicator();
            }

            @Override
            protected void onImageUnavailable() {
                getLoadInto().setImageResource(R.drawable.ic_file_gray_24dp);
            }
        };

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(loader != null && !loader.isImageLoaded()) {
                    loader.load();
                }
            }
        });
        imageView.setOnLongClickListener(new View.OnLongClickListener() {


            @Override
            public boolean onLongClick(View v) {
                final SelectImageRenderDetailsDialog dialogFactory = new SelectImageRenderDetailsDialog(getContext());
                AlertDialog dialog = dialogFactory.buildDialog(imageView.getMaxZoom(), currentImageUrlDisplayed, model.getAvailableFiles(), new SelectImageRenderDetailsDialog.RenderDetailSelectListener() {
                    @Override
                    public void onSelection(String selectedUrl, float rotateDegrees, float maxZoom) {
                        currentImageUrlDisplayed = selectedUrl;
                        loader.setUriToLoad(currentImageUrlDisplayed);
                        //TODO work out how to do auto rotation!
                        if(0 != Float.compare(rotateDegrees, 0f)) {
                            loader.setRotation(rotateDegrees);
                        }
                        loader.load();
                        imageView.setMaxZoom(maxZoom);
                    }
                });
                dialog.show();
                return true;
            }
        });
        return imageView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(savedInstanceState != null) {
            currentImageUrlDisplayed = savedInstanceState.getString(STATE_CURRENT_IMAGE_URL);
        }

        // reset the screen state if we're entering for the first time
        if(currentImageUrlDisplayed == null) {
            // currently only the loader needs resetting.
            loader.resetAll();
        }

        // Load the content into the screen.

        PictureResourceItem model = getModel();
        if(currentImageUrlDisplayed == null) {
            String preferredImageSize = prefs.getString(getContext().getString(R.string.preference_gallery_item_slideshow_image_size_key), getContext().getString(R.string.preference_gallery_item_slideshow_image_size_default));
            for(ResourceItem.ResourceFile rf : model.getAvailableFiles()) {
                if(rf.getName().equals(preferredImageSize)) {
                    currentImageUrlDisplayed = rf.getUrl();
                    break;
                }
            }
            if(currentImageUrlDisplayed == null) {
                //Oh no - image couldn't be found - use the default.
                currentImageUrlDisplayed = model.getFullScreenImage().getUrl();
            }
        }
        loader.cancelImageLoadIfRunning();
        loader.setPlaceholderImageUri(model.getThumbnailUrl());
        loader.setUriToLoad(currentImageUrlDisplayed);
        loader.load();
    }

    @Override
    protected void onDownloadItem(final PictureResourceItem model) {
        super.onDownloadItem(model);
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Integer.MAX_VALUE, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_download));

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {

        if(getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                //Granted
                DownloadSelectionDialog dialogFactory = new DownloadSelectionDialog(getContext());
                AlertDialog dialog = dialogFactory.buildDialog(getModel().getName(), getModel().getAvailableFiles(), new DownloadSelectionDialog.DownloadSelectionListener() {
                    @Override
                    public void onSelection(ResourceItem.ResourceFile selectedItem) {
                        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File outputFile = new File(downloadsFolder, getModel().getDownloadFileName(selectedItem));
                        //TODO check what happens if file exists
                        //NOTE: Don't add to active service calls (we want control over the dialog displayed).
                        addDownloadAction(new ImageGetToFileHandler(selectedItem.getUrl(), outputFile).invokeAsync(getContext()));
                    }
                });
                dialog.show();


            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                AlbumPictureItemFragment.this.onGalleryItemActionFinished();
            }
        }
    }

    @Override
    public void onGetResource(final PiwigoResponseBufferingHandler.UrlToFileSuccessResponse response) {
        super.onGetResource(response);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_image_download_title, getString(R.string.alert_image_download_complete_message));
    }

    /**
     * Need to be certain to wipe this view down as we'll be reusing it.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        UIHelper.recycleImageViewContent(imageView);
        currentImageUrlDisplayed = null;
        //Enable the next line to cancel download of the image if not yet complete. This is very wasteful of network traffic though possibly essential for memory.
        loader.cancelImageLoadIfRunning();
        loader = null;
    }
}