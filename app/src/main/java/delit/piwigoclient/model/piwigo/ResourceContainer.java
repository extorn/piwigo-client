package delit.piwigoclient.model.piwigo;

/**
 * Created by gareth on 06/04/18.
 */

public abstract class ResourceContainer<S extends Identifiable, T extends Identifiable> extends IdentifiablePagedList<T> implements Identifiable {

    private S containerDetails;

    public ResourceContainer(S containerDetails, String itemType) {
        this(containerDetails, itemType, 10);
    }

    public ResourceContainer(S containerDetails, String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
        this.containerDetails = containerDetails;
    }

    public ResourceItem getResourceItemById(long itemId) {
        T item = getItemById(itemId);
        if(item instanceof ResourceItem) {
            return (ResourceItem) item;
        }
        throw new RuntimeException("Item is present, but is not an album resource, is a " + item.getClass().getName());
    }

    public int getResourcesCount() {
        return getItemCount();
    }

    public abstract int getImgResourceCount();

    public S getContainerDetails() {
        return containerDetails;
    }

    @Override
    public long getId() {
        return containerDetails.getId();
    }

    public void setContainerDetails(S item) {
        containerDetails = item;
    }
}
