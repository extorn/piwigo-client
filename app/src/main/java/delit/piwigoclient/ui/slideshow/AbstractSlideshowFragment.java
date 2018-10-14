package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
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

import java.util.HashSet;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImagesGetResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomViewPager;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.util.SetUtils;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/05/17.
 */

public abstract class AbstractSlideshowFragment<T extends Identifiable&Parcelable> extends MyFragment {

    //    private static final String TAG = "SlideshowFragment";
    private static final String STATE_GALLERY = "gallery";
    private static final String STATE_GALLERY_ITEM_DISPLAYED = "galleryIndexOfItemToDisplay";
    private static final String STATE_ACTIVE_LOAD_THREADS = "activeLoadThreads";
    private final HashSet<Integer> pagesBeingLoaded = new HashSet<>();
    private CustomViewPager viewPager;
    private ResourceContainer<T, GalleryItem> gallery;
    private int rawCurrentGalleryItemPosition;
    private View progressIndicator;
    private GalleryItemAdapter<T, CustomViewPager> galleryItemAdapter;
    private AdView adView;

    public static Bundle buildArgs(ResourceContainer gallery, GalleryItem currentGalleryItem) {
        Bundle args = new Bundle();
        args.putParcelable(STATE_GALLERY, gallery);
        args.putInt(STATE_GALLERY_ITEM_DISPLAYED, gallery.getItemIdx(currentGalleryItem));
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_GALLERY, gallery);
        outState.putInt(STATE_GALLERY_ITEM_DISPLAYED, rawCurrentGalleryItemPosition);
        BundleUtils.putIntHashSet(outState, STATE_ACTIVE_LOAD_THREADS, pagesBeingLoaded);
    }

    @Override
    protected void updatePageTitle() {
        // Do nothing. This is handled by the items in the slideshow.
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().postSticky(new PiwigoAlbumUpdatedEvent(gallery));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Bundle configurationBundle = savedInstanceState;
        if (configurationBundle == null) {
            configurationBundle = getArguments();
        }
        if (configurationBundle != null) {
            gallery = configurationBundle.getParcelable(STATE_GALLERY);
            rawCurrentGalleryItemPosition = configurationBundle.getInt(STATE_GALLERY_ITEM_DISPLAYED);
            pagesBeingLoaded.clear();
            SetUtils.setNotNull(pagesBeingLoaded, BundleUtils.getIntHashSet(configurationBundle, STATE_ACTIVE_LOAD_THREADS));
        }

        super.onCreateView(inflater, container, savedInstanceState);

        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

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
            galleryItemAdapter = new GalleryItemAdapter(gallery, shouldShowVideos, rawCurrentGalleryItemPosition, getChildFragmentManager());
            galleryItemAdapter.setMaxFragmentsToSaveInState(40);
        } else {
            // update settings.
            galleryItemAdapter.setShouldShowVideos(shouldShowVideos);
        }

        galleryItemAdapter.setContainer(viewPager);
        viewPager.setAdapter(galleryItemAdapter);

        ViewPager.OnPageChangeListener slideshowPageChangeListener = new ViewPager.OnPageChangeListener() {

            int lastPage = -1;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (!gallery.isFullyLoaded()) {
                    if ((viewPager.getAdapter()).getCount() - position < 10) {
                        //if within 10 items of the end of those items currently loaded, load some more.
                        loadMoreGalleryResources();
                    }
                }

                if (adView != null && adView.getVisibility() == View.VISIBLE) {
                    ((AdsManager.MyBannerAdListener) adView.getAdListener()).replaceAd();
                }

                rawCurrentGalleryItemPosition = ((GalleryItemAdapter) viewPager.getAdapter()).getRawGalleryItemPosition(position);
                if (lastPage >= 0) {
                    ((GalleryItemAdapter) viewPager.getAdapter()).onPageDeselected(lastPage);
                }
                ((GalleryItemAdapter) viewPager.getAdapter()).onPageSelected(position);
                lastPage = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };
        viewPager.clearOnPageChangeListeners();
        viewPager.addOnPageChangeListener(slideshowPageChangeListener);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        if (viewPager != null && isSessionDetailsChanged()) {
            // If the page has been initialised already (not first visit), and the session token has changed, force reload.
            getFragmentManager().popBackStack();
            return;
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {

        super.onViewStateRestored(savedInstanceState);

        if (viewPager == null || viewPager.getAdapter() == null) {
            return;
        }

        try {
            int slideshowIndexToShow = ((GalleryItemAdapter) viewPager.getAdapter()).getSlideshowIndex(rawCurrentGalleryItemPosition);
            viewPager.setCurrentItem(slideshowIndexToShow);
        } catch (IllegalStateException e) {
            Crashlytics.logException(e);
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), "Slideshow item cannot be found. Waiting until items have loaded");
            }
        }
    }

    private void hideProgressIndicator() {
        progressIndicator.setVisibility(GONE);
    }

    public void showProgressIndicator() {
        progressIndicator.setVisibility(VISIBLE);
    }

    protected ResourceContainer<T, GalleryItem> getGallery() {
        return gallery;
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
        if (event.getItem().getParentId().equals(gallery.getId())) {
            getUiHelper().setTrackingRequest(event.getActionId());
            viewPager.setEnabled(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemActionFinishedEvent event) {
        //TODO this is rubbish, store a reference to the parent in the resource items so we can test if this screen is relevant.
        // parentId will be null if the parent is a Tag not an Album (viewing contents of a Tag).
        if ((event.getItem().getParentId() == null || event.getItem().getParentId().equals(gallery.getId()))
                && getUiHelper().isTrackingRequest(event.getActionId())) {
            viewPager.setEnabled(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent event) {
        if(gallery.getId() == event.item.getParentId()) {
            GalleryItemAdapter adapter = ((GalleryItemAdapter) viewPager.getAdapter());
            int fullGalleryIdx = adapter.getRawGalleryItemPosition(event.getAlbumResourceItemIdx());
            adapter.deleteGalleryItem(fullGalleryIdx);
        }
    }

    @Subscribe
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (gallery instanceof PiwigoAlbum && gallery.getId() == albumAlteredEvent.getAlbumAltered()) {
//            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_slideshow_out_of_sync_with_album));
        }
    }
/*
    @Override
    public void onResume() {
        super.onResume();
        if(viewPager != null && viewPager.getAdapter() != null) {
            ((GalleryItemAdapter) viewPager.getAdapter()).onResume();
        }
    }*/

    private void loadMoreGalleryResources() {
        int pageToLoad = gallery.getPagesLoaded();
        loadAlbumResourcesPage(pageToLoad);
    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (pagesBeingLoaded) {
            if (pagesBeingLoaded.contains(Integer.valueOf(pageToLoad))) {
                // already loading this page, ignore the request.
                return;
            }
            pagesBeingLoaded.add(pageToLoad);
            String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, getContext());
            String multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, getContext());
            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, getContext());

            long loadingMessageId;
            loadingMessageId = invokeResourcePageLoader(gallery, sortOrder, pageToLoad, pageSize, multimediaExtensionList);
            addActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId);
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
            synchronized (pagesBeingLoaded) {

                if (response instanceof BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) {
                    onGetResources((BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) response);
                    pagesBeingLoaded.remove(((BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) response).getPage());
                }
            }
        }

        public void onGetResources(final BaseImagesGetResponseHandler.PiwigoGetResourcesResponse response) {
            synchronized (this) {
                gallery.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
                pagesBeingLoaded.remove(response.getPage());
                viewPager.getAdapter().notifyDataSetChanged();
            }
        }
    }
}
