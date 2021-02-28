package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.TouchObservingRelativeLayout;
import delit.libs.util.Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.model.piwigo.PiwigoFavorites;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.SlideshowItemPageFinished;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;
import delit.piwigoclient.ui.slideshow.action.ReloadTagSlideshowModelAction;
import delit.piwigoclient.ui.slideshow.action.SlideshowDriver;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment<F extends SlideshowFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends Identifiable & Parcelable & PhotoContainer> extends AbstractSlideshowFragment<F,FUIH,T> {

    private static final String TAG = "SlideshowFragment";
    private SlideshowDriver<F,FUIH> currentSlideshowDriver = new SlideshowDriver<>((F) this);

    public static <F extends SlideshowFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends Identifiable & Parcelable & PhotoContainer> F  newInstance(Class<ViewModelContainer> modelType, ResourceContainer<T, GalleryItem> gallery, GalleryItem currentGalleryItem) {
        F fragment = (F) new SlideshowFragment<>();
        fragment.setArguments(buildArgs(modelType, gallery, currentGalleryItem));
        return fragment;
    }

    @Override
    public void onResume() {
        Logging.log(Log.VERBOSE,TAG, "Resuming Slideshow : " + Utils.getId(this));
        super.onResume();
        getUiHelper().showUserHint(TAG, 1, R.string.hint_slideshow_paid_view_3);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // Add a touch observer for the whole slideshow view.
        ((TouchObservingRelativeLayout)view).setTouchObserver(ev -> {
            int currentSlideshowPage = getViewPager().getCurrentItem();
            if(currentSlideshowDriver != null && currentSlideshowDriver.isActive(currentSlideshowPage)) {
                if(AlbumViewPreferences.isAutoDriveSlideshow(getPrefs(), container.getContext())) {
                    getUiHelper().showDetailedMsg(R.string.alert_information, R.string.slideshow_auto_drive_paused);
                }
                // stop the driver from being used on this slide
                currentSlideshowDriver.cancel(currentSlideshowPage);
                if(BuildConfig.DEBUG) {
                    Log.d(TAG, "Slideshow driver cancelled for page : " + getViewPager().getCurrentItem());
                }
            }
        });
        return view;
    }

    @Override
    protected void reloadSlideshowModel(T album, String preferredAlbumThumbnailSize) {
        if(album instanceof Tag) {
            reloadTagSlideshowModel((Tag)album, preferredAlbumThumbnailSize);
        } else {
            super.reloadSlideshowModel(album, preferredAlbumThumbnailSize);
        }
    }

    @Override
    public String getLogTag() {
        return TAG;
    }

    private void reloadTagSlideshowModel(Tag tag, String preferredAlbumThumbnailSize) {
        UIHelper.Action action = new ReloadTagSlideshowModelAction<>();

        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_tags, new TagsGetAdminListResponseHandler(1, Integer.MAX_VALUE), action);
        } else {
            getUiHelper().invokeActiveServiceCall(R.string.progress_loading_tags, new TagsGetListResponseHandler(0, Integer.MAX_VALUE), action);
        }
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
    protected long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> container, String sortOrder, int pageToLoad, int pageSize) {
        T containerDetails = container.getContainerDetails();
        long loadingMessageId;
        if(containerDetails instanceof CategoryItem) {
            loadingMessageId = new AlbumGetImagesResponseHandler((CategoryItem) containerDetails, sortOrder, pageToLoad, pageSize).invokeAsync(getContext());
        } else if(containerDetails instanceof Tag) {
            loadingMessageId = new TagGetImagesResponseHandler((Tag) containerDetails, sortOrder, pageToLoad, pageSize).invokeAsync(getContext());
        } else if(container instanceof PiwigoFavorites) {
            // not sure which of these blocks is irrelevant if either!
            if(container.getImgResourceCount() > 0) {
                // no need to load the images as already loaded.
                loadingMessageId = 0;
            } else {
                //TODO maybe this occurs when the favorites are not available as the page has been reopened after closing the app. Maybe need to reload the favorites here...
                loadingMessageId = new FavoritesGetImagesResponseHandler(sortOrder, pageToLoad, pageSize).invokeAsync(getContext());
            }
        } else {
            throw new IllegalArgumentException("unsupported container type : " + container);
        }
        return loadingMessageId;
    }

    @Subscribe
    public void onEvent(TagContentAlteredEvent tagContentAlteredEvent) {
        ResourceContainer<T, GalleryItem> gallery = getResourceContainer();
        if(gallery instanceof PiwigoTag && gallery.getId() == tagContentAlteredEvent.getId()) {
            getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_slideshow_out_of_sync_with_tag));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(SlideshowItemPageFinished event) {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Handling slideshow item finished with event for item at pager index : " + event.getPagerItemIndex());
        }
        if(AlbumViewPreferences.isAutoDriveSlideshow(prefs, requireContext())) {
            int currentSlideshowPage = getViewPager().getCurrentItem();
            int moveToItem = currentSlideshowPage + 1;
            int items = getGalleryItemAdapter().getCount();
            if(items > moveToItem) {
                if(currentSlideshowDriver.isActive(currentSlideshowPage)) {
                    if(AlbumViewPreferences.isAutoDriveSlideshow(getPrefs(), requireContext())) {
                        currentSlideshowDriver.setMoveToPage(moveToItem);
                        GalleryItem item = getGalleryItemAdapter().getItemByPagerPosition(currentSlideshowPage);
                        if (item instanceof VideoResourceItem) {
                            DisplayUtils.runOnUiThread(currentSlideshowDriver, AlbumViewPreferences.getAutoDriveVideoDelayMillis(prefs, requireContext()));
                        } else {
                            DisplayUtils.runOnUiThread(currentSlideshowDriver, AlbumViewPreferences.getAutoDriveDelayMillis(prefs, requireContext()));
                        }
                    }
                } else {
//                     create a blank driver for use on the next slide (cannot be certain the existing one isn't already scheduled to run)
                    currentSlideshowDriver = new SlideshowDriver<>((F) this);
                }
            }
        }
    }

}
