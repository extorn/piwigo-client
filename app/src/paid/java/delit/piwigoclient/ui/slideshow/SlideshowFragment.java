package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Parcelable;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetAdminListResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment<T extends Identifiable&Parcelable> extends AbstractSlideshowFragment<T> {

    public static <S extends Identifiable&Parcelable> SlideshowFragment<S> newInstance(ResourceContainer<S, GalleryItem> gallery, GalleryItem currentGalleryItem) {
        SlideshowFragment<S> fragment = new SlideshowFragment<>();
        fragment.setArguments(buildArgs(gallery, currentGalleryItem));
        return fragment;
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
        UIHelper.Action action = new UIHelper.Action<AbstractSlideshowFragment,TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse>() {

            @Override
            public boolean onSuccess(UIHelper uiHelper, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
                boolean updated = false;
                for(Tag t : response.getTags()) {
                    if(t.getId() == getGalleryModel().getId()) {
                        // tag has been located!
                        setContainerDetails((ResourceContainer<T, GalleryItem>) new PiwigoTag(t));
                        updated = true;
                    }
                }
                if(!updated) {
                    //Something wierd is going on - this should never happen
                    Crashlytics.log(Log.ERROR, getTag(), "Closing tag slideshow - tag was not available after refreshing session");
                    getFragmentManager().popBackStack();
                    return false;
                }
                loadMoreGalleryResources();
                return false;
            }

            @Override
            public boolean onFailure(UIHelper uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
                getFragmentManager().popBackStack();
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
    protected long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> container, String sortOrder, int pageToLoad, int pageSize, String multimediaExtensionList) {
        T containerDetails = container.getContainerDetails();
        long loadingMessageId;
        if(containerDetails instanceof CategoryItem) {
            loadingMessageId = new ImagesGetResponseHandler((CategoryItem) containerDetails, sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
        } else if(containerDetails instanceof Tag) {
            loadingMessageId = new TagGetImagesResponseHandler((Tag) containerDetails, sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
        } else {
            throw new IllegalArgumentException("unsupported container type : " + container);
        }
        return loadingMessageId;
    }

    @Subscribe
    public void onEvent(TagContentAlteredEvent tagContentAlteredEvent) {
        ResourceContainer<T,GalleryItem> gallery = getGalleryModel();
        if(gallery instanceof PiwigoTag && gallery.getId() == tagContentAlteredEvent.getId()) {
            getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_slideshow_out_of_sync_with_tag));
        }
    }
}
