package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 07/07/17.
 */

public class TagSelectionNeededEvent extends LongSetSelectionNeededEvent {

    private HashSet<Tag> newUnsavedTags;

    public TagSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, boolean initialSelectionLocked, HashSet<Long> initialSelection) {
        super(allowMultiSelect, allowEditing, initialSelectionLocked, initialSelection);
    }

    public HashSet<Tag> getNewUnsavedTags() {
        return newUnsavedTags;
    }

    public void setNewUnsavedTags(HashSet<Tag> newUnsavedTags) {
        this.newUnsavedTags = newUnsavedTags;
    }
}
