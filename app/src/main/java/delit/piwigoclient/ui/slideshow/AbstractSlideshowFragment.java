package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImagesGetResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomViewPager;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/05/17.
 */

public abstract class AbstractSlideshowFragment<T extends Identifiable&Parcelable> extends MyFragment {

    private static final String STATE_GALLERY = "galleryModel";
    private static final String ARG_GALLERY_ITEM_DISPLAYED = "galleryIndexOfItemToDisplay";
    private CustomViewPager viewPager;
    private ResourceContainer<T, GalleryItem> galleryModel;
    private int rawCurrentGalleryItemPosition;
    private View progressIndicator;
    private GalleryItemAdapter<T, CustomViewPager> galleryItemAdapter;
    private AdView adView;

    public static <T extends Identifiable&Parcelable> Bundle buildArgs(ResourceContainer<T, GalleryItem> gallery, GalleryItem currentGalleryItem) {
        Bundle args = new Bundle();
        args.putParcelable(STATE_GALLERY, gallery);
        args.putInt(ARG_GALLERY_ITEM_DISPLAYED, gallery.getItemIdx(currentGalleryItem));
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_GALLERY, galleryModel);
        outState.putInt(ARG_GALLERY_ITEM_DISPLAYED, rawCurrentGalleryItemPosition);
    }

