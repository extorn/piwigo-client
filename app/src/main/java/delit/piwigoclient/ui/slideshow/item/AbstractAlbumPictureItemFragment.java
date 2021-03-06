package delit.piwigoclient.ui.slideshow.item;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.documentfile.provider.DocumentFile;

import com.ortiz.touchview.TouchImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.CustomClickTouchListener;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.BaseSlideshowViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import pl.droidsonroids.gif.GifImageView;

import static delit.piwigoclient.business.CustomImageDownloader.EXIF_WANTED_URI_FLAG;

public class AbstractAlbumPictureItemFragment<F extends AbstractAlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends PictureResourceItem> extends SlideshowItemFragment<F,FUIH,T> implements PicassoLoader.PictureItemImageLoaderListener<TouchImageView> {

    private static final String STATE_CURRENT_IMAGE_URL = "currentImageUrl";
    private static final String TAG = "AbPicItemFrag";
    private String currentImageUrlDisplayed;
    private PicassoLoader<?> loader;
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

    protected PicassoLoader<?> getPicassoImageLoader() {
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

        imageLoadErrorView = Objects.requireNonNull(container).findViewById(R.id.image_load_error);

        imageView.setOnClickListener(v -> {
            if (loader != null && !loader.isImageLoaded()) {
                loader.cancelImageLoadIfRunning();
                loader.loadFromServer();
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
        imageView.setScaleType(BaseSlideshowViewPreferences.getSlideshowImageScalingType(prefs, requireContext()));
        //imageView.setRotateImageToFitScreen(AlbumViewPreferences.isRotateImageSoAspectMatchesScreenAspect(prefs, requireContext()));
        //This is sadly unavoidable. No other way seems to work at present.
        imageView.setOnTouchListener((v, e) -> {
            if(e.getAction() == MotionEvent.ACTION_UP) {
                getOverlaysVisibilityControl().runWithDelay(imageView);
            }
            return false;
        });

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
        new OnTouchInterceptListener(requireContext(), () -> getOverlaysVisibilityControl().runWithDelay(imageView)).attach(imageView);
        //CustomClickTouchListener.callClickOnTouch(imageView, (v) -> getOverlaysVisibilityControl().runWithDelay(imageView));
        return imageView;
    }

    /**
     * This passes all events down. it notes but does not sink them.
     */
    private static class OnTouchInterceptListener implements View.OnTouchListener {
        private final GestureDetectorCompat detector;

        public OnTouchInterceptListener(Context context, Runnable action) {
            GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    action.run();
                    return false;
                }
            };
            detector = new GestureDetectorCompat(context, listener);
            detector.setIsLongpressEnabled(false);
        }

        public void attach(View v) {
            v.setOnTouchListener(this);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            detector.onTouchEvent(event);
            return false;
        }
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
    protected void configureItemContent(@Nullable View itemContent, final T model, @Nullable Bundle savedInstanceState) {
        super.configureItemContent(itemContent, model, savedInstanceState);

        imageView.setOnLongClickListener(v -> {
            final SelectImageRenderDetailsDialog dialogFactory = new SelectImageRenderDetailsDialog(getContext());
            AlertDialog dialog = dialogFactory.buildDialog(getCurrentImageUrlDisplayed(), model, (selectedUrl, rotateDegrees, maxZoom) -> {
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
            });
            dialog.show();
            return true;
        });

        // Load the content into the screen.
        if (currentImageUrlDisplayed == null) {

            String preferredImageSize = BaseSlideshowViewPreferences.getPreferredSlideshowImageSize(prefs, requireContext());
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
        if(model != null) {
            loader.setPlaceholderImageUri(model.getThumbnailUrl());
        }
        if(currentImageUrlDisplayed != null) {
            char separator = currentImageUrlDisplayed.indexOf('?') > 0 ? '&' : '?';
            String uriToLoad = currentImageUrlDisplayed + separator + EXIF_WANTED_URI_FLAG;
            loader.setUriToLoad(uriToLoad);
        } else {
            StringBuilder availableFilesSb = new StringBuilder();
            for (Iterator<AbstractBaseResourceItem.ResourceFile> iterator = Objects.requireNonNull(model).getAvailableFiles().iterator(); iterator.hasNext(); ) {
                ResourceItem.ResourceFile rf = iterator.next();
                availableFilesSb.append(rf.getName());
                if(iterator.hasNext()) {
                    availableFilesSb.append(",");
                }
            }
            Logging.log(Log.ERROR, TAG, "Picture Item uri is null. Available files : " + availableFilesSb.toString());
        }
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
    protected void onDownloadItem(final T model) {
        super.onDownloadItem(model);
        DocumentFile downloadFolder = AppPreferences.getAppDownloadFolder(getPrefs(), requireContext());
        String permission = IOUtils.getManifestFilePermissionsNeeded(requireContext(), downloadFolder.getUri(), IOUtils.URI_PERMISSION_READ_WRITE);
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, permission, getString(R.string.alert_write_permission_needed_for_download));

    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {

        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                //Granted
                DownloadSelectionMultiItemDialog dialogFactory = new DownloadSelectionMultiItemDialog(getContext());
                AbstractBaseResourceItem.ResourceFile resourceFile = getModel().getResourceFileWithUri(getCurrentImageUrlDisplayed());
                AlertDialog dialog = dialogFactory.buildDialog(resourceFile.getName(), getModel(), new MyDownloadSelectionMultiItemListener());
                dialog.show();

            } else {
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                } else {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions_scoped_storage));
                }
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

    @Override
    public void onDestroy() {
        getUiHelper().setPiwigoResponseListener(null);// clear the reference to ensure no leaks.
        super.onDestroy();
    }

    private class MyDownloadSelectionMultiItemListener implements DownloadSelectionMultiItemDialog.DownloadSelectionMultiItemListener {

        @Override
        public void onDownload(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
            if(filesUnavailableToDownload.size() > 0) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new MyFilesUnavailableQuestionResultAdapter<>(getUiHelper(), items, selectedPiwigoFilesizeName));
            } else {
                doDownloadAction(items, selectedPiwigoFilesizeName, false);
            }

        }

        @Override
        public void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
            if(filesUnavailableToDownload.size() > 0) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<>(getUiHelper(), items, selectedPiwigoFilesizeName));
            } else {
                doDownloadAction(items, selectedPiwigoFilesizeName, true);
            }
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
                getUiHelper().showShortMsg(R.string.copied_to_clipboard);
            } else {
                Logging.logAnalyticEvent(context,"NoClipMgr", null);
            }
            EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), item));
        }



    }

    private static class UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<F extends AbstractAlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends PictureResourceItem> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        private final Set<ResourceItem> items;
        private final String selectedPiwigoFilesizeName;

        public UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter(FUIH uiHelper, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
            super(uiHelper);
            this.items = items;
            this.selectedPiwigoFilesizeName = selectedPiwigoFilesizeName;
        }

        protected UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter(Parcel in) {
            super(in);
            selectedPiwigoFilesizeName = in.readString();
            items = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(selectedPiwigoFilesizeName);
            ParcelUtils.writeSet(dest, items);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<?,?,?>> CREATOR = new Creator<UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<?,?,?>>() {
            @Override
            public UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<?,?,?> createFromParcel(Parcel in) {
                return new UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<>(in);
            }

            @Override
            public UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter<?,?,?>[] newArray(int size) {
                return new UIHelperAbstractAlbumPictureItemFragmentQuestionResultAdapter[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            getParent().doDownloadAction(items, selectedPiwigoFilesizeName, true);
        }
    }

    private static class MyFilesUnavailableQuestionResultAdapter<F extends AbstractAlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends PictureResourceItem> extends QuestionResultAdapter<FUIH,F> implements Parcelable {
        private final Set<ResourceItem> items;
        private final String selectedPiwigoFilesizeName;

        public MyFilesUnavailableQuestionResultAdapter(FUIH uiHelper, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
            super(uiHelper);
            this.items = items;
            this.selectedPiwigoFilesizeName = selectedPiwigoFilesizeName;
        }

        protected MyFilesUnavailableQuestionResultAdapter(Parcel in) {
            super(in);
            selectedPiwigoFilesizeName = in.readString();
            items = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(selectedPiwigoFilesizeName);
            ParcelUtils.writeSet(dest, items);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<MyFilesUnavailableQuestionResultAdapter<?,?,?>> CREATOR = new Creator<MyFilesUnavailableQuestionResultAdapter<?,?,?>>() {
            @Override
            public MyFilesUnavailableQuestionResultAdapter<?,?,?> createFromParcel(Parcel in) {
                return new MyFilesUnavailableQuestionResultAdapter<>(in);
            }

            @Override
            public MyFilesUnavailableQuestionResultAdapter<?,?,?>[] newArray(int size) {
                return new MyFilesUnavailableQuestionResultAdapter[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            getUiHelper().getParent().doDownloadAction(items, selectedPiwigoFilesizeName, false);
        }
    }

    public void doDownloadAction(Set<ResourceItem> items, String selectedPiwigoFilesizeName, boolean shareWithOtherAppsAfterDownload) {
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
}