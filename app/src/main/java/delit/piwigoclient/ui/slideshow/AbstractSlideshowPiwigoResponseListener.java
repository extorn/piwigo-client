package delit.piwigoclient.ui.slideshow;

import android.os.Parcelable;

import java.util.ArrayList;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;

class AbstractSlideshowPiwigoResponseListener<F extends AbstractSlideshowFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends Identifiable & Parcelable & PhotoContainer> extends BasicPiwigoResponseListener<FUIH,F> {

    @Override
    public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        if (getParent().isVisible()) {
            getParent().updateActiveSessionDetails();
        }
        super.onBeforeHandlePiwigoResponse(response);
    }

    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        if (response instanceof AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) {
            onGetResources((AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) response);
        } else {
            getParent().onGetResourcesFailed(response);
        }
    }

    public void onGetResources(final AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse response) {
        ArrayList<GalleryItem> resources = response.getResources();
        getParent().onResourcesReceived(response.getPage(), response.getPageSize(), resources);

    }
}
