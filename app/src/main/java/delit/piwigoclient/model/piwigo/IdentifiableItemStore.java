package delit.piwigoclient.model.piwigo;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by gareth on 08/04/18.
 */

public interface IdentifiableItemStore<T> {
    T getItemByIdx(int idx);

    ArrayList<T> getItems();

    int getItemCount();

    T getItemById(long selectedItemId);

    void addItem(T item);

    int getItemIdx(T newTag);

    boolean removeAll(Collection<T> itemsForDeletion);

    void remove(T r);
}
