package delit.piwigoclient.util;

import java.util.ArrayList;
import java.util.List;

public class ArrayUtils {
    public static long[] getLongArray(String[] values) {
        long[] retVal = new long[values.length];
        for(int i = 0; i < values.length; i++) {
            retVal[i] = Long.valueOf(values[i]);
        }
        return retVal;
    }

    public static List<Long> toList(long[] values) {
        List<Long> retVal = new ArrayList<>(values.length);
        for(int i = 0; i < values.length; i++) {
            retVal.add(values[i]);
        }
        return retVal;
    }

    public static Integer[] wrap(int[] values) {
        if(values == null) {
            return null;
        }
        Integer[] retVal = new Integer[values.length];
        for(int i = 0; i < values.length; i++) {
            retVal[i] = values[i];
        }
        return retVal;
    }

    public static long[] getLongArray(int[] intArray) {
        long[] retVal = new long[intArray.length];
        for(int i = 0; i < intArray.length; i++) {
            retVal[i] = intArray[i];
        }
        return retVal;
    }
}
