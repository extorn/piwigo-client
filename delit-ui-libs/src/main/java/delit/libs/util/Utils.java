package delit.libs.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class Utils {

    private Utils() {
    }

    /**
     * Will throw AssertionError, if expression is not true
     *
     * @param expression    result of your asserted condition
     * @param failedMessage message to be included in error log
     * @throws java.lang.AssertionError
     */
    public static void asserts(final boolean expression, final String failedMessage) {
        if (!expression) {
            throw new AssertionError(failedMessage);
        }
    }

    /**
     * Will throw IllegalArgumentException if provided object is null on runtime
     *
     * @param argument object that should be asserted as not null
     * @param name     name of the object asserted
     * @throws java.lang.IllegalArgumentException
     */
    public static <T> T notNull(final T argument, final String name) {
        if (argument == null) {
            throw new IllegalArgumentException(name + " should not be null!");
        }
        return argument;
    }

    public static String getId(Object object) {
        if(object != null) {
            Class<?> objectClass = object.getClass();
            while (!Object.class.equals(objectClass)){
                try {
                    Field f = objectClass.getDeclaredField("TAG");
                    f.setAccessible(true);
                    if (f.getType() == String.class && Modifier.isFinal(f.getModifiers())) {
                        return (String) f.get(object);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    objectClass = objectClass.getSuperclass();
                }
            }
            return object.toString();
        } else {
            return "?";
        }
    }
}
