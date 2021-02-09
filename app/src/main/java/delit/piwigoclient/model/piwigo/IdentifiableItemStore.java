package delit.piwigoclient.model.piwigo;

import java.util.Collection;

/**
 * Created by gareth on 08/04/18.
 */

public interface IdentifiableItemStore<T extends Identifiable> extends ItemStore<T> {

    T getItemById(long selectedItemId);

    boolean removeAllById(Collection<T> itemsForDeletion);

}
