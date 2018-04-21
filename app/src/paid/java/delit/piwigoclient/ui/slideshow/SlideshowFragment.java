package delit.piwigoclient.ui.slideshow;

import android.content.Context;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.TagGetImagesResponseHandler;
import delit.piwigoclient.ui.events.TagAlteredEvent;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment<T extends Identifiable> extends AbstractSlideshowFragment<T> {

    public static <S extends Identifiable> SlideshowFragment<S> newInstance(ResourceContainer<S> gallery, GalleryItem currentGalleryItem) {
        SlideshowFragment<S> fragment = new SlideshowFragment<S>();
        fragment.setArguments(buildArgs(gallery, currentGalleryItem));
        return fragment;
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
    protected long invokeResourcePageLoader(ResourceContainer<T> container, String sortOrder, int pageToLoad, int pageSize, String multimediaExtensionList) {
        T containerDetails = container.getContainerDetails();
        long loadingMessageId;
        if(containerDetails instanceof CategoryItem) {
            loadingMessageId = new ImagesGetResponseHandler((CategoryItem) containerDetails, sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
        } else if(containerDetails instanceof Tag) {
            loadingMessageId = new TagGetImagesResponseHandler((Tag) containerDetails, sortOrder, pageToLoad, pageSize, getContext(), multimediaExtensionList).invokeAsync(getContext());
        } else {
            throw new IllegalArgumentException("unsupported container type : " + container);
        }
        return loadingMessageId;
    }

    @Subscribe
    public void onEvent(TagAlteredEvent tagAlteredEvent) {
        ResourceContainer<T> gallery = getGallery();
        if(gallery instanceof PiwigoTag && gallery.getId() == tagAlteredEvent.id) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_slideshow_out_of_sync));
        }
    }
}
