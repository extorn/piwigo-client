package delit.libs.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by gareth on 03/10/17.
 */

public class SetUtils {
    public static HashSet<Long> asSet(long[] itemsArr) {
        HashSet<Long> items = new HashSet<>(itemsArr != null ? itemsArr.length : 0);
        if (itemsArr != null) {
            for (long id : itemsArr) {
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
        for (T item : masterSet) {
            if (!comparisionSet.contains(item)) {
                difference.add(item);
            }
        }
        return difference;
    }

    /**
     * Returns all those items in either setA or setB but not in both
     * Empty set if there are no elements fitting the above description (including if setA or B is null)
     *
     * @param setA
     * @param setB
     * @param <T>
     * @return
     */
    public static <T> HashSet<T> differences(Set<T> setA, Set<T> setB) {
        if (setA == null) {
            if (setB == null) {
                return new HashSet<>(0);
            } else {
                return new HashSet<>(setB);
            }
        }
        if (setB == null) {
            return new HashSet<>(setA);
        }
        HashSet<T> diffs = difference(setA, setB);
        diffs.addAll(difference(setB, setA));
        return diffs;
    }

}
