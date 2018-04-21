package delit.piwigoclient.ui.slideshow;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment<T extends Identifiable> extends AbstractSlideshowFragment<T> {

    public static SlideshowFragment newInstance(ResourceContainer gallery, GalleryItem currentGalleryItem) {
        SlideshowFragment fragment = new SlideshowFragment();
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
        } else {
            throw new IllegalArgumentException("unsupported container type : " + container);
        }
        return loadingMessageId;
    }
}
