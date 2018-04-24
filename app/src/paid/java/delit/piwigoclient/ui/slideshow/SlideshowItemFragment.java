package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import delit.piwigoclient.ui.events.TagAlteredEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.util.SetUtils;


public abstract class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {

    private static final String STATE_UPDATED_TAGS_SET = "updatedTagSet";
    private static final String STATE_CHANGED_TAGS_SET = "changedTagSet";
    private HashSet<Tag> updatedTagsSet;
    private HashSet<Tag> changedTags;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_UPDATED_TAGS_SET, updatedTagsSet);
        outState.putSerializable(STATE_CHANGED_TAGS_SET, changedTags);
    }

    @Override
    protected void onSaveModelChanges(T model) {
        boolean allowTagEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.getInstance().isUseUserTagPluginForUpdate();
        boolean allowFullEdit = !isAppInReadOnlyMode() && PiwigoSessionDetails.isAdminUser();

        if (allowTagEdit) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new PluginUserTagsUpdateResourceTagsListResponseHandler(model).invokeAsync(getContext()));
        }
        if(allowFullEdit) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler(model).invokeAsync(getContext()));
        }
    }

    @Override
    protected void onDiscardChanges() {
        updatedTagsSet = null;
        super.onDiscardChanges();
    }

    @Override
    protected HashSet<Tag> getLatestTagListForResource() {
        HashSet<Tag> currentSelection = updatedTagsSet;
        if (currentSelection == null) {
            currentSelection = super.getLatestTagListForResource();
        }
        return currentSelection;
    }

    @Override
    public void onImageDeleted() {
        for (Tag tag : getModel().getTags()) {
            EventBus.getDefault().post(new TagAlteredEvent(tag.getId()));
        }
        super.onImageDeleted();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            //restore saved state
            updatedTagsSet = (HashSet<Tag>) savedInstanceState.getSerializable(STATE_UPDATED_TAGS_SET);
            changedTags = (HashSet<Tag>) savedInstanceState.getSerializable(STATE_CHANGED_TAGS_SET);
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            updatedTagsSet = event.getSelectedItems();
            HashSet<Tag> currentTags = getModel().getTags();
            HashSet<Tag> newTags = updatedTagsSet;
            if (newTags.size() == currentTags.size() && newTags.containsAll(currentTags)) {
                // no changes
                updatedTagsSet = null;
            } else {
                changedTags = SetUtils.differences(newTags, currentTags);
            }
        }
    }

    @Override
    protected void onResourceInfoAltered(final T resourceItem) {
        super.onResourceInfoAltered(resourceItem);
        if (changedTags != null) {
            // ensure all necessary tags are updated.
            for (Tag tag : changedTags) {
                EventBus.getDefault().post(new TagAlteredEvent(tag.getId()));
            }
            changedTags = null;
        }
    }

    @Override
    protected void updateModelFromFields() {
        super.updateModelFromFields();
        if (updatedTagsSet != null) {
            getModel().setTags(updatedTagsSet);
        }
    }
}
