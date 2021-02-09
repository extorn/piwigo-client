package delit.libs.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArrayUtils {
    public static long[] getLongArray(String[] values) {
        if(values == null) {
            return null;
        }
        long[] retVal = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            retVal[i] = Long.parseLong(values[i]);
        }
        return retVal;
    }

    public static ArrayList<Long> toList(long[] values) {
        if(values == null) {
            return null;
        }
        ArrayList<Long> retVal = new ArrayList<>(values.length);
        for (long value : values) {
            retVal.add(value);
        }
        return retVal;
    }

    public static Integer[] wrap(int[] values) {
        if (values == null) {
            return null;
        }
        Integer[] retVal = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            retVal[i] = values[i];
        }
        return retVal;
    }

    public static long[] getLongArray(int[] intArray) {
        if(intArray == null) {
            return null;
        }
        long[] retVal = new long[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            retVal[i] = intArray[i];
        }
        return retVal;
    }

    public static Long[] wrap(long[] values) {
        if (values == null) {
            return null;
        }
        Long[] retVal = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            retVal[i] = values[i];
        }
        return retVal;
    }

    /**
     * Warning. If values contains a null, this will fail!
     * @param values
     * @return
     */
    public static long[] unwrapLongs(Collection<Long> values) {
        if (values == null) {
            return null;
        }
        long[] arr = new long[values.size()];
        int i = 0;
        for (Long val : values) {
            if(val == null) {
                throw new IllegalArgumentException("collection contained a null");
            }
            arr[i++] = val;
        }
        return arr;
    }

    public static int[] unwrapInts(Collection<Integer> values) {
        if (values == null) {
            return null;
        }
        int[] arr = new int[values.size()];
        int i = 0;
        for (Integer val : values) {
            if(val == null) {
                throw new IllegalArgumentException("collection contained a null");
            }
            arr[i++] = val;
        }
        return arr;
    }

    public static Map<String, String> toMap(String[] stringArray) {
        if(stringArray.length % 2 != 0) {
            throw new IllegalArgumentException("The array provided does not divide into the sizes of group requested");
        }
        Map<String, String> groups = new LinkedHashMap<>();
        for(int i = 0; i < stringArray.length;) {
            groups.put(stringArray[i++], stringArray[i++]);
        }
        return groups;
    }

    public static Map<String, List<String>> toMap(String[] stringArray, int childrenPerGroup) {
        if(stringArray.length % (1 + childrenPerGroup) != 0) {
            throw new IllegalArgumentException("The array provided does not divide into the sizes of group requested");
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for(int i = 0; i < stringArray.length;) {
            String group = stringArray[i++];
            List<String> children = new ArrayList<>(childrenPerGroup);
            for(int j = 0; j < childrenPerGroup; j++) {
                children.add(stringArray[i++]);
            }
            groups.put(group, children);
        }
        return groups;
    }
}
