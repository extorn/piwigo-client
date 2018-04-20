package delit.piwigoclient.ui.slideshow;

import android.content.Context;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;


public class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {
    @Override
    protected void onSaveModelChanges(T model) {
        boolean allowTagEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.getInstance().isUseUserTagPluginForUpdate();
        boolean allowFullEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.isAdminUser();

        if (allowTagEdit) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new PluginUserTagsUpdateResourceTagsListResponseHandler(model).invokeAsync(getContext()));
        } else if(allowFullEdit) {
            //TODO add some logic to ensure the tags are updated in the update resource info call.
        }
        if(allowFullEdit) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler(model).invokeAsync(getContext()));
        }
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            updatedTagsSet = event.getSelectedItems();
            HashSet<Tag> currentTags = model.getTags();
            HashSet<Tag> newTags = updatedTagsSet;
            if (newTags.size() == currentTags.size() && newTags.containsAll(currentTags)) {
                // no changes
                updatedTagsSet = null;
            }
        }
    }
}
