package delit.piwigoclient.ui.events;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * Created by gareth on 02/10/17.
 */

public class AlbumItemSelectedEvent {
    private final Class<ViewModelContainer> modelType;
    private final ResourceContainer album;
    private final GalleryItem selectedItem;

    public AlbumItemSelectedEvent(Class<ViewModelContainer> modelType, ResourceContainer<?, GalleryItem> album, GalleryItem selectedItem) {
        this.album = album;
        this.selectedItem = selectedItem;
        this.modelType = modelType;
    }

    public ResourceContainer<?, GalleryItem> getResourceContainer() {
        return album;
    }

    public GalleryItem getSelectedItem() {
        return selectedItem;
    }

    public Class<ViewModelContainer> getModelType() {
        return modelType;
    }
}
