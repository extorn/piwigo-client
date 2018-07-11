package delit.piwigoclient.model.piwigo;

/**
 * Created by gareth on 02/01/18.
 */

public class IdentifiablePagedList<T extends Identifiable> extends PagedList<T> {

    public IdentifiablePagedList(String itemType) {
        super(itemType);
    }

    public IdentifiablePagedList(String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
    }

    @Override
    public Long getItemId(T item) {
        return item.getId();
    }

}
