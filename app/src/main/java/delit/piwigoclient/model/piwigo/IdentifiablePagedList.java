package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

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
