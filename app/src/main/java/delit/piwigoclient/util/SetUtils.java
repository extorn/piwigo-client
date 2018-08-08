package delit.piwigoclient.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
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

    public static String[] asStringArray(Collection<String> items) {
        String[] arr = new String[items.size()];
        int i = 0;
        for (String id : items) {
            arr[i++] = id;
        }
        return arr;
    }

    public static long[] asArray(Collection<Long> items) {
        long[] arr = new long[items.size()];
        int i = 0;
        for (Long id : items) {
            arr[i++] = id;
        }
        return arr;
    }

    public static <T extends Collection<?>, S extends Collection<?>> boolean equals(T collectionA, S collectionB) {
        return collectionA.size() == collectionB.size()
                && collectionA.containsAll(collectionB)
                && collectionB.containsAll(collectionA);
    }

    public static <X extends Map<K, V>, K, V> void setNotNull(X memberSaveActionIds, Map<K, V> newValues) {
        memberSaveActionIds.clear();
        if (newValues != null) {
            memberSaveActionIds.putAll(newValues);
        }
    }

    public static <X extends Collection<T>, T> void setNotNull(X memberSaveActionIds, Collection<T> newValues) {
        memberSaveActionIds.clear();
        if (newValues != null) {
            memberSaveActionIds.addAll(newValues);
        }
    }
}
