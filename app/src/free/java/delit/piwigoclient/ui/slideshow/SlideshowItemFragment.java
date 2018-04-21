package delit.piwigoclient.ui.slideshow;

import android.content.Context;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;


public class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {
    @Override
    protected void onSaveModelChanges(T model) {
        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler(model).invokeAsync(getContext()));
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
