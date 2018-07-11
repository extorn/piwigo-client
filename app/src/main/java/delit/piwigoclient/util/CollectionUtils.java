package delit.piwigoclient.util;

import java.util.ArrayList;
import java.util.Collection;

public class CollectionUtils {

    private CollectionUtils() {}

    public static String toCsvList(Collection<? extends Number> items) {

        if(items == null || items.size() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for(Number item : items) {
            sb.append(item);
            sb.append(',');
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public static ArrayList<Integer> integersFromCsvList(String csvValue) {
        ArrayList<Integer> value = new ArrayList<>();
        if(csvValue == null) {
            return value;
        }
        String[] strValues = csvValue.split(",");
        for(String val : strValues) {
            value.add(Integer.valueOf(val));
        }
        return value;
    }
}