//    @Override
//    protected void updatePageTitle() {
//        // Do nothing. This is handled by the items in the slideshow.
//    }


    @Override
    protected String buildPageHeading() {
        return null;
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().postSticky(new PiwigoAlbumUpdatedEvent(galleryModel));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        Bundle configurationBundle = savedInstanceState;
        if (configurationBundle == null) {
            configurationBundle = getArguments();
        }
        if (configurationBundle != null && galleryModel == null) {
            galleryModel = configurationBundle.getParcelable(STATE_GALLERY);
            rawCurrentGalleryItemPosition = configurationBundle.getInt(ARG_GALLERY_ITEM_DISPLAYED);
        }

        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        progressIndicator = view.findViewById(R.id.slideshow_page_loadingIndicator);
        hideProgressIndicator();

        boolean largeEnoughScreenSizeForAdvert = false;
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float yInches= metrics.heightPixels/metrics.ydpi;
        if (yInches>=3) {
            largeEnoughScreenSizeForAdvert = true;
        }

        adView = view.findViewById(R.id.slideshow_adView);
        if (AdsManager.getInstance().shouldShowAdverts()
                && (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT
                || largeEnoughScreenSizeForAdvert)) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setAdListener(new AdsManager.MyBannerAdListener(adView));
            adView.setVisibility(VISIBLE);
        } else {
            adView.setVisibility(GONE);
        }

        viewPager = view.findViewById(R.id.slideshow_viewpager);
        boolean shouldShowVideos = AlbumViewPreferences.isIncludeVideosInSlideshow(prefs, getContext());
        shouldShowVideos &= AlbumViewPreferences.isVideoPlaybackEnabled(prefs, getContext());
        if (galleryItemAdapter == null) {
            galleryItemAdapter = new GalleryItemAdapter<>(galleryModel, shouldShowVideos, rawCurrentGalleryItemPosition, getChildFragmentManager());
            galleryItemAdapter.setMaxFragmentsToSaveInState(40);
        } else {
            // update settings.
            galleryItemAdapter.setShouldShowVideos(shouldShowVideos);
        }

        galleryItemAdapter.setContainer(viewPager);
        viewPager.setAdapter(galleryItemAdapter);

        ViewPager.OnPageChangeListener slideshowPageChangeListener = new CustomPageChangeListener();
        viewPager.clearOnPageChangeListeners();
        viewPager.addOnPageChangeListener(slideshowPageChangeListener);
        int pagerItemsIdx = galleryItemAdapter.getSlideshowIndex(rawCurrentGalleryItemPosition);
        viewPager.setCurrentItem(pagerItemsIdx);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        if (isSessionDetailsChanged()) {
            if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
                //trigger total screen refresh. Any errors will result in screen being closed.
                reloadSlideshowModel();
            } else {
                // immediately leave this screen.
                getFragmentManager().popBackStack();
            }
        }
    }

    private void reloadSlideshowModel() {
        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, getContext());
        T album = galleryModel.getContainerDetails();
        reloadSlideshowModel(album, preferredAlbumThumbnailSize);
    }

    protected void reloadSlideshowModel(T album, String preferredAlbumThumbnailSize) {
        if(album instanceof CategoryItem) {
            reloadAlbumSlideshowModel((CategoryItem)album, preferredAlbumThumbnailSize);
        }
    }

    private void reloadAlbumSlideshowModel(CategoryItem album, String preferredAlbumThumbnailSize) {
        UIHelper.Action action = new AlbumLoadResponseAction();
        getUiHelper().invokeActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetSubAlbumsResponseHandler(album, preferredAlbumThumbnailSize, false), action);
    }

    private void hideProgressIndicator() {
        progressIndicator.setVisibility(GONE);
    }

    public void showProgressIndicator() {
        progressIndicator.setVisibility(VISIBLE);
    }

    protected ResourceContainer<T, GalleryItem> getGalleryModel() {
        return galleryModel;
    }

    protected void setContainerDetails(ResourceContainer<T, GalleryItem> piwigoTag) {
        galleryModel = piwigoTag;
    }

    /**
     * Can't clean up on destroy view as events are then not delivered.
     */
    @Override
    public void onDestroy() {
        if (viewPager != null) {
            // clean up any existing adapter.
            GalleryItemAdapter adapter = (GalleryItemAdapter) viewPager.getAdapter();
            if (adapter != null) {
                adapter.destroy();
            }
            viewPager.setAdapter(null);
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PiwigoSessionTokenUseNotificationEvent event) {
        updateActiveSessionDetails();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemActionStartedEvent event) {
        if (event.getItem().getParentId().equals(galleryModel.getId())) {
            getUiHelper().setTrackingRequest(event.getActionId());
            viewPager.setEnabled(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemActionFinishedEvent event) {
        //TODO this is rubbish, store a reference to the parent in the resource items so we can test if this screen is relevant.
        // parentId will be null if the parent is a Tag not an Album (viewing contents of a Tag).
        if ((event.getItem().getParentId() == null || event.getItem().getParentId().equals(galleryModel.getId()))
                && getUiHelper().isTrackingRequest(event.getActionId())) {
            viewPager.setEnabled(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent event) {
        if(galleryModel.getId() == event.item.getParentId()) {
            GalleryItemAdapter adapter = ((GalleryItemAdapter) viewPager.getAdapter());
            int fullGalleryIdx = adapter.getRawGalleryItemPosition(event.getAlbumResourceItemIdx());
            adapter.deleteGalleryItem(fullGalleryIdx);
            if(adapter.getCount() == 0) {
                // slideshow is now empty close this page.
                getFragmentManager().popBackStack();
            }
        }
    }

    @Subscribe
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (galleryModel instanceof PiwigoAlbum && galleryModel.getId() == albumAlteredEvent.getAlbumAltered()) {
        }
    }

    protected void loadMoreGalleryResources() {
        int pageToLoad = galleryModel.getPagesLoaded();
        loadAlbumResourcesPage(pageToLoad);
    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        galleryModel.acquirePageLoadLock();
        try {
            if (galleryModel.isPageLoadedOrBeingLoaded(pageToLoad)) {
                return;
            }

            String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs,getContext());
            String multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs,getContext());
            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs,getContext());
            long loadingMessageId = invokeResourcePageLoader(galleryModel, sortOrder, pageToLoad, pageSize, multimediaExtensionList);
            galleryModel.recordPageBeingLoaded(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId), pageToLoad);
        } finally {
            galleryModel.releasePageLoadLock();
        }
    }

    protected abstract long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> containerDetails, String sortOrder, int pageToLoad, int pageSize, String multimediaExtensionList);

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) {
                onGetResources((BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) response);
            }
        }

        public void onGetResources(final BaseImagesGetResponseHandler.PiwigoGetResourcesResponse response) {
            galleryModel.acquirePageLoadLock();
            try {
                galleryModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
                ((GalleryItemAdapter)viewPager.getAdapter()).onDataAppended(response.getResources().size());
            } finally {
                galleryModel.releasePageLoadLock();
            }
        }
    }

    private class CustomPageChangeListener implements ViewPager.OnPageChangeListener {

        int lastPage = -1;

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            if (!galleryModel.isFullyLoaded()) {
                if ((viewPager.getAdapter()).getCount() - position < 10) {
                    //if within 10 items of the end of those items currently loaded, load some more.
                    loadMoreGalleryResources();
                }
            }

            if (adView != null && adView.getVisibility() == View.VISIBLE) {
                ((AdsManager.MyBannerAdListener) adView.getAdListener()).replaceAd();
            }

            if (lastPage >= 0) {
                ((GalleryItemAdapter) viewPager.getAdapter()).onPageDeselected(lastPage);
            }
            ((GalleryItemAdapter) viewPager.getAdapter()).onPageSelected(position);
            lastPage = position;
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    }

    private static class AlbumLoadResponseAction extends UIHelper.Action<AbstractSlideshowFragment,AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse> {

        @Override
        public boolean onSuccess(UIHelper uiHelper, AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {
            AbstractSlideshowFragment fragment = getActionParent(uiHelper);
            if(response.getAlbums().isEmpty()) {
                // will occur if the album no longer exists.
                fragment.getFragmentManager().popBackStack();
                return false;
            }
            CategoryItem currentAlbum = response.getAlbums().get(0);
            if(currentAlbum.getId() != fragment.galleryModel.getId()) {
                //Something wierd is going on - this should never happen
                Crashlytics.log(Log.ERROR, fragment.getTag(), "Closing slideshow - reloaded album had different id to that expected!");
                fragment.getFragmentManager().popBackStack();
                return false;
            }
            fragment.setContainerDetails(new PiwigoAlbum(currentAlbum));
            fragment.loadMoreGalleryResources();
            return true;
        }

        @Override
        public boolean onFailure(UIHelper uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            AbstractSlideshowFragment fragment = getActionParent(uiHelper);
            fragment.getFragmentManager().popBackStack();
            return false;
        }
    }
}
