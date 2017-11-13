package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 26/09/17.
 */

public class AlbumSelectionNeededEvent extends LongSetSelectionNeededEvent {
    public AlbumSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection) {
        super(allowMultiSelect, allowEditing, currentSelection);
    }
}
