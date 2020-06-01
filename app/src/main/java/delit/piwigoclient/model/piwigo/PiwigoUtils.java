package delit.piwigoclient.model.piwigo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by gareth on 07/04/18.
 */

public class PiwigoUtils {

    private PiwigoUtils() {
    }

    public static <T extends Identifiable> HashSet<Long> toSetOfIds(Collection<T> identifiables) {
        if (identifiables == null) {
            return null;
        }
        HashSet<Long> ids = new HashSet<>(identifiables.size());
        for (T identifiable : identifiables) {
            ids.add(identifiable.getId());
        }

        return ids;
    }

    public static <T extends Identifiable> boolean containsItemWithId(ArrayList<T> items, long id) {
        for (T item : items) {
            if (item.getId() == id ) {
                return true;
            }
        }
        return false;
    }

    public static <T extends Identifiable> void removeAll(HashSet<T> items, HashSet<Long> itemIds) {
        for (Iterator<T> it = items.iterator(); it.hasNext(); ) {
            T r = it.next();
            if (itemIds.contains(r.getId())) {
                it.remove();
            }
        }
    }
}
