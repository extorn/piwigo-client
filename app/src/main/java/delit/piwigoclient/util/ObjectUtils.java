package delit.piwigoclient.util;

public class ObjectUtils {

    public static boolean areEqual(Object left, Object right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
