package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomViewPager;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.SlideshowEmptyEvent;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.util.SetUtils;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/05/17.
 */

public abstract class AbstractSlideshowFragment<T extends Identifiable> extends MyFragment {

//    private static final String TAG = "SlideshowFragment";
    private static final String STATE_GALLERY = "gallery";
    private static final String STATE_GALLERY_ITEM_DISPLAYED = "galleryIndexOfItemToDisplay";
    private static final String STATE_ACTIVE_LOAD_THREADS = "activeLoadThreads";
    private CustomViewPager viewPager;
    private ResourceContainer<T, GalleryItem> gallery;
    private int rawCurrentGalleryItemPosition;
    private View progressIndicator;
    private final HashSet<Integer> pagesBeingLoaded = new HashSet<>();
    private GalleryItemAdapter galleryItemAdapter;

    public static Bundle buildArgs(ResourceContainer gallery, GalleryItem currentGalleryItem) {
        Bundle args = new Bundle();
        args.putSerializable(STATE_GALLERY, gallery);
        args.putInt(STATE_GALLERY_ITEM_DISPLAYED, gallery.getItemIdx(currentGalleryItem));
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_GALLERY, gallery);
        outState.putInt(STATE_GALLERY_ITEM_DISPLAYED, rawCurrentGalleryItemPosition);
        outState.putSerializable(STATE_ACTIVE_LOAD_THREADS, pagesBeingLoaded);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().postSticky(new PiwigoAlbumUpdatedEvent(gallery));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        if(isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        progressIndicator = view.findViewById(R.id.slideshow_page_loadingIndicator);
        hideProgressIndicator();

        AdView adView = view.findViewById(R.id.slideshow_adView);
        if(AdsManager.getInstance().shouldShowAdverts()
                && getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(VISIBLE);
        } else {
            adView.setVisibility(GONE);
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
        Bundle configurationBundle = savedInstanceState;
        if(configurationBundle == null) {
            configurationBundle = getArguments();
        }
        if (configurationBundle != null) {
            gallery = (ResourceContainer) configurationBundle.getSerializable(STATE_GALLERY);
            rawCurrentGalleryItemPosition = configurationBundle.getInt(STATE_GALLERY_ITEM_DISPLAYED);
            pagesBeingLoaded.clear();
            SetUtils.setNotNull(pagesBeingLoaded, (HashSet<Integer>) configurationBundle.getSerializable(STATE_ACTIVE_LOAD_THREADS));
        }

        if(viewPager != null && isSessionDetailsChanged()) {
            // If the page has been initialised already (not first visit), and the session token has changed, force reload.
            getFragmentManager().popBackStack();
            return;
        }

        viewPager = view.findViewById(R.id.slideshow_viewpager);
        boolean shouldShowVideos = prefs.getBoolean(getString(R.string.preference_gallery_include_videos_in_slideshow_key), getResources().getBoolean(R.bool.preference_gallery_include_videos_in_slideshow_default));
        shouldShowVideos &= prefs.getBoolean(getString(R.string.preference_gallery_enable_video_playback_key), getResources().getBoolean(R.bool.preference_gallery_enable_video_playback_default));
        if(galleryItemAdapter == null) {
            galleryItemAdapter = new GalleryItemAdapter(shouldShowVideos, rawCurrentGalleryItemPosition, getChildFragmentManager());
            galleryItemAdapter.setMaxFragmentsToSaveInState(1);
        } else {
            // update settings.
            galleryItemAdapter.setShouldShowVideos(shouldShowVideos);
            galleryItemAdapter.setFragmentManager(getChildFragmentManager());
        }

        viewPager.setAdapter(galleryItemAdapter);

        ViewPager.OnPageChangeListener slideshowPageChangeListener = new ViewPager.OnPageChangeListener() {

            int lastPage = -1;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if(!gallery.isFullyLoaded()) {
                    if((viewPager.getAdapter()).getCount() - position < 10) {
                        //if within 10 items of the end of those items currently loaded, load some more.
                        loadMoreGalleryResources();
                    }
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
        viewPager.addOnPageChangeListener(slideshowPageChangeListener);

    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {

        super.onViewStateRestored(savedInstanceState);

        if(viewPager == null || viewPager.getAdapter() == null) {
            return;
        }

        try {
            int slideshowIndexToShow = ((GalleryItemAdapter) viewPager.getAdapter()).getSlideshowIndex(rawCurrentGalleryItemPosition);
            viewPager.setCurrentItem(slideshowIndexToShow);
        } catch(IllegalStateException e) {
            if(BuildConfig.DEBUG) {
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
        if(viewPager != null) {
            // clean up any existing adapter.
            GalleryItemAdapter adapter = (GalleryItemAdapter)viewPager.getAdapter();
            if(adapter != null) {
                adapter.destroy();
            }
            viewPager.setAdapter(null);
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoSessionTokenUseNotificationEvent event) {
        updateActiveSessionDetails();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemActionStartedEvent event) {
        if(event.getItem().getParentId().equals(gallery.getId())) {
            getUiHelper().setTrackingRequest(event.getActionId());
            viewPager.setEnabled(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemActionFinishedEvent event) {
        //TODO this is rubbish, store a reference to the parent in the resource items so we can test if this screen is relevant.
        // parentId will be null if the parent is a Tag not an Album (viewing contents of a Tag).
        if((event.getItem().getParentId() == null || event.getItem().getParentId().equals(gallery.getId()))
                && getUiHelper().isTrackingRequest(event.getActionId())) {
            viewPager.setEnabled(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemDeletedEvent event) {
        int fullGalleryIdx = gallery.getItemIdx(event.item);
        ((GalleryItemAdapter) viewPager.getAdapter()).deleteGalleryItem(fullGalleryIdx);
        if(viewPager.getAdapter().getCount() == 0) {
            EventBus.getDefault().post(new SlideshowEmptyEvent());
        }
    }

    @Subscribe
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if(gallery instanceof PiwigoAlbum && gallery.getId() == albumAlteredEvent.id) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_slideshow_out_of_sync_with_album));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(viewPager != null && viewPager.getAdapter() != null) {
            ((GalleryItemAdapter) viewPager.getAdapter()).onResume();
        }
    }

    class GalleryItemAdapter extends MyFragmentRecyclerPagerAdapter {

        private final List<Integer> galleryResourceItems;
        private boolean shouldShowVideos;

        public GalleryItemAdapter(boolean shouldShowVideos, int selectedItem, FragmentManager fm) {
            super(fm);
            galleryResourceItems = new ArrayList<>(gallery.getResourcesCount());
            this.shouldShowVideos = shouldShowVideos;
            addResourcesToIndex(0, selectedItem);
        }

        private void addResourcesToIndex(int fromIdx, int selectedItem) {
            for (int i = fromIdx; i < gallery.getItemCount(); i++) {
                if (gallery.getItemByIdx(i) instanceof CategoryItem) {
                    continue;
                }
                if (!shouldShowVideos && gallery.getItemByIdx(i) instanceof VideoResourceItem && i != selectedItem) {
                    continue;
                }
                galleryResourceItems.add(i);
            }
        }


        public int getRawGalleryItemPosition(int slideshowPosition) {
            return galleryResourceItems.get(slideshowPosition);
        }


        @Override
        public int getItemPosition(@NonNull Object item) {
            ResourceItem model = ((SlideshowItemFragment) item).getModel();
            int fullGalleryIdx = gallery.getItemIdx(model);
            int newIndexPosition = galleryResourceItems.indexOf(fullGalleryIdx);
            if (newIndexPosition < 0) {
                return POSITION_NONE;
            }
            return newIndexPosition;
        }

        @Override
        public int getCount() {
            return galleryResourceItems.size();
        }

        private long getTotalSlideshowItems() {
            long ignoredResourceCount = gallery.getResourcesCount() - galleryResourceItems.size();
            return gallery.isFullyLoaded() ? galleryResourceItems.size() : gallery.getImgResourceCount() - ignoredResourceCount;
        }

        @Override
        public Class<? extends Fragment> getFragmentType(int position) {
            int slideshowIdx = galleryResourceItems.get(position);
            GalleryItem galleryItem = gallery.getItemByIdx(slideshowIdx);
            if (galleryItem instanceof PictureResourceItem) {
                return AlbumPictureItemFragment.class;
            } else if (galleryItem instanceof VideoResourceItem) {
                return AlbumVideoItemFragment.class;
            }
            throw new IllegalArgumentException("Unsupported slideshow item type at position " + position);
        }

        @Override
        protected void bindDataToFragment(Fragment fragment, int position) {
            GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItems.get(position));
            long totalSlideshowItems = getTotalSlideshowItems();

            if (galleryItem instanceof PictureResourceItem) {
                fragment.setArguments(SlideshowItemFragment.buildArgs((PictureResourceItem)galleryItem, position, galleryResourceItems.size(), totalSlideshowItems));
            } else if (galleryItem instanceof VideoResourceItem) {
                fragment.setArguments(AlbumVideoItemFragment.buildArgs((VideoResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems, false));
            }
        }

        @Override
        public Fragment createNewItem(Class<? extends Fragment> fragmentTypeNeeded, int position) {

            GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItems.get(position));
            SlideshowItemFragment fragment = null;
            long totalSlideshowItems = getTotalSlideshowItems();
            if (galleryItem instanceof PictureResourceItem) {
                fragment = new AlbumPictureItemFragment();
                Bundle b = AbstractSlideshowItemFragment.buildArgs((PictureResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems);
                fragment.setArguments(b);
            } else if (galleryItem instanceof VideoResourceItem) {
                fragment = new AlbumVideoItemFragment();
                Bundle args = AlbumVideoItemFragment.buildArgs((VideoResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems, false);
                fragment.setArguments(args);
            }
            if (fragment != null) {
                return fragment;
            }
            //TODO handle this better.
            throw new RuntimeException("unsupported gallery item.");
        }

        public void onPageSelected(int position) {
            SlideshowItemFragment selectedPage = (SlideshowItemFragment)instantiateItem(viewPager, position);
            selectedPage.onPageSelected();
        }

        public void onPageDeselected(int position) {
            Fragment managedFragment = getActiveFragment(position);
            if(managedFragment != null) {
                // if this slideshow item still exists (not been deleted by user)
                SlideshowItemFragment selectedPage = (SlideshowItemFragment)managedFragment;
                selectedPage.onPageDeselected();
            }
        }

        public int getSlideshowIndex(int rawCurrentGalleryItemPosition) {
            int idx = galleryResourceItems.indexOf(rawCurrentGalleryItemPosition);
            if(idx < 0) {
                throw new IllegalStateException("Item to show was not found in the gallery - weird!");
            }
            return idx;
        }

        public void deleteGalleryItem(int fullGalleryIdx) {
            int positionToDelete = galleryResourceItems.indexOf(fullGalleryIdx);
            if(positionToDelete >= 0) {
                // remove the item from the resource index and the backing gallery model
                gallery.remove(galleryResourceItems.remove(positionToDelete));
                // now recalcualate the positions of the remaining slideshow items in the main album
                for(int i = positionToDelete ; i < galleryResourceItems.size(); i++) {
                    galleryResourceItems.set(i, galleryResourceItems.get(i)-1);
                }
                notifyDataSetChanged();
            }
        }

        public void onResume() {
            int pageToShow = Math.max(0, viewPager.getCurrentItem());
            if(pageToShow < galleryResourceItems.size()) {
                Fragment selectedPage = (Fragment)instantiateItem(viewPager, pageToShow);
                if(selectedPage.isAdded()) {
                    if (selectedPage instanceof AlbumVideoItemFragment) {
                        ((AlbumVideoItemFragment) selectedPage).onManualResume();
                    }
                }
            } else {
                // immediately leave this screen. For whatever reason, we can't show a valid item.
                getFragmentManager().popBackStack();
            }
        }

        @Override
        public void notifyDataSetChanged() {
            int lastLoadedIdx = galleryResourceItems.get(galleryResourceItems.size() - 1);
            addResourcesToIndex(1 + lastLoadedIdx, -1);
            EventBus.getDefault().post(new SlideshowSizeUpdateEvent(galleryResourceItems.size(), getTotalSlideshowItems()));
            super.notifyDataSetChanged();
        }

        public void setShouldShowVideos(boolean shouldShowVideos) {
            this.shouldShowVideos = shouldShowVideos;
        }
    }

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
            String sortOrder = prefs.getString(getString(R.string.preference_gallery_sortOrder_key), getString(R.string.preference_gallery_sortOrder_default));
            String multimediaExtensionList = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
            int pageSize = prefs.getInt(getString(R.string.preference_album_request_pagesize_key), getResources().getInteger(R.integer.preference_album_request_pagesize_default));

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
            if(isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            synchronized (pagesBeingLoaded) {

                if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) {
                    onGetResources((PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) response);
                    pagesBeingLoaded.remove(((PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) response).getPage());
                }
            }
        }

        public void onGetResources(final PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse response) {
            synchronized (this) {
                gallery.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
                pagesBeingLoaded.remove(response.getPage());
                viewPager.getAdapter().notifyDataSetChanged();
            }
        }
    }
}
