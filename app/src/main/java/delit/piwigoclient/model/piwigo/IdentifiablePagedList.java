package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by gareth on 02/01/18.
 */

public abstract class IdentifiablePagedList<T extends Identifiable&Parcelable> extends PagedList<T> {

    public IdentifiablePagedList(String itemType) {
        super(itemType);
    }

    public IdentifiablePagedList(String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
    }

    public IdentifiablePagedList(Parcel in) {
        super(in);
    }

    @Override
    public Long getItemId(T item) {
        return item.getId();
    }

    public long findAnIdNotYetPresentInTheList() {
        if(getItemCount() == 0) {
            return -1;
        }
        List<T> sortedItems = new ArrayList<>(getItems());
        Collections.sort(sortedItems, (o1, o2) -> {
            long x = o1.getId();
            long y = o2.getId();
            return Long.compare(x, y);
        });
        long id = sortedItems.get(0).getId() - 1;
        if(id > 0) {
            id = -1;
        }
        return id;
    }
}
