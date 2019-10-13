package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.CustomViewPager;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImagesGetResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/05/17.
 */

public abstract class AbstractSlideshowFragment<T extends Identifiable & Parcelable & PhotoContainer> extends MyFragment {

    private static final String TAG = "AbsSlideshowFragment";
    private static final String ARG_GALLERY_TYPE = "containerModelType";
    private static final String ARG_GALLERY_ID = "containerId";
    private static final String ARG_GALLERY_ITEM_DISPLAYED = "indexOfItemInContainerToDisplay";
    private CustomViewPager viewPager;
    private ResourceContainer<T, GalleryItem> resourceContainer;
    private View progressIndicator;
    private GalleryItemAdapter<T, CustomViewPager, ? extends SlideshowItemFragment<? extends ResourceItem>> galleryItemAdapter;
    private AdView adView;

    public static <T extends Identifiable & Parcelable> Bundle buildArgs(Class<? extends ViewModelContainer> modelType, ResourceContainer<T, GalleryItem> resourceContainer, GalleryItem currentItem) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_GALLERY_TYPE, modelType);
        args.putLong(ARG_GALLERY_ID, resourceContainer.getId());
        args.putInt(ARG_GALLERY_ITEM_DISPLAYED, resourceContainer.getDisplayIdx(currentItem));
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (BuildConfig.DEBUG) {
            BundleUtils.logSize("SlideshowFragment", outState);
        }
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
        // Need to unregister it now because if done in onDetach, it alters the UI after the saveInstanceState is called and thus crashes!
        getUiHelper().deregisterFromActiveServiceCalls();
        EventBus.getDefault().postSticky(new PiwigoAlbumUpdatedEvent(resourceContainer));
    }

    @Override
    public void onResume() {
        super.onResume();
        getUiHelper().showUserHint(TAG, 1, R.string.hint_slideshow_view_1);
        getUiHelper().showUserHint(TAG, 2, R.string.hint_slideshow_view_2);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            Class<? extends ViewModelContainer> galleryModelClass = (Class) getArguments().getSerializable(ARG_GALLERY_TYPE);
            long galleryModelId = getArguments().getLong(ARG_GALLERY_ID);

            ViewModelContainer viewModelContainer = ViewModelProviders.of(requireActivity()).get("" + galleryModelId, galleryModelClass);
            resourceContainer = viewModelContainer.getModel();


            if (resourceContainer == null) {
                // attempt to get back to a working fragment.
                try {
                    requireFragmentManager().popBackStackImmediate();
                } catch (RuntimeException e) {
                    Crashlytics.log(Log.WARN, TAG, "Unable to popBackStackImmediate - requesting it instead");
                    requireFragmentManager().popBackStack(); //TODO - work out why resource container can be null - after app kill and restore?
                }
                // stop child fragment creation
                throw new ModelUnavailableException();
            }

            super.onCreate(savedInstanceState);

        } catch (ModelUnavailableException e) {
            Crashlytics.log(Log.ERROR, getTag(), "Unable to create fragment as model isn't available.");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        if (resourceContainer == null) {
            return null;
        }

        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        progressIndicator = view.findViewById(R.id.slideshow_page_loadingIndicator);
        hideProgressIndicator();

        boolean largeEnoughScreenSizeForAdvert = false;
        DisplayMetrics metrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float yInches= metrics.heightPixels/metrics.ydpi;
        if (yInches>=3) {
            largeEnoughScreenSizeForAdvert = true;
        }

        adView = view.findViewById(R.id.slideshow_adView);
        if (AdsManager.getInstance().shouldShowAdverts()
                && (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT
                || largeEnoughScreenSizeForAdvert)) {
            adView.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
            adView.setAdListener(new AdsManager.MyBannerAdListener(adView));
            adView.setVisibility(VISIBLE);
        } else {
            adView.setVisibility(GONE);
        }

        viewPager = view.findViewById(R.id.slideshow_viewpager);
        boolean shouldShowVideos = AlbumViewPreferences.isIncludeVideosInSlideshow(prefs, requireContext());
        shouldShowVideos &= AlbumViewPreferences.isVideoPlaybackEnabled(prefs, getContext());

        Class<? extends ViewModelContainer> galleryModelClass = (Class) getArguments().getSerializable(ARG_GALLERY_TYPE);
        int rawCurrentGalleryItemPosition = getArguments().getInt(ARG_GALLERY_ITEM_DISPLAYED);

        if (galleryItemAdapter == null) {
            galleryItemAdapter = new GalleryItemAdapter<>(galleryModelClass, resourceContainer, shouldShowVideos, rawCurrentGalleryItemPosition, getChildFragmentManager());
            galleryItemAdapter.setMaxFragmentsToSaveInState(5); //TODO increase to 15 again once keep PiwigoAlbum model separate to the fragments.
        } else {
            // update settings with newest values.
            galleryItemAdapter.setShouldShowVideos(shouldShowVideos);
        }

        viewPager.clearOnPageChangeListeners(); // do before setContainer because that adds a listener
        galleryItemAdapter.setContainer(viewPager);
        viewPager.setAdapter(galleryItemAdapter);
        ViewPager.OnPageChangeListener slideshowPageChangeListener = new MyPageChangeListener();
        viewPager.addOnPageChangeListener(slideshowPageChangeListener);

        try {
            int pagerItemsIdx = galleryItemAdapter.getSlideshowIndex(rawCurrentGalleryItemPosition);
            viewPager.setCurrentItem(pagerItemsIdx);
        } catch (IllegalArgumentException e) {
            Crashlytics.log(Log.WARN, TAG, "returning to album - slideshow empty");
            requireFragmentManager().popBackStack();
        }

        return view;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        // overrride the gallery item adapter settings restored with up to date user choices
        boolean shouldShowVideos = AlbumViewPreferences.isIncludeVideosInSlideshow(prefs, requireContext());
        // update settings with newest values.
        galleryItemAdapter.setShouldShowVideos(shouldShowVideos);

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
                requireFragmentManager().popBackStack();
            }
        }
    }

    private void reloadSlideshowModel() {
        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
        T album = resourceContainer.getContainerDetails();
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

    protected ResourceContainer<T, GalleryItem> getResourceContainer() {
        return resourceContainer;
    }

    protected void setContainerDetails(ResourceContainer<T, GalleryItem> model) {
        resourceContainer = model;
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
        if (event.getItem().getParentId().equals(resourceContainer.getId())) {
            getUiHelper().setTrackingRequest(event.getActionId());
            viewPager.setEnabled(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemActionFinishedEvent event) {
        //TODO this is rubbish, store a reference to the parent in the resource items so we can test if this screen is relevant.
        // parentId will be null if the parent is a Tag not an Album (viewing contents of a Tag).
        if ((event.getItem().getParentId() == null || event.getItem().getParentId().equals(resourceContainer.getId()))
                && getUiHelper().isTrackingRequest(event.getActionId())) {
            viewPager.setEnabled(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent event) {
        if (resourceContainer.getId() == event.item.getParentId()) {
            GalleryItemAdapter adapter = ((GalleryItemAdapter) viewPager.getAdapter());
            if (adapter != null) {
                int fullGalleryIdx = adapter.getRawGalleryItemPosition(event.getAlbumResourceItemIdx());
                adapter.deleteGalleryItem(fullGalleryIdx);
                if (adapter.getCount() == 0) {
                    // slideshow is now empty close this page.
                    requireFragmentManager().popBackStack();
                }
            }
        }
    }

    @Subscribe
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (resourceContainer instanceof PiwigoAlbum && resourceContainer.getId() == albumAlteredEvent.getAlbumAltered()) {
        }
    }

    protected void loadMoreGalleryResources() {
        int pageToLoad = resourceContainer.getPagesLoaded();
        loadAlbumResourcesPage(pageToLoad);
    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        resourceContainer.acquirePageLoadLock();
        try {
            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
            int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);
            if (pageToActuallyLoad < 0) {
                // the sort order is inverted so we know for a fact this page is invalid.
                return;
            }

            if (resourceContainer.isPageLoadedOrBeingLoaded(pageToActuallyLoad)) {
                return;
            }

            String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, requireContext());
            Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, requireContext());

            long loadingMessageId = invokeResourcePageLoader(resourceContainer, sortOrder, pageToActuallyLoad, pageSize, multimediaExtensionList);
            resourceContainer.recordPageBeingLoaded(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId, "loadResources"), pageToActuallyLoad);
        } finally {
            resourceContainer.releasePageLoadLock();
        }
    }

    private int getPageToActuallyLoad(int pageRequested, int pageSize) {
        boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        resourceContainer.setRetrieveItemsInReverseOrder(invertSortOrder);
        int pageToActuallyLoad = pageRequested;
        if (invertSortOrder) {
            int pagesOfPhotos = resourceContainer.getContainerDetails().getPagesOfPhotos(pageSize);
            pageToActuallyLoad = pagesOfPhotos - pageRequested;
        }
        return pageToActuallyLoad;
    }

    protected abstract long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> containerDetails, String sortOrder, int pageToLoad, int pageSize, Set<String> multimediaExtensionList);

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private static class AlbumLoadResponseAction extends UIHelper.Action<AbstractSlideshowFragment, AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse> {

        @Override
        public boolean onSuccess(UIHelper<AbstractSlideshowFragment> uiHelper, AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {
            AbstractSlideshowFragment fragment = getActionParent(uiHelper);
            if (response.getAlbums().isEmpty()) {
                // will occur if the album no longer exists.
                fragment.requireFragmentManager().popBackStack();
                return false;
            }
            CategoryItem currentAlbum = response.getAlbums().get(0);
            if (currentAlbum.getId() != fragment.resourceContainer.getId()) {
                //Something wierd is going on - this should never happen
                Crashlytics.log(Log.ERROR, fragment.getTag(), "Closing slideshow - reloaded album had different id to that expected!");
                fragment.requireFragmentManager().popBackStack();
                return false;
            }
            fragment.setContainerDetails(new PiwigoAlbum(currentAlbum));
            fragment.loadMoreGalleryResources();
            return true;
        }

        @Override
        public boolean onFailure(UIHelper<AbstractSlideshowFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            AbstractSlideshowFragment fragment = getActionParent(uiHelper);
            fragment.requireFragmentManager().popBackStack();
            return false;
        }
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
            resourceContainer.acquirePageLoadLock();
            try {
                int firstPositionAddedAt = resourceContainer.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
                ((GalleryItemAdapter) viewPager.getAdapter()).onDataAppended(firstPositionAddedAt, response.getResources().size());
            } finally {
                resourceContainer.releasePageLoadLock();
            }
        }
    }

    private class MyPageChangeListener implements ViewPager.OnPageChangeListener {


        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            if (!resourceContainer.isFullyLoaded()) {
                if ((viewPager.getAdapter()).getCount() - position < 10) {
                    //if within 10 items of the end of those items currently loaded, load some more.
                    loadMoreGalleryResources();
                }
            }

            if (adView != null && adView.getVisibility() == View.VISIBLE) {
                ((AdsManager.MyBannerAdListener) adView.getAdListener()).replaceAd();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    }
}
