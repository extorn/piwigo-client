package delit.piwigoclient.ui.slideshow;

import android.support.v4.widget.TextViewCompat;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;


public abstract class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {
    @Override
    protected void onSaveModelChanges(T model) {
        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler<T>(model, false).invokeAsync(getContext()));
    }

    protected void populateResourceExtraFields() {
        super.populateResourceExtraFields();
        tagsField.setText(R.string.paid_feature_only);
        TextViewCompat.setTextAppearance(tagsField, R.style.Custom_TextAppearance_AppCompat_Body1);
    }
}
