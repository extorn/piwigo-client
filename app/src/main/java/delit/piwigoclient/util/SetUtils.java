package delit.piwigoclient.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by gareth on 03/10/17.
 */

public class SetUtils {
    public static HashSet<Long> asSet(long[] itemsArr) {
        HashSet<Long> items = new HashSet<>(itemsArr != null ? itemsArr.length : 0);
        if(itemsArr != null) {
            for(long id : itemsArr) {
                items.add(id);
            }
        }
        return items;
    }

    /**
     * returns all items in master set but not in the comparison set.
     *
     * @param masterSet
     * @param comparisionSet
     * @param <T>
     * @return
     */
    public static <T> HashSet<T> difference(Set<T> masterSet, Set<T> comparisionSet) {
        HashSet<T> difference = new HashSet<>();
        for(T item : masterSet) {
            if(!comparisionSet.contains(item)) {
                difference.add(item);
            }
        }
        return difference;
    }

    public static long[] asArray(Collection<Long> items) {
        long[] arr = new long[items.size()];
        int i = 0;
        for(Long id : items) {
            arr[i++] = id.longValue();
        }
        return arr;
    }

    public static <T extends Collection<?>, S extends Collection<?>> boolean equals(T collectionA, S collectionB) {
        return collectionA.size() == collectionB.size()
                && collectionA.containsAll(collectionB)
                && collectionB.containsAll(collectionA);
    }
}
