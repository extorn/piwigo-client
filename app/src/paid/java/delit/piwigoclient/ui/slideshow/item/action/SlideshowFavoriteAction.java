package delit.piwigoclient.ui.slideshow.item.action;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.slideshow.item.SlideshowItemFragment;

public abstract class SlideshowFavoriteAction<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem, S extends PiwigoResponseBufferingHandler.Response> extends UIHelper.Action<FUIH, F, S> {

    SlideshowFavoriteAction(){}

    protected abstract boolean getValueOnSucess();

    @Override
    public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
        getActionParent(uiHelper).onFavoriteUpdateFailed(!getValueOnSucess());
        return super.onFailure(uiHelper, response);
    }

    @Override
    public boolean onSuccess(FUIH uiHelper, S response) {
        getActionParent(uiHelper).onFavoriteUpdateSucceeded(getValueOnSucess());
        return super.onSuccess(uiHelper, response);
    }
}
