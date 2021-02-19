package delit.piwigoclient.model.piwigo;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by gareth on 08/04/18.
 */

public interface ItemStore<T> {
    T getItemByIdx(int idx);

    ArrayList<T> getItems();

    int getItemCount();

    void addItem(T item);

//    void add(int insertAtIdx, T newItem);

    int getItemIdx(T newTag);

    boolean removeAllByEquality(Collection<T> itemsForDeletion);

    boolean replace(T item, T newItem);

    T remove(int idx);

    boolean removeByEquality(T item);

    boolean remove(T r);
}
