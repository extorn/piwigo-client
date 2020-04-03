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

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Set;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.TouchObservingRelativeLayout;
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
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.SlideshowItemPageFinished;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment<T extends Identifiable & Parcelable & PhotoContainer> extends AbstractSlideshowFragment<T> {

    private static final String TAG = "SlideshowFragment";
    private SlideshowDriver currentSlideshowDriver;

    public static <S extends Identifiable & Parcelable & PhotoContainer> SlideshowFragment<S> newInstance(Class<ViewModelContainer> modelType, ResourceContainer<S, GalleryItem> gallery, GalleryItem currentGalleryItem) {
        SlideshowFragment<S> fragment = new SlideshowFragment<>();
        fragment.setArguments(buildArgs(modelType, gallery, currentGalleryItem));
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        getUiHelper().showUserHint(TAG, 1, R.string.hint_slideshow_view_3);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        ((TouchObservingRelativeLayout)view).setTouchObserver(ev -> {
            if(currentSlideshowDriver != null) {
                getUiHelper().showDetailedMsg(R.string.alert_information, R.string.slideshow_auto_drive_paused);
                currentSlideshowDriver.cancel();
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

    private void reloadTagSlideshowModel(Tag tag, String preferredAlbumThumbnailSize) {
        UIHelper.Action action = new UIHelper.Action<FragmentUIHelper<AbstractSlideshowFragment>,
                AbstractSlideshowFragment, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse>() {

            @Override
            public boolean onSuccess(FragmentUIHelper<AbstractSlideshowFragment> uiHelper, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
                boolean updated = false;
                for(Tag t : response.getTags()) {
                    if (t.getId() == getResourceContainer().getId()) {
                        // tag has been located!
                        setContainerDetails((ResourceContainer<T, GalleryItem>) new PiwigoTag(t));
                        updated = true;
                    }
                }
                if(!updated) {
                    //Something wierd is going on - this should never happen
                    Crashlytics.log(Log.ERROR, getTag(), "Closing tag slideshow - tag was not available after refreshing session");
                    getParentFragmentManager().popBackStack();
                    return false;
                }
                loadMoreGalleryResources();
                return false;
            }

            @Override
            public boolean onFailure(FragmentUIHelper<AbstractSlideshowFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
                getParentFragmentManager().popBackStack();
                return false;
            }
        };

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
    protected long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> container, String sortOrder, int pageToLoad, int pageSize, Set<String> multimediaExtensionList) {
        T containerDetails = container.getContainerDetails();
        long loadingMessageId;
        if(containerDetails instanceof CategoryItem) {
            loadingMessageId = new ImagesGetResponseHandler((CategoryItem) containerDetails, sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
        } else if(containerDetails instanceof Tag) {
            loadingMessageId = new TagGetImagesResponseHandler((Tag) containerDetails, sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
        } else if(container instanceof PiwigoFavorites) {
            // not sure which of these blocks is irrelevant if either!
            if(container.getImgResourceCount() > 0) {
                // no need to load the images as already loaded.
                loadingMessageId = 0;
            } else {
                //TODO maybe this occurs when the favorites are not available as the page has been reopened after closing the app. Maybe need to reload the favorites here...
                loadingMessageId = new FavoritesGetImagesResponseHandler(sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
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
            GalleryItem item = getGalleryItemAdapter().getItemByPagerPosition(getViewPager().getCurrentItem());
            int moveToItem = getViewPager().getCurrentItem() + 1;
            int items = getGalleryItemAdapter().getCount();
            if(items > moveToItem) {
                currentSlideshowDriver = new SlideshowDriver(moveToItem);
                if (item instanceof VideoResourceItem) {
                    DisplayUtils.runOnUiThread(currentSlideshowDriver, AlbumViewPreferences.getAutoDriveVideoDelayMillis(prefs, requireContext()));
                } else {
                    DisplayUtils.runOnUiThread(currentSlideshowDriver, AlbumViewPreferences.getAutoDriveDelayMillis(prefs, requireContext()));
                }
            }
        }
    }

    private class SlideshowDriver implements Runnable {

        private final int moveToPage;
        private boolean cancelled;

        public SlideshowDriver(int moveToPage) {
            this.moveToPage = moveToPage;
        }

        @Override
        public void run() {
            if(!cancelled) {
                getViewPager().setCurrentItem(moveToPage);
            }
        }

        public void cancel() {
            cancelled = true;
        }
    }
}
