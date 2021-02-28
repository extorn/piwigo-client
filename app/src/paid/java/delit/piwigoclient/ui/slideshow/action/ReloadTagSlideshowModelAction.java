package delit.piwigoclient.ui.slideshow.action;

import android.os.Parcelable;
import android.util.Log;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.model.piwigo.PiwigoTag;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.slideshow.SlideshowFragment;

public class ReloadTagSlideshowModelAction<F extends SlideshowFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends Identifiable & Parcelable & PhotoContainer> extends UIHelper.Action<FUIH,F, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse> {

    private static final String TAG = "ReloadTagSlideshowModelAction";

    @Override
    public boolean onSuccess(FUIH uiHelper, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
        boolean updated = false;
        F slideshowFragment = uiHelper.getParent();
        for(Tag t : response.getTags()) {
            if (t.getId() == slideshowFragment.getResourceContainer().getId()) {
                // tag has been located!
                slideshowFragment.setContainerDetails((ResourceContainer<T, GalleryItem>) new PiwigoTag(t));
                updated = true;
            }
        }
        if(!updated) {
            //Something wierd is going on - this should never happen
            Logging.log(Log.ERROR, slideshowFragment.getLogTag(), "Closing tag slideshow - tag was not available after refreshing session");
            slideshowFragment.getParentFragmentManager().popBackStack();
            return false;
        }
        slideshowFragment.loadMoreGalleryResources();
        return false;
    }

    @Override
    public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
        Logging.log(Log.INFO, TAG, "removing from activity on piwigo failure");
        uiHelper.getParent().getParentFragmentManager().popBackStack();
        return false;
    }
}
