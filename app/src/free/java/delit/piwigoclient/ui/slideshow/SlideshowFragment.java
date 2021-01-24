package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Parcelable;

import org.greenrobot.eventbus.EventBus;

import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 14/05/17.
 */

public class SlideshowFragment<F extends SlideshowFragment<F,FUIH,T>,FUIH extends FragmentUIHelper<FUIH,F>,T extends Identifiable & Parcelable & PhotoContainer> extends AbstractSlideshowFragment<F,FUIH,T> {

    private static final String TAG = "SlideshowFragment";

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
    public void onResume() {
        super.onResume();
        getUiHelper().showUserHint(TAG, 1, R.string.hint_slideshow_free_view_1);
    }

    @Override
    protected long invokeResourcePageLoader(ResourceContainer<T, GalleryItem> container, String sortOrder, int pageToLoad, int pageSize, Set<String> multimediaExtensionList) {
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
