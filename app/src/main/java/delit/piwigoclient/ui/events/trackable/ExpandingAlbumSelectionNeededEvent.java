package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 26/09/17.
 */

public class ExpandingAlbumSelectionNeededEvent extends LongSetSelectionNeededEvent {
    private final Long initialRoot;
    private String connectionProfileName;

    public ExpandingAlbumSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection, Long initialRoot) {
        super(allowMultiSelect, allowEditing, currentSelection);
        this.initialRoot = initialRoot;
    }

    public Long getInitialRoot() {
        return initialRoot;
    }

    public void setConnectionProfileName(String connectionProfileName) {
        this.connectionProfileName = connectionProfileName;
    }

    public String getConnectionProfileName() {
        return connectionProfileName;
    }
}
