package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.ortiz.touchview.TouchImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.Set;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import pl.droidsonroids.gif.GifImageView;

import static delit.piwigoclient.business.CustomImageDownloader.EXIF_WANTED_URI_FLAG;

public class AbstractAlbumPictureItemFragment extends SlideshowItemFragment<PictureResourceItem> implements PicassoLoader.PictureItemImageLoaderListener<TouchImageView> {

    private static final String STATE_CURRENT_IMAGE_URL = "currentImageUrl";
    private String currentImageUrlDisplayed;
    private PicassoLoader loader;
    private ImageView imageView;
    private ImageView imageLoadErrorView;
    private String fileSizeToShow;

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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CURRENT_IMAGE_URL, currentImageUrlDisplayed);
    }

    protected PicassoLoader getPicassoImageLoader() {
        return loader;
    }

    /**
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Nullable
    @Override
    public View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        imageView = createImageViewer();

        loader = new PicassoLoader<>(imageView, this);
        loader.rotateToFitScreen(AlbumViewPreferences.isRotateImageSoAspectMatchesScreenAspect(prefs, requireContext()));
        loader.setUsePlaceholderIfError(true);

        imageLoadErrorView = container.findViewById(R.id.image_load_error);

        imageView.setOnClickListener(v -> {
            if (loader != null && !loader.isImageLoaded()) {
                loader.loadNoCache();
            }
        });

        return imageView;
    }

    protected ImageView createImageViewer() {
        return createStaticImageViewer();
    }

    protected ImageView createStaticImageViewer() {
        //        imageView = container.findViewById(R.id.slideshow_image);
        final TouchImageView imageView = new TouchImageView(requireContext());
        imageView.setMinimumHeight(DisplayUtils.dpToPx(requireContext(), 120));
        imageView.setMinimumWidth(DisplayUtils.dpToPx(requireContext(), 120));
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(AlbumViewPreferences.getSlideshowImageScalingType(prefs, requireContext()));
        imageView.setRotateImageToFitScreen(AlbumViewPreferences.isRotateImageSoAspectMatchesScreenAspect(prefs, requireContext()));

        imageView.setOnTouchImageViewListener(() -> getOverlaysVisibilityControl().runWithDelay(imageView));

        return imageView;
    }

    protected ImageView createAnimatedGifViewer() {
        final GifImageView imageView = new GifImageView(getContext());

        imageView.setMinimumHeight(DisplayUtils.dpToPx(requireContext(), 120));
        imageView.setMinimumWidth(DisplayUtils.dpToPx(requireContext(), 120));
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imageView.setFreezesAnimation(true);

        imageView.setLayoutParams(layoutParams);
        //TODO allow zooming in on the image.... or scrap all of this and load the gif into the ExoPlayer as a movie (probably better!)
//        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        imageView.setOnTouchListener((v, event) -> {
            getOverlaysVisibilityControl().runWithDelay(imageView);
            return false;
        });

        return imageView;
    }

    @Override
    public void onBeforeImageLoad(PicassoLoader<TouchImageView> loader) {
        showProgressIndicator();
        imageView.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onImageLoaded(PicassoLoader<TouchImageView> loader, boolean success) {
        if (success) {
            // hide the placeholder marker if appropriate.
            imageView.setBackgroundColor(Color.TRANSPARENT);
            if (imageView instanceof TouchImageView) {
                TouchImageView touchImageView = ((TouchImageView) imageView);
                touchImageView.setMinZoom(TouchImageView.AUTOMATIC_MIN_ZOOM);
                float calcMinZoom = touchImageView.getMinZoom();
                touchImageView.setMinZoom(calcMinZoom / 2);
            }
            if (loader.hasPlaceholder()) {
                // placeholder loaded
                if (loader.isImageLoaded()) {
                    // hide the placeholder marker if appropriate.
                    imageLoadErrorView.setVisibility(View.GONE);
                }
            }
        }
        EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
        hideProgressIndicator();
        getOverlaysVisibilityControl().runWithDelay(imageView);
    }

    @Override
    public void onImageUnavailable(PicassoLoader<TouchImageView> loader, String lastLoadError) {
        if (!loader.hasPlaceholder()) {
            imageView.setBackgroundColor(ContextCompat.getColor(imageView.getContext(), R.color.color_scrim_heavy));
            imageView.setImageResource(R.drawable.ic_file_gray_24dp);
        } else {
            // show the placeholder marker
            imageLoadErrorView.setVisibility(View.VISIBLE);
        }
        if (!PiwigoSessionDetails.isCached(ConnectionPreferences.getActiveProfile()) && lastLoadError != null) {
            getUiHelper().showDetailedMsg(R.string.alert_error, lastLoadError);
        }
    }

    protected ImageView getImageView() {
        return imageView;
    }

    @Override
    protected void doOnceOnPageSelectedAndAdded() {
        super.doOnceOnPageSelectedAndAdded();
        boolean showFileSizeShowingMessage = AlbumViewPreferences.isShowFileSizeShowingMessage(prefs, requireContext());
        if (fileSizeToShow != null && showFileSizeShowingMessage) {
            getUiHelper().doOnce("currentImageSizeDisplayed", fileSizeToShow, () -> getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_showing_images_of_size, fileSizeToShow)));
        }
    }

    @Override
    protected void configureItemContent(@Nullable View itemContent, final PictureResourceItem model, @Nullable Bundle savedInstanceState) {
        super.configureItemContent(itemContent, model, savedInstanceState);

        imageView.setOnLongClickListener(v -> {
            final SelectImageRenderDetailsDialog dialogFactory = new SelectImageRenderDetailsDialog(getContext());
            AlertDialog dialog = dialogFactory.buildDialog(getCurrentImageUrlDisplayed(), model, new SelectImageRenderDetailsDialog.RenderDetailSelectListener() {
                @Override
                public void onSelection(String selectedUrl, float rotateDegrees, float maxZoom) {
                    currentImageUrlDisplayed = selectedUrl;
                    char separator = currentImageUrlDisplayed.indexOf('?') > 0 ? '&' : '?';
                    String uriToLoad = currentImageUrlDisplayed + separator + EXIF_WANTED_URI_FLAG;
                    loader.setUriToLoad(uriToLoad);
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
        });

        // Load the content into the screen.
        if (currentImageUrlDisplayed == null) {

            String preferredImageSize = AlbumViewPreferences.getPreferredSlideshowImageSize(prefs, requireContext());
            fileSizeToShow = preferredImageSize;
            for (ResourceItem.ResourceFile rf : model.getAvailableFiles()) {
                if (rf.getName().equals(preferredImageSize)) {
                    currentImageUrlDisplayed = model.getFileUrl(rf.getName());
                    break;
                }
            }
            if (currentImageUrlDisplayed == null) {
                //Oh no - image couldn't be found - use the default.
                ResourceItem.ResourceFile bestFitFile = null;
                if (itemContent != null) {
                    int appHeight = itemContent.getRootView().getMeasuredHeight();
                    int appWidth = itemContent.getRootView().getMeasuredWidth();
                    if (appHeight == 0 || appWidth == 0) {
                        Point p = DisplayUtils.getRealScreenSize(requireContext());
                        appHeight = p.y;
                        appWidth = p.x;
                    }
                    bestFitFile = model.getBestFitFile(appWidth, appHeight);
                }
                if (bestFitFile != null) {
                    fileSizeToShow = bestFitFile.getName();
                    currentImageUrlDisplayed = model.getFileUrl(bestFitFile.getName());
                } else {
                    // this is theoretically never going to happen. Only if bug in the image selection code.
                    fileSizeToShow = "original";
                    currentImageUrlDisplayed = model.getFileUrl(fileSizeToShow);
                }
            }
        }

        loader.resetAll();
        loader.cancelImageLoadIfRunning();
        loader.setPlaceholderImageUri(model.getThumbnailUrl());
        char separator = currentImageUrlDisplayed.indexOf('?') > 0 ? '&' : '?';
        String uriToLoad = currentImageUrlDisplayed + separator + EXIF_WANTED_URI_FLAG;
        loader.setUriToLoad(uriToLoad);
        loader.load();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentImageUrlDisplayed = savedInstanceState.getString(STATE_CURRENT_IMAGE_URL);
        }
    }

    @Override
    protected void onDownloadItem(final PictureResourceItem model) {
        super.onDownloadItem(model);
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Integer.MAX_VALUE, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_download));
        //        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.R, Integer.MAX_VALUE, Manifest.permission.MANAGE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_download));

    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {

        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                //Granted
                DownloadSelectionMultiItemDialog dialogFactory = new DownloadSelectionMultiItemDialog(getContext());
                AbstractBaseResourceItem.ResourceFile resourceFile = getModel().getResourceFileWithUri(getCurrentImageUrlDisplayed());
                AlertDialog dialog = dialogFactory.buildDialog(resourceFile.getName(), getModel(), new DownloadSelectionMultiItemDialog.DownloadSelectionMultiItemListener() {

                    private static final long serialVersionUID = -2904423848050523923L;

                    @Override
                    public void onDownload(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
                        if(filesUnavailableToDownload.size() > 0) {
                            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new UIHelper.QuestionResultAdapter(getUiHelper()) {
                                private static final long serialVersionUID = 1083163567307597434L;

                                @Override
                                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                    doDownloadAction(items, selectedPiwigoFilesizeName, false);
                                }
                            });
                        } else {
                            doDownloadAction(items, selectedPiwigoFilesizeName, false);
                        }

                    }

                    @Override
                    public void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
                        if(filesUnavailableToDownload.size() > 0) {
                            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new UIHelper.QuestionResultAdapter(getUiHelper()) {
                                private static final long serialVersionUID = -7827362664960685261L;

                                @Override
                                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                    doDownloadAction(items, selectedPiwigoFilesizeName, true);
                                }
                            });
                        } else {
                            doDownloadAction(items, selectedPiwigoFilesizeName, true);
                        }
                    }

                    private void doDownloadAction(Set<ResourceItem> items, String selectedPiwigoFilesizeName, boolean shareWithOtherAppsAfterDownload) {
                        ResourceItem item = items.iterator().next();
                        DownloadFileRequestEvent evt = new DownloadFileRequestEvent(shareWithOtherAppsAfterDownload);
                        if(item instanceof VideoResourceItem) {
                            File localCache = RemoteAsyncFileCachingDataSource.getFullyLoadedCacheFile(getContext(), Uri.parse(item.getFileUrl(item.getFullSizeFile().getName())));
                            if(localCache != null) {
                                String downloadFilename = item.getDownloadFileName(item.getFullSizeFile());
                                String remoteUri = item.getFileUrl(item.getFullSizeFile().getName());
                                evt.addFileDetail(item.getName(), remoteUri, downloadFilename, Uri.fromFile(localCache));
                            }
                        } else {
                            String downloadFilename = item.getDownloadFileName(item.getFile(selectedPiwigoFilesizeName));
                            String remoteUri = item.getFileUrl(selectedPiwigoFilesizeName);
                            evt.addFileDetail(item.getName(), remoteUri, downloadFilename);
                        }
                        EventBus.getDefault().post(evt);
                        EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), item));
                    }

                    @Override
                    public void onCopyLink(Context context, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
                        ResourceItem item = items.iterator().next();
                        String resourceName = item.getName();
                        ResourceItem.ResourceFile resourceFile = item.getFile(selectedPiwigoFilesizeName);
                        Uri uri = Uri.parse(item.getFileUrl(resourceFile.getName()));
                        ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(mgr != null) {
                            ClipData clipData = ClipData.newRawUri(context.getString(R.string.download_link_clipboard_data_desc, resourceName), uri);
                            mgr.setPrimaryClip(clipData);
                        } else {
                            FirebaseAnalytics.getInstance(context).logEvent("NoClipMgr", null);
                        }
                        EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), item));
                    }
                });
                dialog.show();

            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), getModel()));
            }
        }
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