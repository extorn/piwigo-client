package delit.piwigoclient.ui.slideshow;

import android.annotation.SuppressLint;
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
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomViewPager;
import delit.libs.util.Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PagedList;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.SlideshowItemRefreshRequestEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;
import delit.piwigoclient.ui.slideshow.action.AlbumLoadResponseAction;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/05/17.
 */

public abstract class AbstractSlideshowFragment<F extends AbstractSlideshowFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends Identifiable & Parcelable & PhotoContainer> extends MyFragment<F,FUIH> {

    private static final String TAG = "AbsSlideshowFragment";
    private static final String STATE_ARG_GALLERY_TYPE = "containerModelType";
    private static final String ARG_GALLERY_ID = "containerId";
    private static final String ARG_GALLERY_ITEM_DISPLAYED = "indexOfItemInContainerToDisplay";
    private static final String STATE_FIRST_RESOURCE_IDX = "firstResourceIdx";
    private CustomViewPager viewPager;
    private int firstResourceIdx;
    private ResourceContainer<T, GalleryItem> resourceContainer;
    private View progressIndicator;
    private Class<? extends ViewModelContainer> modelType;
    private GalleryItemAdapter<T, CustomViewPager, ?,?> galleryItemAdapter;
    private AdView adView;
    private final PagedList.ChangeListener modelChangeListener = new SlideshowModelContentChangeListener();

