package delit.piwigoclient.ui.album.view;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

public class AddTagsToResourcesAction implements Parcelable {
    private final HashSet<ResourceItem> selectedResources;
    private final HashSet<ResourceItem> resourcesReadyToProcess;
    private HashMap<ResourceItem, ArrayList<Tag>> tagMembershipChangesPending;
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

    public HashSet<ResourceItem> getResourcesReadyToProcess() {
        return resourcesReadyToProcess;
    }

    public HashSet<ResourceItem> getSelectedResources() {
        return selectedResources;
    }
}
