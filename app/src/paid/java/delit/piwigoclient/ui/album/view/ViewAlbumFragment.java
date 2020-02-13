package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.events.TagContentAlteredEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;

public class ViewAlbumFragment extends AbstractViewAlbumFragment {
    private static final String STATE_TAG_MEMBERSHIP_CHANGES_ACTION_PENDING = "tagMembershipChangesAction";
    AddTagsToResourcesAction tagMembershipChangesAction;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_TAG_MEMBERSHIP_CHANGES_ACTION_PENDING, tagMembershipChangesAction);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            tagMembershipChangesAction = savedInstanceState.getParcelable(STATE_TAG_MEMBERSHIP_CHANGES_ACTION_PENDING);
        }
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    protected AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {
        AlbumItemRecyclerViewAdapterPreferences prefs = super.updateViewPrefs();
        return prefs;
    }

    @Override
    protected boolean isPreventItemSelection() {
        return super.isPreventItemSelection() && !isTagSelectionAllowed();
    }

    @Override
    protected void setupBulkActionsControls(Basket basket) {
        super.setupBulkActionsControls(basket);


        if (showBulkTagAction(basket)) {
            bulkActionButtonTag.show();
        } else {
            bulkActionButtonTag.hide();
        }
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

    private boolean showBulkTagAction(Basket basket) {
        return isTagSelectionAllowed() && viewAdapter != null && viewAdapter.isItemSelectionAllowed() && getSelectedItems().size() > 0 && basket.isEmpty();
    }

    protected void updateBasketDisplay(Basket basket) {
        super.updateBasketDisplay(basket);

        if (!isAlbumDataLoading()) {
            // if gallery is dirty, then the album contents are being reloaded and won't yet be available. This method is recalled once it is
            if (showBulkTagAction(basket)) {
                bulkActionButtonTag.show();
            } else {
                bulkActionButtonTag.hide();
            }
        }
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
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean tagsCanBeDeselected = !sessionDetails.isUseUserTagPluginForUpdate();
        TagSelectionNeededEvent tagSelectEvent = new TagSelectionNeededEvent(true, isTagSelectionAllowed(), tagsCanBeDeselected, null);
        getUiHelper().setTrackingRequest(tagSelectEvent.getActionId());
        EventBus.getDefault().post(tagSelectEvent);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(TagSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            viewAdapter.toggleItemSelection();
            tagMembershipChangesAction.setTagsToAdd(event.getSelectedItems());
            getResourceInfo(tagMembershipChangesAction.selectedResources);
        }
    }

    @Override
    protected void onPiwigoUpdateResourceInfoResponse(BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse response) {
        if(tagMembershipChangesAction != null) {
        tagMembershipChangesAction.recordTagListUpdated(response.getPiwigoResource());
            // changes made.
            HashSet<Tag> tagsToAdd = tagMembershipChangesAction.getTagsToAdd();
            if (tagsToAdd != null) {
                for (Tag t : tagsToAdd) {
                    int newTagMembers = Collections.frequency(tagMembershipChangesAction.getTagUpdateEvents(), t);
                    if (newTagMembers > 0) {
                        EventBus.getDefault().post(new TagContentAlteredEvent(t.getId(), newTagMembers));
                    }
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
    protected void onResourceInfoRetrieved(BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse response) {
        if(tagMembershipChangesAction != null) {
            if(tagMembershipChangesAction.addResourceReadyToProcess(response.getResource())) {
                // action is ready for the next step.
                tagMembershipChangesAction.makeChangesToLocalResources();
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                boolean allowTagEdit = !isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();

                for(ResourceItem item : tagMembershipChangesAction.resourcesReadyToProcess) {
                    if (allowTagEdit) {
                        addActiveServiceCall(R.string.progress_resource_details_updating, new PluginUserTagsUpdateResourceTagsListResponseHandler(item));
                    } else {
                        addActiveServiceCall(R.string.progress_resource_details_updating, new ImageUpdateInfoResponseHandler(item, true));
                    }
                }
            }
        }
        super.onResourceInfoRetrieved(response);
    }

    private void getResourceInfo(HashSet<ResourceItem> selectedResources) {
        Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, getContext());
        for(ResourceItem item : selectedResources) {
            addActiveServiceCall(R.string.progress_resource_details_updating, new ImageGetInfoResponseHandler(item, multimediaExtensionList));
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new ViewAlbumPiwigoResponseListener();
    }

    protected class ViewAlbumPiwigoResponseListener extends CustomPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            super.onAfterHandlePiwigoResponse(response);
        }
    }

    private static class AddTagsToResourcesAction implements Parcelable {
        private final HashSet<ResourceItem> selectedResources;
        private final HashSet<ResourceItem> resourcesReadyToProcess;
        private HashMap<ResourceItem,ArrayList<Tag>> tagMembershipChangesPending;
        private ArrayList<Tag> tagUpdateEvents; // Will contain duplicates which we'll use to create a tally later.
        private HashSet<Tag> tagsToAdd;

        public AddTagsToResourcesAction(Set<ResourceItem> selectedItems) {
            selectedResources = new HashSet<>(selectedItems);
            resourcesReadyToProcess = new HashSet<>(selectedItems.size());
        }

        public AddTagsToResourcesAction(Parcel in) {
            selectedResources = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            resourcesReadyToProcess = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            tagMembershipChangesPending = ParcelUtils.readMap(in, getClass().getClassLoader());
            tagUpdateEvents = ParcelUtils.readArrayList(in, Tag.class.getClassLoader());
            tagsToAdd = ParcelUtils.readHashSet(in, getClass().getClassLoader());
        }

        public static final Creator<AddTagsToResourcesAction> CREATOR = new Creator<AddTagsToResourcesAction>() {
            @Override
            public AddTagsToResourcesAction createFromParcel(Parcel in) {
                return new AddTagsToResourcesAction(in);
            }

            @Override
            public AddTagsToResourcesAction[] newArray(int size) {
                return new AddTagsToResourcesAction[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeSet(dest, selectedResources);
            ParcelUtils.writeSet(dest,resourcesReadyToProcess);
            ParcelUtils.writeMap(dest, tagMembershipChangesPending);
            ParcelUtils.writeArrayList(dest, tagUpdateEvents);
            ParcelUtils.writeSet(dest, tagsToAdd);
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
            if(tagsToAdd != null) {
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
            }
            // remove all those resources for which no change will be made.
            Set<ResourceItem> resourcesWithoutChange = SetUtils.difference(resourcesReadyToProcess, tagMembershipChangesPending.keySet());
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

        @Override
        public int describeContents() {
            return 0;
        }

    }
}
