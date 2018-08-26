package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.common.collect.Sets;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.GetMethodsAvailableResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateTagsResponseHandler;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class ViewAlbumFragment extends AbstractViewAlbumFragment {
    private static final String STATE_TAG_MEMBERSHIP_CHANGES_ACTION_PENDING = "tagMembershipChangesAction";
    AddTagsToResourcesAction tagMembershipChangesAction;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_TAG_MEMBERSHIP_CHANGES_ACTION_PENDING, tagMembershipChangesAction);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            tagMembershipChangesAction = (AddTagsToResourcesAction) savedInstanceState.getSerializable(STATE_TAG_MEMBERSHIP_CHANGES_ACTION_PENDING);
        }
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    protected AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {
        AlbumItemRecyclerViewAdapterPreferences prefs = super.updateViewPrefs();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails != null && sessionDetails.isFullyLoggedIn() && !sessionDetails.isMethodsAvailableListAvailable()) {
            addActiveServiceCall(new GetMethodsAvailableResponseHandler().invokeAsync(getContext()));
        }
        return prefs;
    }

    @Override
    protected boolean isPreventItemSelection() {
        return super.isPreventItemSelection() && !isTagSelectionAllowed();
    }

    @Override
    protected void setupBulkActionsControls(Basket basket) {
        super.setupBulkActionsControls(basket);

        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_add_tag_black_24dp).into(bulkActionButtonTag);
        bulkActionButtonTag.setVisibility(isTagSelectionAllowed() && viewAdapter.isItemSelectionAllowed()?VISIBLE:GONE);
        bulkActionButtonTag.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionTagButtonPressed();
                }
                return true; // consume the event
            }
        });
    }

    protected void updateBasketDisplay(Basket basket) {
        super.updateBasketDisplay(basket);
        bulkActionButtonTag.setVisibility(isTagSelectionAllowed() && viewAdapter.isItemSelectionAllowed()?VISIBLE:GONE);
    }

    private void onBulkActionTagButtonPressed() {
        tagMembershipChangesAction = new AddTagsToResourcesAction(viewAdapter.getSelectedItems());
        onShowTagsSelection();
    }

    private boolean isTagSelectionAllowed() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(sessionDetails == null || !sessionDetails.isFullyLoggedIn() || isAppInReadOnlyMode()) {
            return false;
        }
        boolean allowAdminEdit = sessionDetails.isAdminUser();
        boolean allowUserEdit = sessionDetails.isUseUserTagPluginForUpdate();
        return allowAdminEdit || allowUserEdit;
    }

    private void onShowTagsSelection() {
        //disable tag deselection if user tags plugin is not present but allow editing if is admin user. (bug in PIWIGO API)
        TagSelectionNeededEvent tagSelectEvent = new TagSelectionNeededEvent(true, isTagSelectionAllowed(), false, null);
        getUiHelper().setTrackingRequest(tagSelectEvent.getActionId());
        EventBus.getDefault().post(tagSelectEvent);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            viewAdapter.toggleItemSelection();
            tagMembershipChangesAction.setTagsToAdd(event.getSelectedItems());
            getResourceInfo(tagMembershipChangesAction.selectedResources);
        }
    }

    @Override
    protected void onPiwigoUpdateResourceInfoResponse(PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse response) {
        if(tagMembershipChangesAction != null) {
        tagMembershipChangesAction.recordTagListUpdated(response.getPiwigoResource());
            // changes made.
            for (Tag t : tagMembershipChangesAction.getTagsToAdd()) {
                int newTagMembers = Collections.frequency(tagMembershipChangesAction.getTagUpdateEvents(), t);
                if(newTagMembers > 0) {
                    EventBus.getDefault().post(new TagContentAlteredEvent(t.getId(), newTagMembers));
                }
            }
            tagMembershipChangesAction.getTagUpdateEvents().clear();
            if(tagMembershipChangesAction.isActionComplete()) {
                tagMembershipChangesAction.reset();
                tagMembershipChangesAction = null;
            }
        } else {
            super.onPiwigoUpdateResourceInfoResponse(response);
        }
    }

    @Override
    protected void onResourceInfoRetrieved(PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse response) {
        if(tagMembershipChangesAction != null) {
            if(tagMembershipChangesAction.addResourceReadyToProcess(response.getResource())) {
                // action is ready for the next step.
                tagMembershipChangesAction.makeChangesToLocalResources();
                for(ResourceItem item : tagMembershipChangesAction.resourcesReadyToProcess) {
                    if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                        addActiveServiceCall(R.string.progress_resource_details_updating,new ImageUpdateInfoResponseHandler(item).invokeAsync(getContext()));
                    } else {
                        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateTagsResponseHandler(item).invokeAsync(getContext()));
                    }
                }
            }
        }
        super.onResourceInfoRetrieved(response);
    }

    private void getResourceInfo(HashSet<ResourceItem> selectedResources) {
        String multimediaExtensionList = prefs.getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
        for(ResourceItem item : selectedResources) {
            addActiveServiceCall(R.string.progress_resource_details_updating,new ImageGetInfoResponseHandler(item, multimediaExtensionList).invokeAsync(getContext()));
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new ViewAlbumPiwigoResponseListener();
    }

    protected class ViewAlbumPiwigoResponseListener extends CustomPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetMethodsAvailableResponse) {
                //FIXME - this is not how to prevent item selection mode from being enabled! See all uses of preventItemSelection!!!! All wrong wrong wrong
                boolean itemSelectionModeEnabled = !isPreventItemSelection();
                getViewPrefs().withAllowMultiSelect(itemSelectionModeEnabled);
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }
    }

    private class AddTagsToResourcesAction implements Serializable {
        private static final long serialVersionUID = -6944626147044296967L;
        private final HashSet<ResourceItem> selectedResources;
        private final HashSet<ResourceItem> resourcesReadyToProcess;
        private HashMap<ResourceItem,ArrayList<Tag>> tagMembershipChangesPending;
        private ArrayList<Tag> tagUpdateEvents; // Will contain duplicates which we'll use to create a tally later.
        private HashSet<Tag> tagsToAdd;

        public AddTagsToResourcesAction(Set<ResourceItem> selectedItems) {
            selectedResources = new HashSet<>(selectedItems);
            resourcesReadyToProcess = new HashSet<>(selectedItems.size());
        }

        public void setTagsToAdd(HashSet<Tag> tagsToAdd) {
            this.tagsToAdd = tagsToAdd;
        }

        public boolean addResourceReadyToProcess(ResourceItem r) {
            if(selectedResources.contains(r)) {
                resourcesReadyToProcess.add(r);
            }
            return resourcesReadyToProcess.size() == selectedResources.size();
        }

        public void makeChangesToLocalResources() {
            if(resourcesReadyToProcess.size() != selectedResources.size()) {
                throw new IllegalStateException("Unable to process. Not all resources are ready yet");
            }
            HashMap<ResourceItem, ArrayList<Tag>> tagMembershipChangesPending = new HashMap<>(resourcesReadyToProcess.size());
            for (ResourceItem r : resourcesReadyToProcess) {
                ArrayList<Tag> tagsAdded = new ArrayList<>(tagsToAdd.size());
                for (Tag t : tagsToAdd) {
                    if (r.getTags().add(t)) {
                        tagsAdded.add(t);
                    }
                }
                if (tagsAdded.size() > 0) {
                    tagMembershipChangesPending.put(r, tagsAdded);
                }
            }
            // remove all those resources for which no change will be made.
            Set<ResourceItem> resourcesWithoutChange = Sets.difference(resourcesReadyToProcess, tagMembershipChangesPending.keySet());
            resourcesReadyToProcess.removeAll(resourcesWithoutChange);
            selectedResources.removeAll(resourcesWithoutChange);
            this.tagMembershipChangesPending = tagMembershipChangesPending;
        }

        public ArrayList<Tag> getTagUpdateEvents() {
            return tagUpdateEvents;
        }

        public HashSet<Tag> getTagsToAdd() {
            return tagsToAdd;
        }

        public boolean isActionComplete() {
            return tagMembershipChangesPending.isEmpty();
        }

        public boolean recordTagListUpdated(ResourceItem resourceItem) {
            if(tagUpdateEvents == null) {
                tagUpdateEvents = new ArrayList<>();
            }
            ArrayList<Tag> changesForResourceItem = tagMembershipChangesPending.remove(resourceItem);
            if(changesForResourceItem != null) {
                tagUpdateEvents.addAll(changesForResourceItem);
            }
            return tagMembershipChangesPending.isEmpty();
        }

        public void reset() {
            selectedResources.clear();
            resourcesReadyToProcess.clear();
            tagUpdateEvents.clear();
            tagsToAdd.clear();
        }
    }
}