    public static <T extends Identifiable & Parcelable> Bundle buildArgs(Class<? extends ViewModelContainer> modelType, ResourceContainer<T, GalleryItem> resourceContainer, GalleryItem currentItem) {
        Bundle args = new Bundle();
        Logging.log(Log.INFO, TAG, "Building slideshow using model type " + modelType);
        storeGalleryModelClassToBundle(args, modelType);
        args.putLong(ARG_GALLERY_ID, resourceContainer.getId());
        args.putInt(ARG_GALLERY_ITEM_DISPLAYED, resourceContainer.getItemIdx(currentItem));
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (BuildConfig.DEBUG) {
            BundleUtils.logSize("SlideshowFragment", outState);
        }
        outState.putInt(STATE_FIRST_RESOURCE_IDX, resourceContainer.getFirstResourceIdx());
        storeGalleryModelClassToBundle(outState, modelType);
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
    public void onDetach() {
        super.onDetach();
        resourceContainer.removeChangeListener(modelChangeListener);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    public void showItemAtSlideshowIndex(int moveToPage) {
        getViewPager().setCurrentItem(moveToPage);
    }

    private class SlideshowModelContentChangeListener implements PagedList.ChangeListener {

        @Override
        public void onChange(int changeType) {
            switch(changeType) {
                case ResourceContainer.CHANGE_RESOURCES_CLEARED:
                    // Deliberate fall-through
                case PagedList.CHANGE_ALL_ITEMS_REMOVED:
                    // Deliberate fall-through
                case PagedList.CHANGE_SORT_ORDER:
                    Logging.log(Log.DEBUG,TAG, "Slideshow accepts model change type : %1$d", changeType);
                    galleryItemAdapter.onModelChange();
                    galleryItemAdapter.notifyDataSetChanged();
                    break;
                default:
                    //Ignore
                    Logging.log(Log.DEBUG,TAG, "Slideshow ignores model change type : %1$d", changeType);
            }
        }
    }

    @Override
    public void onResume() {
        if (resourceContainer == null) {
            loadModelFromArguments();
        } else {
            if(galleryItemAdapter.isOutOfDate()) {
                try {
                    galleryItemAdapter.getItemByPagerPosition(viewPager.getCurrentItem());
                } catch(IndexOutOfBoundsException e) {
//                    viewPager.setCurrentItem(0);
                    loadMoreGalleryResources();
                }
            }
        }
        super.onResume();
        DisplayUtils.postOnUiThread(()-> {
            getUiHelper().showUserHint(TAG, 1, R.string.hint_slideshow_base_view_1);
            getUiHelper().showUserHint(TAG, 2, R.string.hint_slideshow_base_view_2);
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            if(savedInstanceState != null) {
                firstResourceIdx = savedInstanceState.getInt(STATE_FIRST_RESOURCE_IDX);
            } else {
                firstResourceIdx = -1;
            }
            loadModelFromArguments();
            super.onCreate(savedInstanceState);
        } catch (ModelUnavailableException e) {
            Logging.log(Log.ERROR, TAG, "Unable to create slideshow content as model isn't available.");
        }
    }

    protected void closeSlideshowAsapIfResourceContainerContentUnusable() {
        boolean resourceContainerEmpty = resourceContainer == null || resourceContainer.getItemCount() == 0;
        if (resourceContainerEmpty || firstResourceIdx >= 0 && resourceContainer.getFirstResourceIdx() != firstResourceIdx) {
            // attempt to get back to a working fragment.
            try {
                Logging.log(Log.INFO, TAG, "removing from activity immediately");
                getParentFragmentManager().popBackStackImmediate();
            } catch (RuntimeException e) {
                Logging.log(Log.WARN, TAG, "Unable to popBackStackImmediate - requesting it instead");
                getParentFragmentManager().popBackStack(); //TODO - work out why resource container can be null - after app kill and restore?
            }
        }
    }

    public static void storeGalleryModelClassToBundle(Bundle b, Class<? extends ViewModelContainer> modelClassname) {
        b.putString(STATE_ARG_GALLERY_TYPE, modelClassname.getName());
        Logging.log(Log.DEBUG, TAG, "Stored MVC type "+ modelClassname);
    }

    public static Class<? extends ViewModelContainer> loadGalleryModelClassFromBundle(Bundle b, String tag) {
        String modelClassname =  b.getString(STATE_ARG_GALLERY_TYPE);
        if(modelClassname == null) {
            Logging.log(Log.ERROR, tag, "Failed to load MVC type. Bundle does not contain required key");
            return null;
        }
        Logging.log(Log.DEBUG, tag, "Loaded MVC type "+ modelClassname);
        try {
            return Class.forName(modelClassname, true, ViewModelContainer.class.getClassLoader()).asSubclass(ViewModelContainer.class);
        } catch (ClassNotFoundException e) {
            Logging.log(Log.ERROR, tag, "Failed to load MVC type. Class not found");
            Logging.recordException(e);
            return null;
        }
    }

    private void loadModelFromArguments() {
        Logging.log(Log.VERBOSE,TAG, "Loading model from arguments : " + Utils.getId(this));
        Bundle arguments = getArguments();
        if(arguments == null) {
            throw new IllegalStateException("Unable to load model from null arguments");
        }
        Class<? extends ViewModelContainer> galleryModelClass = loadGalleryModelClassFromBundle(arguments,TAG);
        long galleryModelId = arguments.getLong(ARG_GALLERY_ID);

        if(galleryModelClass == null) {
            throw new IllegalStateException("gallery model type not available");
        }
        modelType = galleryModelClass;
        ViewModelContainer viewModelContainer = obtainActivityViewModel(requireActivity(), "" + galleryModelId, galleryModelClass);
        setContainerDetails(viewModelContainer.getModel());
        closeSlideshowAsapIfResourceContainerContentUnusable();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        if (resourceContainer == null) {
            return view; // because we can't use this, a pop backstack must be in progress
        }

        super.onCreateView(inflater, container, savedInstanceState);


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
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()
                && (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT
                || largeEnoughScreenSizeForAdvert)) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setAdListener(new AdsManager.MyBannerAdListener(adView));
            adView.setVisibility(VISIBLE);
        } else {
            adView.setVisibility(GONE);
        }

        viewPager = view.findViewById(R.id.slideshow_viewpager);
        boolean shouldShowVideos = AlbumViewPreferences.isIncludeVideosInSlideshow(prefs, requireContext());
        shouldShowVideos &= AlbumViewPreferences.isVideoPlaybackEnabled(prefs, requireContext());

        Bundle arguments = getArguments();
        if(arguments == null) {
            throw new IllegalStateException("Unable to load model from null arguments");
        }
        Class<? extends ViewModelContainer> galleryModelClass = loadGalleryModelClassFromBundle(arguments, TAG);
        int rawCurrentGalleryItemPosition = arguments.getInt(ARG_GALLERY_ITEM_DISPLAYED);

        if (galleryItemAdapter == null) {
            modelType = galleryModelClass;
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
            int pagerItemsIdx = galleryItemAdapter.getSlideshowIndexUsingFullGalleryIdx(rawCurrentGalleryItemPosition);
            viewPager.setCurrentItem(pagerItemsIdx);
        } catch (IllegalArgumentException e) {
            Logging.log(Log.WARN, TAG, "returning to album - slideshow empty");
            getParentFragmentManager().popBackStack();
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
                Logging.log(Log.INFO, TAG, "removing from activity as now not logged in");
                getParentFragmentManager().popBackStack();
            }
        }
    }

