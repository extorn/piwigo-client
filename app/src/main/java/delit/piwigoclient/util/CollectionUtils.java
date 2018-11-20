package delit.piwigoclient.util;

import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.Collection;

public class CollectionUtils {

    private static final String TAG = "CollUtil";

    private CollectionUtils() {
    }

    public static String toCsvList(Collection<? extends Object> items) {

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
}
