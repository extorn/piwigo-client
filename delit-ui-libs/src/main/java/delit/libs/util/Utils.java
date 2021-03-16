package delit.libs.util;

import android.os.Bundle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import delit.libs.core.util.Logging;

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

    public static boolean doubleEquals(double a, double b, double epsilon) {
        return Math.abs(a - b) < epsilon;
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
                    if (f.getType() == String.class && Modifier.isStatic(f.getModifiers())) {
                        if(!Modifier.isFinal(f.getModifiers())) {
                            Bundle b = new Bundle();
                            b.putString("classname", objectClass.toString());
                            Logging.logAnalyticEventIfPossible("ClassFieldTagNotFinal", b);
                        }
                        return (String) f.get(object) + '(' + object.getClass() +')';
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // do nothing.
                }
                objectClass = objectClass.getSuperclass();
            }
            return object.getClass().toString();
        } else {
            return "?";
        }
    }

    public static String safeToString(byte[] data) {
        int len = Math.min(data.length, 1000);
        if(len < data.length) {
            data[len - 1] = '.';
            data[len - 2] = '.';
            data[len - 3] = '.';
        }
        return new String(data, 0, len);
    }

    public static long longFractionOf(long total, int numerator, int divisor) {
        return (int)Math.round(((double)(total / divisor)) * numerator);
    }

    public static int intFractionOf(int total, int numerator, int divisor) {
        return (int)Math.round(((double)(total / divisor)) * numerator);
    }
}
