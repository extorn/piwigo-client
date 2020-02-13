package delit.libs.util;

public class ObjectUtils {

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
}
