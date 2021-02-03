package delit.libs.util;

import android.util.Log;

import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;

public class ObjectUtils {

    private static final String TAG = "ObjectUtils";

    public static boolean areEqual(Object left, Object right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    public static <T extends Comparable<T>> int compare(T o1, T o2) {
        if (o1 == null) {
            return 1;
        }
        if (o2 == null) {
            return -1;
        }
        return o1.compareTo(o2);
    }

    private static int geStaticIntFieldValue(@NonNull Class<?> clazz, @NonNull String fieldName) {
        try {
            return clazz.getField(fieldName).getInt(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Logging.log(Log.ERROR, TAG, "Unable to get static field from class");
        }
        return -1;
    }

    private static String geStaticStringFieldValue(Class<?> clazz, @NonNull String fieldName) {
        try {
            return (String)clazz.getField(fieldName).get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Logging.log(Log.ERROR, TAG, "Unable to get static field from class");
        }
        return null;
    }
}
