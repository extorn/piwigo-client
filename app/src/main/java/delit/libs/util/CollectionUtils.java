package delit.libs.util;

import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class CollectionUtils {

    private static final String TAG = "CollUtil";

    private CollectionUtils() {
    }

    public static String toCsvList(Collection<?> items) {

        if (items == null || items.size() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        String itemVal;
        for (Object item : items) {
            itemVal = item.toString();
            sb.append(itemVal);
            if(itemVal.contains(",")) {
                Crashlytics.log(Log.ERROR, TAG, "generated CSV list is corrupt as value contained a comma: " + itemVal);
            }
            sb.append(',');
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

//    public static String toCsvList(Collection<? extends Number> items) {
//
//        if (items == null || items.size() == 0) {
//            return null;
//        }
//
//        StringBuilder sb = new StringBuilder();
//
//        for (Number item : items) {
//            sb.append(item);
//            sb.append(',');
//        }
//        sb.deleteCharAt(sb.length() - 1);
//        return sb.toString();
//    }

    public static ArrayList<Long> longsFromCsvList(String csvValue) {
        ArrayList<Long> value = new ArrayList<>();
        if (csvValue == null || csvValue.isEmpty()) {
            return value;
        }
        String[] strValues = csvValue.split(",");
        for (String val : strValues) {
            value.add(Long.valueOf(val));
        }
        return value;
    }

    public static ArrayList<Integer> integersFromCsvList(String csvValue) {
        ArrayList<Integer> value = new ArrayList<>();
        if (csvValue == null) {
            return value;
        }
        String[] strValues = csvValue.split(",");
        for (String val : strValues) {
            value.add(Integer.valueOf(val));
        }
        return value;
    }

    public static ArrayList<String> stringsFromCsvList(String csvValue) {
        ArrayList<String> value = new ArrayList<>();
        if (csvValue == null) {
            return value;
        }
        String[] strValues = csvValue.split(",");
        for (String val : strValues) {
            value.add(val);
        }
        return value;
    }

    public static String[] asStringArray(Collection<String> items) {
        String[] arr = new String[items.size()];
        int i = 0;
        for (String id : items) {
            arr[i++] = id;
        }
        return arr;
    }

    public static long[] asLongArray(Collection<Long> items) {
        long[] arr = new long[items.size()];
        int i = 0;
        for (Long id : items) {
            arr[i++] = id;
        }
        return arr;
    }

    public static int[] asIntArray(Collection<Integer> items) {
        int[] arr = new int[items.size()];
        int i = 0;
        for (Integer id : items) {
            arr[i++] = id;
        }
        return arr;
    }

    public static <T extends Collection<?>, S extends Collection<?>> boolean equals(T collectionA, S collectionB) {
        return collectionA.size() == collectionB.size()
                && collectionA.containsAll(collectionB)
                && collectionB.containsAll(collectionA);
    }

    public static <X extends Map<K, V>, K, V> void addToMapNullSafe(X memberSaveActionIds, Map<K, V> newValues) {
        memberSaveActionIds.clear();
        if (newValues != null) {
            memberSaveActionIds.putAll(newValues);
        }
    }

    public static <X extends Collection<T>, T> void addToCollectionNullSafe(X memberSaveActionIds, Collection<T> newValues) {
        memberSaveActionIds.clear();
        if (newValues != null) {
            memberSaveActionIds.addAll(newValues);
        }
    }

    public static void removeItemsNotInRhsCollection(Collection<?> items, Collection<?> controlList) {
        Iterator<?> iter = items.iterator();
        while (iter.hasNext()) {
            if (!controlList.contains(iter.next())) {
                iter.remove();
            }
        }
    }
}
