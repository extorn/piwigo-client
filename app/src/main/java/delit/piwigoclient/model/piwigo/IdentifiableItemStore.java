package delit.piwigoclient.model.piwigo;

import java.util.ArrayList;

/**
 * Created by gareth on 08/04/18.
 */

public interface IdentifiableItemStore<T> {
    T getItemByIdx(int idx);

    ArrayList<T> getItems();

    int getItemCount();

    T getItemById(Long selectedItemId);

    void addItem(T item);

    int getItemIdx(T newTag);
}
