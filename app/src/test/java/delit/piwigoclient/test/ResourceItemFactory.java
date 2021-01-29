package delit.piwigoclient.test;

import java.util.Date;

import delit.piwigoclient.model.piwigo.ResourceItem;

public class ResourceItemFactory extends GalleryItemFactory {


    public ResourceItemFactory() {
        super("Resource");
    }

    public ResourceItem getNextByName() {
        return getNewGalleryItem(incrementAndGetName(), null, null);
    }

    public ResourceItem getNextByCreateDate() {
        return getNewGalleryItem(getAndIncrementAndDate(createDateCalendar));
    }

    public ResourceItem getNextByLastAlterDate() {
        return getNewGalleryItem(getAndIncrementAndDate(createDateCalendar));
    }

    public ResourceItem getNewGalleryItem(String name) {
        return getNewGalleryItem(name, null, null);
    }

    private ResourceItem getNewGalleryItem(Date createDate) {
        return getNewGalleryItem(null, createDate, null);
    }

    private ResourceItem getNewGalleryItemAlteredOn(Date lastAlterDate) {
        return getNewGalleryItem(null, null, lastAlterDate);
    }

    private ResourceItem getNewGalleryItem(String name, Date createDate, Date lastAlterDate) {
        return new ResourceItem(nextItemId++, name, null, createDate, lastAlterDate, null);
    }
}
