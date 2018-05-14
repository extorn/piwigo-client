package delit.piwigoclient.model.piwigo;

/**
 * Created by gareth on 06/04/18.
 */

public abstract class ResourceContainer<T extends Identifiable> extends IdentifiablePagedList<GalleryItem> implements Identifiable {

    private final T containerDetails;

    public ResourceContainer(T containerDetails, String itemType) {
        this(containerDetails, itemType, 10);
    }

    public ResourceContainer(T containerDetails, String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
        this.containerDetails = containerDetails;
    }

    public ResourceItem getResourceItemById(long itemId) {
        GalleryItem item = getItemById(itemId);
        if(item instanceof ResourceItem) {
            return (ResourceItem) item;
        }
        throw new RuntimeException("Item is present, but is not an album resource, is a " + item.getClass().getName());
    }

    public int getResourcesCount() {
        return getItemCount();
    }

    public abstract long getImgResourceCount();

    public T getContainerDetails() {
        return containerDetails;
    }

    @Override
    public long getId() {
        return containerDetails.getId();
    }

}
