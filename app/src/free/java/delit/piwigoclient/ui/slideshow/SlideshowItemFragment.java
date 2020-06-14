package delit.piwigoclient.ui.slideshow;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;


public abstract class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {
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
