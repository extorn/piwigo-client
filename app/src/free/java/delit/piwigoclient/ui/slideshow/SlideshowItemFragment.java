package delit.piwigoclient.ui.slideshow;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;


public abstract class SlideshowItemFragment<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends AbstractSlideshowItemFragment<F,FUIH,T> {
    @Override
    protected void onSaveModelChanges(T model) {
        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler<>(model, false));
    }

    protected void populateResourceExtraFields() {
        super.populateResourceExtraFields();
        getTagsField().setText(R.string.paid_feature_only);
        getTagsField().setClickable(false);
    }
}
