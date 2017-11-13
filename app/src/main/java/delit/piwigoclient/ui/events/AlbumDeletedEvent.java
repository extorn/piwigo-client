package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.CategoryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumDeletedEvent {

    private final CategoryItem item;

    public AlbumDeletedEvent(final CategoryItem item) {
        this.item = item;
    }

    public CategoryItem getItem() {
        return item;
    }
}