    private void reloadSlideshowModel() {
        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
        if(resourceContainer == null) {
            Logging.log(Log.WARN,TAG, , "Unable to reload slideshow because resource container is null");
        } else {
            T album = resourceContainer.getContainerDetails();
            reloadSlideshowModel(album, preferredAlbumThumbnailSize);
        }
    }

    protected void reloadSlideshowModel(T album, String preferredAlbumThumbnailSize) {
        if(album instanceof CategoryItem) {
            reloadAlbumSlideshowModel((CategoryItem)album, preferredAlbumThumbnailSize);
        }
    }

    private void reloadAlbumSlideshowModel(CategoryItem album, String preferredAlbumThumbnailSize) {
        AlbumLoadResponseAction<F,FUIH,?> action = new AlbumLoadResponseAction<>();
        getUiHelper().invokeActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetChildAlbumsResponseHandler(album, preferredAlbumThumbnailSize, false), action);
    }

    private void hideProgressIndicator() {
        progressIndicator.setVisibility(GONE);
    }

    public void showProgressIndicator() {
        progressIndicator.setVisibility(VISIBLE);
    }

    public ResourceContainer<T, GalleryItem> getResourceContainer() {
        return resourceContainer;
    }

    public void setContainerDetails(ResourceContainer<T, GalleryItem> model) {
        resourceContainer = model;
        resourceContainer.addChangeListener(modelChangeListener);
    }

    /**
     * Can't clean up on destroy view as events are then not delivered.
     */
    @Override
    public void onDestroy() {
        if (viewPager != null) {
            // clean up any existing adapter.
            if (galleryItemAdapter != null) {
                galleryItemAdapter.destroy();
            }
            viewPager.setAdapter(null);
        }
        super.onDestroy();
    }

    @Subscribe
    public void onEvent(SlideshowItemRefreshRequestEvent event) {
        if(!event.isHandled()) {
            try {
                if (null != galleryItemAdapter.getItemByPagerPosition(event.getSlideshowPageIdx())) {
//                    getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_slideshow_feature_refresh_coming_soon));
                    galleryItemAdapter.refreshItemAtSlideshowIdx(event.getSlideshowPageIdx());
                    // force a total refresh of the view pager content.
                    resyncPagerWithAdapter();
                } else {
                    int pageToLoad = calculatePageToLoad(galleryItemAdapter.getFullGalleryItemIdxFromSlideshowIdx(event.getSlideshowPageIdx()));
                    loadAlbumResourcesPage(pageToLoad);
                }
            } catch(IndexOutOfBoundsException e) {
                int pageToLoad = calculatePageToLoad(galleryItemAdapter.getFullGalleryItemIdxFromSlideshowIdx(event.getSlideshowPageIdx()));
                loadAlbumResourcesPage(pageToLoad);
            }
        }
    }

    private void resyncPagerWithAdapter() {
        int currentItem = viewPager.getCurrentItem();
        viewPager.setAdapter(galleryItemAdapter); // force a reload of all cached adapter items in the pager
        viewPager.setCurrentItem(currentItem);
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
    public void onEvent(AlbumItemDeletedEvent<?> event) {
        if (resourceContainer.getId() == event.item.getParentId()) {
            if (galleryItemAdapter != null) {
                galleryItemAdapter.deleteGalleryItem(event.getAlbumResourceItemIdx());
                Logging.log(Log.INFO, TAG, "item delete triggered in slideshow.");
                if (galleryItemAdapter.getCount() == 0) {
                    // slideshow is now empty close this page.
                    Logging.log(Log.INFO, TAG, "removing from activity as slideshow empty");
                    getParentFragmentManager().popBackStack();
                }
            }
        }
    }

    @Subscribe
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (resourceContainer instanceof PiwigoAlbum && resourceContainer.getId() == albumAlteredEvent.getAlbumAltered()) {
        }
    }

    protected GalleryItemAdapter<T, CustomViewPager, ?,?> getGalleryItemAdapter() {
        return galleryItemAdapter;
    }

    public CustomViewPager getViewPager() {
        return viewPager;
    }

    public void loadMoreGalleryResources() {
        int viewPagerPosition = viewPager.getCurrentItem();
        int currentGalleryIdx = galleryItemAdapter.getFullGalleryItemIdxFromSlideshowIdx(viewPagerPosition);
        int pageToLoad = calculatePageToLoad(currentGalleryIdx-1);
        loadAlbumResourcesPage(pageToLoad);
        int nextPageToLoad = calculatePageToLoad(currentGalleryIdx+1);
        if(nextPageToLoad != pageToLoad) {
            loadAlbumResourcesPage(nextPageToLoad);    // will need this too because the slideshow caches either side.
        }
    }

    private int calculatePageToLoad(int currentGalleryIdxShown) {
        int pageToLoad = resourceContainer.getPagesLoadedIdxToSizeMap();
        int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
        int pageNeeded = Math.max(0,Math.min(currentGalleryIdxShown, resourceContainer.getImgResourceCount())) / pageSize; // integer division
        if(currentGalleryIdxShown % pageSize > 0) {
            pageNeeded++;
        }
        if(!resourceContainer.isPageLoadedOrBeingLoaded(pageNeeded)) {
            return pageNeeded;
        }
        return pageToLoad;
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


            long loadingMessageId = invokeResourcePageLoader(resourceContainer, sortOrder, pageToActuallyLoad, pageSize);
            resourceContainer.recordPageBeingLoaded(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId, "loadResources"), pageToActuallyLoad);
        } finally {
            resourceContainer.releasePageLoadLock();
        }
    }

    private int getPageToActuallyLoad(int pageRequested, int pageSize) {
        boolean invertResourceSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        try {
            resourceContainer.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
        } catch(IllegalStateException e) {
            resourceContainer.removeAllResources();
            resourceContainer.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
        }
        int pageToActuallyLoad = pageRequested;
        if (invertResourceSortOrder) {
            int lastPageId = resourceContainer.getContainerDetails().getPagesOfPhotos(pageSize) -1;
            pageToActuallyLoad = lastPageId - pageRequested;
        }
        return pageToActuallyLoad;
    }

    protected abstract long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> containerDetails, String sortOrder, int pageToLoad, int pageSize);

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new AbstractSlideshowPiwigoResponseListener<>();
    }

    public String getLogTag() {
        return TAG;
    }

    protected void onGetResourcesFailed(PiwigoResponseBufferingHandler.Response response) {
        resourceContainer.recordPageLoadFailed(response.getMessageId());
    }

    void onResourcesReceived(int page, int pageSize, ArrayList<GalleryItem> resources) {
        resourceContainer.acquirePageLoadLock();
        boolean currentPageItemNeeded = false;
        try {
            galleryItemAdapter.getItemByPagerPosition(viewPager.getCurrentItem());
        } catch(IndexOutOfBoundsException e) {
            currentPageItemNeeded = true;
        }
        try {
            int firstPositionAddedAt = resourceContainer.addItemPage(page, pageSize, resources);
            galleryItemAdapter.onDataAppended(firstPositionAddedAt, resources.size());
        } finally {
            resourceContainer.releasePageLoadLock();
        }
        if(currentPageItemNeeded) {
            boolean currentPageItemLoaded;
            int currentSlideshowIdxInPager = 0;
            try {
                GalleryItem item = galleryItemAdapter.getItemByPagerPosition(viewPager.getCurrentItem());
                currentSlideshowIdxInPager =  galleryItemAdapter.getGalleryItemPosition(item);
                currentPageItemLoaded = true;
            } catch (IndexOutOfBoundsException e) {
                currentPageItemLoaded = false;
            }
            if( currentPageItemLoaded) {
                galleryItemAdapter.refreshItemAtSlideshowIdx(currentSlideshowIdxInPager);
                resyncPagerWithAdapter();
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
