package delit.piwigoclient.ui.slideshow.action;

import android.os.Parcelable;
import android.util.Log;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PhotoContainer;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.slideshow.AbstractSlideshowFragment;

public class AlbumLoadResponseAction<F extends AbstractSlideshowFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends Identifiable & Parcelable & PhotoContainer> extends UIHelper.Action<FUIH, F, AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse> {

    private static final String TAG = "AlbumLoadResponseAction";

    @Override
    public boolean onSuccess(FUIH uiHelper, AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {
        F fragment = getActionParent(uiHelper);
        if (response.getAlbums().isEmpty()) {
            // will occur if the album no longer exists.
            Logging.log(Log.INFO, TAG, "removing from activity as album no longer exists");
            fragment.getParentFragmentManager().popBackStack();
            return false;
        }
        CategoryItem currentAlbum = response.getAlbums().get(0);
        if (currentAlbum.getId() != fragment.getResourceContainer().getId()) {
            //Something wierd is going on - this should never happen
            Logging.log(Log.ERROR, TAG, "Closing slideshow - reloaded album had different id to that expected!");
            fragment.getParentFragmentManager().popBackStack();
            return false;
        }
        fragment.setContainerDetails(new PiwigoAlbum(currentAlbum));
        fragment.loadMoreGalleryResources();
        return true;
    }

    @Override
    public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
        F fragment = getActionParent(uiHelper);
        Logging.log(Log.INFO, TAG, "removing from activity after piwigo error response");
        fragment.getParentFragmentManager().popBackStack();
        return false;
    }
}
