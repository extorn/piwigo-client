package delit.piwigoclient.ui.slideshow;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageAlterRatingResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.slideshow.item.AbstractSlideshowItemFragment;

public class SlideshowPiwigoResponseListener<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends BasicPiwigoResponseListener<FUIH,F> {

    @Override
    public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
    }


    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

        if (response instanceof ImageAlterRatingResponseHandler.PiwigoRatingAlteredResponse) {
            getParent().processModelRatings(((ImageAlterRatingResponseHandler.PiwigoRatingAlteredResponse) response).getPiwigoResource());
        } else if (response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
            getParent().onImageDeleted(((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response).getDeletedItems());
        } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
            getParent().onResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<T>) response);
        } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
            BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<T> r = ((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<T>) response);
            getParent().onResourceInfoAltered(r.getPiwigoResource());
        } else if (response instanceof AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
            getParent().onGetSubAlbumNames((AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
        } else if (response instanceof AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) {
            getParent().onAlbumThumbnailUpdated((AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) response);
        }
        getParent().onGalleryItemActionFinished();
    }
}
