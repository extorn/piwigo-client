package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.ortiz.touchview.TouchImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToFileHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.DisplayUtils;
import pl.droidsonroids.gif.GifImageView;

import static delit.piwigoclient.business.CustomImageDownloader.EXIF_WANTED_URI_FLAG;

public class AbstractAlbumPictureItemFragment extends SlideshowItemFragment<PictureResourceItem> {

    private static final String STATE_CURRENT_IMAGE_URL = "currentImageUrl";
    private String currentImageUrlDisplayed;
    private PicassoLoader loader;
    private ImageView imageView;

    public AbstractAlbumPictureItemFragment() {
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
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Nullable
    @Override
    public View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        imageView = createImageViewer();

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loader != null && !loader.isImageLoaded()) {
                    loader.loadNoCache();
                }
            }
        });

        return imageView;
    }

    protected ImageView createImageViewer() {
        return createStaticImageViewer();
    }

    protected ImageView createAnimatedGifViewer() {
        final GifImageView imageView = new GifImageView(getContext());

        imageView.setMinimumHeight(DisplayUtils.dpToPx(getContext(), 120));
        imageView.setMinimumWidth(DisplayUtils.dpToPx(getContext(), 120));
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(layoutParams);
        //TODO allow zooming in on the image.... or scrap all of this and load the gif into the ExoPlayer as a movie (probably better!)
//        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                getOverlaysVisibilityControl().runWithDelay(imageView);
                return false;
            }
        });

        loader = new PicassoLoader(imageView) {

            @Override
            public void load() {
                showProgressIndicator();
                imageView.setBackgroundColor(Color.TRANSPARENT);
                super.load();
            }

            @Override
            protected void onImageLoad(boolean success) {
                if (success) {
                    // do nothing
                } else {
                    imageView.setBackgroundColor(Color.DKGRAY);
                }
                EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
                hideProgressIndicator();
                getOverlaysVisibilityControl().runWithDelay(imageView);
            }

            @Override
            protected void onImageUnavailable() {
                getLoadInto().setImageResource(R.drawable.ic_file_gray_24dp);
            }
        };
        return imageView;
    }

    protected ImageView createStaticImageViewer() {
        //        imageView = container.findViewById(R.id.slideshow_image);
        final TouchImageView imageView = new TouchImageView(getContext());
        imageView.setMinimumHeight(DisplayUtils.dpToPx(getContext(), 120));
        imageView.setMinimumWidth(DisplayUtils.dpToPx(getContext(), 120));
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setOnTouchImageViewListener(new TouchImageView.OnTouchImageViewListener() {
            @Override
            public void onMove() {
                getOverlaysVisibilityControl().runWithDelay(imageView);
            }

            @Override
            public void onDrag(float deltaX, float deltaY, boolean actionAlteredImageViewState) {
//                if(!actionAlteredImageViewState && Math.abs(deltaX) < 10 && Math.abs(deltaY) > 30) {
//                    ToolbarEvent toolbarEvent = new ToolbarEvent();
//                    if(deltaY > 0) {
//                        toolbarEvent.setTitle(getModel().getName());
//                        toolbarEvent.setExpandToolbarView(true);
//                        EventBus.getDefault().post(toolbarEvent);
//                    } else {
//                        toolbarEvent.setTitle(getModel().getName());
//                        toolbarEvent.setContractToolbarView(true);
//                        EventBus.getDefault().post(toolbarEvent);
//                    }
//                }
            }
        });

        loader = new PicassoLoader(imageView) {

            @Override
            public void load() {
                showProgressIndicator();
                imageView.setBackgroundColor(Color.TRANSPARENT);
                super.load();
            }

            @Override
            protected void onImageLoad(boolean success) {
                if (success) {
                    imageView.resetZoom();
                } else {
                    imageView.setBackgroundColor(Color.DKGRAY);
                }
                EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
                hideProgressIndicator();
                getOverlaysVisibilityControl().runWithDelay(imageView);
            }

            @Override
            protected void onImageUnavailable() {
                getLoadInto().setImageResource(R.drawable.ic_file_gray_24dp);
            }
        };
        return imageView;
    }

    @Override
    protected void configureItemContent(@Nullable View itemContent, final PictureResourceItem model, @Nullable Bundle savedInstanceState) {
        super.configureItemContent(itemContent, model, savedInstanceState);

        imageView.setOnLongClickListener(new View.OnLongClickListener() {


            @Override
            public boolean onLongClick(View v) {
                final SelectImageRenderDetailsDialog dialogFactory = new SelectImageRenderDetailsDialog(getContext());
                AlertDialog dialog = dialogFactory.buildDialog(getCurrentImageUrlDisplayed(), model.getAvailableFiles(), new SelectImageRenderDetailsDialog.RenderDetailSelectListener() {
                    @Override
                    public void onSelection(String selectedUrl, float rotateDegrees, float maxZoom) {
                        currentImageUrlDisplayed = selectedUrl;
                        char separator = currentImageUrlDisplayed.indexOf('?') > 0 ? '&' : '?';
                        String uriToLoad = currentImageUrlDisplayed + separator + EXIF_WANTED_URI_FLAG;
                        loader.setUriToLoad(uriToLoad);
                        //TODO work out how to do auto rotation!
                        if (0 != Float.compare(rotateDegrees, 0f)) {
                            loader.setRotation(rotateDegrees);
                        } else {
                            loader.setRotation(0f);
                        }
                        loader.load();
                        if(imageView instanceof TouchImageView) {
                            ((TouchImageView)imageView).setMaxZoom(maxZoom);
                        }
                    }
                });
                dialog.show();
                return true;
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentImageUrlDisplayed = savedInstanceState.getString(STATE_CURRENT_IMAGE_URL);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getOverlaysVisibilityControl().addBottomSheetTransparency(getBottomSheet());

        // reset the screen state if we're entering for the first time
        if (currentImageUrlDisplayed == null) {
            // currently only the loader needs resetting.
            loader.resetAll();
        }

        // Load the content into the screen.

        PictureResourceItem model = getModel();
        if (currentImageUrlDisplayed == null) {
            String preferredImageSize = AlbumViewPreferences.getPreferredSlideshowImageSize(prefs, getContext());
            for (ResourceItem.ResourceFile rf : model.getAvailableFiles()) {
                if (rf.getName().equals(preferredImageSize)) {
                    currentImageUrlDisplayed = rf.getUrl();
                    break;
                }
            }
            if (currentImageUrlDisplayed == null) {
                //Oh no - image couldn't be found - use the default.
                int appHeight = getView().getRootView().getMeasuredHeight();
                int appWidth = getView().getRootView().getMeasuredWidth();
                if(appHeight == 0 || appWidth == 0) {
                    Point p = DisplayUtils.getRealScreenSize(getContext());
                    appHeight = p.y;
                    appWidth = p.x;
                }
                ResourceItem.ResourceFile fullscreenImage = model.getBestFitFile(appWidth, appHeight);
                if (fullscreenImage != null) {
                    currentImageUrlDisplayed = fullscreenImage.getUrl();
                } else {
                    // this is theoretically never going to happen. Only if bug in the image selection code.
                    currentImageUrlDisplayed = model.getFile("original").getUrl();
                }
            }
        }
        loader.cancelImageLoadIfRunning();
        loader.setPlaceholderImageUri(model.getThumbnailUrl());
        char separator = currentImageUrlDisplayed.indexOf('?') > 0 ? '&' : '?';
        String uriToLoad = currentImageUrlDisplayed + separator + EXIF_WANTED_URI_FLAG;
        loader.setUriToLoad(uriToLoad);
        loader.load();
    }

    @Override
    protected void onDownloadItem(final PictureResourceItem model) {
        super.onDownloadItem(model);
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Integer.MAX_VALUE, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_download));

    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {

        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                //Granted
                DownloadSelectionDialog dialogFactory = new DownloadSelectionDialog(getContext());
                AlertDialog dialog = dialogFactory.buildDialog(getModel().getName(), getCurrentImageUrlDisplayed(), getModel().getAvailableFiles(), new DownloadSelectionDialog.DownloadSelectionListener() {

                    @Override
                    public void onDownload(ResourceItem.ResourceFile selectedItem, String resourceName) {
                        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File outputFile = new File(downloadsFolder, getModel().getDownloadFileName(selectedItem));
                        //TODO check what happens if file exists
                        //NOTE: Don't add to active service calls (we want control over the dialog displayed).
                        addDownloadAction(getUiHelper().invokeSilentServiceCall(new ImageGetToFileHandler(selectedItem.getUrl(), outputFile)), false);
                    }

                    @Override
                    public void onShare(ResourceItem.ResourceFile selectedItem, String resourceName) {

                        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File outputFile = new File(downloadsFolder, getModel().getDownloadFileName(selectedItem));
                        //TODO check what happens if file exists
                        //NOTE: Don't add to active service calls (we want control over the dialog displayed).
                        addDownloadAction(getUiHelper().invokeSilentServiceCall(new ImageGetToFileHandler(selectedItem.getUrl(), outputFile)), true);
                    }
                });
                dialog.show();


            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                onGalleryItemActionFinished();
            }
        }
    }

    @Override
    public void onGetResource(final PiwigoResponseBufferingHandler.UrlToFileSuccessResponse response) {
        super.onGetResource(response);
        getUiHelper().showDetailedMsg(R.string.alert_image_download_title, getString(R.string.alert_image_download_complete_message));
    }

    public String getCurrentImageUrlDisplayed() {
        return currentImageUrlDisplayed;
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
    }
}