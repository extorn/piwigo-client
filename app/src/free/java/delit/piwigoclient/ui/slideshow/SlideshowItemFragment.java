package delit.piwigoclient.ui.slideshow;

import android.content.Context;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;


public class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {
    @Override
    protected void onSaveModelChanges(T model) {
        addActiveServiceCall(R.string.progress_resource_details_updating, PiwigoAccessService.startActionUpdateResourceInfo(model, getContext()));
    }

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
}
