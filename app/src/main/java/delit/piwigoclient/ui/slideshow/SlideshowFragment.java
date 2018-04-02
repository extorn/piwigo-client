package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomViewPager;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.MyFragmentStatePagerAdapter;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.events.SlideshowEmptyEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment extends MyFragment {

    private static final String STATE_GALLERY = "gallery";
    private static final String STATE_GALLERY_ITEM_DISPLAYED = "galleryIndexOfItemToDisplay";
    private CustomViewPager viewPager;
    private PiwigoAlbum gallery;
    private int rawCurrentGalleryItemPosition;


    public static SlideshowFragment newInstance(PiwigoAlbum gallery, GalleryItem currentGalleryItem) {
        SlideshowFragment fragment = new SlideshowFragment();
        Bundle args = new Bundle();
        args.putSerializable(STATE_GALLERY, gallery);
        args.putInt(STATE_GALLERY_ITEM_DISPLAYED, gallery.getItems().indexOf(currentGalleryItem));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_GALLERY, gallery);
        outState.putInt(STATE_GALLERY_ITEM_DISPLAYED, rawCurrentGalleryItemPosition);
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

        AdView adView = view.findViewById(R.id.slideshow_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()
                && getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(VISIBLE);
        } else {
            adView.setVisibility(GONE);
        }

        return view;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoSessionTokenUseNotificationEvent event) {
        updateActiveSessionDetails();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        if(view == null) {
            // exiting this screen
            return;
        }

        super.onViewCreated(view, savedInstanceState);
        Bundle configurationBundle = savedInstanceState;
        if(configurationBundle == null) {
            configurationBundle = getArguments();
        }
        if (configurationBundle != null) {
            gallery = (PiwigoAlbum) configurationBundle.getSerializable(STATE_GALLERY);
            rawCurrentGalleryItemPosition = configurationBundle.getInt(STATE_GALLERY_ITEM_DISPLAYED);
        }

        if(viewPager != null && isSessionDetailsChanged()) {
            // If the page has been initialised already (not first visit), and the session token has changed, force reload.
            getFragmentManager().popBackStack();
            return;
        }

        viewPager = view.findViewById(R.id.slideshow_viewpager);
        boolean shouldShowVideos = prefs.getBoolean(getString(R.string.preference_gallery_include_videos_in_slideshow_key), getResources().getBoolean(R.bool.preference_gallery_include_videos_in_slideshow_default));
        shouldShowVideos &= prefs.getBoolean(getString(R.string.preference_gallery_enable_video_playback_key), getResources().getBoolean(R.bool.preference_gallery_enable_video_playback_default));
        GalleryItemAdapter galleryItemAdapter = new GalleryItemAdapter(viewPager, shouldShowVideos, rawCurrentGalleryItemPosition, getChildFragmentManager());
        galleryItemAdapter.setMaxFragmentsToSaveInState(0);

        viewPager.setAdapter(galleryItemAdapter);

        ViewPager.OnPageChangeListener slideshowPageChangeListener = new ViewPager.OnPageChangeListener() {

            int lastPage = -1;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
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

        int slideshowIndexToShow = ((GalleryItemAdapter) viewPager.getAdapter()).getSlideshowIndex(rawCurrentGalleryItemPosition);
        viewPager.setCurrentItem(slideshowIndexToShow);
//        if (slideshowIndexToShow == 0) {
//            //force call of page change listener to ensure video is started properly.
//            slideshowPageChangeListener.onPageSelected(0);
//        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroyView() {
        if(viewPager != null && viewPager.getAdapter() != null) {
            ((GalleryItemAdapter) viewPager.getAdapter()).tidyUpAllVideoResources();
        }
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
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
        if(event.getItem().getParentId().equals(gallery.getId()) && getUiHelper().isTrackingRequest(event.getActionId())) {
            viewPager.setEnabled(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemDeletedEvent event) {
        int fullGalleryIdx = gallery.getItems().indexOf(event.item);
        ((GalleryItemAdapter) viewPager.getAdapter()).deleteGalleryItem(fullGalleryIdx);
        if(viewPager.getAdapter().getCount() == 0) {
            EventBus.getDefault().post(new SlideshowEmptyEvent());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(viewPager != null && viewPager.getAdapter() != null) {
            ((GalleryItemAdapter) viewPager.getAdapter()).onResume();
        }
    }

    class GalleryItemAdapter extends MyFragmentStatePagerAdapter {

        private List<Integer> galleryResourceItems;
        private ViewPager pagerComponent;

        public GalleryItemAdapter(ViewPager pagerComponent, boolean shouldShowVideos, int selectedItem, FragmentManager fm) {
            super(fm);
            this.pagerComponent = pagerComponent;
            galleryResourceItems = new ArrayList<>(gallery.getItems().size());
            for (int i = 0; i < gallery.getItems().size(); i++) {
                if (gallery.getItems().get(i) instanceof CategoryItem) {
                    continue;
                }
                if (!shouldShowVideos && gallery.getItems().get(i) instanceof VideoResourceItem && i != selectedItem) {
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
            int fullGalleryIdx = gallery.getItems().indexOf(model);
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

        @Override
        public Fragment createNewItem(int position) {

            GalleryItem galleryItem = gallery.getItems().get(galleryResourceItems.get(position));
            SlideshowItemFragment fragment = null;
            if (galleryItem instanceof PictureResourceItem) {
                fragment = AlbumPictureItemFragment.newInstance((PictureResourceItem) galleryItem);
            } else if (galleryItem instanceof VideoResourceItem) {
                boolean startOnResume = false;
                fragment = AlbumVideoItemFragment.newInstance((VideoResourceItem) galleryItem, startOnResume);
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
            List<Fragment> managedFragments = getManagedFragments();
            if(managedFragments.size() > position) {
                // if this slideshow item still exists (not been deleted by user)
                SlideshowItemFragment selectedPage = (SlideshowItemFragment)managedFragments.get(position);
                if(selectedPage != null) {
                    selectedPage.onPageDeselected();
                }
            }
        }

        public int getSlideshowIndex(int rawCurrentGalleryItemPosition) {
            int posToTry = Math.min(galleryResourceItems.size() - 1, rawCurrentGalleryItemPosition);
            Integer val;
            boolean found = false;
            do {
                val = galleryResourceItems.get(posToTry);
                if (val != rawCurrentGalleryItemPosition) {
                    posToTry--;
                    if (posToTry < 0) {
                        throw new RuntimeException("Item to show was not found in the gallery - weird!");
                    }
                } else {
                    found = true;
                }
            } while (!found);
            return posToTry;
        }

        public void tidyUpAllVideoResources() {
            AlbumVideoItemFragment lastFragment = null;
            for(Fragment f : getManagedFragments()) {
                if (f instanceof AlbumVideoItemFragment) {
                    lastFragment = (AlbumVideoItemFragment)f;
                    lastFragment.cleanupVideoResources();
                }
            }
            if(lastFragment != null) {
                lastFragment.manageCache();
            }
        }

        public void deleteGalleryItem(int fullGalleryIdx) {
            int positionToDelete = galleryResourceItems.indexOf(fullGalleryIdx);
            if(positionToDelete >= 0) {
                galleryResourceItems.remove(positionToDelete);
                notifyDataSetChanged();
            }
        }

        public void onResume() {
            int pageToShow = Math.max(0, viewPager.getCurrentItem());
            if(pageToShow < galleryResourceItems.size() && pageToShow >= 0) {
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
    }

    @Subscribe
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if(gallery.getId() == albumAlteredEvent.id) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_slideshow_out_of_sync));
        }
    }
}
