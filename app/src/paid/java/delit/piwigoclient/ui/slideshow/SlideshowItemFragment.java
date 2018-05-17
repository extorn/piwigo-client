package delit.piwigoclient.ui.slideshow;

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
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.util.SetUtils;


public abstract class SlideshowItemFragment<T extends ResourceItem> extends AbstractSlideshowItemFragment<T> {

    private static final String STATE_UPDATED_TAGS_SET = "updatedTagSet";
    private static final String STATE_CHANGED_TAGS_SET = "changedTagSet";
    private HashSet<Tag> updatedTagsSet;
    private HashSet<TagContentAlteredEvent> changedTagsEvents;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_UPDATED_TAGS_SET, updatedTagsSet);
        outState.putSerializable(STATE_CHANGED_TAGS_SET, changedTagsEvents);
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
            EventBus.getDefault().post(new TagContentAlteredEvent(tag.getId(), -1));
        }
        super.onImageDeleted();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            //restore saved state
            updatedTagsSet = (HashSet<Tag>) savedInstanceState.getSerializable(STATE_UPDATED_TAGS_SET);
            changedTagsEvents = (HashSet<TagContentAlteredEvent>) savedInstanceState.getSerializable(STATE_CHANGED_TAGS_SET);
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
                Set<Tag> added = SetUtils.difference(newTags, currentTags);
                Set<Tag> deleted = SetUtils.difference(currentTags, newTags);
                changedTagsEvents = new HashSet<>(added.size() + deleted.size());
                for(Tag newTag : added) {
                    changedTagsEvents.add(new TagContentAlteredEvent(newTag.getId(), 1));
                }
                for(Tag newTag : deleted) {
                    changedTagsEvents.add(new TagContentAlteredEvent(newTag.getId(), -1));
                }
            }
        }
    }

    @Override
    protected void onResourceInfoAltered(final T resourceItem) {
        super.onResourceInfoAltered(resourceItem);
        if (changedTagsEvents != null) {
            // ensure all necessary tags are updated.
            for (TagContentAlteredEvent tagEvent : changedTagsEvents) {
                EventBus.getDefault().post(tagEvent);
            }
            changedTagsEvents.clear();
            changedTagsEvents = null;
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
