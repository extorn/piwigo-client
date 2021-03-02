package delit.piwigoclient.ui.slideshow.item;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.PiwigoSessionTokenUseNotificationEvent;
import delit.piwigoclient.ui.slideshow.SlideshowPiwigoResponseListener;

public class PaidSideshowPiwigoResponseListener<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends SlideshowPiwigoResponseListener<F, FUIH,T> {

    @Override
    public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        EventBus.getDefault().post(new PiwigoSessionTokenUseNotificationEvent(PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile())));
    }

    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        if (response instanceof PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) {
            if(((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response).hasError()) {
                showOrQueueMessage(R.string.alert_error, ((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response).getError());
            } else {
                getParent().onResourceTagsUpdated(((PluginUserTagsUpdateResourceTagsListResponseHandler.PiwigoUserTagsUpdateTagsListResponse) response).getPiwigoResource());
            }
            getParent().onGalleryItemActionFinished();
        } else {
            super.onAfterHandlePiwigoResponse(response);
        }
    }
}
